package org.openmmo.bytedex.api.server

import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.openmmo.bytedex.api.jooq.Tables.REFRESH_TOKENS
import org.openmmo.bytedex.api.jooq.Tables.SESSIONS
import org.openmmo.bytedex.api.jooq.Tables.USERS
import org.jooq.DSLContext

private const val AUTH = HttpHeaders.Authorization

class AccountDeletionIntegrationTest {

    private fun seedSession(dsl: DSLContext, userId: UUID, gameVersion: Long): UUID =
        dsl.insertInto(SESSIONS)
            .set(SESSIONS.SUBMITTED_BY, userId)
            .set(SESSIONS.GAME_VERSION, gameVersion)
            .returning(SESSIONS.ID)
            .fetchOne()!!
            .get(SESSIONS.ID)

    @Test
    fun `deletion reassigns content to anonymous and removes the user`() {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        var sessionId: UUID? = null
        try {
            RefreshTokens(dsl, ItEnv.config).issue(userId)
            sessionId = seedSession(dsl, userId, randomGameVersion())

            UserService(dsl).deleteAccount(userId)

            assertNull(
                dsl.selectFrom(USERS).where(USERS.ID.eq(userId)).fetchOne(),
                "the user row must be gone",
            )
            assertEquals(
                ANONYMOUS_USER_ID,
                dsl.select(SESSIONS.SUBMITTED_BY).from(SESSIONS)
                    .where(SESSIONS.ID.eq(sessionId)).fetchOne()!!.value1(),
                "the session must survive, reattributed to the anonymous account",
            )
            assertEquals(
                0,
                dsl.fetchCount(
                    dsl.selectFrom(REFRESH_TOKENS).where(REFRESH_TOKENS.USER_ID.eq(userId)),
                ),
                "refresh tokens must cascade-delete with the user",
            )
        } finally {
            sessionId?.let {
                dsl.deleteFrom(SESSIONS).where(SESSIONS.ID.eq(it)).execute()
            }
            deleteUser(dsl, userId)
        }
    }

    @Test
    fun `DELETE users-me needs auth, then 204s and the user is gone`() = testApplication {
        val dsl = requireDb()
        val userId = seedUser(dsl)
        application { module() }
        try {
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.delete("/users/me").status,
            )
            assertEquals(
                HttpStatusCode.NoContent,
                client.delete("/users/me") { header(AUTH, bearer(userId)) }.status,
            )
            assertNull(dsl.selectFrom(USERS).where(USERS.ID.eq(userId)).fetchOne())
        } finally {
            deleteUser(dsl, userId)
        }
    }
}
