package org.openmmo.bytedex.proxy.netty

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

object GamePackets {

    data class Movement(val x: Short, val y: Short, val direction: Byte)
    data class FaceDirection(val direction: Byte)
    data class EntityFace(val entityId: Long, val direction: Int)
    data class EntityMovement(val entityId: Long, val x: Short, val y: Short, val direction: Byte)
    data class EntityStep(val entityId: Long, val x: Int, val y: Int, val flags: Int, val direction: Int)
    data class EntityStop(val entityId: Long)
    data class SpawnEntity(val entityId: Long, val type: Int, val name: String, val mapId: Int?, val x: Int?, val y: Int?, val variant: Int, val sprite: Int?, val movement: Int?, val direction: Int?, val layer: Int?, val spawnX: Int?, val spawnY: Int?, val tailFlags: List<Int>, val labels: List<String>, val guild: String?)
    data class ChatMessage(val channel: Int, val text: String)
    data class LocalChatMessage(val channel: Int, val entityId: Long?, val name: String?, val text: String)
    data class LoadMap(val region: Int, val mapId: Int, val x: Int, val y: Int, val layer: Int, val flags: Int, val tiles: List<Int>, val tail: List<Int>)
    data class IntValue(val value: Int)
    data class ByteValue(val value: Int)
    data class KeepAlive(val kind: Int, val token: Long)
    data class InteractionRequest(val entityId: Long, val token: Long)
    data class UiState(val value: Int)
    data class DialogAction(val action: Int)
    data class DialogState(val mode: Int, val code: Int, val entityId: Long, val value: Long)
    data class ShopInventory(val mode: Int, val shopId: Int, val entityId: Long, val entries: List<ShopEntry>)
    data class ShopEntry(val itemId: Int, val quantity: Int, val price: Int)
    data class ShopBuy(val itemId: Int, val quantity: Int, val extra: Int)
    data class NpcMarker(val index: Int, val entityId: Long, val kind: Int, val a: Int, val b: Int, val mapId: Int, val x: Int, val y: Int, val flags: Int)
    data class UShortList(val count: Int, val values: List<Int>)
    data class IndexedUShortList(val index: Int, val count: Int, val values: List<Int>)
    data class EntityShortValue(val entityId: Long, val value: Int)
    data class ByteList(val kind: Int, val values: List<Int>)
    data class ShortHeader(val a: Int, val b: Int)
    data class TripleShort(val a: Int, val b: Int, val c: Int)
    data class EntityShortTail(val entityId: Long, val value: Int)
    data class BytePair(val a: Int, val b: Int)
    data class RawSummary(val size: Int, val hex: String)
    data class RawFull(val size: Int, val hex: String)
    data class EntityDetail(val entityId: Long, val mode: Int, val values: List<Int>)
    data class PairStateList(val count: Int, val entries: List<PairState>)
    data class PairState(val id: Int, val state: Int)
    data class NamedState(val id: Long, val group: Int, val index: Int, val name: String, val value: Int)
    data class ThreeByteState(val a: Int, val b: Int, val c: Int)
    data class ShortState(val value: Int)
    data class EntityState14(val mode: Int, val kind: Int, val entityId: Long?, val a: Int?, val b: Int?)
    data class State16(val kind: Int, val a: Int, val b: Long, val c: Int, val d: Int)
    data class EntityBytePair(val entityId: Long, val before: Int, val after: Int)
    data class EntityInteraction(val entityId: Long, val action: Int, val mode: Int, val kind: Int, val state: Int, val targetEntityId: Long, val a: Int, val b: Int, val c: Int)
    data class EntityStateBatch(val mode: Int, val a: Int, val b: Int, val flag1: Int, val flag2: Int, val flag3: Int, val entityId: Long, val slots: List<BattleSlot?>, val tail: List<Int>)
    data class BattleSlot(val species: Int, val level: Int, val currentHp: Int, val maxHp: Int, val tail: List<Int>)
    data class PokemonParty(val mode: Int, val index: Int, val partySize: Int, val names: List<String>, val tail: List<Int>)
    data class BattleMoveResult(val mode: Int, val moveId: Int, val count: Int, val flags: List<Int>, val entityId: Long, val tail: List<Int>)
    data class EntityUShort(val entityId: Long, val value: Int)
    data class BattleStats(val entityId: Long, val stats: List<Int>)

