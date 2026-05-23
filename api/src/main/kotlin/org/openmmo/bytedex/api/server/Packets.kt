package org.openmmo.bytedex.api.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.openmmo.bytedex.api.jooq.Tables.PACKETS
import org.openmmo.bytedex.api.jooq.Tables.SESSIONS
import org.openmmo.bytedex.api.jooq.enums.DirectionKind
import org.openmmo.bytedex.api.jooq.enums.ProtocolKind
import org.openmmo.bytedex.api.jooq.enums.SessionStatus
import org.openmmo.bytedex.api.jooq.tables.records.PacketsRecord
import org.openmmo.bytedex.api.model.Packet
import org.openmmo.bytedex.api.model.PacketBatch
import org.openmmo.bytedex.api.model.PacketBatchAck
import org.openmmo.bytedex.api.model.PacketBatchPartial
import org.openmmo.bytedex.api.model.PacketBatchPartialResultsInner
import org.openmmo.bytedex.api.model.PacketPage
import org.openmmo.bytedex.api.model.PacketSubmission
import org.openmmo.bytedex.api.model.PacketSummary
import org.openmmo.bytedex.api.model.Pagination

private const val MAX_PAYLOAD_BYTES = 1_048_576
private const val MAX_BATCH = 10_000

private class RejectedPacket(message: String) : RuntimeException(message)

private fun PacketsRecord.toPacket(payloadB64: String) = Packet(
    id = get(PACKETS.ID).toString(),
    sessionId = get(PACKETS.SESSION_ID).toString(),
    gameVersion = get(PACKETS.GAME_VERSION),
    submittedBy = get(PACKETS.SUBMITTED_BY).toString(),
    submittedAt = get(PACKETS.SUBMITTED_AT).toString(),
    payloadSize = get(PACKETS.PAYLOAD_SIZE),
    protocol = protocolFromJooq(get(PACKETS.PROTOCOL)),
    direction = directionFromJooq(get(PACKETS.DIRECTION)),
    packetId = get(PACKETS.PACKET_ID).toInt(),
    capturedAt = get(PACKETS.CAPTURED_AT).toString(),
    payload = payloadB64,
    clientSeq = get(PACKETS.CLIENT_SEQ),
    notes = get(PACKETS.NOTES),
)

private fun PacketsRecord.toSummary() = PacketSummary(
    id = get(PACKETS.ID).toString(),
    sessionId = get(PACKETS.SESSION_ID).toString(),
    gameVersion = get(PACKETS.GAME_VERSION),
    protocol = protocolFromJooq(get(PACKETS.PROTOCOL)),
    direction = directionFromJooq(get(PACKETS.DIRECTION)),
    packetId = get(PACKETS.PACKET_ID).toInt(),
    payloadSize = get(PACKETS.PAYLOAD_SIZE),
    capturedAt = get(PACKETS.CAPTURED_AT).toString(),
    submittedBy = get(PACKETS.SUBMITTED_BY).toString(),
)

class PacketService(private val dsl: DSLContext) {

    private fun openSession(sessionId: UUID): Long {
        val session = dsl.selectFrom(SESSIONS).where(SESSIONS.ID.eq(sessionId)).fetchOne()
            ?: notFound("session $sessionId not found")
        if (session.get(SESSIONS.STATUS) == SessionStatus.closed) {
            conflict("session $sessionId is closed")
        }
        return session.get(SESSIONS.GAME_VERSION)
    }

    private fun insert(sessionId: UUID, gameVersion: Long, userId: UUID, sub: PacketSubmission): PacketsRecord {
        if (sub.packetId !in 0..255) throw RejectedPacket("packetId must be in 0..255")
        if ((sub.notes?.length ?: 0) > 2000) throw RejectedPacket("notes must be at most 2000 characters")
        val payload = decodeBase64(sub.payload)
        if (payload.size > MAX_PAYLOAD_BYTES) throw RejectedPacket("payload exceeds 1 MB")
        if (looksZlibCompressed(payload)) {
            throw RejectedPacket("payload still carries a zlib header. Submit it decompressed")
        }
        val capturedAt = runCatching { OffsetDateTime.parse(sub.capturedAt) }
            .getOrElse { throw RejectedPacket("capturedAt is not a valid date-time") }
        return dsl.insertInto(PACKETS)
            .set(PACKETS.SESSION_ID, sessionId)
            .set(PACKETS.SUBMITTED_BY, userId)
            .set(PACKETS.GAME_VERSION, gameVersion)
            .set(PACKETS.PROTOCOL, protocolToJooq(sub.protocol))
            .set(PACKETS.DIRECTION, directionToJooq(sub.direction))
            .set(PACKETS.PACKET_ID, sub.packetId.toShort())
            .set(PACKETS.CLIENT_SEQ, sub.clientSeq)
            .set(PACKETS.CAPTURED_AT, capturedAt)
            .set(PACKETS.PAYLOAD, payload)
            .set(PACKETS.PAYLOAD_SIZE, payload.size)
            .set(PACKETS.PAYLOAD_SHA256, sha256(payload))
            .set(PACKETS.NOTES, sub.notes)
            .returning()
            .fetchOne()!!
    }

