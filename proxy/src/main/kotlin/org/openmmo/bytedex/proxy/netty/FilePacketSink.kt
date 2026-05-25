package org.openmmo.bytedex.proxy.netty

import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

class FilePacketSink(private val path: Path) : PacketSink {

    private val writer: PrintWriter

    init {
        Files.createDirectories(path.parent)
        writer = PrintWriter(
            Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND),
            true
        )
    }

    override fun accept(
        protocol: PacketSink.Protocol,
        direction: PacketSink.Direction,
        packetId: Int,
        payload: ByteArray,
        capturedAtEpochMillis: Long,
        name: String?,
    ) {
        val ts = Instant.ofEpochMilli(capturedAtEpochMillis).toString()
        val hex = payload.joinToString(" ") { "%02x".format(it) }
        val label = name?.let { " $it" } ?: ""
        writer.println("$ts | $protocol | $direction | 0x%02x$label | ${payload.size} | $hex".format(packetId))
    }

    fun close() = writer.close()
}
