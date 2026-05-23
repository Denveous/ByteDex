package org.openmmo.bytedex.proxy.netty.handlers

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.MessageToByteEncoder
import org.openmmo.bytedex.proxy.tls.TlsContext

class TlsDecryptionHandler(private val tls: TlsContext) : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, buffer: ByteBuf, out: MutableList<Any>) {
        val n = buffer.readableBytes()
        if (n == 0) return
        val cipherBytes = ByteArray(n).also { buffer.readBytes(it) }
        val plain = tls.decrypt(cipherBytes)
        out.add(ctx.alloc().buffer(plain.size).writeBytes(plain))
    }
}

class TlsEncryptionHandler(private val tls: TlsContext) : MessageToByteEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val n = msg.readableBytes()
        val plain = ByteArray(n).also { msg.readBytes(it) }
        out.writeBytes(tls.encrypt(plain))
    }
}