    data class TeamCreateRequest(val name: String, val tag: String)
    data class TeamCreateResult(val status: Int, val teamId: Long, val name: String, val tag: String, val messageId: Long, val message: String, val tail: List<Int>)
    data class EntityTextTag(val entityId: Long, val tag: String)
    data class TeamMemberState(val mode: Int, val state: Int, val entityId: Long, val rank: Int, val teamId: Long, val name: String, val tail: List<Int>)
    data class LoginCredentials(val username: String, val blocks: List<LoginCredentialBlock>, val locale: String, val clientA: Long?, val clientB: Long?, val tailHex: String, val size: Int, val hex: String)
    data class LoginCredentialBlock(val type: Int, val size: Int, val hex: String)
    data class LoginEndpoint(val index: Int, val ipv4: String?, val ipv6Hex: String?, val port: Int, val weight: Int)
    data class ExistingSession(val sessionId: Long, val sessionKeyHex: String, val marker: Int, val serverName: String, val extraHex: String, val empty: String, val zeroInt: Long, val zeroShortA: Int, val zeroShortB: Int, val flag: Int, val endpoints: List<LoginEndpoint>, val tailHex: String)
    data class LoginToken(val sessionId: Long, val token: Long, val keyHex: String)
    data class LoginStatus(val code: Int, val flags: Int, val extra: Int)
    data class LoginServerList(val count: Int, val servers: List<LoginServerEntry>, val tailHex: String)
    data class LoginServerEntry(val index: Int, val status: Int, val name: String, val online: Int, val capacity: Int)
    data class LoginGameEndpoint(val state: Int, val sessionId: Long?, val keyHex: String, val marker: Int?, val localAddress: String, val host: String, val port: Long?, val nodes: List<LoginEndpoint>, val tailHex: String)
    data class EntityActionValue(val entityId: Long, val value: Int)
    data class EntityByteValue(val entityId: Long, val value: Int)
    data class EntityUpdate(val entityId: Long, val code: Long, val tail: String)
    data class PokemonStorageMove(val kind: Int, val fromBox: Int, val fromSlot: Int, val toBox: Int, val toSlot: Int)
    data class SmallRequest(val a: Int, val b: Long)
    data class EntityKindRequest(val kind: Int, val entityId: Long)
    data class EntityTargetPosition(val mode: Int, val entityId: Long, val x: Int, val y: Int)
    data class ByteUShortByte(val a: Int, val b: Int, val c: Int)
    data class ByteUShort(val a: Int, val b: Int)
    data class ByteShortShort(val a: Int, val b: Int, val c: Int)
    data class EntityPairShort(val entityA: Long, val entityB: Long, val value: Int)
    data class EntityPairTail(val entityA: Long, val entityB: Long, val tailHex: String)
    data class ChatFrame(val kind: Int, val marker: Int?, val encodedSize: Int, val tail: Int?, val encodedHex: String)
    data class BattleOrSceneBlob(val mode: Int, val a: Long, val b: Long, val c: Long, val d: Long, val names: List<String>, val entityIds: List<Long>, val size: Int, val hex: String)
    data class EntityMove(val entityId: Long, val area: Int, val x: Int, val y: Int, val kind: Int, val flags: Int)

    fun decodeMovement(payload: ByteBuf): Movement {
        return Movement(payload.readShortLE(), payload.readShortLE(), payload.readByte())
    }

    fun encodeMovement(packet: Movement): ByteBuf {
        val buf = Unpooled.buffer(5)
        buf.writeShortLE(packet.x.toInt())
        buf.writeShortLE(packet.y.toInt())
        buf.writeByte(packet.direction.toInt())
        return buf
    }

    fun decodeFaceDirection(payload: ByteBuf): FaceDirection {
        return FaceDirection(payload.readByte())
    }

    fun encodeFaceDirection(packet: FaceDirection): ByteBuf {
        val buf = Unpooled.buffer(1)
        buf.writeByte(packet.direction.toInt())
        return buf
    }

    fun decodeEntityFace(payload: ByteBuf): EntityFace {
        return EntityFace(payload.readLongLE(), payload.readUnsignedByte().toInt())
    }

    fun decodeEntityMovement(payload: ByteBuf): EntityMovement {
        return EntityMovement(payload.readLongLE(), payload.readShortLE(), payload.readShortLE(), payload.readByte())
    }

    fun encodeEntityMovement(packet: EntityMovement): ByteBuf {
        val buf = Unpooled.buffer(11)
        buf.writeLongLE(packet.entityId)
        buf.writeShortLE(packet.x.toInt())
        buf.writeShortLE(packet.y.toInt())
        buf.writeByte(packet.direction.toInt())
        return buf
    }

    fun decodeEntityStep(payload: ByteBuf): EntityStep {
        val entityId = payload.readLongLE()
        val x = payload.readUnsignedByte().toInt()
        val y = payload.readUnsignedByte().toInt()
        val flags = payload.readUnsignedByte().toInt()
        return EntityStep(entityId, x, y, flags, flags and 0x03)
    }

    fun decodeEntityStop(payload: ByteBuf): EntityStop {
        return EntityStop(payload.readLongLE())
    }

    fun encodeEntityStop(packet: EntityStop): ByteBuf {
        val buf = Unpooled.buffer(8)
        buf.writeLongLE(packet.entityId)
        return buf
    }

    fun decodeSpawnEntity(payload: ByteBuf): SpawnEntity {
        val bytes = ByteArray(payload.readableBytes()).also { payload.getBytes(payload.readerIndex(), it) }
        val entityId = Unpooled.wrappedBuffer(bytes).readLongLE()
        val type = bytes.getOrNull(8)?.toInt()?.and(0xff) ?: -1
        val nameEnd = findUtf16Null(bytes, 10)
        val nameStart = findLikelyUtf16Start(bytes, nameEnd)
        val name = if (nameStart >= 0 && nameEnd > nameStart) safeText(utf16Le(bytes, nameStart, nameEnd)) else ""
        val tail = if (nameEnd >= 0) nameEnd + 2 else bytes.size
        val mapId = readUShortLE(bytes, tail)
        val x = readUShortLE(bytes, tail + 3)
        val y = readUShortLE(bytes, tail + 5)
        val variant = bytes.getOrNull(9)?.toInt()?.and(0xff) ?: -1
        val sprite = readUShortLE(bytes, 10)
        val movement = bytes.getOrNull(tail + 7)?.toInt()?.and(0xff)
        val direction = bytes.getOrNull(tail + 8)?.toInt()?.and(0x03)
        val layer = if (bytes.size > tail + 2) ((bytes[tail].toInt() and 0xff) or ((bytes[tail + 1].toInt() and 0xff) shl 8)) else null
        val spawnX = readUShortLE(bytes, tail + 3)
        val spawnY = readUShortLE(bytes, tail + 5)
        val guildRange = findTrailingUtf16String(bytes, tail + 9)
        val guild = guildRange?.let { safeText(utf16Le(bytes, it.first, it.second)) }
        val flagsEnd = guildRange?.first ?: bytes.size
        val tailFlags = bytes.copyOfRange(tail + 7, minOf(flagsEnd, bytes.size)).map { it.toInt() and 0xff }
        val labels = if (flagsEnd < bytes.size) utf16NullStrings(bytes.copyOfRange(flagsEnd, bytes.size)).filter { it.isNotEmpty() }.map { safeText(it) } else emptyList()
        return SpawnEntity(entityId, type, name, mapId, x, y, variant, sprite, movement, direction, layer, spawnX, spawnY, tailFlags, labels, guild)
    }

