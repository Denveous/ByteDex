package org.openmmo.bytedex.proxy.netty.patchers

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.openmmo.bytedex.proxy.netty.PacketPatcher
import org.slf4j.LoggerFactory

class GamePatcher(
    private val onChatServerDiscovered: (host: String, port: Int) -> Unit,
    private val targetChatProxyPort: Int = 7778,
) : PacketPatcher {

    private val log = LoggerFactory.getLogger("proxy.game.patcher")

    override fun patch(packet: ByteBuf, direction: PacketPatcher.Direction): ByteBuf {
        if (direction != PacketPatcher.Direction.SERVER_TO_CLIENT) return packet
        if (packet.readableBytes() < 1) return packet
        val id = packet.getByte(packet.readerIndex()).toInt() and 0xFF
        if (id != ID_CHAT_ENDPOINT) return packet
        log.info("rewriting 0xFC chat endpoint -> 127.0.0.1:{}", targetChatProxyPort)
        return runCatching { rewriteChatEndpoint(packet) }
            .onFailure { log.warn("0xFC rewrite failed: {}", it.toString()) }
            .getOrDefault(packet)
    }

    private fun rewriteChatEndpoint(packet: ByteBuf): ByteBuf {
        val src = packet.duplicate()
        val out = Unpooled.buffer(src.readableBytes() + 16)

        out.writeByte(src.readByte().toInt())
        out.writeByte(src.readByte().toInt())

        val chunkLen = src.readUnsignedByte().toInt()
        out.writeByte(chunkLen)
        out.writeBytes(src, chunkLen)

        val first = NodeListRewriter.rewrite(src, out, targetChatProxyPort)
        if (first != null) {
            log.info("discovered chat-server upstream {}:{}", first.ipv4, first.port)
            onChatServerDiscovered(first.ipv4, first.port)
        }
        return out
    }

    companion object {
        private const val ID_CHAT_ENDPOINT = 0xFC
    }
}
