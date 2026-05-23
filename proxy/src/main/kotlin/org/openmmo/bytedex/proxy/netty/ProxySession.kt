package org.openmmo.bytedex.proxy.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import org.openmmo.bytedex.proxy.tls.Checksum
import org.openmmo.bytedex.proxy.tls.NoOpChecksum
import org.openmmo.bytedex.proxy.tls.NoOpTlsContext
import org.openmmo.bytedex.proxy.tls.TlsContext
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.ArrayDeque
import java.util.zip.Inflater

class ProxySession(
    val portName: String,
    val signingPrivateKey: ECPrivateKey,
    val signingPublicKey: ECPublicKey,
    val patcher: PacketPatcher,
    val checksumSize: Int = 0,
    val serverToClientCompressed: Boolean = false,
    val protocol: PacketSink.Protocol = PacketSink.Protocol.LOGIN,
    val sink: PacketSink = PacketSink.NONE,
) {
    val serverInflater: Inflater? =
        if (serverToClientCompressed) Inflater(true) else null
    @Volatile var clientChannel: Channel? = null
    @Volatile var upstreamChannel: Channel? = null

    @Volatile var clientFacingTls: TlsContext = NoOpTlsContext
    @Volatile var upstreamFacingTls: TlsContext = NoOpTlsContext

    @Volatile var clientFacingChecksumIn: Checksum = NoOpChecksum
    @Volatile var clientFacingChecksumOut: Checksum = NoOpChecksum
    @Volatile var upstreamFacingChecksumIn: Checksum = NoOpChecksum
    @Volatile var upstreamFacingChecksumOut: Checksum = NoOpChecksum

    @Volatile var clientHandshakeDone = false
    @Volatile var upstreamHandshakeDone = false

    val pendingForClient: ArrayDeque<ByteBuf> = ArrayDeque()
    val pendingForUpstream: ArrayDeque<ByteBuf> = ArrayDeque()

    val ready: Boolean get() = clientHandshakeDone && upstreamHandshakeDone

    fun onSideReady() {
        synchronized(this) {
            if (!ready) return
            drain(pendingForClient, clientChannel)
            drain(pendingForUpstream, upstreamChannel)
        }
    }

    private fun drain(queue: ArrayDeque<ByteBuf>, channel: Channel?) {
        if (channel == null) return
        while (queue.isNotEmpty()) channel.writeAndFlush(queue.pollFirst())
    }
}
