package org.openmmo.bytedex.proxy.netty

fun interface PacketSink {

    enum class Protocol { LOGIN, GAME, CHAT }
    enum class Direction { C2S, S2C }

    fun accept(
        protocol: Protocol,
        direction: Direction,
        packetId: Int,
        payload: ByteArray,
        capturedAtEpochMillis: Long,
        name: String?,
    )

    companion object {
        val NONE: PacketSink = PacketSink { _, _, _, _, _, _ -> }
    }
}
