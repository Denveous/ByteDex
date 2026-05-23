package org.openmmo.bytedex.api.server

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import org.openmmo.bytedex.api.jooq.enums.DirectionKind
import org.openmmo.bytedex.api.jooq.enums.InferredType
import org.openmmo.bytedex.api.jooq.enums.ProtocolKind
import org.openmmo.bytedex.api.model.Direction
import org.openmmo.bytedex.api.model.InferredFieldType
import org.openmmo.bytedex.api.model.Protocol

fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

private val b64 = Base64.getDecoder()

fun decodeBase64(value: String): ByteArray =
    runCatching { b64.decode(value) }.getOrElse { badRequest("payload is not valid base64") }

fun looksZlibCompressed(bytes: ByteArray): Boolean {
    if (bytes.size < 2) return false
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    return (b0 and 0x0F) == 8 && (b0 shr 4) <= 7 && ((b0 shl 8) or b1) % 31 == 0
}

fun protocolToJooq(p: Protocol): ProtocolKind = ProtocolKind.valueOf(p.value)
fun directionToJooq(d: Direction): DirectionKind = DirectionKind.valueOf(d.value)
fun protocolFromJooq(p: ProtocolKind): Protocol = Protocol.entries.first { it.value == p.literal }
fun directionFromJooq(d: DirectionKind): Direction = Direction.entries.first { it.value == d.literal }
fun inferredTypeToJooq(t: InferredFieldType): InferredType =
    InferredType.entries.first { it.literal == t.value }

fun ApplicationCall.pathUuid(name: String): UUID =
    parameters[name]?.let {
        runCatching { UUID.fromString(it) }.getOrElse { badRequest("$name must be a UUID") }
    } ?: badRequest("$name is required")

fun ApplicationCall.queryUuid(name: String): UUID? =
    request.queryParameters[name]?.let {
        runCatching { UUID.fromString(it) }.getOrElse { badRequest("$name must be a UUID") }
    }

fun ApplicationCall.intParam(name: String, default: Int, min: Int, max: Int): Int {
    val raw = request.queryParameters[name] ?: return default
    val value = raw.toIntOrNull() ?: badRequest("$name must be an integer")
    if (value < min || value > max) badRequest("$name must be in $min..$max")
    return value
}

fun clampPage(page: Int?): Int = (page ?: 1).coerceAtLeast(1)
fun clampPageSize(pageSize: Int?): Int = (pageSize ?: 50).coerceIn(1, 200)

fun require400(ok: Boolean, message: String) {
    if (!ok) badRequest(message)
}

suspend fun ApplicationCall.decodedBodyText(): String {
    val raw = receive<ByteArray>()
    val gzipped = request.headers["Content-Encoding"]?.lowercase()?.contains("gzip") == true
    val bytes = if (gzipped) withContext(Dispatchers.IO) {
        GZIPInputStream(raw.inputStream()).use { it.readBytes() }
    } else raw
    return String(bytes, Charsets.UTF_8)
}

fun checkLength(value: String?, field: String, max: Int) {
    if (value != null && value.length > max) badRequest("$field must be at most $max characters")
}
