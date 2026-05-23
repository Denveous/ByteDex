package org.openmmo.bytedex.proxy.netty.patchers

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.openmmo.bytedex.proxy.netty.PacketPatcher
import org.slf4j.LoggerFactory

class LoginPatcher(
    private val onGameServerDiscovered: (host: String, port: Int) -> Unit,
    private val targetGameProxyPort: Int = 7777,
) : PacketPatcher {

    private val log = LoggerFactory.getLogger("proxy.login.patcher")

    override fun patch(packet: ByteBuf, direction: PacketPatcher.Direction): ByteBuf {
        if (direction != PacketPatcher.Direction.SERVER_TO_CLIENT) return packet
        if (packet.readableBytes() < 1) return packet
        return when (val id = packet.getByte(packet.readerIndex()).toInt() and 0xFF) {
            ID_GAME_SERVER_NODES -> {
                log.info("rewriting 0x03 GameServerNodesPacket -> 127.0.0.1:{}", targetGameProxyPort)
                runCatching { rewriteGameServerNodes(packet) }
                    .onFailure { log.warn("0x03 rewrite failed: {}", it.toString()) }
                    .getOrDefault(packet)
            }
            ID_EXISTING_SESSION -> {
                log.info("rewriting 0x26 ExistingSessionPacket -> 127.0.0.1:{}", targetGameProxyPort)
                runCatching { rewriteExistingSession(packet) }
                    .onFailure { log.warn("0x26 rewrite failed: {}", it.toString()) }
                    .getOrDefault(packet)
            }
            else -> packet
        }
    }

    private fun reportDiscovered(first: NodeListRewriter.FirstUpstream?) {
        if (first != null) {
            log.info("discovered game-server upstream {}:{}", first.ipv4, first.port)
            onGameServerDiscovered(first.ipv4, first.port)
        }
    }

    private fun rewriteGameServerNodes(packet: ByteBuf): ByteBuf {
        val src = packet.duplicate()
        val out = Unpooled.buffer(src.readableBytes() + 32)

        copyByte(src, out)

        val state = src.readByte()
        out.writeByte(state.toInt())
        if (state.toInt() != STATE_AUTHED) {
            out.writeBytes(src, src.readableBytes())
            return out
        }

        copyIntLE(src, out)
        copyByteLengthPrefixedBytes(src, out)
        copyByte(src, out)

        val origLocalAddrSize = src.readUnsignedByte().toInt()
        src.skipBytes(origLocalAddrSize)
        out.writeByte(4)
        out.writeBytes(IPV4_LOCALHOST_BE)

        copyUtf16LeNullTerminated(src, out)

        src.readIntLE()
        out.writeIntLE(targetGameProxyPort)

        reportDiscovered(NodeListRewriter.rewrite(src, out, targetGameProxyPort))

        return out
    }

    private fun rewriteExistingSession(packet: ByteBuf): ByteBuf {
        val src = packet.duplicate()
        val out = Unpooled.buffer(src.readableBytes() + 32)

        copyByte(src, out) // packet id
        copyLongLE(src, out) // sessionId
        copyByteLengthPrefixedBytes(src, out) // sessionKey

        copyByte(src, out) // marker 0
        copyUtf16LeNullTerminated(src, out) // serverName

        copyByteLengthPrefixedBytes(src, out) // unk

        copyUtf16LeNullTerminated(src, out) // empty string

        copyIntLE(src, out) // 0
        copyShortLE(src, out) // 0
        copyShortLE(src, out) // 0
        copyByte(src, out) // bool

        reportDiscovered(NodeListRewriter.rewrite(src, out, targetGameProxyPort))
        return out
    }

    private fun copyByte(src: ByteBuf, out: ByteBuf) = out.writeByte(src.readByte().toInt())
    private fun copyShortLE(src: ByteBuf, out: ByteBuf) = out.writeShortLE(src.readShortLE().toInt())
    private fun copyIntLE(src: ByteBuf, out: ByteBuf) = out.writeIntLE(src.readIntLE())
    private fun copyLongLE(src: ByteBuf, out: ByteBuf) = out.writeLongLE(src.readLongLE())

    private fun copyByteLengthPrefixedBytes(src: ByteBuf, out: ByteBuf) {
        val n = src.readUnsignedByte().toInt()
        out.writeByte(n)
        out.writeBytes(src, n)
    }

    private fun copyUtf16LeNullTerminated(src: ByteBuf, out: ByteBuf) {
        while (true) {
            val ch = src.readShortLE()
            out.writeShortLE(ch.toInt())
            if (ch.toInt() == 0) return
        }
    }

    companion object {
        private const val ID_GAME_SERVER_NODES = 0x03
        private const val ID_EXISTING_SESSION = 0x26
        private const val STATE_AUTHED = 0

        private val IPV4_LOCALHOST_BE = byteArrayOf(127, 0, 0, 1)
    }
}
