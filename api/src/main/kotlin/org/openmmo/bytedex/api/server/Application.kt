package org.openmmo.bytedex.api.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openmmo.bytedex.api.model.Health

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.installHttp() {
    install(XForwardedHeaders)
    install(ContentNegotiation) { json() }
    install(CallLogging) {
        format { call ->
            val status = call.response.status()?.toString() ?: "Unhandled"
            val line = "$status: ${call.request.httpMethod.value} - " +
                "${call.request.path()} in ${call.processingTimeMillis()}ms"
            val location = call.response.headers[HttpHeaders.Location]
            when {
                location == null -> line
                '?' in location -> "$line -> ${location.substringBefore('?')}?<redacted>"
                else -> "$line -> $location"
            }
        }
    }
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.ContentEncoding)
        allowHeader("Idempotency-Key")
    }
    installProblemHandling()
}

fun Route.healthRoute(healthy: suspend () -> Boolean = { true }) {
    get("/health") {
        val status = if (healthy()) Health.Status.ok else Health.Status.degraded
        call.respond(Health(status = status))
    }
}

fun Application.module() {
    val config = Config()
    val db = Database(config)
    db.migrate()
    monitor.subscribe(ApplicationStopped) { db.close() }

    val jwt = JwtService(config)
    val users = Users(db.dsl)
    val refreshTokens = RefreshTokens(db.dsl, config)
    val github = GitHubOAuth(config)
    val idempotency = Idempotency(db.dsl)
    val sessions = SessionService(db.dsl)
    val packets = PacketService(db.dsl)
    val search = SearchService(db.dsl)
    val schemas = SchemaService(db.dsl)
    val userService = UserService(db.dsl)
    val rateLimiter = RateLimiter(config.rateLimitRequests, config.rateLimitWindowSeconds)

    installHttp()
    installAuth(jwt)
    startWorkers(db.dsl)

    routing {
        healthRoute {
            runCatching { withContext(Dispatchers.IO) { db.dsl.selectOne().fetch() } }.isSuccess
        }
        authRoutes(config, jwt, users, refreshTokens, github)
        authenticate(JWT_REALM) {
            install(RateLimitPlugin) { limiter = rateLimiter }
            sessionRoutes(sessions, idempotency)
            packetRoutes(packets, idempotency)
            searchRoutes(search)
            schemaRoutes(schemas)
            userRoutes(userService)
        }
    }
}
