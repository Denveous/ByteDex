package org.openmmo.bytedex.api.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.JSONB
import org.openmmo.bytedex.api.jooq.Tables.IDEMPOTENCY_KEYS

class ApiResult(val status: HttpStatusCode, val json: String)

class Idempotency(private val dsl: DSLContext) {
    fun replay(userId: UUID, key: UUID): ApiResult? =
        dsl.selectFrom(IDEMPOTENCY_KEYS)
            .where(IDEMPOTENCY_KEYS.USER_ID.eq(userId))
            .and(IDEMPOTENCY_KEYS.KEY.eq(key))
            .fetchOne()
            ?.let { ApiResult(HttpStatusCode.fromValue(it.get(IDEMPOTENCY_KEYS.RESPONSE_STATUS).toInt()), it.get(IDEMPOTENCY_KEYS.RESPONSE_BODY).data()) }

    fun save(userId: UUID, key: UUID, requestHash: ByteArray, result: ApiResult) {
        dsl.insertInto(IDEMPOTENCY_KEYS)
            .set(IDEMPOTENCY_KEYS.KEY, key)
            .set(IDEMPOTENCY_KEYS.USER_ID, userId)
            .set(IDEMPOTENCY_KEYS.REQUEST_HASH, requestHash)
            .set(IDEMPOTENCY_KEYS.RESPONSE_STATUS, result.status.value.toShort())
            .set(IDEMPOTENCY_KEYS.RESPONSE_BODY, JSONB.valueOf(result.json))
            .onConflict(IDEMPOTENCY_KEYS.USER_ID, IDEMPOTENCY_KEYS.KEY)
            .doNothing()
            .execute()
    }
}

fun ApplicationCall.idempotencyKey(): UUID? =
    request.header("Idempotency-Key")?.let {
        runCatching { UUID.fromString(it) }.getOrElse { badRequest("Idempotency-Key must be a UUID") }
    }

suspend fun ApplicationCall.withIdempotency(
    idem: Idempotency,
    userId: UUID,
    requestBody: ByteArray,
    produce: () -> ApiResult,
) {
    val key = idempotencyKey()
    if (key != null) {
        idem.replay(userId, key)?.let {
            respondText(it.json, ContentType.Application.Json, it.status)
            return
        }
    }
    val result = produce()
    if (key != null) {
        val fingerprint = "${request.httpMethod.value}\n${request.path()}\n".toByteArray() + requestBody
        idem.save(userId, key, sha256(fingerprint), result)
    }
    respondText(result.json, ContentType.Application.Json, result.status)
}
