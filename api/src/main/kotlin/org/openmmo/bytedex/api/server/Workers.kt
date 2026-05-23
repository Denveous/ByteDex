package org.openmmo.bytedex.api.server

import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.openmmo.bytedex.api.jooq.Tables.IDEMPOTENCY_KEYS
import org.openmmo.bytedex.api.jooq.Tables.PACKETS
import org.openmmo.bytedex.api.jooq.Tables.PACKET_SCHEMAS
import org.openmmo.bytedex.api.jooq.Tables.SCHEMA_DIRTY_QUEUE
import org.openmmo.bytedex.api.jooq.Tables.SESSIONS
import org.openmmo.bytedex.api.jooq.enums.SessionStatus
import org.openmmo.bytedex.api.model.InferredField
import org.openmmo.bytedex.api.model.InferredFieldType
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

private val log = LoggerFactory.getLogger("Workers")

private const val INFERENCE_SAMPLE_LIMIT = 1000
private const val SESSION_IDLE_HOURS = 24L
private const val IDEMPOTENCY_TTL_HOURS = 24L
private const val CLASS_GLOBAL = 0
private const val CLASS_SESSION = 1
private const val CLASS_VARIABLE = 2

private fun byteClass(payloads: List<Pair<UUID, ByteArray>>, offset: Int): Int {
    val first = payloads[0].second[offset]
    if (payloads.all { it.second[offset] == first }) return CLASS_GLOBAL
    val constantPerSession = payloads.groupBy({ it.first }, { it.second[offset] })
        .all { (_, bytes) -> bytes.all { it == bytes[0] } }
    return if (constantPerSession) CLASS_SESSION else CLASS_VARIABLE
}

private fun field(cls: Int, offset: Int, length: Int) = InferredField(
    offset = offset,
    length = length,
    type = InferredFieldType.bytes,
    confidence = if (cls == CLASS_VARIABLE) 0.5f else 1.0f,
    isGlobalConstant = cls == CLASS_GLOBAL,
    isSessionConstant = cls == CLASS_SESSION,
)

internal fun inferFields(payloads: List<Pair<UUID, ByteArray>>): List<InferredField> {
    if (payloads.isEmpty()) return emptyList()
    val minLen = payloads.minOf { it.second.size }
    if (minLen == 0) return emptyList()
    val maxLen = payloads.maxOf { it.second.size }

    val fields = mutableListOf<InferredField>()
    var start = 0
    var cls = byteClass(payloads, 0)
    for (i in 1 until minLen) {
        val c = byteClass(payloads, i)
        if (c != cls) {
            fields += field(cls, start, i - start)
            start = i
            cls = c
        }
    }
    fields += field(cls, start, minLen - start)
    if (maxLen > minLen) fields += field(CLASS_VARIABLE, minLen, maxLen - minLen)
    return fields
}

fun Application.startWorkers(dsl: DSLContext) {
    launch { loop(5_000) { runInference(dsl) } }
    launch { loop(60_000) { autoCloseIdleSessions(dsl) } }
    launch { loop(3_600_000) { gcIdempotencyKeys(dsl) } }
}

private suspend fun Application.loop(periodMs: Long, body: suspend () -> Unit) {
    while (isActive) {
        runCatching { body() }.onFailure { log.error("worker iteration failed", it) }
        delay(periodMs.milliseconds)
    }
}

internal suspend fun runInference(dsl: DSLContext) = withContext(Dispatchers.IO) {
    val dirty = dsl.selectFrom(SCHEMA_DIRTY_QUEUE).fetch()
    for (row in dirty) {
        val gv = row.get(SCHEMA_DIRTY_QUEUE.GAME_VERSION)
        val proto = row.get(SCHEMA_DIRTY_QUEUE.PROTOCOL)
        val dir = row.get(SCHEMA_DIRTY_QUEUE.DIRECTION)
        val pid = row.get(SCHEMA_DIRTY_QUEUE.PACKET_ID)
        val markedAt = row.get(SCHEMA_DIRTY_QUEUE.MARKED_AT)

        val samples = dsl.select(PACKETS.SESSION_ID, PACKETS.PAYLOAD)
            .from(PACKETS)
            .where(PACKETS.GAME_VERSION.eq(gv))
            .and(PACKETS.PROTOCOL.eq(proto))
            .and(PACKETS.DIRECTION.eq(dir))
            .and(PACKETS.PACKET_ID.eq(pid))
            .orderBy(PACKETS.SUBMITTED_AT.desc())
            .limit(INFERENCE_SAMPLE_LIMIT)
            .fetch()
            .map { it.value1() to it.value2() }

        if (samples.isNotEmpty()) {
            val fields = inferFields(samples)
            dsl.insertInto(PACKET_SCHEMAS)
                .set(PACKET_SCHEMAS.GAME_VERSION, gv)
                .set(PACKET_SCHEMAS.PROTOCOL, proto)
                .set(PACKET_SCHEMAS.DIRECTION, dir)
                .set(PACKET_SCHEMAS.PACKET_ID, pid)
                .set(PACKET_SCHEMAS.SAMPLE_COUNT, samples.size)
                .set(PACKET_SCHEMAS.SESSION_COUNT, samples.map { it.first }.distinct().size)
                .set(PACKET_SCHEMAS.FIELDS, JSONB.valueOf(Json.encodeToString(ListSerializer(InferredField.serializer()), fields)))
                .set(PACKET_SCHEMAS.GENERATED_AT, now())
                .onConflict(PACKET_SCHEMAS.GAME_VERSION, PACKET_SCHEMAS.PROTOCOL, PACKET_SCHEMAS.DIRECTION, PACKET_SCHEMAS.PACKET_ID)
                .doUpdate()
                .set(PACKET_SCHEMAS.SAMPLE_COUNT, samples.size)
                .set(PACKET_SCHEMAS.SESSION_COUNT, samples.map { it.first }.distinct().size)
                .set(PACKET_SCHEMAS.FIELDS, JSONB.valueOf(Json.encodeToString(ListSerializer(InferredField.serializer()), fields)))
                .set(PACKET_SCHEMAS.GENERATED_AT, now())
                .execute()
        }

        dsl.deleteFrom(SCHEMA_DIRTY_QUEUE)
            .where(SCHEMA_DIRTY_QUEUE.GAME_VERSION.eq(gv))
            .and(SCHEMA_DIRTY_QUEUE.PROTOCOL.eq(proto))
            .and(SCHEMA_DIRTY_QUEUE.DIRECTION.eq(dir))
            .and(SCHEMA_DIRTY_QUEUE.PACKET_ID.eq(pid))
            .and(SCHEMA_DIRTY_QUEUE.MARKED_AT.eq(markedAt))
            .execute()
    }
}

private suspend fun autoCloseIdleSessions(dsl: DSLContext) = withContext(Dispatchers.IO) {
    val cutoff = now().minusHours(SESSION_IDLE_HOURS)
    dsl.update(SESSIONS)
        .set(SESSIONS.STATUS, SessionStatus.closed)
        .set(SESSIONS.CLOSED_AT, now())
        .where(SESSIONS.STATUS.eq(SessionStatus.open))
        .and(DSL.coalesce(SESSIONS.LAST_PACKET_AT, SESSIONS.CREATED_AT).lt(cutoff))
        .execute()
}

private suspend fun gcIdempotencyKeys(dsl: DSLContext) = withContext(Dispatchers.IO) {
    dsl.deleteFrom(IDEMPOTENCY_KEYS)
        .where(IDEMPOTENCY_KEYS.CREATED_AT.lt(now().minusHours(IDEMPOTENCY_TTL_HOURS)))
        .execute()
}
