package org.openmmo.bytedex.app.capture

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.openmmo.bytedex.proxy.netty.PacketSink
import org.slf4j.LoggerFactory

class PacketBlacklist(entries: List<Entry>) {

    @Serializable
    data class Entry(
        val packetId: Int,
        val protocol: PacketSink.Protocol,
        val direction: PacketSink.Direction,
    )

    private val blocked: Set<Entry> = entries.toSet()

    fun isBlocked(
        protocol: PacketSink.Protocol,
        direction: PacketSink.Direction,
        packetId: Int,
    ): Boolean = Entry(packetId, protocol, direction) in blocked

    companion object {
        private val log = LoggerFactory.getLogger("bytedex.blacklist")
        private const val RESOURCE = "/packet-blacklist.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun load(): PacketBlacklist {
            val text = PacketBlacklist::class.java.getResource(RESOURCE)?.readText()
            if (text == null) {
                log.warn("{} not on classpath. no packets will be withheld", RESOURCE)
                return PacketBlacklist(emptyList())
            }
            val entries = runCatching { json.decodeFromString<List<Entry>>(text) }
                .getOrElse {
                    log.error("failed to parse {}. no packets will be withheld: {}", RESOURCE, it.toString())
                    emptyList()
                }
            log.info("loaded {} packet blacklist entries", entries.size)
            return PacketBlacklist(entries)
        }
    }
}
