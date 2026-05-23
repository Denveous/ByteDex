package org.openmmo.bytedex.proxy.netty

import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import org.openmmo.bytedex.proxy.netty.handlers.ChecksumFrameDecoder
import org.openmmo.bytedex.proxy.netty.handlers.ChecksumFrameEncoder
import org.openmmo.bytedex.proxy.netty.handlers.PacketFrameDecoder
import org.openmmo.bytedex.proxy.netty.handlers.PacketFrameEncoder
import org.openmmo.bytedex.proxy.netty.handlers.TlsDecryptionHandler
import org.openmmo.bytedex.proxy.netty.handlers.TlsEncryptionHandler
import org.openmmo.bytedex.proxy.tls.NoOpChecksum
import org.openmmo.bytedex.proxy.tls.NoOpTlsContext
import org.slf4j.LoggerFactory
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey

class ProxyServer(
    private val name: String,
    private val listenHost: String,
    private val listenPort: Int,
    initialUpstreamHost: String? = null,
    initialUpstreamPort: Int = 0,
    private val signingPrivateKey: ECPrivateKey,
    private val signingPublicKey: ECPublicKey,
    private val patcher: PacketPatcher = PacketPatcher.PASSTHROUGH,
    private val clientFacingChecksumSize: Int = 0,
    private val compressedServerToClient: Boolean = false,
    private val protocol: PacketSink.Protocol,
    private val sink: PacketSink = PacketSink.NONE,
) {
    private val log = LoggerFactory.getLogger("proxy.$name")
    private val bossGroup: EventLoopGroup = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
    private val workerGroup: EventLoopGroup = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())
    @Volatile private var bound: ChannelFuture? = null
    @Volatile private var upstreamHost: String? = initialUpstreamHost
    @Volatile private var upstreamPort: Int = initialUpstreamPort

    fun start() {
        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    accept(ch)
                }
            })

        bound = bootstrap.bind(listenHost, listenPort).sync()
        val upstream = upstreamHost?.let { "$it:$upstreamPort" } ?: "<discovered at runtime>"
        log.info("listening on {}:{} -> {}", listenHost, listenPort, upstream)
    }

    fun stop() {
        bound?.channel()?.close()?.sync()
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }

    fun setUpstream(host: String, port: Int) {
        val prev = upstreamHost?.let { "$it:$upstreamPort" } ?: "(none)"
        upstreamHost = host
        upstreamPort = port
        log.info("upstream {} -> {}:{}", prev, host, port)
    }

    private fun accept(clientChannel: SocketChannel) {
        val host = upstreamHost
        val port = upstreamPort
        if (host == null || port <= 0) {
            log.warn("no upstream configured - dropping client {}", clientChannel.remoteAddress())
            clientChannel.close()
            return
        }
        log.info("accepted {} from {}", name, clientChannel.remoteAddress())

        val session = ProxySession(
            portName = name,
            signingPrivateKey = signingPrivateKey,
            signingPublicKey = signingPublicKey,
            patcher = patcher,
            checksumSize = clientFacingChecksumSize,
            serverToClientCompressed = compressedServerToClient,
            protocol = protocol,
            sink = sink,
        )
        session.clientChannel = clientChannel

        configurePipeline(clientChannel, ClientFacingHandshakeHandler(session))

        val upstreamBootstrap = Bootstrap()
            .group(workerGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    configurePipeline(ch, UpstreamFacingHandshakeHandler(session))
                }
            })

        upstreamBootstrap.connect(host, port).addListener {
            if (!it.isSuccess) {
                log.warn("upstream {}:{} dial failed: {}", host, port, it.cause()?.toString())
                clientChannel.close()
                return@addListener
            }
            val ch = (it as ChannelFuture).channel()
            session.upstreamChannel = ch
            ch.closeFuture().addListener { clientChannel.close() }
            clientChannel.closeFuture().addListener { ch.close() }
        }
    }

    private fun configurePipeline(
        ch: SocketChannel,
        handshakeHandler: io.netty.channel.ChannelHandler,
    ) {
        ch.pipeline()
            .addLast("frame-decoder", PacketFrameDecoder())
            .addLast("frame-encoder", PacketFrameEncoder())
            .addLast("checksum-decoder", ChecksumFrameDecoder(NoOpChecksum))
            .addLast("checksum-encoder", ChecksumFrameEncoder(NoOpChecksum))
            .addLast("tls-decryption", TlsDecryptionHandler(NoOpTlsContext))
            .addLast("tls-encryption", TlsEncryptionHandler(NoOpTlsContext))
            .addLast("handshake", handshakeHandler)
    }
}
