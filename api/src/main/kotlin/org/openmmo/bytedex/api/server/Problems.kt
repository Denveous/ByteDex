package org.openmmo.bytedex.api.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import org.openmmo.bytedex.api.model.Problem
import org.slf4j.LoggerFactory

private const val BLANK_TYPE = "about:blank"

class ProblemException(
    val httpStatus: HttpStatusCode,
    val title: String,
    val detail: String? = null,
    val type: String = BLANK_TYPE,
) : RuntimeException(detail ?: title)

fun notFound(detail: String): Nothing =
    throw ProblemException(HttpStatusCode.NotFound, "Not Found", detail)

fun forbidden(detail: String): Nothing =
    throw ProblemException(HttpStatusCode.Forbidden, "Forbidden", detail)

fun conflict(detail: String): Nothing =
    throw ProblemException(HttpStatusCode.Conflict, "Conflict", detail)

fun badRequest(detail: String): Nothing =
    throw ProblemException(HttpStatusCode.BadRequest, "Bad Request", detail)

private val problemJson = ContentType("application", "problem+json")
private val log = LoggerFactory.getLogger("Problems")

private suspend fun ApplicationCall.respondProblem(status: HttpStatusCode, title: String, detail: String?, type: String) {
    val body = Problem(type = type, title = title, status = status.value, detail = detail, instance = request.path())
    respondText(Json.encodeToString(Problem.serializer(), body), problemJson, status)
}

fun io.ktor.server.application.Application.installProblemHandling() {
    install(StatusPages) {
        exception<ProblemException> { call, cause ->
            call.respondProblem(cause.httpStatus, cause.title, cause.detail, cause.type)
        }
        exception<BadRequestException> { call, cause ->
            call.respondProblem(HttpStatusCode.BadRequest, "Bad Request", cause.message, BLANK_TYPE)
        }
        exception<Throwable> { call, cause ->
            log.error("unhandled exception", cause)
            call.respondProblem(HttpStatusCode.InternalServerError, "Internal Server Error", null, BLANK_TYPE)
        }
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respondProblem(status, "Unauthorized", "Missing or invalid credentials", BLANK_TYPE)
        }
    }
}
