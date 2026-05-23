package org.openmmo.bytedex.api.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.openmmo.bytedex.api.jooq.Tables.FIELD_ANNOTATIONS
import org.openmmo.bytedex.api.jooq.Tables.PACKETS
import org.openmmo.bytedex.api.jooq.Tables.PACKET_SCHEMAS
import org.openmmo.bytedex.api.jooq.Tables.SESSIONS
import org.openmmo.bytedex.api.jooq.Tables.USERS
import org.openmmo.bytedex.api.model.ArchiveStats
import org.openmmo.bytedex.api.model.ArchiveStatsByGameVersionInner
import org.openmmo.bytedex.api.model.Direction
import org.openmmo.bytedex.api.model.FieldAnnotation
import org.openmmo.bytedex.api.model.GameVersionsResponse
import org.openmmo.bytedex.api.model.InferredField
import org.openmmo.bytedex.api.model.IngestBucket
import org.openmmo.bytedex.api.model.IngestSeries
import org.openmmo.bytedex.api.model.Leaderboard
import org.openmmo.bytedex.api.model.LeaderboardEntry
import org.openmmo.bytedex.api.model.PacketPage
import org.openmmo.bytedex.api.model.PacketSchema
import org.openmmo.bytedex.api.model.PacketSummary
import org.openmmo.bytedex.api.model.Pagination
import org.openmmo.bytedex.api.model.Protocol
import org.openmmo.bytedex.api.model.PublicUser
import org.openmmo.bytedex.api.model.SearchQuery
import org.openmmo.bytedex.api.model.User
import java.util.UUID

