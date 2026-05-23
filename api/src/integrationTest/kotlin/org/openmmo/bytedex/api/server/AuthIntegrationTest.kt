package org.openmmo.bytedex.api.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.openmmo.bytedex.api.model.TokenResponse
import org.openmmo.bytedex.api.model.User

private const val AUTH = HttpHeaders.Authorization

class AuthIntegrationTest {

    @Test
    fun `rotation burns ancestors and reuse revokes the whole family`() {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        try {
            val tokens = RefreshTokens(dsl, ItEnv.config)

            val raw0 = tokens.issue(userId)
            val r1 = tokens.rotate(raw0)
            assertEquals(userId, r1.userId)
            assertNotEquals(raw0, r1.refreshToken, "rotation must mint a new token")

            val r2 = tokens.rotate(r1.refreshToken)
            assertNotEquals(r1.refreshToken, r2.refreshToken)

            assertFailsWith<ProblemException> { tokens.rotate(raw0) }

            assertFailsWith<ProblemException> { tokens.rotate(r2.refreshToken) }
        } finally {
            deleteUser(dsl, userId)
        }
    }

    @Test
    fun `concurrent rotation of the same token does not fork the family`() {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        try {
            val tokens = RefreshTokens(dsl, ItEnv.config)
            val raw = tokens.issue(userId)

            val results = runBlocking {
                awaitAll(
                    async(Dispatchers.IO) { runCatching { tokens.rotate(raw) } },
                    async(Dispatchers.IO) { runCatching { tokens.rotate(raw) } },
                )
            }

            val succeeded = results.mapNotNull { it.getOrNull() }
            assertEquals(
                1,
                succeeded.size,
                "exactly one rotation may win; a fork means the family split into two live chains",
            )
            // The race is resolved as reuse, so even the winner's token is revoked along with the family.
            assertFailsWith<ProblemException> { tokens.rotate(succeeded.single().refreshToken) }
        } finally {
            deleteUser(dsl, userId)
        }
    }

    @Test
    fun `refresh endpoint exchanges a valid token for a new pair`() = testApplication {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        application { module() }
        try {
            val raw = RefreshTokens(dsl, ItEnv.config).issue(userId)

            val res = client.post("/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refreshToken":"$raw"}""")
            }
            assertEquals(HttpStatusCode.OK, res.status)
            val pair = Json.decodeFromString(TokenResponse.serializer(), res.bodyAsText())
            assertNotEquals(raw, pair.refreshToken)
            assertEquals(TokenResponse.TokenType.Bearer, pair.tokenType)

            val replay = client.post("/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refreshToken":"$raw"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, replay.status)
        } finally {
            deleteUser(dsl, userId)
        }
    }

    @Test
    fun `refresh with an unknown token is unauthorized`() = testApplication {
        requireDb()
        application { module() }
        val res = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"not-a-real-token"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `logout revokes every refresh token for the user`() = testApplication {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        application { module() }
        try {
            val raw = RefreshTokens(dsl, ItEnv.config).issue(userId)

            val loggedOut = client.post("/auth/logout") { header(AUTH, bearer(userId)) }
            assertEquals(HttpStatusCode.NoContent, loggedOut.status)

            val refresh = client.post("/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refreshToken":"$raw"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, refresh.status)
        } finally {
            deleteUser(dsl, userId)
        }
    }

    @Test
    fun `users-me returns the authenticated user`() = testApplication {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        application { module() }
        try {
            val res = client.get("/users/me") { header(AUTH, bearer(userId)) }
            assertEquals(HttpStatusCode.OK, res.status)
            val user = Json.decodeFromString(User.serializer(), res.bodyAsText())
            assertEquals(userId.toString(), user.id)
        } finally {
            deleteUser(dsl, userId)
        }
    }

    @Test
    fun `users-me without a bearer token is unauthorized`() = testApplication {
        requireDb()
        application { module() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me").status)
    }

    @Test
    fun `login rejects a redirect_uri that is not allow-listed`() = testApplication {
        requireDb()
        application { module() }
        val noRedirects = createClient { followRedirects = false }
        val res = noRedirects.get("/auth/github/login?redirect_uri=https://evil.example/steal")
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `login with an allow-listed redirect_uri redirects to GitHub`() = testApplication {
        requireDb()
        application { module() }
        val noRedirects = createClient { followRedirects = false }
        val allowed = ItEnv.config.frontendRedirectUri
        val res = noRedirects.get("/auth/github/login?redirect_uri=$allowed")
        assertEquals(HttpStatusCode.Found, res.status)
        assertTrue(
            res.headers[HttpHeaders.Location]
                ?.startsWith("https://github.com/login/oauth/authorize")!!,
            "should redirect to the GitHub authorize endpoint",
        )
    }

    @Test
    fun `login allows any loopback redirect_uri without config`() = testApplication {
        requireDb()
        application { module() }
        val noRedirects = createClient { followRedirects = false }
        val res = noRedirects.get("/auth/github/login?redirect_uri=http://localhost:9999/cb")
        assertEquals(HttpStatusCode.Found, res.status)
    }

    @Test
    fun `exchange with an unknown code is unauthorized`() = testApplication {
        requireDb()
        application { module() }
        val res = client.post("/auth/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"not-a-real-code"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `auth endpoints sit behind the rate limiter`() = testApplication {
        requireDb()
        application { module() }
        val noRedirects = createClient { followRedirects = false }
        val allowed = ItEnv.config.frontendRedirectUri
        val res = noRedirects.get("/auth/github/login?redirect_uri=$allowed")
        assertNotNull(res.headers["X-RateLimit-Limit"])
    }
}