    fun decodeChatMessage(payload: ByteBuf): ChatMessage {
        val channel = payload.readUnsignedByte().toInt()
        val bytes = ByteArray(payload.readableBytes()).also { payload.readBytes(it) }
        val end = findUtf16Null(bytes, 0).takeIf { it >= 0 } ?: bytes.size
        return ChatMessage(channel, utf16Le(bytes, 0, end))
    }

    fun decodeLocalChatMessage(payload: ByteBuf): LocalChatMessage {
        val channel = payload.readUnsignedByte().toInt()
        if (channel != 0 || payload.readableBytes() < 8) {
            val bytes = ByteArray(payload.readableBytes()).also { payload.readBytes(it) }
            val end = findUtf16Null(bytes, 0).takeIf { it >= 0 } ?: bytes.size
            return LocalChatMessage(channel, null, null, utf16Le(bytes, 0, end))
        }
        val entityId = payload.readLongLE()
        val strings = utf16NullStrings(ByteArray(payload.readableBytes()).also { payload.readBytes(it) }).filter { it.isNotEmpty() }
        return LocalChatMessage(channel, entityId, strings.firstOrNull(), strings.drop(1).lastOrNull() ?: "")
    }

    fun decodeOutgoingLocalChat(payload: ByteBuf): LocalChatMessage {
        val channel = payload.readUnsignedByte().toInt()
        val bytes = ByteArray(payload.readableBytes()).also { payload.readBytes(it) }
        val end = findUtf16Null(bytes, 0).takeIf { it >= 0 } ?: bytes.size
        return LocalChatMessage(channel, null, null, utf16Le(bytes, 0, end))
    }

    fun decodeLoadMap(payload: ByteBuf): LoadMap {
        val region = payload.readUnsignedByte().toInt()
        val mapId = payload.readUnsignedShortLE()
        val x = payload.readUnsignedByte().toInt()
        val y = payload.readUnsignedShortLE()
        val layer = payload.readUnsignedByte().toInt()
        val flags = payload.readIntLE()
        val rest = ByteArray(payload.readableBytes()).also { payload.readBytes(it) }
        val tiles = mutableListOf<Int>()
        var i = 0
        while (i + 1 < rest.size) { tiles += (rest[i].toInt() and 0xff) or ((rest[i + 1].toInt() and 0xff) shl 8); i += 2 }
        return LoadMap(region, mapId, x, y, layer, flags, tiles, if (i < rest.size) rest.drop(i).map { it.toInt() and 0xff } else emptyList())
    }

    fun decodeIntValue(payload: ByteBuf): IntValue {
        return IntValue(payload.readIntLE())
    }

    fun decodeByteValue(payload: ByteBuf): ByteValue {
        return ByteValue(payload.readUnsignedByte().toInt())
    }

    fun decodeKeepAlive(payload: ByteBuf): KeepAlive {
        return KeepAlive(payload.readUnsignedByte().toInt(), payload.readLongLE())
    }

    fun decodeInteractionRequest(payload: ByteBuf): InteractionRequest {
        return InteractionRequest(payload.readLongLE(), payload.readLongLE())
    }

    fun decodeUiState(payload: ByteBuf): UiState {
        return UiState(payload.readUnsignedByte().toInt())
    }

    fun decodeDialogAction(payload: ByteBuf): DialogAction {
        return DialogAction(payload.readUnsignedShortLE())
    }

    fun decodeDialogState(payload: ByteBuf): DialogState {
        val mode = payload.readUnsignedByte().toInt()
        val code = payload.readUnsignedShortLE()
        payload.skipBytes(3)
        val entityId = payload.readLongLE()
        val value = payload.readIntLE().toLong() and 0xffffffffL
        payload.skipBytes(1)
        return DialogState(mode, code, entityId, value)
    }

    fun decodeShopInventory(payload: ByteBuf): ShopInventory {
        val mode = payload.readUnsignedByte().toInt()
        val shopId = payload.readUnsignedShortLE()
        val count = payload.readUnsignedShortLE()
        payload.skipBytes(2)
        val entityId = payload.readLongLE()
        payload.skipBytes(2)
        val entries = mutableListOf<ShopEntry>()
        while (payload.readableBytes() >= 10) entries += ShopEntry(payload.readUnsignedShortLE(), payload.readIntLE(), payload.readIntLE())
        return ShopInventory(mode, shopId, entityId, entries.take(count))
    }

    fun decodeShopBuy(payload: ByteBuf): ShopBuy {
        return ShopBuy(payload.readUnsignedShortLE(), payload.readUnsignedShortLE(), payload.readUnsignedByte().toInt())
    }

    fun decodeNpcMarker(payload: ByteBuf): NpcMarker {
        return NpcMarker(payload.readUnsignedByte().toInt(), payload.readLongLE(), payload.readUnsignedByte().toInt(), payload.readUnsignedShortLE(), payload.readUnsignedShortLE(), payload.readUnsignedShortLE(), payload.readUnsignedShortLE(), payload.readUnsignedShortLE(), payload.readUnsignedShortLE())
    }

