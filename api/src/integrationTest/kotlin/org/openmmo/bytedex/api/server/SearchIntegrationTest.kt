package org.openmmo.bytedex.api.server

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
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.openmmo.bytedex.api.model.PacketPage
import org.openmmo.bytedex.api.model.Session

private const val AUTH = HttpHeaders.Authorization

class SearchIntegrationTest {

    @Test
    fun `payloadHex matches packets whose payload contains the bytes`() = testApplication {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        val gv = randomGameVersion()
        application { module() }
        try {
            val token = bearer(userId)
            val sessionId = run {
                val r = client.post("/sessions") {
                    header(AUTH, token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"gameVersion":$gv}""")
                }
                Json.decodeFromString(Session.serializer(), r.bodyAsText()).id
            }

            for ((pid, b64) in listOf(0 to "AQID", 1 to "CgsM")) {
                client.post("/sessions/$sessionId/packets") {
                    header(AUTH, token)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"protocol":"GAME","direction":"C2S","packetId":$pid,"payload":"$b64","capturedAt":"2026-01-01T00:00:00Z"}""",
                    )
                }
            }

            suspend fun search(payloadHex: String) =
                client.post("/search") {
                    header(AUTH, token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"gameVersions":[$gv],"payloadHex":"$payloadHex"}""")
                }

            val a = search("02 03")
            assertEquals(HttpStatusCode.OK, a.status)
            val pageA = Json.decodeFromString(PacketPage.serializer(), a.bodyAsText())
            assertEquals(1, pageA.pagination.total)
            assertEquals(0, pageA.items.single().packetId)

            val b = Json.decodeFromString(
                PacketPage.serializer(),
                search("0a0b").bodyAsText(),
            )
            assertEquals(1, b.pagination.total)
            assertEquals(1, b.items.single().packetId)

            assertEquals(
                0,
                Json.decodeFromString(PacketPage.serializer(), search("ff").bodyAsText())
                    .pagination.total,
            )

            assertEquals(HttpStatusCode.BadRequest, search("zz").status)
            assertEquals(HttpStatusCode.BadRequest, search("abc").status)
        } finally {
            deleteUser(dsl, userId)
            cleanupGameVersion(dsl, gv)
        }
    }
}
