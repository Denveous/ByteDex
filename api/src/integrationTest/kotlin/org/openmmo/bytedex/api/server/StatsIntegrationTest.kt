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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.openmmo.bytedex.api.model.ArchiveStats
import org.openmmo.bytedex.api.model.IngestSeries
import org.openmmo.bytedex.api.model.Leaderboard
import org.openmmo.bytedex.api.model.Session

private const val AUTH = HttpHeaders.Authorization
private const val ANON = "00000000-0000-0000-0000-000000000000"

class StatsIntegrationTest {

    @Test
    fun `stats, leaderboard and ingest reflect submitted packets`() = testApplication {
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
            repeat(3) { i ->
                client.post("/sessions/$sessionId/packets") {
                    header(AUTH, token)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"protocol":"GAME","direction":"C2S","packetId":$i,"payload":"AQID","capturedAt":"2026-01-01T00:00:00Z"}""",
                    )
                }
            }
            runInference(dsl)

            val statsRes = client.get("/stats?gameVersion=$gv") { header(AUTH, token) }
            assertEquals(HttpStatusCode.OK, statsRes.status)
            val stats = Json.decodeFromString(ArchiveStats.serializer(), statsRes.bodyAsText())
            assertEquals(3L, stats.totalPackets)
            assertEquals(1L, stats.totalSessions)
            assertEquals(1, stats.totalContributors)
            assertEquals(1, stats.activeContributors)
            assertTrue((stats.totalSchemas ?: 0) >= 1, "inference produced a schema for this gv")
            assertEquals(3, stats.byProtocol?.get("GAME"))

            val lb = Json.decodeFromString(
                Leaderboard.serializer(),
                client.get("/stats/leaderboard?limit=100") { header(AUTH, token) }.bodyAsText(),
            )
            val mine = assertNotNull(
                lb.propertyEntries.firstOrNull { it.userId == userId.toString() },
                "the contributor is ranked",
            )
            assertTrue(mine.packetCount >= 3, "packet count counts our submissions")
            assertTrue(mine.sessionCount >= 1)
            assertTrue(
                lb.propertyEntries.none { it.userId == ANON },
                "the anonymous account is excluded",
            )

            val ingest = Json.decodeFromString(
                IngestSeries.serializer(),
                client.get("/stats/ingest?days=365") { header(AUTH, token) }.bodyAsText(),
            )
            assertEquals(365, ingest.days)
            assertTrue(ingest.buckets.isNotEmpty())
            assertTrue(ingest.buckets.sumOf { it.c2s } >= 3, "our 3 C2S packets are bucketed")
        } finally {
            deleteUser(dsl, userId)
            cleanupGameVersion(dsl, gv)
        }
    }
}