private fun parseHexPattern(raw: String): ByteArray {
    val clean = raw.replace(Regex("[\\s:]+"), "")
    if (clean.isEmpty() || clean.length % 2 != 0 || !clean.matches(Regex("[0-9a-fA-F]+"))) {
        badRequest("payloadHex must be an even-length hex string")
    }
    return ByteArray(clean.length / 2) {
        clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}

class SearchService(private val dsl: DSLContext) {
    fun search(q: SearchQuery): PacketPage {
        val page = clampPage(q.page)
        val pageSize = clampPageSize(q.pageSize)
        var c: Condition = DSL.noCondition()
        q.gameVersions?.takeIf { it.isNotEmpty() }?.let { c = c.and(PACKETS.GAME_VERSION.`in`(it)) }
        q.protocols?.takeIf { it.isNotEmpty() }?.let { c = c.and(PACKETS.PROTOCOL.`in`(it.map(::protocolToJooq))) }
        q.directions?.takeIf { it.isNotEmpty() }?.let { c = c.and(PACKETS.DIRECTION.`in`(it.map(::directionToJooq))) }
        q.packetIds?.takeIf { it.isNotEmpty() }?.let { ids -> c = c.and(PACKETS.PACKET_ID.`in`(ids.map { it.toShort() })) }
        q.sessionIds?.takeIf { it.isNotEmpty() }?.let { c = c.and(PACKETS.SESSION_ID.`in`(it.map(UUID::fromString))) }
        q.minPayloadSize?.let { c = c.and(PACKETS.PAYLOAD_SIZE.ge(it)) }
        q.maxPayloadSize?.let { c = c.and(PACKETS.PAYLOAD_SIZE.le(it)) }
        q.capturedAfter?.let { c = c.and(PACKETS.CAPTURED_AT.ge(OffsetDateTime.parse(it))) }
        q.capturedBefore?.let { c = c.and(PACKETS.CAPTURED_AT.le(OffsetDateTime.parse(it))) }
        q.payloadHex?.takeIf { it.isNotBlank() }?.let {
            val bytes = parseHexPattern(it)
            c = c.and(DSL.condition("position({0} in {1}) > 0", DSL.value(bytes), PACKETS.PAYLOAD))
        }

        val total = dsl.fetchCount(dsl.selectFrom(PACKETS).where(c))
        val rows = dsl.selectFrom(PACKETS).where(c)
            .orderBy(PACKETS.SUBMITTED_AT.desc())
            .limit(pageSize).offset((page - 1) * pageSize)
            .fetch()
        return PacketPage(
            rows.map {
                PacketSummary(
                    id = it.get(PACKETS.ID).toString(),
                    sessionId = it.get(PACKETS.SESSION_ID).toString(),
                    gameVersion = it.get(PACKETS.GAME_VERSION),
                    protocol = protocolFromJooq(it.get(PACKETS.PROTOCOL)),
                    direction = directionFromJooq(it.get(PACKETS.DIRECTION)),
                    packetId = it.get(PACKETS.PACKET_ID).toInt(),
                    payloadSize = it.get(PACKETS.PAYLOAD_SIZE),
                    capturedAt = it.get(PACKETS.CAPTURED_AT).toString(),
                    submittedBy = it.get(PACKETS.SUBMITTED_BY).toString(),
                )
            },
            Pagination(page, pageSize, total),
        )
    }

    fun stats(gameVersion: Long?): ArchiveStats {
        val packetCond = gameVersion?.let { PACKETS.GAME_VERSION.eq(it) } ?: DSL.noCondition()
        val sessionCond = gameVersion?.let { SESSIONS.GAME_VERSION.eq(it) } ?: DSL.noCondition()
        val byProtocol = dsl.select(PACKETS.PROTOCOL, DSL.count()).from(PACKETS).where(packetCond)
            .groupBy(PACKETS.PROTOCOL).fetch().associate { it.value1().literal to it.value2() }
        val byDirection = dsl.select(PACKETS.DIRECTION, DSL.count()).from(PACKETS).where(packetCond)
            .groupBy(PACKETS.DIRECTION).fetch().associate { it.value1().literal to it.value2() }
        val byGameVersion = dsl.select(PACKETS.GAME_VERSION, DSL.count()).from(PACKETS).where(packetCond)
            .groupBy(PACKETS.GAME_VERSION).orderBy(PACKETS.GAME_VERSION)
            .fetch().map { ArchiveStatsByGameVersionInner(it.value1(), it.value2()) }
        val schemaCond = gameVersion?.let { PACKET_SCHEMAS.GAME_VERSION.eq(it) } ?: DSL.noCondition()
        return ArchiveStats(
            totalSessions = dsl.fetchCount(dsl.selectFrom(SESSIONS).where(sessionCond)).toLong(),
            totalPackets = dsl.fetchCount(dsl.selectFrom(PACKETS).where(packetCond)).toLong(),
            totalContributors = dsl.fetchCount(
                dsl.selectDistinct(SESSIONS.SUBMITTED_BY).from(SESSIONS).where(sessionCond),
            ),
            activeContributors = dsl.fetchCount(
                dsl.selectDistinct(SESSIONS.SUBMITTED_BY).from(SESSIONS).where(sessionCond)
                    .and(SESSIONS.CREATED_AT.ge(now().minusDays(7L))),
            ),
            totalSchemas = dsl.fetchCount(dsl.selectFrom(PACKET_SCHEMAS).where(schemaCond)),
            byProtocol = byProtocol,
            byDirection = byDirection,
            byGameVersion = byGameVersion,
        )
    }

    fun leaderboard(limit: Int): Leaderboard {
        val n = limit.coerceIn(1, 100)
        val rows = dsl.select(
            PACKETS.SUBMITTED_BY,
            DSL.count(),
            DSL.countDistinct(PACKETS.SESSION_ID),
        ).from(PACKETS)
            .where(PACKETS.SUBMITTED_BY.ne(ANONYMOUS_USER_ID))
            .groupBy(PACKETS.SUBMITTED_BY)
            .orderBy(DSL.count().desc())
            .limit(n)
            .fetch()
        val users = dsl.selectFrom(USERS)
            .where(USERS.ID.`in`(rows.map { it.value1() }))
            .fetch()
            .associateBy { it.get(USERS.ID) }
        return Leaderboard(
            rows.mapIndexed { i, r ->
                val u = users[r.value1()]
                LeaderboardEntry(
                    rank = i + 1,
                    userId = r.value1().toString(),
                    githubLogin = u?.get(USERS.GITHUB_LOGIN) ?: "unknown",
                    packetCount = r.value2().toLong(),
                    sessionCount = r.value3(),
                    avatarUrl = u?.get(USERS.AVATAR_URL),
                )
            },
        )
    }

    fun gameVersions(): GameVersionsResponse {
        val versions = dsl.selectDistinct(SESSIONS.GAME_VERSION)
            .from(SESSIONS)
            .orderBy(SESSIONS.GAME_VERSION.desc())
            .fetch(SESSIONS.GAME_VERSION)
        return GameVersionsResponse(versions = versions)
    }

    fun ingestSeries(days: Int): IngestSeries {
        val d = days.coerceIn(1, 365)
        val today = now().toLocalDate()
        val sinceDate = today.minusDays((d - 1).toLong())
        val since = sinceDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()
        val day = DSL.field(
            "date_trunc('day', {0})",
            OffsetDateTime::class.java,
            PACKETS.CAPTURED_AT,
        )
        val rows = dsl.select(day, PACKETS.DIRECTION, DSL.count())
            .from(PACKETS)
            .where(PACKETS.CAPTURED_AT.ge(since))
            .groupBy(day, PACKETS.DIRECTION)
            .fetch()
        val byDay = HashMap<LocalDate, LongArray>()
        for (r in rows) {
            val arr = byDay.getOrPut(r.value1().toLocalDate()) { LongArray(2) }
            val c = r.value3().toLong()
            if (r.value2().literal == "C2S") arr[0] += c else arr[1] += c
        }
        return IngestSeries(
            days = d,
            buckets = (0 until d).map { i ->
                val ld = sinceDate.plusDays(i.toLong())
                val a = byDay[ld] ?: LongArray(2)
                IngestBucket(
                    day = ld.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime().toString(),
                    c2s = a[0],
                    s2c = a[1],
                )
            },
        )
    }
}

class SchemaService(private val dsl: DSLContext) {
    fun get(gameVersion: Long, protocol: Protocol, direction: Direction, packetId: Int): PacketSchema {
        val rec = dsl.selectFrom(PACKET_SCHEMAS)
            .where(PACKET_SCHEMAS.GAME_VERSION.eq(gameVersion))
            .and(PACKET_SCHEMAS.PROTOCOL.eq(protocolToJooq(protocol)))
            .and(PACKET_SCHEMAS.DIRECTION.eq(directionToJooq(direction)))
            .and(PACKET_SCHEMAS.PACKET_ID.eq(packetId.toShort()))
            .fetchOne() ?: notFound("no inferred schema for that packet type")
        val fields = Json.decodeFromString(
            ListSerializer(InferredField.serializer()), rec.get(PACKET_SCHEMAS.FIELDS).data(),
        )
        return PacketSchema(
            gameVersion = rec.get(PACKET_SCHEMAS.GAME_VERSION),
            protocol = protocol,
            direction = direction,
            packetId = rec.get(PACKET_SCHEMAS.PACKET_ID).toInt(),
            sampleCount = rec.get(PACKET_SCHEMAS.SAMPLE_COUNT),
            fields = fields,
            generatedAt = rec.get(PACKET_SCHEMAS.GENERATED_AT).toString(),
            sessionCount = rec.get(PACKET_SCHEMAS.SESSION_COUNT),
        )
    }

    fun annotate(
        gameVersion: Long,
        protocol: Protocol,
        direction: Direction,
        packetId: Int,
        authorId: UUID,
        body: FieldAnnotation,
    ): FieldAnnotation {
        require400(body.offset >= 0, "offset must be >= 0")
        require400(body.length >= 0, "length must be >= 0")
        checkLength(body.name, "name", 200)
        checkLength(body.notes, "notes", 2000)
        dsl.selectFrom(PACKET_SCHEMAS)
            .where(PACKET_SCHEMAS.GAME_VERSION.eq(gameVersion))
            .and(PACKET_SCHEMAS.PROTOCOL.eq(protocolToJooq(protocol)))
            .and(PACKET_SCHEMAS.DIRECTION.eq(directionToJooq(direction)))
            .and(PACKET_SCHEMAS.PACKET_ID.eq(packetId.toShort()))
            .fetchOne() ?: notFound("no inferred schema to annotate yet")
        dsl.insertInto(FIELD_ANNOTATIONS)
            .set(FIELD_ANNOTATIONS.GAME_VERSION, gameVersion)
            .set(FIELD_ANNOTATIONS.PROTOCOL, protocolToJooq(protocol))
            .set(FIELD_ANNOTATIONS.DIRECTION, directionToJooq(direction))
            .set(FIELD_ANNOTATIONS.PACKET_ID, packetId.toShort())
            .set(FIELD_ANNOTATIONS.AUTHOR_ID, authorId)
            .set(FIELD_ANNOTATIONS.OFFSET_BYTES, body.offset)
            .set(FIELD_ANNOTATIONS.LENGTH_BYTES, body.length)
            .set(FIELD_ANNOTATIONS.TYPE, inferredTypeToJooq(body.type))
            .set(FIELD_ANNOTATIONS.NAME, body.name)
            .set(FIELD_ANNOTATIONS.NOTES, body.notes)
            .execute()
        return body
    }
}

val ANONYMOUS_USER_ID: UUID =
    UUID.fromString("00000000-0000-0000-0000-000000000000")

class UserService(private val dsl: DSLContext) {
    fun me(userId: UUID): User {
        val u = dsl.selectFrom(USERS).where(USERS.ID.eq(userId)).fetchOne()
            ?: notFound("user $userId not found")
        return User(
            id = u.get(USERS.ID).toString(),
            githubLogin = u.get(USERS.GITHUB_LOGIN),
            createdAt = u.get(USERS.CREATED_AT).toString(),
            githubId = u.get(USERS.GITHUB_ID),
            displayName = u.get(USERS.DISPLAY_NAME),
            avatarUrl = u.get(USERS.AVATAR_URL),
        )
    }

    fun publicProfile(userId: UUID): PublicUser {
        val u = dsl.selectFrom(USERS).where(USERS.ID.eq(userId)).fetchOne()
            ?: notFound("user $userId not found")
        return PublicUser(
            id = u.get(USERS.ID).toString(),
            githubLogin = u.get(USERS.GITHUB_LOGIN),
            createdAt = u.get(USERS.CREATED_AT).toString(),
            submissionCount = dsl.fetchCount(dsl.selectFrom(PACKETS).where(PACKETS.SUBMITTED_BY.eq(userId))),
            displayName = u.get(USERS.DISPLAY_NAME),
            avatarUrl = u.get(USERS.AVATAR_URL),
            sessionCount = dsl.fetchCount(dsl.selectFrom(SESSIONS).where(SESSIONS.SUBMITTED_BY.eq(userId))),
        )
    }

    fun deleteAccount(userId: UUID) {
        if (userId == ANONYMOUS_USER_ID) badRequest("the anonymous account cannot be deleted")
        dsl.transaction { cfg ->
            val tx = cfg.dsl()
            tx.update(SESSIONS)
                .set(SESSIONS.SUBMITTED_BY, ANONYMOUS_USER_ID)
                .where(SESSIONS.SUBMITTED_BY.eq(userId))
                .execute()
            tx.update(PACKETS)
                .set(PACKETS.SUBMITTED_BY, ANONYMOUS_USER_ID)
                .where(PACKETS.SUBMITTED_BY.eq(userId))
                .execute()
            tx.update(FIELD_ANNOTATIONS)
                .set(FIELD_ANNOTATIONS.AUTHOR_ID, ANONYMOUS_USER_ID)
                .where(FIELD_ANNOTATIONS.AUTHOR_ID.eq(userId))
                .execute()
            val deleted = tx.deleteFrom(USERS).where(USERS.ID.eq(userId)).execute()
            if (deleted == 0) notFound("user $userId not found")
        }
    }
}

private fun ApplicationCall.protocolPath(): Protocol =
    Protocol.entries.firstOrNull { it.value == parameters["protocol"] }
        ?: badRequest("unknown protocol")

private fun ApplicationCall.directionPath(): Direction =
    Direction.entries.firstOrNull { it.value == parameters["direction"] }
        ?: badRequest("unknown direction")

private fun ApplicationCall.longPath(name: String): Long =
    parameters[name]?.toLongOrNull() ?: badRequest("$name must be an integer")

private fun ApplicationCall.packetIdPath(): Int {
    val v = parameters["packetId"]?.toIntOrNull() ?: badRequest("packetId must be an integer")
    if (v !in 0..255) badRequest("packetId must be in 0..255")
    return v
}

fun Route.searchRoutes(search: SearchService) {
    post("/search") { call.respond(search.search(call.receive<SearchQuery>())) }
    get("/game-versions") { call.respond(search.gameVersions()) }
    get("/stats") {
        call.respond(search.stats(call.request.queryParameters["gameVersion"]?.toLongOrNull()))
    }
    get("/stats/leaderboard") {
        call.respond(search.leaderboard(call.request.queryParameters["limit"]?.toIntOrNull() ?: 10))
    }
    get("/stats/ingest") {
        call.respond(search.ingestSeries(call.request.queryParameters["days"]?.toIntOrNull() ?: 30))
    }
}

fun Route.schemaRoutes(schemas: SchemaService) {
    route("/schemas/{gameVersion}/{protocol}/{direction}/{packetId}") {
        get {
            call.respond(
                schemas.get(call.longPath("gameVersion"), call.protocolPath(), call.directionPath(), call.packetIdPath()),
            )
        }
        post("/annotations") {
            val body = call.receive<FieldAnnotation>()
            val result = schemas.annotate(
                call.longPath("gameVersion"), call.protocolPath(), call.directionPath(),
                call.packetIdPath(), call.currentUserId(), body,
            )
            call.respond(HttpStatusCode.Created, result)
        }
    }
}

fun Route.userRoutes(users: UserService) {
    route("/users") {
        get("/me") { call.respond(users.me(call.currentUserId())) }
        delete("/me") {
            users.deleteAccount(call.currentUserId())
            call.respond(HttpStatusCode.NoContent)
        }
        get("/{userId}") { call.respond(users.publicProfile(call.pathUuid("userId"))) }
    }
}