    fun decodeUShortList(payload: ByteBuf): UShortList {
        val count = payload.readUnsignedShortLE()
        val values = mutableListOf<Int>()

        while (payload.readableBytes() >= 2) values += payload.readUnsignedShortLE()
        return UShortList(count, values)
    }

    fun decodeIndexedUShortList(payload: ByteBuf): IndexedUShortList {
        val index = payload.readUnsignedByte().toInt()
        val count = payload.readUnsignedShortLE()
        val values = mutableListOf<Int>()
        while (payload.readableBytes() >= 4) {
            values += payload.readUnsignedShortLE()
            payload.skipBytes(2)
        }
        return IndexedUShortList(index, count, values)
    }

    fun decodeEntityShortValue(payload: ByteBuf): EntityShortValue {
        return EntityShortValue(payload.readLongLE(), payload.readUnsignedShortLE())
    }

    fun decodeByteList(payload: ByteBuf): ByteList {
        val kind = payload.readUnsignedByte().toInt()
        val count = payload.readUnsignedShortLE()
        val values = mutableListOf<Int>()
        repeat(minOf(count, payload.readableBytes())) { values += payload.readUnsignedByte().toInt() }
        return ByteList(kind, values)
    }

    fun decodeShortHeader(payload: ByteBuf): ShortHeader {
        return ShortHeader(payload.readUnsignedByte().toInt(), payload.readUnsignedByte().toInt())
    }

    fun decodeTripleShort(payload: ByteBuf): TripleShort {
        return TripleShort(payload.readUnsignedShortLE(), payload.readUnsignedShortLE(), payload.readUnsignedShortLE())
    }

    fun decodeEntityShortTail(payload: ByteBuf): EntityShortTail {
        return EntityShortTail(payload.readLongLE(), payload.readUnsignedShortLE())
    }

    fun decodeBytePair(payload: ByteBuf): BytePair {
        return BytePair(payload.readUnsignedByte().toInt(), payload.readUnsignedByte().toInt())
    }

    fun decodeEntityActionValue(payload: ByteBuf): EntityActionValue {
        return EntityActionValue(payload.readLongLE(), payload.readUnsignedShortLE())
    }

    fun decodeEntityByteValue(payload: ByteBuf): EntityByteValue {
        return EntityByteValue(payload.readLongLE(), payload.readUnsignedByte().toInt())
    }

    fun decodeEntityUpdate(payload: ByteBuf): EntityUpdate {
        val entityId = payload.readLongLE()
        val code = payload.readIntLE().toLong() and 0xffffffffL
        val bytes = ByteArray(payload.readableBytes()).also { payload.getBytes(payload.readerIndex(), it) }
        val tail = if (code == 64L && bytes.size >= 3) "box=${bytes[0].toInt() and 0xff}, slot=${readUShortLE(bytes, 1)}" else bytes.take(16).joinToString(" ") { "%02x".format(it) }
        return EntityUpdate(entityId, code, tail)
    }

    fun decodePokemonStorageMove(payload: ByteBuf): PokemonStorageMove {
        return PokemonStorageMove(payload.readUnsignedByte().toInt(), payload.readUnsignedByte().toInt(), payload.readUnsignedShortLE(), payload.readUnsignedByte().toInt(), payload.readUnsignedShortLE())
    }


    fun decodeEntityDetail(payload: ByteBuf): EntityDetail {
        val entityId = payload.readLongLE()
        val mode = payload.readUnsignedShortLE()
        val values = mutableListOf<Int>()
        while (payload.readableBytes() >= 2) values += payload.readUnsignedShortLE()
        return EntityDetail(entityId, mode, values)
    }

    fun decodePairStateList(payload: ByteBuf): PairStateList {
        val count = payload.readUnsignedShortLE()
        val entries = mutableListOf<PairState>()
        repeat(count.coerceAtMost(payload.readableBytes() / 3)) { entries += PairState(payload.readUnsignedShortLE(), payload.readUnsignedByte().toInt()) }
        return PairStateList(count, entries)
    }

    fun decodeNamedState(payload: ByteBuf): NamedState {
        val id = payload.readUnsignedIntLE()
        val group = payload.readUnsignedShortLE()
        val index = payload.readUnsignedByte().toInt()
        val bytes = ByteArray(payload.readableBytes()).also { payload.readBytes(it) }
        val end = findUtf16Null(bytes, 0).takeIf { it >= 0 } ?: bytes.size
        val name = utf16Le(bytes, 0, end)
        val value = bytes.getOrNull(end + 2)?.toInt()?.and(0xff) ?: -1
        return NamedState(id, group, index, name, value)
    }

    fun decodeThreeByteState(payload: ByteBuf): ThreeByteState {
        return ThreeByteState(payload.readUnsignedByte().toInt(), payload.readUnsignedByte().toInt(), payload.readUnsignedByte().toInt())
    }

    fun decodeShortState(payload: ByteBuf): ShortState {
        return ShortState(payload.readUnsignedShortLE())
    }

    fun decodeEntityState14(payload: ByteBuf): EntityState14 {
        val mode = payload.readUnsignedByte().toInt()
        val kind = payload.readUnsignedByte().toInt()
        if (payload.readableBytes() >= 12) return EntityState14(mode, kind, payload.readLongLE(), payload.readUnsignedShortLE(), payload.readUnsignedShortLE())
        return EntityState14(mode, kind, null, if (payload.readableBytes() >= 2) payload.readUnsignedShortLE() else null, if (payload.readableBytes() >= 2) payload.readUnsignedShortLE() else null)
    }


