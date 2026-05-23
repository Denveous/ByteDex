package org.openmmo.bytedex.proxy.netty

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.openmmo.bytedex.proxy.tls.sign
import org.openmmo.bytedex.proxy.tls.toECPublicKey
import org.openmmo.bytedex.proxy.tls.toUncompressedPoint
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import kotlin.random.Random

object HandshakePackets {

    const val ID_CLIENT_HELLO: Byte = 0x00
    const val ID_SERVER_HELLO: Byte = 0x01
    const val ID_CLIENT_READY: Byte = 0x02

    private const val KEY1 = 3214621489648854472L
    private const val KEY2 = -4214651440992349575L

    data class ClientHello(val timestamp: Long)
    data class ServerHello(
        val publicKey: ECPublicKey,
        val signature: ByteArray,
        val checksumSize: Int,
    )
    data class ClientReady(val publicKey: ECPublicKey)

    fun encodeClientHello(packet: ClientHello): ByteBuf {
        val buf = Unpooled.buffer(1 + 16)
        buf.writeByte(ID_CLIENT_HELLO.toInt())
        val random = Random.nextLong()
        buf.writeLongLE(random xor KEY1)
        buf.writeLongLE(packet.timestamp xor KEY2 xor random)
        return buf
    }

    fun decodeClientHello(payload: ByteBuf): ClientHello {
        val xoredRandom = payload.readLongLE()
        val xoredTimestamp = payload.readLongLE()
        val random = xoredRandom xor KEY1
        val timestamp = xoredTimestamp xor KEY2 xor random
        return ClientHello(timestamp)
    }

    fun encodeServerHello(packet: ServerHello, signingKey: ECPrivateKey): ByteBuf {
        val pubBytes = packet.publicKey.toUncompressedPoint()
        val signature = signingKey.sign(pubBytes)
        val buf = Unpooled.buffer(1 + 2 + pubBytes.size + 2 + signature.size + 1)
        buf.writeByte(ID_SERVER_HELLO.toInt())
        buf.writeShortLE(pubBytes.size)
        buf.writeBytes(pubBytes)
        buf.writeShortLE(signature.size)
        buf.writeBytes(signature)
        buf.writeByte(packet.checksumSize)
        return buf
    }

    fun decodeServerHello(payload: ByteBuf): ServerHello {
        val pubSize = payload.readShortLE().toInt() and 0xffff
        val pubBytes = ByteArray(pubSize).also { payload.readBytes(it) }
        val sigSize = payload.readShortLE().toInt() and 0xffff
        val signature = ByteArray(sigSize).also { payload.readBytes(it) }
        val checksumSize = payload.readByte().toInt() and 0xff
        return ServerHello(pubBytes.toECPublicKey(), signature, checksumSize)
    }

    fun encodeClientReady(packet: ClientReady): ByteBuf {
        val pubBytes = packet.publicKey.toUncompressedPoint()
        val buf = Unpooled.buffer(1 + 2 + pubBytes.size)
        buf.writeByte(ID_CLIENT_READY.toInt())
        buf.writeShortLE(pubBytes.size)
        buf.writeBytes(pubBytes)
        return buf
    }

    fun decodeClientReady(payload: ByteBuf): ClientReady {
        val pubSize = payload.readShortLE().toInt() and 0xffff
        val pubBytes = ByteArray(pubSize).also { payload.readBytes(it) }
        return ClientReady(pubBytes.toECPublicKey())
    }

    fun consumeId(payload: ByteBuf): Byte = payload.readByte()
}
