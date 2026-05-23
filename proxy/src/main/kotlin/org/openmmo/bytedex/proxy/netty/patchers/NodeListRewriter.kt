package org.openmmo.bytedex.proxy.netty.patchers

import io.netty.buffer.ByteBuf

internal object NodeListRewriter {

    data class FirstUpstream(val ipv4: String, val port: Int)

    private const val IPV4_LOCALHOST_INT = 0x7F000001

    fun rewrite(src: ByteBuf, out: ByteBuf, targetPort: Int): FirstUpstream? {
        val nodeCount = src.readUnsignedByte().toInt()
        out.writeByte(nodeCount)
        var first: FirstUpstream? = null
        for (i in 0 until nodeCount) {
            out.writeByte(src.readByte().toInt())   // padding/index
            val origIpv4 = readIp(src)
            writeIpV4LocalLE(out)
            readIp(src)
            writeIpV6LocalLE(out)
            val origPort = src.readUnsignedShortLE()
            out.writeShortLE(targetPort)
            out.writeByte(src.readByte().toInt())   // weight
            if (first == null && origIpv4 != null) {
                first = FirstUpstream(origIpv4, origPort)
            }
        }
        return first
    }

    private fun readIp(src: ByteBuf): String? = when (val type = src.readUnsignedByte().toInt()) {
        4 -> {
            val v = src.readIntLE()
            "${(v ushr 24) and 0xFF}.${(v ushr 16) and 0xFF}.${(v ushr 8) and 0xFF}.${v and 0xFF}"
        }
        6 -> { src.skipBytes(16); null }
        else -> error("unexpected IP type byte $type")
    }

    private fun writeIpV4LocalLE(out: ByteBuf) {
        out.writeByte(4)
        out.writeIntLE(IPV4_LOCALHOST_INT)
    }

    private fun writeIpV6LocalLE(out: ByteBuf) {
        out.writeByte(6)
        out.writeLongLE(0L) // h
        out.writeLongLE(1L) // l
    }
}
