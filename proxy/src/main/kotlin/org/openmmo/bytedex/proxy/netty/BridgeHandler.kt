package org.openmmo.bytedex.proxy.netty

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.Deque
import java.util.zip.Inflater

class BridgeHandler(
    private val session: ProxySession,
    private val side: Side,
) : SimpleChannelInboundHandler<ByteBuf>() {

    private val log = LoggerFactory.getLogger("proxy.${session.portName}.bridge.${side.name.lowercase()}")

    private val GREEN = "\u001B[32m"
    private val RED = "\u001B[31m"
    private val RESET = "\u001B[0m"
    private val CYAN = "\u001B[36m"
    private val YELLOW = "\u001B[33m"
    private val BLUE = "\u001B[34m"
    private val MAGENTA = "\u001B[35m"
    private val WHITE = "\u001B[37m"
    private val BRIGHT_GREEN = "\u001B[92m"
    private val BRIGHT_CYAN = "\u001B[96m"
    private val BRIGHT_YELLOW = "\u001B[93m"
    private val BRIGHT_MAGENTA = "\u001B[95m"
    private val BRIGHT_BLUE = "\u001B[94m"
    private fun opcodeColor(opcode: Int): String = when (opcode) {
        0x01 -> BRIGHT_CYAN
        0x02 -> WHITE
        0x05 -> BRIGHT_MAGENTA
        0x06, 0x07 -> CYAN
        0x08 -> BRIGHT_YELLOW
        0x09 -> YELLOW
        0x0A, 0x10 -> BRIGHT_BLUE
        0x0B -> MAGENTA
        0x0E -> BRIGHT_GREEN
        0x0F -> CYAN
        0x12 -> BRIGHT_MAGENTA
        0x13 -> BRIGHT_CYAN
        0x1A -> BLUE
        0x21, 0x22, 0x23 -> BRIGHT_YELLOW
        0x28 -> CYAN
        0x2C, 0x2D -> BLUE
        0x30 -> BRIGHT_BLUE
        0x33 -> BRIGHT_GREEN
        0x35 -> MAGENTA
        0x37 -> CYAN
        0x38 -> BRIGHT_MAGENTA
        0x39 -> YELLOW
        0x3E -> BRIGHT_YELLOW
        0x41 -> BRIGHT_CYAN
        0x43 -> CYAN
        0x59 -> BRIGHT_YELLOW
        0x6E -> BRIGHT_BLUE
        0x79 -> BRIGHT_GREEN
        0x80, 0x81, 0x88 -> MAGENTA
        0x90 -> BRIGHT_MAGENTA
        0xA8 -> BLUE
        0xB0 -> CYAN
        0xB2, 0xB4, 0xB6 -> BRIGHT_CYAN
        0xB9 -> YELLOW
        0xC2 -> WHITE
        0xE0, 0xE4 -> CYAN
        0xE8, 0xEA -> BRIGHT_YELLOW
        0xFC -> BRIGHT_GREEN
        else -> GREEN
    }
    private val undecodedLog = Path.of("C:\\Users\\Tim\\Desktop\\PokeMMO\\Research\\logs\\undecoded.log")
    private val decodedDir = Path.of("C:\\Users\\Tim\\Desktop\\PokeMMO\\Research\\decoded")
    private val npcMappingLog = Path.of("C:\\Users\\Tim\\Desktop\\PokeMMO\\Research\\logs\\npc_mapping.log")
    private val npcMappingJson = Path.of("C:\\Users\\Tim\\Desktop\\PokeMMO\\Research\\decoded\\NpcDialogMapping.json")
    private val savePacketIds = setOf(0x02, 0x05, 0x10, 0x12, 0x21, 0x23)
    private val spamBlacklist = setOf(0x06, 0x07, 0x08, 0x0F, 0xE0, 0xE2, 0xE4, 0xE8, 0xEA, 0xC2)
    private val jsonlFile = Path.of("C:\\Users\\Tim\\Desktop\\PokeMMO\\Research\\logs\\decoded_" + session.protocol.name.lowercase() + "_${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.jsonl")
    private val gson = GsonBuilder().create()


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
            val packetId = wire[0].toInt() and 0xFF
            val spam = session.protocol == PacketSink.Protocol.GAME && packetId in spamBlacklist
            if (!spam) {
                val inflater = if (side == Side.UPSTREAM_FACING) session.serverInflater else null
                val payload = extractPayload(wire, inflater, direction)
                if (payload != null) {
                    var packetName: String? = null
                    try {
                        packetName = logDecoded(packetId, payload, sinkDirection)
                    } catch (t: Throwable) {
                        writeUndecoded(packetId, payload, sinkDirection, "logger-failed ${t}")
                        log.info("{}[{}] logger-failed {}: size={}, err={}{}", RED, sinkDirection, "0x%02X".format(packetId), payload.size, t.toString(), RESET)
                    }
                    if (packetName != null && packetId in savePacketIds) saveDecoded(packetId, packetName, packetName, sinkDirection)
                    if (session.protocol != PacketSink.Protocol.LOGIN) reportToSink(sinkDirection, packetId, payload, capturedAt, packetName)
                }
            }
        }

        val patched = session.patcher.patch(msg, direction)

        if (patched === msg) patched.retain()
        forwardOrBuffer(patched)
    }

    private fun logDecoded(packetId: Int, payload: ByteArray, direction: PacketSink.Direction): String? {
        if (session.protocol == PacketSink.Protocol.LOGIN) {
            val decoded = when (packetId) {
                0x01 -> if (payload.size >= 3) GamePackets.decodeLoginStatus(Unpooled.wrappedBuffer(payload)).let { "LoginStatus(code=${it.code}, flags=${it.flags}, extra=${it.extra})" } else "LoginStatus(code=${payload[0].toInt() and 0xff})"
                0x02 -> if (payload.isEmpty()) "RequestServerList" else GamePackets.decodeRawFull(Unpooled.wrappedBuffer(payload)).let { "LoginRequest02(size=${it.size}, hex=${it.hex})" }
                0x03 -> GamePackets.decodeLoginGameEndpoint(Unpooled.wrappedBuffer(payload)).let { "GameEndpoint(state=${it.state}, sessionId=${it.sessionId}, key=${it.keyHex}, marker=${it.marker}, localAddress=${it.localAddress}, host=${it.host}, port=${it.port}, nodes=${it.nodes}, tail=${it.tailHex})" }
                0x09 -> GamePackets.decodeLoginToken(Unpooled.wrappedBuffer(payload)).let { "LoginToken(sessionId=${it.sessionId}, token=${it.token}, key=${it.keyHex})" }
                0x11 -> GamePackets.decodeLoginCredentials(Unpooled.wrappedBuffer(payload)).let { "LoginCredentials(username=${it.username}, blocks=${it.blocks}, locale=${it.locale}, clientA=${it.clientA}, clientB=${it.clientB}, tail=${it.tailHex}, size=${it.size}, hex=${it.hex})" }
                0x22 -> GamePackets.decodeLoginServerList(Unpooled.wrappedBuffer(payload)).let { "ServerList(count=${it.count}, servers=${it.servers}, tail=${it.tailHex})" }
                0x26 -> GamePackets.decodeExistingSession(Unpooled.wrappedBuffer(payload)).let { "ExistingSession(sessionId=${it.sessionId}, sessionKey=${it.sessionKeyHex}, marker=${it.marker}, serverName=${it.serverName}, extra=${it.extraHex}, empty=${it.empty}, zeroInt=${it.zeroInt}, zeroShortA=${it.zeroShortA}, zeroShortB=${it.zeroShortB}, flag=${it.flag}, endpoints=${it.endpoints}, tail=${it.tailHex})" }
                0x07 -> {
                    val end = GamePackets.findUtf16Null(payload, 0)
                    val text = if (end > 0) GamePackets.utf16Le(payload, 0, end) else ""
                    "LoginUsernameEcho(text=\"$text\", tail=${payload.drop((text.length + 1) * 2).joinToString(" ") { "%02x".format(it) }})"
                }
                0x08 -> {
                    val end = GamePackets.findUtf16Null(payload, 0)
                    val text = if (end > 0) GamePackets.utf16Le(payload, 0, end) else ""
                    "LoginString08(dir=$direction, text=\"$text\", tail=${payload.drop((text.length + 1) * 2).joinToString(" ") { "%02x".format(it) }})"
                }
                0xAC -> if (payload.size > 100) "LargeBlob8K(size=${payload.size}, head=${payload.take(8).joinToString(" ") { "%02x".format(it) }})" else "SmallBlob(size=${payload.size}, hex=${payload.joinToString(" ") { "%02x".format(it) }})"
                else -> return logUndecoded(packetId, payload, direction)
            }
            log.info("{}[{}] decoded {}: {}{}", opcodeColor(packetId), direction, "0x%02X".format(packetId), decoded, RESET)
            writeJsonl(packetId, decoded, direction, payload.size)
            return decoded.substringBefore('(')
        }

        if (session.protocol == PacketSink.Protocol.CHAT) {
            val decoded = when (packetId) {
                0x00 -> if (payload.isEmpty()) "ChatPing" else "ChatPong(${payload[0].toInt() and 0xff})"
                0x01 -> if (direction == PacketSink.Direction.C2S) "ChatAuth(raw=${payload.joinToString(" ") { "%02x".format(it) }})" else if (payload.isEmpty()) "ChatAuthAck(empty)" else "ChatAuthAck(status=${payload[0].toInt() and 0xff})"
                0x02 -> { val kind = payload[0].toInt() and 0xff; val body = payload.drop(1).toByteArray(); val tail = if (body.size >= 2) (body[body.size-2].toInt() and 0xff) or ((body[body.size-1].toInt() and 0xff) shl 8) else null; val end = if (body.size >= 2) body.size - 2 else body.size; val marker = body.firstOrNull()?.toInt()?.and(0xff); val encHex = body.copyOfRange(if (marker == null) 0 else 1, end).joinToString(" ") { "%02x".format(it) }; "ChatFrame(kind=$kind, marker=$marker, tail=$tail, encoded=$encHex)" }
                else -> return logUndecoded(packetId, payload, direction)
            }
            log.info("{}[{}] decoded {}: {}{}", opcodeColor(packetId), direction, "0x%02X".format(packetId), decoded, RESET)
            writeJsonl(packetId, decoded, direction, payload.size)
            return decoded.substringBefore('(')
        }
        if (session.protocol != PacketSink.Protocol.GAME) return null
        val buf = Unpooled.wrappedBuffer(payload)
        try {
            val decoded = when (packetId) {
                0x01 -> GamePackets.decodeRawSummary(buf).let { "GameJoin(size=${it.size}, hex=${it.hex})" }
                0x02 -> if (payload.isEmpty()) "RequestCharacterList" else GamePackets.decodeRawSummary(buf).let { "CharacterList(size=${it.size}, hex=${it.hex})" }
                0x04 -> GamePackets.decodeRawSummary(buf).let { "CharacterSelectOrSelected(size=${it.size}, hex=${it.hex})" }
                0x05 -> if (direction == PacketSink.Direction.S2C) { val b = ByteArray(buf.readableBytes()).also { buf.getBytes(buf.readerIndex(), it) }; val eid = readLongLE(b, 0); val typ = b.getOrNull(8)?.toInt()?.and(0xff) ?: -1; val variant = b.getOrNull(9)?.toInt()?.and(0xff) ?: -1; val ne = GamePackets.findUtf16Null(b, 10); val ns = GamePackets.findLikelyUtf16Start(b, ne); val name = if (ns >= 0 && ne > ns) GamePackets.safeText(GamePackets.utf16Le(b, ns, ne)) else ""; val tail = if (ne >= 0) ne + 2 else b.size; val mapId = readUShortLE(b, tail); val x = readUShortLE(b, tail + 3); val y = readUShortLE(b, tail + 5); val layer = if (b.size > tail + 2) ((b[tail].toInt() and 0xff) or ((b[tail+1].toInt() and 0xff) shl 8)) else null; val dir = b.getOrNull(tail + 8)?.toInt()?.and(0x03); val gr = GamePackets.findTrailingUtf16String(b, tail + 9); val guild = gr?.let { GamePackets.safeText(GamePackets.utf16Le(b, it.first, it.second)) }; val layerStr = layer?.toString() ?: ""; val guildStr = guild ?: ""; val spawnMap = mapOf("map" to "$mapId", "x" to "$x", "y" to "$y", "layer" to layerStr, "type" to "$typ", "variant" to "$variant", "name" to name, "guild" to guildStr); session.npcSpawnRegistry[eid] = spawnMap; appendNpcMapping("SPAWN eid=0x${eid.toString(16)} | ${spawnMap.entries.joinToString(" ") { "${it.key}=${it.value}" }}"); "SpawnEntity(eid=$eid, type=$typ, variant=$variant, name=$name, map=$mapId, x=$x, y=$y, layer=$layer, dir=$dir, guild=$guild)" } else "RequestEntityRefresh"

                0x06 -> GamePackets.decodeMovement(buf).let { "Movement(x=${it.x}, y=${it.y}, dir=${it.direction})" }
                0x07 -> if (direction == PacketSink.Direction.S2C) GamePackets.decodeEntityFace(buf).let { "EntityFace(eid=${it.entityId}, dir=${it.direction})" } else GamePackets.decodeFaceDirection(buf).let { "FaceDirection(dir=${it.direction})" }
                0x08 -> if (direction == PacketSink.Direction.C2S && payload.size != 8) GamePackets.decodeOutgoingLocalChat(buf).let { "LocalChatSend(channel=${it.channel}, text=${it.text})" } else "EntityStop(eid=${readLongLE(payload, 0)})"
                0x09 -> if (direction == PacketSink.Direction.S2C) GamePackets.decodeLocalChatMessage(buf).let { "LocalChat(channel=${it.channel}, eid=${it.entityId}, name=${it.name}, text=${it.text})" } else GamePackets.decodePokemonStorageMove(buf).let { "PokemonStorageMove(kind=${it.kind}, from=${it.fromBox}:${it.fromSlot}, to=${it.toBox}:${it.toSlot})" }
                0x0A -> GamePackets.decodeRawSummary(buf).let { "TileBlob(size=${it.size}, hex=${it.hex})" }
                0x0B -> GamePackets.decodeByteUShortByte(buf).let { "BattleStart(mode=${it.a}, kind=${it.b}, flag=${it.c})" }
                0x0C -> "TripleShort(a=${readUShortLE(payload, 0)}, b=${readUShortLE(payload, 2)}, c=${readUShortLE(payload, 4)})"
                0x0D -> "EntityStatusList(eid=${readLongLE(payload, 0)}, mode=${payload.getOrNull(8)?.toInt()?.and(0xff)}, count=${payload.getOrNull(9)?.toInt()?.and(0xff)}, values=${payload.drop(10).map { it.toInt() and 0xff }})"
                0x0E -> GamePackets.decodeUiState(buf).let { "UiState(${it.value})" }
                0x0F -> GamePackets.decodeEntityByteValue(buf).let { "EntityByteValue(eid=${it.entityId}, value=${it.value})" }
                0x10 -> if (direction == PacketSink.Direction.S2C) { val region = buf.readUnsignedByte().toInt(); val mapId = buf.readUnsignedShortLE(); val x = buf.readUnsignedByte().toInt(); val y = buf.readUnsignedShortLE(); val layer = buf.readUnsignedByte().toInt(); val flags = buf.readIntLE(); val rest = ByteArray(buf.readableBytes()).also { buf.readBytes(it) }; "LoadMap(region=$region, map=$mapId, x=$x, y=$y, layer=$layer, flags=$flags, tiles=${rest.size / 2})" } else "LoadMapAck"
                0x11 -> "EntityKindRequest(kind=${payload[0].toInt() and 0xff}, eid=${readLongLE(payload, 1)})"
                0x12 -> { val idx = buf.readUnsignedByte().toInt(); val eid = buf.readLongLE(); val kind = buf.readUnsignedByte().toInt(); val a = buf.readUnsignedShortLE(); val b = buf.readUnsignedShortLE(); val mapId = buf.readUnsignedShortLE(); val x = buf.readUnsignedShortLE(); val y = buf.readUnsignedShortLE(); val fl = if (buf.readableBytes() >= 2) buf.readUnsignedShortLE() else -1; "NpcMarker(idx=$idx, eid=$eid, kind=$kind, a=$a, b=$b, map=$mapId, x=$x, y=$y, flags=$fl)" }
                0x13 -> if (payload.size <= 3) "PokemonPartyEnd(mode=${payload[0].toInt() and 0xff}, a=${payload.getOrNull(1)?.toInt()?.and(0xff)}, b=${payload.getOrNull(2)?.toInt()?.and(0xff)})" else GamePackets.decodePokemonParty(buf).let { "PokemonParty(mode=${it.mode}, idx=${it.index}, partySize=${it.partySize}, names=${it.names})" }
                0x1B -> "EmptyMarker"
                0x1C -> GamePackets.decodeUShortList(buf).let { "UShortList(count=${it.count}, values=${it.values.take(12)}${if (it.values.size > 12) "..." else ""})" }
                0x1E -> GamePackets.decodeRawSummary(buf).let { "SmallState(size=${it.size}, hex=${it.hex})" }
                0x1F -> "EntitySlotSync(raw=${payload.joinToString(" ") { "%02x".format(it) }})"
                0x20 -> GamePackets.decodeRawSummary(buf).let { "SessionProbe(size=${it.size}, hex=${it.hex})" }
                0x21 -> if (direction == PacketSink.Direction.C2S) { val action = readUShortLE(payload, 0); "DialogAction(action=$action)" } else if (payload.size >= 19) { val seqId = payload[0].toInt() and 0xff; val type = payload[1].toInt() and 0xff; val unk1 = readUShortLE(payload, 2); val unk2 = readUShortLE(payload, 4); val eid = readLongLE(payload, 6); val unk3 = readUShortLE(payload, 14); val kf0 = (unk2.toLong() shl 16) or unk1.toLong(); val trail = payload[18].toInt() and 0xff; val gt = (kf0 shr 28).toInt(); val narcSel = ((kf0 shr 27) and 1).toInt(); val bank = ((kf0 shr 16) and 0x3FF).toInt(); val entry = (kf0 and 0xFFFF).toInt(); val kf0Info = when (gt) { 0, 1 -> "KF0=0x${kf0.toString(16)} (GBA offset=0x${(kf0 and 0xFFFFFF).toString(16)}${if (gt == 1) ",emerald" else ""})"; 2, 3, 4 -> "KF0=0x${kf0.toString(16)} (NDS gt=$gt narc=$narcSel bank=$bank entry=$entry)"; else -> "KF0=0x${kf0.toString(16)}" }; val spawn = session.npcSpawnRegistry[eid]; val sp = spawn?.entries?.joinToString(" ") { "${it.key}=${it.value}" } ?: "unknown"; appendNpcMapping("DIALOG seqId=$seqId type=0x${type.toString(16)} $kf0Info unk3=0x${unk3.toString(16)} trail=$trail eid=0x${eid.toString(16)} | spawn=[$sp]"); saveNpcMapping(eid, spawn, seqId, type, unk1, unk2, kf0, unk3, trail, gt, narcSel, bank, entry); "DialogFrame(seqId=$seqId, type=$type, $kf0Info, eid=0x${eid.toString(16)}, unk3=0x${unk3.toString(16)})" } else "DialogFrame(raw=${payload.joinToString(" ") { "%02x".format(it) }})"
                0x22 -> if (direction == PacketSink.Direction.C2S && payload.size >= 16) { val eid22 = readLongLE(payload, 0); val spawn = session.npcSpawnRegistry[eid22]; val sp = spawn?.entries?.joinToString(" ") { "${it.key}=${it.value}" } ?: "unknown"; appendNpcMapping("INTERACT eid=0x${eid22.toString(16)} | spawn=[$sp]"); "InteractionRequest(eid=$eid22, token=${payload.drop(8).joinToString(" ") { "%02x".format(it) }})" } else "EntitySlotUpdate(raw=${payload.joinToString(" ") { "%02x".format(it) }})"

                0x23 -> if (direction == PacketSink.Direction.C2S) GamePackets.decodeShopBuy(buf).also { saveDecoded(0x23, "ShopBuy", it, direction) }.let { "ShopBuy(item=${it.itemId}, qty=${it.quantity}, extra=${it.extra})" } else if (payload.size == 1) GamePackets.decodeByteValue(buf).let { "ShopResult(${it.value})" } else GamePackets.decodeShopInventory(buf).also { saveDecoded(0x23, "ShopInventory", it, direction) }.let { "ShopInventory(mode=${it.mode}, shop=${it.shopId}, eid=${it.entityId}, entries=${it.entries})" }
                0x24 -> GamePackets.decodeEntityActionValue(buf).let { "EntityActionValue(eid=${it.entityId}, value=${it.value})" }
                0x25 -> GamePackets.decodeByteUShortByte(buf).let { "ByteUShortByte(a=${it.a}, b=${it.b}, c=${it.c})" }
                0x26 -> GamePackets.decodeRawSummary(buf).let { "RawSummary(size=${it.size}, hex=${it.hex})" }
                0x27 -> if (payload.isEmpty()) "EmptyAction" else GamePackets.decodeByteValue(buf).let { "ByteValue(${it.value})" }
                0x28 -> GamePackets.decodeEntityByteValue(buf).let { "EntityByteValue(eid=${it.entityId}, value=${it.value})" }
                0x29 -> GamePackets.decodeIndexedUShortList(buf).let { "IndexedUShortList(index=${it.index}, count=${it.count}, values=${it.values.take(12)}${if (it.values.size > 12) "..." else ""})" }
                0x2A -> "ByteShortShort(a=${payload[0].toInt() and 0xff}, b=${readUShortLE(payload, 1)}, c=${readUShortLE(payload, 3)})"
                0x2B -> GamePackets.decodeRawSummary(buf).let { "RawSummary(size=${it.size}, hex=${it.hex})" }
                0x2C -> GamePackets.decodeEntityTextTag(buf).let { "EntityTextTag(eid=${it.entityId}, tag=${it.tag})" }
                0x2D -> if (payload.size >= 10) GamePackets.decodeEntityTargetPosition(buf).let { "EntityTargetPosition(mode=${it.mode}, eid=${it.entityId}, x=${it.x}, y=${it.y})" } else { val mode = payload[0].toInt() and 0xff; "EntityTargetShort(mode=$mode, hex=${payload.drop(1).joinToString(" ") { "%02x".format(it) }})" }
                0x30 -> GamePackets.decodeBattleOrSceneBlob(buf).let { "BattleOrSceneBlob(mode=${it.mode}, a=${it.a}, b=${it.b}, c=${it.c}, d=${it.d}, names=${it.names}, entityIds=${it.entityIds}, size=${it.size}, hex=${it.hex.take(96)}${if (it.hex.length > 96) "..." else ""})" }
                0x35 -> { val hex = payload.joinToString(" ") { "%02x".format(it) }; val bb = buf; val mode = bb.readUnsignedByte().toInt(); val a = bb.readUnsignedByte().toInt(); val b = bb.readUnsignedByte().toInt(); val f1 = bb.readUnsignedByte().toInt(); val f2 = bb.readUnsignedByte().toInt(); val f3 = bb.readUnsignedByte().toInt(); val eid = if (bb.readableBytes() >= 8) bb.readLongLE() else 0L; val slots = mutableListOf<String>(); if (bb.readableBytes() >= 13) { val sp = bb.readUnsignedShortLE(); val lv = bb.readUnsignedByte().toInt(); repeat(6) { bb.readByte() }; val chp = bb.readUnsignedShortLE(); val mhp = bb.readUnsignedShortLE(); slots.add("Slot(sp=$sp,lv=$lv,hp=$chp/$mhp)") }; if (bb.readableBytes() >= 2) { var sk = 0; while (bb.readableBytes() >= 2) { val b0 = bb.getUnsignedByte(bb.readerIndex()).toInt(); val b1 = bb.getUnsignedByte(bb.readerIndex() + 1).toInt(); if (b0 == 0xFF && b1 == 0x03) { bb.skipBytes(2); if (bb.readableBytes() > 0) bb.readByte(); break }; bb.readByte(); sk++; if (sk > 20) break } }; if (bb.readableBytes() >= 13) { val sp = bb.readUnsignedShortLE(); val lv = bb.readUnsignedByte().toInt(); repeat(6) { bb.readByte() }; val chp = bb.readUnsignedShortLE(); val mhp = bb.readUnsignedShortLE(); slots.add("Slot(sp=$sp,lv=$lv,hp=$chp/$mhp)") }; val tail = if (bb.readableBytes() > 0) bb.readRetainedSlice(bb.readableBytes()).let { b -> val r = mutableListOf<Int>(); while (b.isReadable) r.add(b.readUnsignedByte().toInt()); r } else emptyList(); "BattleState(mode=$mode, a=$a, b=$b, flags=[$f1,$f2,$f3], eid=$eid, slots=$slots, tail=$tail) HEX=$hex" }
                0x36 -> "ByteUShort(a=${payload[0].toInt() and 0xff}, b=${readUShortLE(payload, 1)})"
                0x31 -> GamePackets.decodeRawSummary(buf).let { "BattleOrSceneState(size=${it.size}, hex=${it.hex})" }
                0x32 -> GamePackets.decodeByteValue(buf).let { "ByteValue(${it.value})" }
                0x33 -> if (payload.isEmpty()) "EmptyAction" else { val eid = buf.readLongLE(); val action = buf.readUnsignedShortLE(); val mode = buf.readUnsignedByte().toInt(); val kind = buf.readUnsignedByte().toInt(); val state = buf.readUnsignedByte().toInt(); val target = if (buf.readableBytes() >= 8) buf.readLongLE() else 0L; val a = if (buf.readableBytes() >= 2) buf.readUnsignedShortLE() else 0; val b = if (buf.readableBytes() >= 2) buf.readUnsignedShortLE() else 0; val c = if (buf.readableBytes() >= 1) buf.readUnsignedByte().toInt() else 0; "EntityInteraction(eid=$eid, action=$action, mode=$mode, kind=$kind, state=$state, target=$target, a=$a, b=$b, c=$c)" }
                0x34 -> GamePackets.decodeUShortList(buf).let { "UShortList(count=${it.count}, values=${it.values})" }
                0x40 -> GamePackets.decodeRawSummary(buf).let { "BagInventory(size=${it.size}, hex=${it.hex})" }
                0x41 -> if (payload.size >= 10) "EntityUShort(eid=${readLongLE(payload, 0)}, value=${readUShortLE(payload, 8)})" else "EntityUShort(raw=${payload.joinToString(" ") { "%02x".format(it) }})"
                0x42 -> "ByteEntityTail(a=${payload[0].toInt() and 0xff}, eid=${readLongLE(payload, 1)}, tail=${payload.drop(9).joinToString(" ") { "%02x".format(it) }})"
                0x44 -> if (payload.isEmpty()) "EmptyAction" else GamePackets.decodeRawSummary(buf).let { "RawSummary(size=${it.size}, hex=${it.hex})" }
                0x4F -> GamePackets.decodePairStateList(buf).let { "PairStateList(count=${it.count}, entries=${it.entries})" }
                0x4A -> GamePackets.decodeByteValue(buf).let { "ByteValue(${it.value})" }
                0x4D -> GamePackets.decodeByteValue(buf).let { "ByteValue(${it.value})" }
                0x55 -> GamePackets.decodeThreeByteState(buf).let { "ThreeByteState(a=${it.a}, b=${it.b}, c=${it.c})" }
                0x59 -> { val mode = buf.readUnsignedByte().toInt(); val kind = buf.readUnsignedByte().toInt(); val eid = if (buf.readableBytes() >= 8) buf.readLongLE() else 0L; val a = if (buf.readableBytes() >= 2) buf.readUnsignedShortLE() else 0; val b = if (buf.readableBytes() >= 2) buf.readUnsignedShortLE() else 0; "EntityState(mode=$mode, kind=$kind, eid=$eid, a=$a, b=$b)" }
                0x5D -> GamePackets.decodeRawSummary(buf).let { "MapBlob(size=${it.size}, hex=${it.hex.take(96)}${if (it.hex.length > 96) "..." else ""})" }
                0x60 -> GamePackets.decodeRawSummary(buf).let { "MedBlob(size=${it.size}, hex=${it.hex.take(96)}${if (it.hex.length > 96) "..." else ""})" }
                0x63 -> GamePackets.decodeRawSummary(buf).let { "OnlineList(size=${it.size}, hex=${it.hex})" }
                0x67 -> GamePackets.decodeShortState(buf).let { "ShortState(value=${it.value})" }
                0x6D -> GamePackets.decodeByteList(buf).let { "ByteList(kind=${it.kind}, values=${it.values})" }
                0x6E -> { val kind = buf.readUnsignedByte().toInt(); val a = buf.readUnsignedShortLE(); val b = buf.readLongLE(); val c = buf.readUnsignedShortLE(); val d = buf.readUnsignedShortLE(); "State16(kind=$kind, a=$a, b=$b, c=$c, d=$d)" }
                0x70 -> GamePackets.decodeRawSummary(buf).let { "LargeBlob(size=${it.size}, hex=${it.hex.take(96)}${if (it.hex.length > 96) "..." else ""})" }
                0x72 -> GamePackets.decodeUShortList(buf).let { "UShortList(count=${it.count}, values=${it.values})" }
                0x79 -> "EntityLongState(eid=${readLongLE(payload, 0)}, stats=${payload.drop(8).joinToString(" ") { "%02x".format(it) }})"
                0x7C -> { val cnt = payload.size / 12; val recs = (0 until cnt).map { i -> Triple(readIntLE(payload, i*12).toLong() and 0xffffffffL, readIntLE(payload, i*12+4).toLong() and 0xffffffffL, readIntLE(payload, i*12+8).toLong() and 0xffffffffL) }; "UInt32TripleList(count=$cnt, first=${recs.firstOrNull()})" }
                0x7F -> GamePackets.decodeRawSummary(buf).let { "LargeTileBlob(size=${it.size}, hex=${it.hex.take(96)}${if (it.hex.length > 96) "..." else ""})" }
                0x39 -> if (payload.size >= 12) { val mode = payload[0].toInt() and 0xff; val b = payload[1].toInt() and 0xff; val moveId = readUShortLE(payload, 2); val count = payload[4].toInt() and 0xff; val flags = (5..8).map { payload.getOrNull(it)?.toInt()?.and(0xff) ?: 0 }; val eid = if (payload.size >= 17) readLongLE(payload, 9) else 0L; val tail = payload.drop(17).joinToString(" ") { "%02x".format(it) }; "BattleMoveResult(mode=$mode, b=$b, moveId=$moveId, count=$count, flags=$flags, eid=0x${eid.toString(16)}, tail=$tail)" } else "BattleMoveResult(raw=${payload.joinToString(" ") { "%02x".format(it) }})"
                0x80 -> if (direction == PacketSink.Direction.C2S) GamePackets.decodeTeamCreateRequest(buf).let { "TeamCreateRequest(name=${it.name}, tag=${it.tag})" } else GamePackets.decodeTeamCreateResult(buf).let { "TeamCreateResult(status=${it.status}, teamId=${it.teamId}, name=${it.name}, tag=${it.tag}, messageId=${it.messageId}, message=${it.message}, tail=${it.tail})" }
                0x81 -> if (direction == PacketSink.Direction.C2S) GamePackets.decodeTeamMessage(buf).let { "TeamMessage(text=${it.text})" } else GamePackets.decodeTeamCreateResult(buf).let { "TeamMessageResult(status=${it.status}, teamId=${it.teamId}, name=${it.name}, tag=${it.tag}, messageId=${it.messageId}, message=${it.message}, tail=${it.tail})" }
                0x88 -> GamePackets.decodeTeamMemberState(buf).let { "TeamMemberState(mode=${it.mode}, state=${it.state}, eid=${it.entityId}, rank=${it.rank}, teamId=${it.teamId}, name=${it.name}, tail=${it.tail})" }
                0x90 -> { val eid = buf.readLongLE(); val mode = buf.readUnsignedShortLE(); val vals = mutableListOf<Int>(); while (buf.readableBytes() >= 2) vals.add(buf.readUnsignedShortLE()); "EntityDetail(eid=$eid, mode=$mode, values=$vals)" }
                0x93 -> GamePackets.decodeRawSummary(buf).let { "EntityBlob13(size=${it.size}, hex=${it.hex})" }
                0x95 -> "BoolState(value=${payload[0].toInt() and 0xff})"
                0x96 -> if (payload.size >= 8) "EntityRequest(eid=${readLongLE(payload, 0)})" else "EntityRequest(raw=${payload.joinToString(" ") { "%02x".format(it) }})"
                0x97 -> { val strings = mutableListOf<String>(); var off = 4; while (off < payload.size - 3) { if (off % 2 == 0 || off % 2 == 1) { var i = off; var valid = true; val chars = mutableListOf<Char>(); while (i + 1 < payload.size) { val c = (payload[i].toInt() and 0xff) or ((payload[i+1].toInt() and 0xff) shl 8); if (c == 0) { i += 2; break }; if (c < 0x20 || c > 0x7e) { valid = false; break }; chars.add(c.toChar()); i += 2 }; if (valid && chars.size >= 3) { strings.add(chars.joinToString("")); off = i } else { off++ } } else { off++ } }; "MailBlob(size=${payload.size}, idx=${if (payload.size >= 2) ((payload[0].toInt() and 0xff) or ((payload[1].toInt() and 0xff) shl 8)) else -1}, total=${if (payload.size >= 4) ((payload[2].toInt() and 0xff) or ((payload[3].toInt() and 0xff) shl 8)) else -1}, names=${strings.take(8)})" }
                0x98 -> GamePackets.decodeRawSummary(buf).let { "SmallState(size=${it.size}, hex=${it.hex})" }
                0x14 -> GamePackets.decodeRawSummary(buf).let { "RawSummary(size=${it.size}, hex=${it.hex})" }
                0x15 -> "ChatChannelSwitch(raw=${payload.joinToString(" ") { "%02x".format(it) }})"
                0x16 -> "BattleStats(eid=${readLongLE(payload, 0)}, stats=${payload.drop(8).joinToString(" ") { "%02x".format(it) }})"
                0x17 -> if (payload.isEmpty()) "EmptyAction" else GamePackets.decodeRawSummary(buf).let { "RawSummary(size=${it.size}, hex=${it.hex})" }
                0x18 -> if (payload.size >= 11) "EntityTriple(eid=${readLongLE(payload, 0)}, a=${readUShortLE(payload, 8)}, b=${payload[10].toInt() and 0xff})" else "EntityTriple(raw=${payload.joinToString(" ") { "%02x".format(it) }})"
                0x1A -> { val eid = buf.readLongLE(); val v = if (buf.readableBytes() >= 2) buf.readUnsignedShortLE() else 0; "EntityShortValue(eid=$eid, value=$v)" }
                0x37 -> if (payload.size >= 8) "EntityAction(a=${payload[0].toInt() and 0xff}, eid=${readLongLE(payload, 1)})" else "EntityActionShort(hex=${payload.joinToString(" ") { "%02x".format(it) }})"
                0x38 -> { val a = buf.readLongLE(); val b = if (buf.readableBytes() >= 8) buf.readLongLE() else 0L; val tail = ByteArray(maxOf(0, buf.readableBytes())).also { buf.readBytes(it) }.joinToString(" ") { "%02x".format(it) }; "EntityPairTail(a=$a, b=$b, tail=$tail)" }
                0x3E -> "TriState(${payload.joinToString(" ") { "%02x".format(it) }})"
                0xB3 -> "IntValue(${readIntLE(payload, 0)})"
                0x99 -> if (direction == PacketSink.Direction.C2S) "ListReq(kind=${payload.getOrNull(0)?.toInt()?.and(0xff)}, a=${payload.getOrNull(1)?.toInt()?.and(0xff)}, b=${payload.getOrNull(2)?.toInt()?.and(0xff)})" else GamePackets.decodeRawSummary(buf).let { "ListResp(size=${it.size}, hex=${it.hex.take(96)}${if (it.hex.length > 96) "..." else ""})" }
                0x9A -> "EntityAck(eid=${if (payload.size >= 9) readLongLE(payload, 1) else -1L}, flag=${payload.getOrNull(0)?.toInt()?.and(0xff)}, tail=${payload.getOrNull(9)?.toInt()?.and(0xff)})"
                0x9B -> if (direction == PacketSink.Direction.C2S) "ClientListRequest(kind=${payload[0].toInt() and 0xff}, value=${if (payload.size >= 5) readIntLE(payload, 1) else null}, tail=${payload.drop(5).joinToString(" ") { "%02x".format(it) }})" else GamePackets.decodeRawSummary(buf).let { "LargeList(size=${it.size}, hex=${it.hex.take(96)}${if (it.hex.length > 96) "..." else ""})" }
                0x9C -> if (direction == PacketSink.Direction.C2S && payload.size >= 10) "EntityAction(eid=${readLongLE(payload, 2)}, kind=${payload.getOrNull(10)?.toInt()?.and(0xff)}, unk=${payload.getOrNull(1)?.toInt()?.and(0xff)})" else GamePackets.decodeRawSummary(buf).let { "ActionResult(size=${it.size}, hex=${it.hex})" }
                0x9D -> GamePackets.decodeByteValue(buf).let { "ByteValue(${it.value})" }
                0xA3 -> "BattleAction10(raw=${payload.joinToString(" ") { "%02x".format(it) }})"
                0xA4 -> if (payload.size >= 3) "BattleAction3(kind=${payload[0].toInt() and 0xff}, param=${readUShortLE(payload, 1)})" else "BattleAction3(raw=${payload.joinToString(" ") { "%02x".format(it) }})"
                0xA5 -> GamePackets.decodeRawSummary(buf).let { "EntityBlob9(size=${it.size}, hex=${it.hex})" }
                0xA6 -> GamePackets.decodeRawSummary(buf).let { "ChallengeBlob(size=${it.size}, hex=${it.hex})" }
                0xB0 -> GamePackets.decodeEntityByteValue(buf).let { "EntityByteValue(eid=${it.entityId}, value=${it.value})" }
                0xB2 -> { val before = buf.readUnsignedByte().toInt(); val eid = buf.readLongLE(); val after = if (buf.readableBytes() >= 1) buf.readUnsignedByte().toInt() else -1; "EntityBytePair(eid=$eid, before=$before, after=$after)" }
                0xB4 -> "ByteValue(${buf.readUnsignedByte().toInt()})"
                0xB6 -> "ByteShortShort(a=${payload[0].toInt() and 0xff}, b=${readUShortLE(payload, 1)}, c=${readUShortLE(payload, 3)})"
                0xB9 -> "ByteValue(${buf.readUnsignedByte().toInt()})"
                0xBE -> if (payload.size == 2) GamePackets.decodeBytePair(buf).let { "BytePair(${it.a}, ${it.b})" } else GamePackets.decodeRawSummary(buf).let { "RawSummary(size=${it.size}, hex=${it.hex})" }
                0x43 -> "EntityByte(eid=${readLongLE(payload, 0)}, value=${payload[8].toInt() and 0xff})"
                0x86 -> "EntityFlag(eid=${readLongLE(payload, 0)}, value=${payload[8].toInt() and 0xff})"
                0xA8 -> { val hdr = payload[1].toInt() and 0xff; val tileTotal = readUShortLE(payload, 3); val recs = mutableListOf<String>(); var off = 5; val zeros = ByteArray(8); while (off + 16 <= payload.size) { if (payload.sliceArray(off + 8 until off + 16).contentEquals(zeros)) { val t = payload[off].toInt() and 0xff; val cnt = payload[off + 1].toInt() and 0xff; val x = readUShortLE(payload, off + 4); val y = readUShortLE(payload, off + 6); recs.add("[t=$t c=$cnt x=$x y=$y]"); off += 16 } else if (off + 9 + 8 <= payload.size && payload.sliceArray(off + 9 until off + 17).contentEquals(zeros)) { val t = payload[off].toInt() and 0xff; val cnt = payload[off + 1].toInt() and 0xff; val x = readUShortLE(payload, off + 4); val y = readUShortLE(payload, off + 6); val extra = payload[off + 8].toInt() and 0xff; recs.add("[t=$t c=$cnt x=$x y=$y ex=0x${extra.toString(16)}]"); off += 17 } else if (off + 14 + 8 <= payload.size && payload.sliceArray(off + 14 until off + 22).contentEquals(zeros)) { val t = payload[off].toInt() and 0xff; val sub = payload.sliceArray(off + 1 until off + 7).joinToString(" ") { "%02x".format(it) }; val t2 = payload[off + 7].toInt() and 0xff; val cnt2 = payload[off + 8].toInt() and 0xff; val x2 = readUShortLE(payload, off + 9); val y2 = readUShortLE(payload, off + 11); recs.add("[t=$t sub=$sub t2=$t2 c=$cnt2 x=$x2 y=$y2]"); off += 22 } else { recs.add("[?@${off}]"); break } }; "TileExplore(records=$hdr, tileTotal=$tileTotal, tiles=${recs.size}, first=${recs.firstOrNull()}, last=${recs.lastOrNull()})" }
                0xC2 -> GamePackets.decodeKeepAlive(buf).let { "KeepAlive(kind=${it.kind}, token=${it.token})" }
                0xC4 -> GamePackets.decodeRawSummary(buf).let { "SmallState(size=${it.size}, hex=${it.hex})" }
                0xC5 -> "UShortPair(a=${readUShortLE(payload, 0)}, b=${readUShortLE(payload, 2)})"
                0xD3 -> GamePackets.decodeByteValue(buf).let { "ByteValue(${it.value})" }
                0xDC -> GamePackets.decodeRawSummary(buf).let { "LargeBlobDC(size=${it.size}, hex=${it.hex.take(96)}${if (it.hex.length > 96) "..." else ""})" }
                0xE0 -> "EntityStep(eid=${readLongLE(payload, 0)}, x=${payload[8].toInt() and 0xff}, y=${payload[9].toInt() and 0xff}, flags=0x${(payload[10].toInt() and 0xff).toString(16)}, dir=${payload[10].toInt() and 0x03})"
                0xE2 -> "EntitySwim(eid=${readLongLE(payload, 0)}, a=${payload[8].toInt() and 0xff}, b=${payload[9].toInt() and 0xff}, c=${payload[10].toInt() and 0xff}, d=${payload[11].toInt() and 0xff}, e=${payload[12].toInt() and 0xff})"
                0xE4 -> if (payload.size == 10) "ClientEntityMove(kind=${payload[0].toInt() and 0xff}, a=${payload[1].toInt() and 0xff}, x=${readUShortLE(payload, 2)}, y=${readUShortLE(payload, 4)}, tail=${payload.drop(6).joinToString(" ") { "%02x".format(it) }})" else { val eid = readLongLE(payload, 0); val x = (payload[8].toInt() and 0xff); val y = (payload[9].toInt() and 0xff); val raw = payload[10].toInt() and 0xff; val flags = if (payload.size > 11) payload[11].toInt() and 0xff else 0; "EntityMove(eid=$eid, x=$x, y=$y, rawDir=0x${raw.toString(16)}, dir=${raw and 0x03}, moveFlags=${(raw shr 2) and 0x3f}, flags=$flags)" }
                0xE8 -> "EntityMove(eid=${readLongLE(payload, 0)}, x=${payload[8].toInt() and 0xff}, y=${payload[9].toInt() and 0xff}, kind=${payload[10].toInt() and 0xff}, flags=0x${(payload[11].toInt() and 0xff).toString(16)}, dir=${payload[11].toInt() and 0x03})"
                0xEA -> GamePackets.decodeEntityMove(buf).let { "EntityMove(eid=${it.entityId}, area=${it.area}, x=${it.x}, y=${it.y}, kind=${it.kind}, flags=${it.flags})" }
                0xF1 -> GamePackets.decodeByteValue(buf).let { "ByteValue(${it.value})" }
                0xF3 -> GamePackets.decodeRawSummary(buf).let { "GameConfig(size=${it.size}, hex=${it.hex})" }
                0xF5 -> { if (payload.size < 8) "NamedStateShort(hex=${payload.joinToString(" ") { "%02x".format(it) }})" else GamePackets.decodeNamedState(buf).let { "NamedState(id=${it.id}, group=${it.group}, index=${it.index}, name=${it.name}, value=${it.value})" } }
                0xFA -> "EntityMarker(raw=${payload.joinToString(" ") { "%02x".format(it) }})"
                0xAB -> "EncryptedBlob(size=${payload.size}, head=${payload.take(8).joinToString(" ") { "%02x".format(it) }})"
                0xAC -> if (payload.size > 100) "LargeBlob8K(size=${payload.size}, head=${payload.take(8).joinToString(" ") { "%02x".format(it) }})" else "SmallBlob(size=${payload.size}, hex=${payload.joinToString(" ") { "%02x".format(it) }})"
                0xFC -> GamePackets.decodeRawSummary(buf).let { "ChatEndpoint(size=${it.size}, hex=${it.hex})" }
                0xFD -> GamePackets.decodeRawSummary(buf).let { "EntitySync(size=${it.size}, hex=${it.hex})" }
                0xFE -> GamePackets.decodeRawSummary(buf).let { "EntityStateBlob(size=${it.size}, hex=${it.hex.take(96)}${if (it.hex.length > 96) "..." else ""})" }
                else -> return logUndecoded(packetId, payload, direction)
            }
            log.info("{}[{}] decoded {}: {}{}", opcodeColor(packetId), direction, "0x%02X".format(packetId), decoded, RESET)
            writeJsonl(packetId, decoded, direction, payload.size)
            return decoded.substringBefore('(')
        } catch (t: Exception) {
            writeUndecoded(packetId, payload, direction, "decode-failed ${t}")
            log.info("{}[{}] decode-failed {}: size={}, err={}{}", RED, direction, "0x%02X".format(packetId), payload.size, t.toString(), RESET)
            return null
        }
    }

    private fun logUndecoded(packetId: Int, payload: ByteArray, direction: PacketSink.Direction): String? {
        writeUndecoded(packetId, payload, direction, "undecoded")
        log.info("{}[{}] undecoded {}: size={}{}", RED, direction, "0x%02X".format(packetId), payload.size, RESET)
        return null
    }

    private fun saveDecoded(packetId: Int, type: String, data: Any, direction: PacketSink.Direction) {
        if (packetId !in savePacketIds) return
        try {
            Files.createDirectories(decodedDir)
            val gson = GsonBuilder().setPrettyPrinting().create()
            val file = decodedDir.resolve("$type.json")
            val root = if (Files.exists(file)) JsonParser.parseString(Files.readString(file)).asJsonArray else JsonArray()
            val entry = JsonObject()
            entry.addProperty("type", type)
            entry.addProperty("protocol", session.protocol.name)
            entry.addProperty("direction", direction.name)
            entry.addProperty("packetId", "0x%02X".format(packetId))
            readField(data, "mapId")?.let { entry.addProperty("mapId", it.toString()) }
            entry.add("data", gson.toJsonTree(data))
            val key = gson.toJson(entry.get("data"))
            if (!root.any { it.asJsonObject.get("data")?.let(gson::toJson) == key }) {
                root.add(entry)
                Files.writeString(file, gson.toJson(root), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            }
        } catch (t: Throwable) {
            log.info("{}decoded-save-failed {}: {}{}", RED, type, t.toString(), RESET)
        }
    }

    private fun readField(data: Any, name: String): Any? {
        return runCatching {
            val f = data.javaClass.declaredFields.firstOrNull { it.name == name } ?: return null
            f.isAccessible = true
            f.get(data)
        }.getOrNull()
    }


    private fun writeUndecoded(packetId: Int, payload: ByteArray, direction: PacketSink.Direction, reason: String) {
        try {
            Files.createDirectories(undecodedLog.parent)
            val hex = payload.joinToString(" ") { "%02x".format(it) }
            Files.writeString(undecodedLog, "${Instant.now()} | ${session.protocol} | $direction | 0x%02X | ${payload.size} | $reason | $hex%n".format(packetId), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (_: Throwable) {}
    }

    private fun writeJsonl(packetId: Int, decoded: String, direction: PacketSink.Direction, size: Int) {
        if (packetId in spamBlacklist && session.protocol == PacketSink.Protocol.GAME) return
        try {
            Files.createDirectories(jsonlFile.parent)
            val obj = JsonObject()
            obj.addProperty("ts", Instant.now().toString())
            obj.addProperty("proto", session.protocol.name)
            obj.addProperty("dir", direction.name)
            obj.addProperty("opcode", "0x%02X".format(packetId))
            obj.addProperty("size", size)
            obj.addProperty("decoded", decoded)
            Files.writeString(jsonlFile, gson.toJson(obj) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (_: Throwable) {}
    }

    private fun appendNpcMapping(line: String) {
        try {
            Files.createDirectories(npcMappingLog.parent)
            Files.writeString(npcMappingLog, "${Instant.now()} | $line\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (_: Throwable) {}
    }

    private fun saveNpcMapping(eid: Long, spawn: Map<String, String>?, seqId: Int, type: Int, unk1: Int, unk2: Int, kf0: Long, unk3: Int, trail: Int, kf0GameType: Int, kf0NarcSel: Int, kf0Bank: Int, kf0Entry: Int) {
        try {
            Files.createDirectories(npcMappingJson.parent)
            val pgson = GsonBuilder().setPrettyPrinting().create()
            val root = if (Files.exists(npcMappingJson)) JsonParser.parseString(Files.readString(npcMappingJson)).asJsonArray else JsonArray()
            val now = Instant.now().toString()
            val eidHex = "0x${eid.toString(16)}"
            val typeHex = "0x${type.toString(16)}"
            val kf0Hex = "0x${kf0.toString(16)}"
            val sp = spawn
            val matched = root.find { e ->
                val o = e.asJsonObject
                val sk = o.get("spawnKnown")?.asBoolean ?: false
                if (sp != null && sk) {
                    o.get("map")?.asString == sp["map"] &&
                    o.get("x")?.asString == sp["x"] &&
                    o.get("y")?.asString == sp["y"] &&
                    o.get("layer")?.asString == sp["layer"] &&
                    o.get("entityType")?.asString == sp["type"] &&
                    o.get("variant")?.asString == sp["variant"] &&
                    o.get("dialogType")?.asString == typeHex &&
                    o.get("KF0")?.asString == kf0Hex
                } else if (sp == null && !sk) {
                    o.get("eid")?.asString == eidHex &&
                    o.get("dialogType")?.asString == typeHex &&
                    o.get("KF0")?.asString == kf0Hex
                } else false
            }?.asJsonObject
            if (matched != null) {
                matched.addProperty("lastSeen", now)
                matched.addProperty("count", matched.get("count").asInt + 1)
            } else {
                val entry = JsonObject()
                entry.addProperty("eid", eidHex)
                entry.addProperty("spawnKnown", sp != null)
                if (sp != null) {
                    entry.addProperty("map", sp["map"])
                    entry.addProperty("x", sp["x"])
                    entry.addProperty("y", sp["y"])
                    entry.addProperty("layer", sp["layer"])
                    entry.addProperty("entityType", sp["type"])
                    entry.addProperty("variant", sp["variant"])
                    entry.addProperty("name", sp["name"])
                    entry.addProperty("guild", sp["guild"])
                }
                entry.addProperty("dialogType", typeHex)
                entry.addProperty("KF0", kf0Hex)
                when (kf0GameType) { 0, 1 -> entry.addProperty("KF0_type", "GBA")
                    2, 3, 4 -> entry.addProperty("KF0_type", "NDS")
                }
                if (kf0GameType in 0..1) {
                    entry.addProperty("KF0_textOffset", "0x${(kf0 and 0xFFFFFF).toString(16)}")
                    if (kf0GameType == 1) entry.addProperty("KF0_emerald", true)
                } else if (kf0GameType in 2..4) {
                    entry.addProperty("KF0_gameType", kf0GameType)
                    entry.addProperty("KF0_narcSel", kf0NarcSel)
                    entry.addProperty("KF0_bank", kf0Bank)
                    entry.addProperty("KF0_entry", kf0Entry)
                    entry.addProperty("KF0_ref", "$kf0Bank:$kf0Entry")
                }
                entry.addProperty("unk1", "0x${unk1.toString(16)}")
                entry.addProperty("unk2", "0x${unk2.toString(16)}")
                entry.addProperty("unk3", "0x${unk3.toString(16)}")
                entry.addProperty("trail", trail)
                entry.addProperty("firstSeen", now)
                entry.addProperty("lastSeen", now)
                entry.addProperty("count", 1)
                root.add(entry)
            }
            Files.writeString(npcMappingJson, pgson.toJson(root), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        } catch (_: Throwable) {}
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
        name: String? = null,
    ) {
        try {
            session.sink.accept(session.protocol, direction, packetId, payload, capturedAt, name)
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


    private fun readUShortLE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xff) or ((bytes[offset + 1].toLong() and 0xff) shl 8) or ((bytes[offset + 2].toLong() and 0xff) shl 16) or ((bytes[offset + 3].toLong() and 0xff) shl 24)
    }

    private fun readLongLE(bytes: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((bytes[offset + i].toLong() and 0xff) shl (i * 8))
        return v
    }
}
