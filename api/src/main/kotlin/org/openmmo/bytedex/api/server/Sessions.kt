package org.openmmo.bytedex.api.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.openmmo.bytedex.api.model.Pagination
import org.openmmo.bytedex.api.model.Session
import org.openmmo.bytedex.api.model.SessionPage
import org.openmmo.bytedex.api.jooq.tables.records.SessionsRecord
import org.openmmo.bytedex.api.jooq.Tables.SESSIONS
import org.openmmo.bytedex.api.jooq.enums.SessionStatus
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.openmmo.bytedex.api.model.SessionStatus as ApiSessionStatus

@Serializable
private data class SessionCreateBody(
    val gameVersion: Long,
    val description: String? = null,
    val tags: List<String>? = null,
    val clientInfo: String? = null,
)

@Serializable
private data class SessionUpdateBody(
    val description: String? = null,
    val tags: List<String>? = null,
)

private const val MAX_GAME_VERSION = 4_294_967_295L
private const val MAX_DESCRIPTION = 2000
private const val MAX_CLIENT_INFO = 200
private const val MAX_TAGS = 32
private const val MAX_TAG_LEN = 50

private fun validateMeta(description: String?, tags: List<String>?, clientInfo: String?) {
    checkLength(description, "description", MAX_DESCRIPTION)
    checkLength(clientInfo, "clientInfo", MAX_CLIENT_INFO)
    if (tags != null) {
        require400(tags.size <= MAX_TAGS, "tags must have at most $MAX_TAGS entries")
        tags.forEach { checkLength(it, "tag", MAX_TAG_LEN) }
    }
}

private fun SessionsRecord.toModel() = Session(
    id = get(SESSIONS.ID).toString(),
    submittedBy = get(SESSIONS.SUBMITTED_BY).toString(),
    gameVersion = get(SESSIONS.GAME_VERSION),
    status = ApiSessionStatus.entries.first { it.value == get(SESSIONS.STATUS).literal },
    packetCount = get(SESSIONS.PACKET_COUNT),
    createdAt = get(SESSIONS.CREATED_AT).toString(),
    description = get(SESSIONS.DESCRIPTION),
    tags = get(SESSIONS.TAGS)?.toList(),
    clientInfo = get(SESSIONS.CLIENT_INFO),
    firstPacketAt = get(SESSIONS.FIRST_PACKET_AT)?.toString(),
    lastPacketAt = get(SESSIONS.LAST_PACKET_AT)?.toString(),
    closedAt = get(SESSIONS.CLOSED_AT)?.toString(),
)

class SessionService(private val dsl: DSLContext) {

    fun list(page: Int, pageSize: Int, submittedBy: UUID?, gameVersion: Long?, status: SessionStatus?): SessionPage {
        var condition = DSL.noCondition()
        if (submittedBy != null) condition = condition.and(SESSIONS.SUBMITTED_BY.eq(submittedBy))
        if (gameVersion != null) condition = condition.and(SESSIONS.GAME_VERSION.eq(gameVersion))
        if (status != null) condition = condition.and(SESSIONS.STATUS.eq(status))

        val total = dsl.fetchCount(dsl.selectFrom(SESSIONS).where(condition))
        val rows = dsl.selectFrom(SESSIONS)
            .where(condition)
            .orderBy(SESSIONS.CREATED_AT.desc())
            .limit(pageSize)
            .offset((page - 1) * pageSize)
            .fetch()
        return SessionPage(
            items = rows.map { it.toModel() },
            pagination = Pagination(page = page, pageSize = pageSize, total = total),
        )
    }

    fun create(owner: UUID, gameVersion: Long, description: String?, tags: List<String>?, clientInfo: String?): Session {
        require400(gameVersion in 0..MAX_GAME_VERSION, "gameVersion must be in 0..$MAX_GAME_VERSION")
        validateMeta(description, tags, clientInfo)
        val rec = dsl.insertInto(SESSIONS)
            .set(SESSIONS.SUBMITTED_BY, owner)
            .set(SESSIONS.GAME_VERSION, gameVersion)
            .set(SESSIONS.DESCRIPTION, description)
            .set(SESSIONS.TAGS, tags?.toTypedArray() ?: emptyArray())
            .set(SESSIONS.CLIENT_INFO, clientInfo)
            .returning()
            .fetchOne()!!
        return rec.toModel()
    }

    private fun load(id: UUID): SessionsRecord =
        dsl.selectFrom(SESSIONS).where(SESSIONS.ID.eq(id)).fetchOne()
            ?: notFound("session $id not found")

    fun get(id: UUID): Session = load(id).toModel()

    private fun requireOwner(rec: SessionsRecord, userId: UUID) {
        if (rec.get(SESSIONS.SUBMITTED_BY) != userId) forbidden("not the session owner")
    }

    fun update(id: UUID, userId: UUID, description: String?, tags: List<String>?): Session {
        val rec = load(id)
        requireOwner(rec, userId)
        validateMeta(description, tags, null)
        if (description != null) rec.set(SESSIONS.DESCRIPTION, description)
        if (tags != null) rec.set(SESSIONS.TAGS, tags.toTypedArray())
        rec.store()
        return rec.toModel()
    }

    fun close(id: UUID, userId: UUID): Session? {
        val rec = load(id)
        requireOwner(rec, userId)
        val empty = rec.get(SESSIONS.PACKET_COUNT) <= 0
        if (empty) {
            rec.delete()
            return null
        }
        if (rec.get(SESSIONS.STATUS) == SessionStatus.open) {
            rec.set(SESSIONS.STATUS, SessionStatus.closed)
            rec.set(SESSIONS.CLOSED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            rec.store()
        }
        return rec.toModel()
    }

    fun delete(id: UUID, userId: UUID) {
        val rec = load(id)
        requireOwner(rec, userId)
        rec.delete()
    }
}

fun Route.sessionRoutes(service: SessionService, idem: Idempotency) {
    route("/sessions") {
        get {
            val submittedBy = call.request.queryParameters["submittedBy"]?.let(UUID::fromString)
            val gameVersion = call.request.queryParameters["gameVersion"]?.toLongOrNull()
            val status = call.request.queryParameters["status"]
                ?.let { s -> SessionStatus.entries.first { it.literal == s } }
            val result = service.list(
                page = call.intParam("page", 1, 1, Int.MAX_VALUE),
                pageSize = call.intParam("pageSize", 50, 1, 200),
                submittedBy = submittedBy,
                gameVersion = gameVersion,
                status = status,
            )
            call.respond(result)
        }

        post {
            val userId = call.currentUserId()
            val bodyText = call.decodedBodyText()
            call.withIdempotency(idem, userId, bodyText.toByteArray()) {
                val body = Json.decodeFromString(SessionCreateBody.serializer(), bodyText)
                val session = service.create(
                    userId, body.gameVersion, body.description, body.tags, body.clientInfo,
                )
                ApiResult(HttpStatusCode.Created, Json.encodeToString(Session.serializer(), session))
            }
        }

        route("/{sessionId}") {
            get {
                call.respond(service.get(call.pathUuid("sessionId")))
            }
            patch {
                val body = call.receive<SessionUpdateBody>()
                call.respond(service.update(call.pathUuid("sessionId"), call.currentUserId(), body.description, body.tags))
            }
            delete {
                service.delete(call.pathUuid("sessionId"), call.currentUserId())
                call.respond(HttpStatusCode.NoContent)
            }
            post("/close") {
                val result = service.close(call.pathUuid("sessionId"), call.currentUserId())
                if (result == null) call.respond(HttpStatusCode.NoContent) else call.respond(result)
            }
        }
    }
}
