package org.openmmo.bytedex.api.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.plugins.origin
import io.ktor.server.response.header
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class RateLimiter(private val limit: Int, private val windowSeconds: Long) {
    private class Window(val startEpoch: Long, var count: Int)

    private val buckets = ConcurrentHashMap<String, Window>()

    data class Decision(
        val allowed: Boolean,
        val limit: Int,
        val remaining: Int,
        val resetEpoch: Long,
        val retryAfterSeconds: Long,
    )

    fun check(key: String): Decision {
        val now = Instant.now().epochSecond
        val window = buckets.compute(key) { _, existing ->
            if (existing == null || now - existing.startEpoch >= windowSeconds) {
                Window(now, 1)
            } else {
                existing.also { it.count++ }
            }
        }!!
        val resetEpoch = window.startEpoch + windowSeconds
        val allowed = window.count <= limit
        return Decision(
            allowed = allowed,
            limit = limit,
            remaining = (limit - window.count).coerceAtLeast(0),
            resetEpoch = resetEpoch,
            retryAfterSeconds = if (allowed) 0 else (resetEpoch - now).coerceAtLeast(1),
        )
    }
}

class RateLimitPluginConfig {
    lateinit var limiter: RateLimiter
}

val RateLimitPlugin = createRouteScopedPlugin("RateLimit", ::RateLimitPluginConfig) {
    val limiter = pluginConfig.limiter
    onCall { call ->
        val key = call.principal<JWTPrincipal>()?.subject ?: call.request.origin.remoteHost
        val d = limiter.check(key)
        call.response.header("X-RateLimit-Limit", d.limit.toString())
        call.response.header("X-RateLimit-Remaining", d.remaining.toString())
        call.response.header("X-RateLimit-Reset", d.resetEpoch.toString())
        if (!d.allowed) {
            call.response.header(HttpHeaders.RetryAfter, d.retryAfterSeconds.toString())
            throw ProblemException(
                HttpStatusCode.TooManyRequests,
                "Too Many Requests",
                "rate limit exceeded. Retry in ${d.retryAfterSeconds}s",
            )
        }
    }
}