    fun decodeEntityBytePair(payload: ByteBuf): EntityBytePair {
        val before = payload.readUnsignedByte().toInt()
        val entityId = payload.readLongLE()
        val after = payload.readUnsignedByte().toInt()
        return EntityBytePair(entityId, before, after)
    }

    fun decodeEntityInteraction(payload: ByteBuf): EntityInteraction {
        val entityId = payload.readLongLE()
        val action = payload.readUnsignedShortLE()
        val mode = payload.readUnsignedByte().toInt()
        val kind = payload.readUnsignedByte().toInt()
        val state = payload.readUnsignedByte().toInt()
        val targetEntityId = payload.readLongLE()
        val a = if (payload.readableBytes() >= 2) payload.readUnsignedShortLE() else 0
        val b = if (payload.readableBytes() >= 2) payload.readUnsignedShortLE() else 0
        val c = if (payload.readableBytes() >= 2) payload.readUnsignedShortLE() else 0
        return EntityInteraction(entityId, action, mode, kind, state, targetEntityId, a, b, c)
    }

    fun decodeEntityStateBatch(payload: ByteBuf): EntityStateBatch {
        val mode = payload.readUnsignedByte().toInt()
        val a = payload.readUnsignedByte().toInt()
        val b = payload.readUnsignedByte().toInt()
        val flag1 = payload.readUnsignedByte().toInt()
        val flag2 = payload.readUnsignedByte().toInt()
        val flag3 = payload.readUnsignedByte().toInt()
        val entityId = if (payload.readableBytes() >= 8) payload.readLongLE() else 0L
        val slots = mutableListOf<BattleSlot?>()
        if (payload.readableBytes() >= 13) {
            val species = payload.readUnsignedShortLE()
            val level = payload.readUnsignedByte().toInt()
            repeat(6) { payload.readByte() }
            val currentHp = payload.readUnsignedShortLE()
            val maxHp = payload.readUnsignedShortLE()
            slots += BattleSlot(species, level, currentHp, maxHp, emptyList())
        }
        if (payload.readableBytes() >= 2) {
            var skipped = 0
            while (payload.readableBytes() >= 2) {
                val b0 = payload.getUnsignedByte(payload.readerIndex()).toInt()
                val b1 = payload.getUnsignedByte(payload.readerIndex() + 1).toInt()
                if (b0 == 0xFF && b1 == 0x03) {
                    payload.skipBytes(2)
                    if (payload.readableBytes() > 0) payload.readByte()
                    break
                }
                payload.readByte()
                skipped++
                if (skipped > 20) break
            }
            if (payload.readableBytes() >= 13) {
                val species = payload.readUnsignedShortLE()
                val level = payload.readUnsignedByte().toInt()
                repeat(6) { payload.readByte() }
                val currentHp = payload.readUnsignedShortLE()
                val maxHp = payload.readUnsignedShortLE()
                slots += BattleSlot(species, level, currentHp, maxHp, emptyList())
            }
        }
        val tail = readRemainingBytes(payload)
        return EntityStateBatch(mode, a, b, flag1, flag2, flag3, entityId, slots, tail)
    }



    fun decodePokemonParty(payload: ByteBuf): PokemonParty {
        val mode = payload.readUnsignedByte().toInt()
        val index = payload.readUnsignedByte().toInt()
        val partySize = payload.readUnsignedByte().toInt()
        val remaining = ByteArray(payload.readableBytes()).also { payload.readBytes(it) }
        val names = mutableListOf<String>()
        // Each pokemon struct has marker bytes 98 9d 95 at offsets 10-12 from struct start
        // Use that to find struct boundaries, then extract OT+Nickname from each
        val marker = byteArrayOf(0x98.toByte(), 0x9d.toByte(), 0x95.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x98.toByte(), 0x9d.toByte(), 0x95.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00)
        var off = 0
        while (off < remaining.size - 30) {
            // Find next marker
            var markerPos = -1
            for (i in off..(remaining.size - 40)) {
                if (remaining.sliceArray(i until i + marker.size).contentEquals(marker)) {
                    markerPos = i
                    break
                }
            }
            if (markerPos == -1) break
            // OT name starts after marker + padding. From hex analysis: double-marker at struct_offset 10-25, OT at struct_offset 43
            // markerPos is at struct_offset 10, so OT = markerPos + 33
            val otStart = markerPos + 33
            if (otStart + 4 > remaining.size) break
            // Read OT name
            var pos = otStart
            val otChars = StringBuilder()
            while (pos + 1 < remaining.size) {
                val c = (remaining[pos].toInt() and 0xff) or ((remaining[pos + 1].toInt() and 0xff) shl 8)
                pos += 2
                if (c == 0) break
                if (c in 0x20..0x7e) otChars.append(c.toChar()) else { otChars.clear(); break }
            }
            if (otChars.isEmpty()) { off = markerPos + marker.size; continue }
            // Read Nickname
            val nickChars = StringBuilder()
            while (pos + 1 < remaining.size) {
                val c = (remaining[pos].toInt() and 0xff) or ((remaining[pos + 1].toInt() and 0xff) shl 8)
                pos += 2
                if (c == 0) break
                if (c in 0x20..0x7e) nickChars.append(c.toChar()) else { nickChars.clear(); break }
            }
            val nick = safeText(nickChars.toString())
            if (nick.isNotEmpty()) names.add(nick)
            off = pos
        }
        return PokemonParty(mode, index, partySize, names, emptyList())
    }

