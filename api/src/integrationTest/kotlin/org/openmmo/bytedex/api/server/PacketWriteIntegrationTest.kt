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
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.openmmo.bytedex.api.model.PacketBatchAck
import org.openmmo.bytedex.api.model.PacketBatchPartial
import org.openmmo.bytedex.api.model.PacketPage
import org.openmmo.bytedex.api.model.PacketSchema
import org.openmmo.bytedex.api.model.Session

private const val AUTH = HttpHeaders.Authorization

private const val PLAIN_PAYLOAD = "AQID"
private const val ZLIB_PAYLOAD = "eJw="

private fun submission(payload: String, packetId: Int = 0) =
    """{"protocol":"GAME","direction":"C2S","packetId":$packetId,"payload":"$payload","capturedAt":"2026-01-01T00:00:00Z"}"""

class PacketWriteIntegrationTest {

    private suspend fun openSession(client: io.ktor.client.HttpClient, token: String, gv: Long): String {
        val resp = client.post("/sessions") {
            header(AUTH, token)
            contentType(ContentType.Application.Json)
            setBody("""{"gameVersion":$gv}""")
        }
        return Json.decodeFromString(Session.serializer(), resp.bodyAsText()).id
    }

    @Test
    fun `single, batch, closed-session and zlib-rejection submit semantics`() = testApplication {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        val gv = randomGameVersion()
        application { module() }
        try {
            val token = bearer(userId)
            val sessionId = openSession(client, token, gv)

            val single = client.post("/sessions/$sessionId/packets") {
                header(AUTH, token)
                contentType(ContentType.Application.Json)
                setBody(submission(PLAIN_PAYLOAD))
            }
            assertEquals(HttpStatusCode.Created, single.status)

            val batch = client.post("/sessions/$sessionId/packets") {
                header(AUTH, token)
                contentType(ContentType.Application.Json)
                setBody("""{"packets":[${submission(PLAIN_PAYLOAD, 1)},${submission(PLAIN_PAYLOAD, 2)}]}""")
            }
            assertEquals(HttpStatusCode.Accepted, batch.status)
            assertEquals(2, Json.decodeFromString(PacketBatchAck.serializer(), batch.bodyAsText()).accepted)

            val mixed = client.post("/sessions/$sessionId/packets") {
                header(AUTH, token)
                contentType(ContentType.Application.Json)
                setBody("""{"packets":[${submission(PLAIN_PAYLOAD, 3)},${submission(ZLIB_PAYLOAD, 4)}]}""")
            }
            assertEquals(HttpStatusCode.MultiStatus, mixed.status)
            val partial = Json.decodeFromString(PacketBatchPartial.serializer(), mixed.bodyAsText())
            assertEquals(1, partial.accepted)
            assertEquals(1, partial.rejected)

            val zlibSingle = client.post("/sessions/$sessionId/packets") {
                header(AUTH, token)
                contentType(ContentType.Application.Json)
                setBody(submission(ZLIB_PAYLOAD))
            }
            assertEquals(HttpStatusCode.BadRequest, zlibSingle.status)

            val listed = client.get("/sessions/$sessionId/packets") { header(AUTH, token) }
            val page = Json.decodeFromString(PacketPage.serializer(), listed.bodyAsText())
            assertEquals(4, page.pagination.total, "1 single + 2 batch + 1 from the mixed batch")

            client.post("/sessions/$sessionId/close") { header(AUTH, token) }
            val afterClose = client.post("/sessions/$sessionId/packets") {
                header(AUTH, token)
                contentType(ContentType.Application.Json)
                setBody(submission(PLAIN_PAYLOAD))
            }
            assertEquals(HttpStatusCode.Conflict, afterClose.status)
        } finally {
            deleteUser(dsl, userId)
            cleanupGameVersion(dsl, gv)
        }
    }

    @Test
    fun `inference produces a schema that can be read and annotated`() = testApplication {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        val gv = randomGameVersion()
        application { module() }
        try {
            val token = bearer(userId)
            val sessionId = openSession(client, token, gv)
            repeat(3) {
                client.post("/sessions/$sessionId/packets") {
                    header(AUTH, token)
                    contentType(ContentType.Application.Json)
                    setBody(submission(PLAIN_PAYLOAD))
                }
            }

            runInference(dsl)

            val schemaResp = client.get("/schemas/$gv/GAME/C2S/0") { header(AUTH, token) }
            assertEquals(HttpStatusCode.OK, schemaResp.status)
            val schema = Json.decodeFromString(PacketSchema.serializer(), schemaResp.bodyAsText())
            assertEquals(gv, schema.gameVersion)
            assertTrue(schema.sampleCount >= 3)
            assertTrue(schema.fields.isNotEmpty())

            val annotation = client.post("/schemas/$gv/GAME/C2S/0/annotations") {
                header(AUTH, token)
                contentType(ContentType.Application.Json)
                setBody("""{"offset":0,"length":1,"type":"uint8","name":"opcode"}""")
            }
            assertEquals(HttpStatusCode.Created, annotation.status)
        } finally {
            deleteUser(dsl, userId)
            cleanupGameVersion(dsl, gv)
        }
    }
}
