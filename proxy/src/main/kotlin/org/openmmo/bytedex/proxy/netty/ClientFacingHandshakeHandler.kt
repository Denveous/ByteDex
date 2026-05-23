package org.openmmo.bytedex.proxy.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
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

class ClientFacingHandshakeHandler(
    private val session: ProxySession,
) : SimpleChannelInboundHandler<ByteBuf>() {

    private val log = LoggerFactory.getLogger("proxy.${session.portName}.client")
    private var ephemeral: KeyPair? = null

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        when (val id = HandshakePackets.consumeId(msg)) {
            HandshakePackets.ID_CLIENT_HELLO -> onClientHello(ctx, msg)
            HandshakePackets.ID_CLIENT_READY -> onClientReady(ctx, msg)
            else -> {
                ctx.close()
                error("unexpected handshake packet id=0x${id.toUByte().toString(16)}")
            }
        }
    }

    private fun onClientHello(ctx: ChannelHandlerContext, msg: ByteBuf) {
        HandshakePackets.decodeClientHello(msg)
        val keyPair = newEphemeral()
        ephemeral = keyPair
        val response = HandshakePackets.encodeServerHello(
            packet = HandshakePackets.ServerHello(
                publicKey = keyPair.public as ECPublicKey,
                signature = ByteArray(0),
                checksumSize = session.checksumSize,
            ),
            signingKey = session.signingPrivateKey,
        )
        ctx.writeAndFlush(response)
    }

    private fun onClientReady(ctx: ChannelHandlerContext, msg: ByteBuf) {
        val ready = HandshakePackets.decodeClientReady(msg)
        val priv = ephemeral!!.private as ECPrivateKey
        val tls = DefaultTlsContext(priv, ready.publicKey, role = TlsRole.SERVER)
        session.clientFacingTls = tls

        val pipeline = ctx.pipeline()
        pipeline.replace("tls-decryption", "tls-decryption", TlsDecryptionHandler(tls))
        pipeline.replace("tls-encryption", "tls-encryption", TlsEncryptionHandler(tls))

        if (session.checksumSize > 0) {
            val ckIn = ChecksumFactory.create(session.checksumSize, tls.clientSeed)
            val ckOut = ChecksumFactory.create(session.checksumSize, tls.serverSeed)
            session.clientFacingChecksumIn = ckIn
            session.clientFacingChecksumOut = ckOut
            pipeline.replace("checksum-decoder", "checksum-decoder", ChecksumFrameDecoder(ckIn))
            pipeline.replace("checksum-encoder", "checksum-encoder", ChecksumFrameEncoder(ckOut))
        }

        session.clientHandshakeDone = true
        pipeline.replace("handshake", "bridge", BridgeHandler(session, BridgeHandler.Side.CLIENT_FACING))
        log.info("client handshake done (checksumSize={})", session.checksumSize)
        session.onSideReady()
    }

    private fun newEphemeral(): KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        return gen.generateKeyPair()
    }
}