    fun decodeBattleMoveResult(payload: ByteBuf): BattleMoveResult {
        val mode = payload.readUnsignedByte().toInt()
        val b = payload.readUnsignedByte().toInt()
        val moveId = payload.readUnsignedShortLE()
        val count = payload.readUnsignedByte().toInt()
        val flags = (1..4).map { if (payload.readableBytes() > 0) payload.readUnsignedByte().toInt() else 0 }
        val entityId = if (payload.readableBytes() >= 8) payload.readLongLE() else 0L
        val tail = readRemainingBytes(payload)
        return BattleMoveResult(mode, moveId, count, flags, entityId, tail)
    }

    fun decodeEntityUShort(payload: ByteBuf): EntityUShort {
        val entityId = payload.readLongLE()
        val value = payload.readUnsignedShortLE()
        return EntityUShort(entityId, value)
    }

    fun decodeBattleStats(payload: ByteBuf): BattleStats {
        val entityId = payload.readLongLE()
        val stats = readRemainingBytes(payload)
        return BattleStats(entityId, stats)
    }


    fun decodeEntityLongState(payload: ByteBuf): BattleStats {
        val entityId = payload.readLongLE()
        val stats = readRemainingBytes(payload)
        return BattleStats(entityId, stats)
    }



    fun decodeTeamCreateRequest(payload: ByteBuf): TeamCreateRequest {
        val bytes = ByteArray(payload.readableBytes()).also { payload.readBytes(it) }
        val strings = utf16NullStrings(bytes).filter { it.isNotEmpty() }.map { safeText(it) }
        return TeamCreateRequest(strings.getOrNull(0) ?: "", strings.getOrNull(1) ?: "")
    }

    fun decodeTeamCreateResult(payload: ByteBuf): TeamCreateResult {
        val status = payload.readUnsignedByte().toInt()
        val teamId = payload.readLongLE()
        val name = readUtf16Null(payload)
        val tag = readUtf16Null(payload)
        val messageId = payload.readUnsignedIntLE()
        val message = readUtf16Null(payload)
        return TeamCreateResult(status, teamId, safeText(name), safeText(tag), messageId, safeText(message), readRemainingBytes(payload))
    }

    fun decodeEntityTextTag(payload: ByteBuf): EntityTextTag {
        val entityId = payload.readLongLE()
        return EntityTextTag(entityId, safeText(readUtf16Null(payload)))
    }

    fun decodeTeamMemberState(payload: ByteBuf): TeamMemberState {
        val mode = payload.readUnsignedByte().toInt()
        val state = payload.readUnsignedByte().toInt()
        val entityId = payload.readLongLE()
        val rank = payload.readUnsignedByte().toInt()
        val teamId = payload.readUnsignedIntLE()
        val name = readUtf16Null(payload)
        return TeamMemberState(mode, state, entityId, rank, teamId, safeText(name), readRemainingBytes(payload))
    }


    fun decodeTeamMessage(payload: ByteBuf): ChatMessage {
        val bytes = ByteArray(payload.readableBytes()).also { payload.readBytes(it) }
        val end = findUtf16Null(bytes, 0).takeIf { it >= 0 } ?: bytes.size
        return ChatMessage(0, safeText(utf16Le(bytes, 0, end)))
    }


    fun decodeState16(payload: ByteBuf): State16 {
        return State16(payload.readUnsignedByte().toInt(), payload.readUnsignedShortLE(), payload.readLongLE(), payload.readUnsignedShortLE(), payload.readUnsignedShortLE())
    }


    fun decodeSmallRequest(payload: ByteBuf): SmallRequest {
        return SmallRequest(payload.readUnsignedShortLE(), payload.readIntLE().toLong() and 0xffffffffL)
    }

    fun decodeEntityKindRequest(payload: ByteBuf): EntityKindRequest {
        return EntityKindRequest(payload.readUnsignedByte().toInt(), payload.readLongLE())
    }

    fun decodeRawSummary(payload: ByteBuf): RawSummary {
        val bytes = ByteArray(payload.readableBytes()).also { payload.getBytes(payload.readerIndex(), it) }
        return RawSummary(bytes.size, bytes.take(24).joinToString(" ") { "%02x".format(it) })
    }

    fun decodeRawFull(payload: ByteBuf): RawFull {
        val bytes = ByteArray(payload.readableBytes()).also { payload.getBytes(payload.readerIndex(), it) }
        return RawFull(bytes.size, bytes.joinToString(" ") { "%02x".format(it) })
    }

    fun decodeEntityTargetPosition(payload: ByteBuf): EntityTargetPosition {
        return EntityTargetPosition(payload.readUnsignedShortLE(), payload.readLongLE(), payload.readUnsignedShortLE(), payload.readUnsignedShortLE())
    }

    fun decodeByteUShortByte(payload: ByteBuf): ByteUShortByte {
        return ByteUShortByte(payload.readUnsignedByte().toInt(), payload.readUnsignedShortLE(), payload.readUnsignedByte().toInt())
    }

    fun decodeByteUShort(payload: ByteBuf): ByteUShort {
        return ByteUShort(payload.readUnsignedByte().toInt(), payload.readUnsignedShortLE())
    }

    fun decodeByteShortShort(payload: ByteBuf): ByteShortShort {
        return ByteShortShort(payload.readUnsignedByte().toInt(), payload.readUnsignedShortLE(), payload.readUnsignedShortLE())
    }

    fun decodeEntityPairShort(payload: ByteBuf): EntityPairShort {
        return EntityPairShort(payload.readLongLE(), payload.readLongLE(), payload.readUnsignedShortLE())
    }

    fun decodeEntityPairTail(payload: ByteBuf): EntityPairTail {
        return EntityPairTail(payload.readLongLE(), payload.readLongLE(), readHex(payload, payload.readableBytes()))
    }

