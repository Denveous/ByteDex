package org.openmmo.bytedex.proxy.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.slf4j.LoggerFactory
import java.util.Deque
import java.util.zip.Inflater

class BridgeHandler(
    private val session: ProxySession,
    private val side: Side,
) : SimpleChannelInboundHandler<ByteBuf>() {

    private val log = LoggerFactory.getLogger("proxy.${session.portName}.bridge.${side.name.lowercase()}")

    enum class Side { CLIENT_FACING, UPSTREAM_FACING }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        val direction = when (side) {
            Side.CLIENT_FACING -> PacketPatcher.Direction.CLIENT_TO_SERVER
            Side.UPSTREAM_FACING -> PacketPatcher.Direction.SERVER_TO_CLIENT
        }
        val sinkDirection = when (side) {
            Side.CLIENT_FACING -> PacketSink.Direction.C2S
            Side.UPSTREAM_FACING -> PacketSink.Direction.S2C
        }
        val capturedAt = System.currentTimeMillis()

        val n = msg.readableBytes()
        if (n > 0) {
            val wire = ByteArray(n).also { msg.getBytes(msg.readerIndex(), it) }
            val inflater = if (side == Side.UPSTREAM_FACING) session.serverInflater else null
            val payload = extractPayload(wire, inflater, direction)
            val packetId = wire[0].toInt() and 0xFF
            if (payload != null) {
                reportToSink(sinkDirection, packetId, payload, capturedAt)
            }
        }

        val patched = session.patcher.patch(msg, direction)
        if (patched === msg) patched.retain()
        forwardOrBuffer(patched)
    }

    private fun extractPayload(
        wire: ByteArray,
        inflater: Inflater?,
        direction: PacketPatcher.Direction,
    ): ByteArray? {
        val n = wire.size
        val firstByte = "0x%02x".format(wire[0].toInt() and 0xff)
        return when {
            inflater != null && n >= 2 && CompressionCodec.isCompressed(wire) -> {
                try {
                    val plain = CompressionCodec.decompress(wire, inflater)
                    log.info(
                        "rx {} bytes (id={}, compressed=true, plain={} bytes) -> forward {}",
                        n, firstByte, plain.size, direction,
                    )
                    plain.copyOfRange(2, plain.size)
                } catch (t: Throwable) {
                    log.warn("inflate diagnostic failed: {}", t.toString())
                    null
                }
            }
            inflater != null && n >= 2 -> {
                log.info("rx {} bytes (id={}, compressed=false) -> forward {}", n, firstByte, direction)
                wire.copyOfRange(2, wire.size)
            }
            else -> {
                log.info("rx {} bytes (id={}) -> forward {}", n, firstByte, direction)
                wire.copyOfRange(1, wire.size)
            }
        }
    }

    private fun reportToSink(
        direction: PacketSink.Direction,
        packetId: Int,
        payload: ByteArray,
        capturedAt: Long,
    ) {
        try {
            session.sink.accept(session.protocol, direction, packetId, payload, capturedAt)
        } catch (t: Throwable) {
            log.warn("packet sink threw: {}", t.toString())
        }
    }

    private fun forwardOrBuffer(packet: ByteBuf) {
        synchronized(session) {
            if (session.ready) {
                val target = targetChannel()
                if (target != null) {
                    target.writeAndFlush(packet)
                } else {
                    packet.release()
                }
            } else {
                pendingForOtherSide().addLast(packet)
            }
        }
    }

    private fun targetChannel(): Channel? = when (side) {
        Side.CLIENT_FACING -> session.upstreamChannel
        Side.UPSTREAM_FACING -> session.clientChannel
    }

    private fun pendingForOtherSide(): Deque<ByteBuf> = when (side) {
        Side.CLIENT_FACING -> session.pendingForUpstream
        Side.UPSTREAM_FACING -> session.pendingForClient
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
        targetChannel()?.close()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        targetChannel()?.close()
    }
}
