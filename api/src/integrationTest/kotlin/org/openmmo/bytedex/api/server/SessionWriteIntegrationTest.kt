package org.openmmo.bytedex.api.server

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.openmmo.bytedex.api.model.Session
import org.openmmo.bytedex.api.model.SessionPage

private const val AUTH = HttpHeaders.Authorization

class SessionWriteIntegrationTest {

    @Test
    fun `session lifecycle persists across create, patch, close, delete`() = testApplication {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        application { module() }
        try {
            val token = bearer(userId)
            val gv = randomGameVersion()

            val created = client.post("/sessions") {
                header(AUTH, token)
                contentType(ContentType.Application.Json)
                setBody("""{"gameVersion":$gv,"description":"itest","tags":["x"]}""")
            }
            assertEquals(HttpStatusCode.Created, created.status)
            val session = Json.decodeFromString(Session.serializer(), created.bodyAsText())
            assertEquals(gv, session.gameVersion)
            assertEquals("open", session.status.value)
            assertEquals(userId.toString(), session.submittedBy)

            val fetched = client.get("/sessions/${session.id}") { header(AUTH, token) }
            assertEquals(HttpStatusCode.OK, fetched.status)

            val listed = client.get("/sessions?gameVersion=$gv") { header(AUTH, token) }
            val page = Json.decodeFromString(SessionPage.serializer(), listed.bodyAsText())
            assertEquals(1, page.items.size)
            assertEquals(session.id, page.items[0].id)

            val patched = client.patch("/sessions/${session.id}") {
                header(AUTH, token)
                contentType(ContentType.Application.Json)
                setBody("""{"description":"updated"}""")
            }
            assertEquals(HttpStatusCode.OK, patched.status)
            assertEquals("updated", Json.decodeFromString(Session.serializer(), patched.bodyAsText()).description)

            val submitted = client.post("/sessions/${session.id}/packets") {
                header(AUTH, token)
                contentType(ContentType.Application.Json)
                setBody("""{"protocol":"GAME","direction":"C2S","packetId":0,"payload":"AQID","capturedAt":"2026-01-01T00:00:00Z"}""")
            }
            assertEquals(HttpStatusCode.Created, submitted.status)

            val closed = client.post("/sessions/${session.id}/close") { header(AUTH, token) }
            assertEquals(HttpStatusCode.OK, closed.status)
            assertEquals("closed", Json.decodeFromString(Session.serializer(), closed.bodyAsText()).status.value)

            assertEquals(
                HttpStatusCode.NoContent,
                client.delete("/sessions/${session.id}") { header(AUTH, token) }.status,
            )
            assertEquals(
                HttpStatusCode.NotFound,
                client.get("/sessions/${session.id}") { header(AUTH, token) }.status,
            )
        } finally {
            deleteUser(dsl, userId)
        }
    }

    @Test
    fun `idempotency-key replays the original create instead of duplicating`() = testApplication {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        application { module() }
        try {
            val token = bearer(userId)
            val gv = randomGameVersion()
            val key = java.util.UUID.randomUUID().toString()
            suspend fun create() = client.post("/sessions") {
                header(AUTH, token)
                header("Idempotency-Key", key)
                contentType(ContentType.Application.Json)
                setBody("""{"gameVersion":$gv}""")
            }

            val first = create()
            val second = create()
            assertEquals(HttpStatusCode.Created, first.status)
            assertEquals(HttpStatusCode.Created, second.status)

            val firstId = Json.decodeFromString(Session.serializer(), first.bodyAsText()).id
            val secondId = Json.decodeFromString(Session.serializer(), second.bodyAsText()).id
            assertEquals(firstId, secondId, "retry must replay the stored response")

            val listed = client.get("/sessions?gameVersion=$gv") { header(AUTH, token) }
            val page = Json.decodeFromString(SessionPage.serializer(), listed.bodyAsText())
            assertEquals(1, page.items.size, "side effect must not run twice")

            val other = client.post("/sessions") {
                header(AUTH, token)
                header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                contentType(ContentType.Application.Json)
                setBody("""{"gameVersion":$gv}""")
            }
            assertNotEquals(firstId, Json.decodeFromString(Session.serializer(), other.bodyAsText()).id)
        } finally {
            deleteUser(dsl, userId)
        }
    }
}
