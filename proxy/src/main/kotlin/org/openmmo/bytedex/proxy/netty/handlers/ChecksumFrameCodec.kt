package org.openmmo.bytedex.proxy.netty.handlers

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.MessageToByteEncoder
import org.openmmo.bytedex.proxy.tls.Checksum

class ChecksumFrameDecoder(private val checksum: Checksum) : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, buffer: ByteBuf, out: MutableList<Any>) {
        if (checksum.size == 0) {
            out.add(buffer.readBytes(buffer.readableBytes()))
            return
        }
        if (buffer.readableBytes() < checksum.size) return

        val payloadLen = buffer.readableBytes() - checksum.size
        val expected = ByteArray(checksum.size)
        buffer.getBytes(buffer.readerIndex() + payloadLen, expected)

        val payload = buffer.slice(buffer.readerIndex(), payloadLen)
        if (!checksum.verify(payload, expected)) {
            buffer.readerIndex(buffer.writerIndex())
            return
        }
        out.add(payload.retain())
        buffer.readerIndex(buffer.writerIndex())
    }
}

class ChecksumFrameEncoder(private val checksum: Checksum) : MessageToByteEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val dataLen = msg.readableBytes()
        val tag = if (checksum.size > 0) checksum.calculate(msg) else ByteArray(0)
        out.writeBytes(msg, msg.readerIndex(), dataLen)
        if (tag.isNotEmpty()) out.writeBytes(tag)
    }
}
