package org.openmmo.bytedex.proxy

import org.openmmo.bytedex.proxy.netty.PacketPatcher
import org.openmmo.bytedex.proxy.netty.PacketSink
import org.openmmo.bytedex.proxy.netty.ProxyServer
import org.openmmo.bytedex.proxy.netty.patchers.GamePatcher
import org.openmmo.bytedex.proxy.netty.patchers.LoginPatcher

private const val LOOPBACK = "127.0.0.1"

class Proxy(
    private val sink: PacketSink = PacketSink.NONE,
) {

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
            initialUpstreamHost = "loginserver.pokemmo.com",
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
    val proxy = Proxy()
    Runtime.getRuntime().addShutdownHook(Thread { proxy.stop() })
    proxy.start()
    Thread.currentThread().join()
}
