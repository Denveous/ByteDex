package org.openmmo.bytedex.proxy

import org.openmmo.bytedex.proxy.netty.FilePacketSink
import org.openmmo.bytedex.proxy.netty.PacketPatcher
import org.openmmo.bytedex.proxy.netty.PacketSink
import org.openmmo.bytedex.proxy.netty.ProxyServer
import org.openmmo.bytedex.proxy.netty.patchers.GamePatcher
import org.openmmo.bytedex.proxy.netty.patchers.LoginPatcher
import java.nio.file.Path


private const val LOOPBACK = "127.0.0.1"

class Proxy(
    private val sink: PacketSink = PacketSink.NONE,
) {
    constructor(logPath: String) : this(FilePacketSink(Path.of(logPath)))

    private val servers = mutableListOf<ProxyServer>()

    fun start() {
        val chatProxy = ProxyServer(
            name = "chat",
            listenHost = LOOPBACK,
            listenPort = 7778,
            initialUpstreamHost = null,
            signingPrivateKey = ProxyKeys.cs.privateKey,
            signingPublicKey = ProxyKeys.cs.publicKey,
            patcher = PacketPatcher.PASSTHROUGH,
            protocol = PacketSink.Protocol.CHAT,
            sink = sink,
        )

        val gameProxy = ProxyServer(
            name = "game",
            listenHost = LOOPBACK,
            listenPort = 7777,
            initialUpstreamHost = null,
            signingPrivateKey = ProxyKeys.gs.privateKey,
            signingPublicKey = ProxyKeys.gs.publicKey,
            patcher = GamePatcher(
                onChatServerDiscovered = chatProxy::setUpstream,
                targetChatProxyPort = 7778,
            ),
            compressedServerToClient = true,
            protocol = PacketSink.Protocol.GAME,
            sink = sink,
        )

        val loginProxy = ProxyServer(
            name = "login",
            listenHost = LOOPBACK,
            listenPort = 2106,
            initialUpstreamHost = "185.180.13.135",
            initialUpstreamPort = 2106,
            signingPrivateKey = ProxyKeys.ls.privateKey,
            signingPublicKey = ProxyKeys.ls.publicKey,
            patcher = LoginPatcher(
                onGameServerDiscovered = gameProxy::setUpstream,
                targetGameProxyPort = 7777,
            ),
            protocol = PacketSink.Protocol.LOGIN,
            sink = sink,
        )

        servers += chatProxy
        servers += gameProxy
        servers += loginProxy
        servers.forEach { it.start() }
    }

    fun stop() {
        servers.forEach { runCatching { it.stop() } }
        servers.clear()
    }
}

fun main() {
    val ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val sink = FilePacketSink(Path.of("C:/Users/Tim/Desktop/PokeMMO/Research/logs/packets_$ts.log"))
    val proxy = Proxy(sink)
    Runtime.getRuntime().addShutdownHook(Thread { sink.close(); proxy.stop() })
    proxy.start()
    Thread.currentThread().join()
}
