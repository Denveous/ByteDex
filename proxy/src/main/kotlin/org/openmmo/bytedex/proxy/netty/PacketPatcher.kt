package org.openmmo.bytedex.proxy.netty

import io.netty.buffer.ByteBuf

fun interface PacketPatcher {
    fun patch(packet: ByteBuf, direction: Direction): ByteBuf

    enum class Direction { CLIENT_TO_SERVER, SERVER_TO_CLIENT }

    companion object {
        val PASSTHROUGH = PacketPatcher { p, _ -> p }
    }
}
