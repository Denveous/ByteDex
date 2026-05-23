package org.openmmo.bytedex.api.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class RateLimitTest {
    @Test
    fun `limiter allows up to the limit then denies with retry-after`() {
        val rl = RateLimiter(limit = 2, windowSeconds = 60)
        val first = rl.check("user-a")
        val second = rl.check("user-a")
        val third = rl.check("user-a")

        assertTrue(first.allowed && second.allowed)
        assertEquals(1, first.remaining)
        assertEquals(0, second.remaining)
        assertTrue(!third.allowed)
        assertEquals(0, third.remaining)
        assertTrue(third.retryAfterSeconds >= 1)
        assertTrue(rl.check("user-b").allowed)
    }

    @Test
    fun `plugin emits X-RateLimit headers and a 429 problem when exhausted`() = testApplication {
        application {
            installProblemHandling()
            routing {
                install(RateLimitPlugin) { limiter = RateLimiter(limit = 1, windowSeconds = 60) }
                get("/ping") { call.respondText("ok") }
            }
        }

        val ok = client.get("/ping")
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("1", ok.headers["X-RateLimit-Limit"])
        assertEquals("0", ok.headers["X-RateLimit-Remaining"])
        assertNotNull(ok.headers["X-RateLimit-Reset"])

        val limited = client.get("/ping")
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertTrue(limited.headers["Content-Type"].orEmpty().startsWith("application/problem+json"))
        assertNotNull(limited.headers["Retry-After"])
        assertTrue(limited.bodyAsText().contains("\"status\":429"))
    }
}