    fun decodeChatFrame(payload: ByteBuf): ChatFrame {
        val kind = payload.readUnsignedByte().toInt()
        val body = ByteArray(payload.readableBytes()).also { payload.readBytes(it) }
        val tail = if (body.size >= 2) (body[body.size - 2].toInt() and 0xff) or ((body[body.size - 1].toInt() and 0xff) shl 8) else null
        val end = if (body.size >= 2) body.size - 2 else body.size
        val marker = body.firstOrNull()?.toInt()?.and(0xff)
        val encoded = body.copyOfRange(if (marker == null) 0 else 1, end)
        return ChatFrame(kind, marker, encoded.size, tail, encoded.joinToString(" ") { "%02x".format(it) })
    }

    fun decodeEntityMove(payload: ByteBuf): EntityMove {
        return EntityMove(payload.readLongLE(), payload.readUnsignedShortLE(), payload.readUnsignedByte().toInt(), payload.readUnsignedByte().toInt(), payload.readUnsignedByte().toInt(), payload.readUnsignedByte().toInt())
    }

    fun decodeBattleOrSceneBlob(payload: ByteBuf): BattleOrSceneBlob {
        val bytes = ByteArray(payload.readableBytes()).also { payload.getBytes(payload.readerIndex(), it) }
        val scan = Unpooled.wrappedBuffer(bytes)
        val mode = if (scan.readableBytes() >= 2) scan.readUnsignedShortLE() else 0
        val a = if (scan.readableBytes() >= 4) scan.readUnsignedIntLE() else 0
        val b = if (scan.readableBytes() >= 4) scan.readUnsignedIntLE() else 0
        val c = if (scan.readableBytes() >= 4) scan.readUnsignedIntLE() else 0
        val d = if (scan.readableBytes() >= 4) scan.readUnsignedIntLE() else 0
        val names = utf16NullStrings(bytes).filter { it.length >= 2 && it.all { ch -> ch.code in 0x20..0x7e } }
        val ids = mutableListOf<Long>()
        var i = 0
        while (i + 7 < bytes.size) {
            val v = (bytes[i].toLong() and 0xff) or ((bytes[i + 1].toLong() and 0xff) shl 8) or ((bytes[i + 2].toLong() and 0xff) shl 16) or ((bytes[i + 3].toLong() and 0xff) shl 24) or ((bytes[i + 4].toLong() and 0xff) shl 32) or ((bytes[i + 5].toLong() and 0xff) shl 40) or ((bytes[i + 6].toLong() and 0xff) shl 48) or ((bytes[i + 7].toLong() and 0xff) shl 56)
            if (v > 1000000L && v !in ids) ids += v
            i++
        }
        return BattleOrSceneBlob(mode, a, b, c, d, names, ids.take(8), bytes.size, bytes.joinToString(" ") { "%02x".format(it) })
    }

    fun decodeLoginToken(payload: ByteBuf): LoginToken {
        return LoginToken(payload.readUnsignedIntLE(), payload.readUnsignedIntLE(), readHex(payload, payload.readableBytes()))
    }

    fun decodeLoginStatus(payload: ByteBuf): LoginStatus {
        return LoginStatus(payload.readUnsignedByte().toInt(), payload.readUnsignedByte().toInt(), payload.readUnsignedByte().toInt())
    }

    fun decodeLoginServerList(payload: ByteBuf): LoginServerList {
        val count = payload.readUnsignedByte().toInt()
        val servers = mutableListOf<LoginServerEntry>()
        repeat(count) {
            val index = payload.readUnsignedByte().toInt()
            val status = payload.readUnsignedByte().toInt()
            val name = readUtf16Null(payload)
            val online = payload.readUnsignedShortLE()
            val capacity = payload.readUnsignedShortLE()
            servers += LoginServerEntry(index, status, name, online, capacity)
        }
        return LoginServerList(count, servers, readHex(payload, payload.readableBytes()))
    }

    fun decodeLoginGameEndpoint(payload: ByteBuf): LoginGameEndpoint {
        val state = payload.readUnsignedByte().toInt()
        if (state != 0) return LoginGameEndpoint(state, null, "", null, "", "", null, emptyList(), readHex(payload, payload.readableBytes()))
        val sessionId = payload.readUnsignedIntLE()
        val keyHex = readByteLenHex(payload)
        val marker = payload.readUnsignedByte().toInt()
        val localAddress = readByteLenAddress(payload)
        val host = readUtf16Null(payload)
        val port = payload.readUnsignedIntLE()
        val nodes = readLoginEndpoints(payload)
        return LoginGameEndpoint(state, sessionId, keyHex, marker, localAddress, host, port, nodes, readHex(payload, payload.readableBytes()))
    }

    fun decodeLoginCredentials(payload: ByteBuf): LoginCredentials {
        val bytes = ByteArray(payload.readableBytes()).also { payload.getBytes(payload.readerIndex(), it) }
        val buf = Unpooled.wrappedBuffer(bytes)
        val username = readUtf16Null(buf)
        val blocks = mutableListOf<LoginCredentialBlock>()
        while (buf.readableBytes() >= 34 && (buf.getUnsignedByte(buf.readerIndex()).toInt() == 0 || buf.getUnsignedByte(buf.readerIndex()).toInt() == 1) && buf.getUnsignedByte(buf.readerIndex() + 1).toInt() <= buf.readableBytes() - 2) {
            val type = buf.readUnsignedByte().toInt()
            val size = buf.readUnsignedByte().toInt()
            blocks += LoginCredentialBlock(type, size, readHex(buf, size))
        }
        val locale = if (buf.readableBytes() >= 2) readUtf16Null(buf) else ""
        val clientA = if (buf.readableBytes() >= 4) buf.readUnsignedIntLE() else null
        val clientB = if (buf.readableBytes() >= 4) buf.readUnsignedIntLE() else null
        val tailHex = readHex(buf, buf.readableBytes())
        return LoginCredentials(username, blocks, locale, clientA, clientB, tailHex, bytes.size, bytes.joinToString(" ") { "%02x".format(it) })
    }