    fun submitOne(sessionId: UUID, userId: UUID, sub: PacketSubmission): Packet {
        val gameVersion = openSession(sessionId)
        val rec = try {
            insert(sessionId, gameVersion, userId, sub)
        } catch (e: RejectedPacket) {
            badRequest(e.message!!)
        }
        return rec.toPacket(sub.payload)
    }

    sealed interface BatchOutcome
    data class AllAccepted(val ids: List<String>) : BatchOutcome
    data class Partial(val accepted: Int, val rejected: Int, val results: List<PacketBatchPartialResultsInner>) : BatchOutcome

    fun submitBatch(sessionId: UUID, userId: UUID, packets: List<PacketSubmission>): BatchOutcome {
        if (packets.isEmpty()) badRequest("batch must contain at least one packet")
        if (packets.size > MAX_BATCH) {
            throw ProblemException(HttpStatusCode.PayloadTooLarge, "Payload Too Large", "batch exceeds $MAX_BATCH packets")
        }
        val gameVersion = openSession(sessionId)
        val results = packets.mapIndexed { index, sub ->
            runCatching { insert(sessionId, gameVersion, userId, sub) }
                .fold(
                    onSuccess = { PacketBatchPartialResultsInner(index = index, ok = true, id = it.get(PACKETS.ID).toString()) },
                    onFailure = { PacketBatchPartialResultsInner(index = index, ok = false, error = it.message) },
                )
        }
        val accepted = results.count { it.ok }
        return if (accepted == results.size) {
            AllAccepted(results.map { it.id!! })
        } else {
            Partial(accepted, results.size - accepted, results)
        }
    }

    private fun filtered(
        condition: Condition,
        page: Int,
        pageSize: Int,
        order: org.jooq.SortField<*>,
    ): PacketPage {
        val total = dsl.fetchCount(dsl.selectFrom(PACKETS).where(condition))
        val rows = dsl.selectFrom(PACKETS)
            .where(condition)
            .orderBy(order)
            .limit(pageSize)
            .offset((page - 1) * pageSize)
            .fetch()
        return PacketPage(rows.map { it.toSummary() }, Pagination(page, pageSize, total))
    }

    fun listInSession(
        sessionId: UUID,
        page: Int,
        pageSize: Int,
        filter: PacketFilter,
    ): PacketPage {
        dsl.selectFrom(SESSIONS).where(SESSIONS.ID.eq(sessionId)).fetchOne()
            ?: notFound("session $sessionId not found")
        var c: Condition = PACKETS.SESSION_ID.eq(sessionId)
        if (filter.protocol != null) c = c.and(PACKETS.PROTOCOL.eq(protocolKind(filter.protocol)))
        if (filter.direction != null) c = c.and(PACKETS.DIRECTION.eq(directionKind(filter.direction)))
        if (filter.packetId != null) c = c.and(PACKETS.PACKET_ID.eq(filter.packetId.toShort()))
        if (filter.capturedBefore != null) c = c.and(PACKETS.CAPTURED_AT.lt(filter.capturedBefore))
        if (filter.capturedAfter != null) c = c.and(PACKETS.CAPTURED_AT.gt(filter.capturedAfter))
        val order = if (filter.sortDesc) PACKETS.CAPTURED_AT.desc() else PACKETS.CAPTURED_AT.asc()
        return filtered(c, page, pageSize, order)
    }

    fun listGlobal(page: Int, pageSize: Int, gameVersion: Long?, protocol: String?, direction: String?, packetId: Int?, submittedBy: UUID?): PacketPage {
        var c: Condition = DSL.noCondition()
        if (gameVersion != null) c = c.and(PACKETS.GAME_VERSION.eq(gameVersion))
        if (protocol != null) c = c.and(PACKETS.PROTOCOL.eq(protocolKind(protocol)))
        if (direction != null) c = c.and(PACKETS.DIRECTION.eq(directionKind(direction)))
        if (packetId != null) c = c.and(PACKETS.PACKET_ID.eq(packetId.toShort()))
        if (submittedBy != null) c = c.and(PACKETS.SUBMITTED_BY.eq(submittedBy))
        return filtered(c, page, pageSize, PACKETS.SUBMITTED_AT.desc())
    }

