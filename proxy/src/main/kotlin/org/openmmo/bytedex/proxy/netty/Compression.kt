package org.openmmo.bytedex.proxy.netty

import java.util.zip.Inflater

internal object CompressionCodec {

    private const val FLAG_RAW: Byte = 0
    private const val FLAG_DEFLATE: Byte = 1

    fun isCompressed(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[1] == FLAG_DEFLATE

    private val SYNC_FLUSH_TRAILER = byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0xFF.toByte())

    fun decompress(bytes: ByteArray, inflater: Inflater): ByteArray {
        require(bytes.size >= 2 && bytes[1] == FLAG_DEFLATE) {
            "decompress called on a non-compressed packet"
        }
        val compressed = bytes.copyOfRange(2, bytes.size) + SYNC_FLUSH_TRAILER
        inflater.setInput(compressed)
        val out = java.io.ByteArrayOutputStream(bytes.size * 4)
        out.write(bytes[0].toInt() and 0xFF)
        out.write(FLAG_RAW.toInt())
        val buf = ByteArray(8192)
        while (true) {
            val n = inflater.inflate(buf)
            if (n == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
                break
            }
            out.write(buf, 0, n)
        }
        val raw = out.toByteArray()
        if (raw.size >= 4
            && raw[raw.size - 2] == 0xFF.toByte()
            && raw[raw.size - 1] == 0xFF.toByte()) {
            return raw.copyOf(raw.size - 2)
        }
        return raw
    }
}
