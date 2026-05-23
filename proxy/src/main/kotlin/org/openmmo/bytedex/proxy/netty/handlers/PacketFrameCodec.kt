package org.openmmo.bytedex.proxy.netty.handlers

import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import java.nio.ByteOrder

class PacketFrameDecoder : LengthFieldBasedFrameDecoder(
    ByteOrder.LITTLE_ENDIAN,
    UShort.MAX_VALUE.toInt(),
    0,
    UShort.SIZE_BYTES,
    -UShort.SIZE_BYTES,
    UShort.SIZE_BYTES,
    false,
)

class PacketFrameEncoder : LengthFieldPrepender(
    ByteOrder.LITTLE_ENDIAN,
    UShort.SIZE_BYTES,
    0,
    true,
)