    private fun load(id: UUID): PacketsRecord =
        dsl.selectFrom(PACKETS).where(PACKETS.ID.eq(id)).fetchOne() ?: notFound("packet $id not found")

    fun get(id: UUID): Packet {
        val rec = load(id)
        return rec.toPacket(java.util.Base64.getEncoder().encodeToString(rec.get(PACKETS.PAYLOAD)))
    }

    fun payload(id: UUID): ByteArray = load(id).get(PACKETS.PAYLOAD)

    fun delete(id: UUID, userId: UUID) {
        val rec = load(id)
        if (rec.get(PACKETS.SUBMITTED_BY) != userId) forbidden("not the packet owner")
        rec.delete()
    }
}

data class PacketFilter(
    val protocol: String? = null,
    val direction: String? = null,
    val packetId: Int? = null,
    val capturedBefore: OffsetDateTime? = null,
    val capturedAfter: OffsetDateTime? = null,
    val sortDesc: Boolean = false,
)

private fun protocolKind(value: String) =
    ProtocolKind.entries.firstOrNull { it.literal == value }
        ?: badRequest("unknown protocol $value")

private fun directionKind(value: String) =
    DirectionKind.entries.firstOrNull { it.literal == value }
        ?: badRequest("unknown direction $value")

private fun ApplicationCall.batchParams() = Triple(
    request.queryParameters["protocol"],
    request.queryParameters["direction"],
    request.queryParameters["packetId"]?.toIntOrNull(),
)

fun Route.packetRoutes(service: PacketService, idem: Idempotency) {
    route("/sessions/{sessionId}/packets") {
        get {
            val (protocol, direction, packetId) = call.batchParams()
            val capturedBefore = call.request.queryParameters["capturedBefore"]
                ?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() ?: badRequest("capturedBefore is not ISO-8601") }
            val capturedAfter = call.request.queryParameters["capturedAfter"]
                ?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() ?: badRequest("capturedAfter is not ISO-8601") }
            val sortDesc = call.request.queryParameters["sort"]?.equals("desc", ignoreCase = true) == true
            call.respond(
                service.listInSession(
                    call.pathUuid("sessionId"),
                    call.intParam("page", 1, 1, Int.MAX_VALUE),
                    call.intParam("pageSize", 50, 1, 200),
                    PacketFilter(protocol, direction, packetId, capturedBefore, capturedAfter, sortDesc),
                ),
            )
        }
        post {
            val sessionId = call.pathUuid("sessionId")
            val userId = call.currentUserId()
            val bodyText = call.decodedBodyText()
            call.withIdempotency(idem, userId, bodyText.toByteArray()) {
                val element = Json.parseToJsonElement(bodyText)
                val isBatch = (element as? kotlinx.serialization.json.JsonObject)?.containsKey("packets") == true
                if (isBatch) {
                    val batch = Json.decodeFromString(PacketBatch.serializer(), bodyText)
                    when (val outcome = service.submitBatch(sessionId, userId, batch.packets)) {
                        is PacketService.AllAccepted -> ApiResult(
                            HttpStatusCode.Accepted,
                            Json.encodeToString(PacketBatchAck.serializer(), PacketBatchAck(outcome.ids.size, outcome.ids)),
                        )
                        is PacketService.Partial -> ApiResult(
                            HttpStatusCode.MultiStatus,
                            Json.encodeToString(
                                PacketBatchPartial.serializer(),
                                PacketBatchPartial(outcome.accepted, outcome.rejected, outcome.results),
                            ),
                        )
                    }
                } else {
                    val sub = Json.decodeFromString(PacketSubmission.serializer(), bodyText)
                    val packet = service.submitOne(sessionId, userId, sub)
                    ApiResult(HttpStatusCode.Created, Json.encodeToString(Packet.serializer(), packet))
                }
            }
        }
    }

    route("/packets") {
        get {
            val (protocol, direction, packetId) = call.batchParams()
            call.respond(
                service.listGlobal(
                    call.intParam("page", 1, 1, Int.MAX_VALUE),
                    call.intParam("pageSize", 50, 1, 200),
                    call.request.queryParameters["gameVersion"]?.toLongOrNull(),
                    protocol, direction, packetId,
                    call.queryUuid("submittedBy"),
                ),
            )
        }
        route("/{packetUuid}") {
            get { call.respond(service.get(call.pathUuid("packetUuid"))) }
            delete {
                service.delete(call.pathUuid("packetUuid"), call.currentUserId())
                call.respond(HttpStatusCode.NoContent)
            }
            get("/payload") {
                call.respondBytes(service.payload(call.pathUuid("packetUuid")), ContentType.Application.OctetStream)
            }
        }
    }
}