    fun decodeExistingSession(payload: ByteBuf): ExistingSession {
        val sessionId = payload.readLongLE()
        val sessionKeyHex = readByteLenHex(payload)
        val marker = payload.readUnsignedByte().toInt()
        val serverName = readUtf16Null(payload)
        val extraHex = readByteLenHex(payload)
        val empty = readUtf16Null(payload)
        val zeroInt = payload.readUnsignedIntLE()
        val zeroShortA = payload.readUnsignedShortLE()
        val zeroShortB = payload.readUnsignedShortLE()
        val flag = payload.readUnsignedByte().toInt()
        val endpoints = readLoginEndpoints(payload)
        return ExistingSession(sessionId, sessionKeyHex, marker, serverName, extraHex, empty, zeroInt, zeroShortA, zeroShortB, flag, endpoints, readHex(payload, payload.readableBytes()))
    }

    private fun readUShortLE(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 1 >= bytes.size) return null
        return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    fun findUtf16Null(bytes: ByteArray, start: Int): Int {
        var i = start
        while (i + 1 < bytes.size) {
            if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte()) return i
            i += 2
        }
        return -1
    }

    fun findTrailingUtf16String(bytes: ByteArray, start: Int): Pair<Int, Int>? {
        var i = start
        while (i + 3 < bytes.size) {
            if ((bytes[i].toInt() and 0xff) in 0x20..0x7e && bytes[i + 1] == 0.toByte()) {
                var j = i
                var count = 0
                while (j + 1 < bytes.size && (bytes[j].toInt() and 0xff) in 0x20..0x7e && bytes[j + 1] == 0.toByte()) { count++; j += 2 }
                if (count >= 2 && j + 1 < bytes.size && bytes[j] == 0.toByte() && bytes[j + 1] == 0.toByte()) return i to j
            }
            i++
        }
        return null
    }


    private fun readRemainingBytes(payload: ByteBuf): List<Int> {
        val out = mutableListOf<Int>()
        while (payload.readableBytes() > 0) out += payload.readUnsignedByte().toInt()
        return out
    }


    fun findLikelyUtf16Start(bytes: ByteArray, end: Int): Int {
        if (end < 2) return -1
        var i = end - 2
        while (i >= 0 && bytes[i + 1] == 0.toByte() && bytes[i].toInt() in 0x20..0x7e) i -= 2
        return i + 2
    }

    fun utf16Le(bytes: ByteArray, start: Int, end: Int): String {
        val chars = StringBuilder()
        var i = start
        while (i + 1 < end) {
            val c = (bytes[i].toInt() and 0xff) or ((bytes[i + 1].toInt() and 0xff) shl 8)
            if (c != 0) chars.append(c.toChar())
            i += 2
        }
        return chars.toString()
    }

    private fun utf16NullStrings(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        while (start < bytes.size) {
            val end = findUtf16Null(bytes, start)
            if (end < 0) break
            out += utf16Le(bytes, start, end)
            start = end + 2
        }
        return out
    }

    fun safeText(value: String): String {
        return value.map { if (it.isSurrogate() || (it.code < 0x20 && it !in "\t\n\r")) '?' else it }.joinToString("")
    }


    private fun readByteLenHex(payload: ByteBuf): String {
        val size = payload.readUnsignedByte().toInt()
        return readHex(payload, size)
    }

    private fun readHex(payload: ByteBuf, size: Int): String {
        val bytes = ByteArray(size.coerceAtMost(payload.readableBytes()))
        payload.readBytes(bytes)
        return bytes.joinToString(" ") { "%02x".format(it) }
    }

    private fun readTypedIp(payload: ByteBuf): String? {
        return when (val type = payload.readUnsignedByte().toInt()) {
            4 -> {
                val v = payload.readIntLE()
                "${(v ushr 24) and 0xff}.${(v ushr 16) and 0xff}.${(v ushr 8) and 0xff}.${v and 0xff}"
            }
            6 -> readHex(payload, 16)
            else -> "type$type"
        }
    }


    private fun readByteLenAddress(payload: ByteBuf): String {
        val size = payload.readUnsignedByte().toInt()
        if (size == 4 && payload.readableBytes() >= 4) return "${payload.readUnsignedByte()}.${payload.readUnsignedByte()}.${payload.readUnsignedByte()}.${payload.readUnsignedByte()}"
        return readHex(payload, size)
    }

    private fun readLoginEndpoints(payload: ByteBuf): List<LoginEndpoint> {
        if (payload.readableBytes() <= 0) return emptyList()
        val count = payload.readUnsignedByte().toInt()
        val endpoints = mutableListOf<LoginEndpoint>()
        repeat(count) {
            val index = payload.readUnsignedByte().toInt()
            val ipv4 = readTypedIp(payload)
            val ipv6Hex = readTypedIp(payload)
            val port = payload.readUnsignedShortLE()
            val weight = payload.readUnsignedByte().toInt()
            endpoints += LoginEndpoint(index, ipv4, ipv6Hex, port, weight)
        }
        return endpoints
    }

    private fun readUtf16Null(payload: ByteBuf): String {
        val chars = StringBuilder()
        while (payload.readableBytes() >= 2) {
            val c = payload.readUnsignedShortLE()
            if (c == 0) break
            chars.append(c.toChar())
        }
        return chars.toString()
    }
}
