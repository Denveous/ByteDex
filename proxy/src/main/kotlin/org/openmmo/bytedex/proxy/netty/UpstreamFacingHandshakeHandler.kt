package org.openmmo.bytedex.proxy.netty

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.buffer.ByteBuf
import org.openmmo.bytedex.proxy.netty.handlers.ChecksumFrameDecoder
import org.openmmo.bytedex.proxy.netty.handlers.ChecksumFrameEncoder
import org.openmmo.bytedex.proxy.netty.handlers.TlsDecryptionHandler
import org.openmmo.bytedex.proxy.netty.handlers.TlsEncryptionHandler
import org.openmmo.bytedex.proxy.tls.ChecksumFactory
import org.openmmo.bytedex.proxy.tls.DefaultTlsContext
import org.openmmo.bytedex.proxy.tls.TlsRole
import org.slf4j.LoggerFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class UpstreamFacingHandshakeHandler(
    private val session: ProxySession,
) : SimpleChannelInboundHandler<ByteBuf>() {

    private val log = LoggerFactory.getLogger("proxy.${session.portName}.upstream")
    private var ephemeral: KeyPair? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        ephemeral = newEphemeral()
        val hello = HandshakePackets.encodeClientHello(
            HandshakePackets.ClientHello(System.currentTimeMillis())
        )
        ctx.writeAndFlush(hello)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        val id = HandshakePackets.consumeId(msg)
        if (id != HandshakePackets.ID_SERVER_HELLO) {
            ctx.close()
            error("expected ServerHello (0x01), got 0x${id.toUByte().toString(16)}")
        }
        val hello = HandshakePackets.decodeServerHello(msg)

        val ourPub = ephemeral!!.public as ECPublicKey
        ctx.writeAndFlush(HandshakePackets.encodeClientReady(HandshakePackets.ClientReady(ourPub)))

        val tls = DefaultTlsContext(
            privateKey = ephemeral!!.private as ECPrivateKey,
            peerPublicKey = hello.publicKey,
            role = TlsRole.CLIENT,
        )
        session.upstreamFacingTls = tls

        val pipeline = ctx.pipeline()
        pipeline.replace("tls-decryption", "tls-decryption", TlsDecryptionHandler(tls))
        pipeline.replace("tls-encryption", "tls-encryption", TlsEncryptionHandler(tls))

        if (hello.checksumSize > 0) {
            val ckIn = ChecksumFactory.create(hello.checksumSize, tls.serverSeed)
            val ckOut = ChecksumFactory.create(hello.checksumSize, tls.clientSeed)
            session.upstreamFacingChecksumIn = ckIn
            session.upstreamFacingChecksumOut = ckOut
            pipeline.replace("checksum-decoder", "checksum-decoder", ChecksumFrameDecoder(ckIn))
            pipeline.replace("checksum-encoder", "checksum-encoder", ChecksumFrameEncoder(ckOut))
        }

        session.upstreamHandshakeDone = true
        pipeline.replace("handshake", "bridge", BridgeHandler(session, BridgeHandler.Side.UPSTREAM_FACING))
        log.info("upstream handshake done (checksumSize={})", hello.checksumSize)
        session.onSideReady()
    }

    private fun newEphemeral(): KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        return gen.generateKeyPair()
    }
}
