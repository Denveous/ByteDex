package org.openmmo.bytedex.api.server

import java.util.UUID
import kotlin.random.Random
import org.jooq.DSLContext
import org.junit.jupiter.api.Assumptions
import org.openmmo.bytedex.api.jooq.Tables.FIELD_ANNOTATIONS
import org.openmmo.bytedex.api.jooq.Tables.PACKET_SCHEMAS
import org.openmmo.bytedex.api.jooq.Tables.SCHEMA_DIRTY_QUEUE
import org.openmmo.bytedex.api.jooq.Tables.USERS

object ItEnv {
    val config = Config()
    val database: Database? =
        runCatching { Database(config).also { it.dsl.selectOne().fetch() } }.getOrNull()
    val jwt = JwtService(config)
}

fun requireDb(): DSLContext {
    Assumptions.assumeTrue(ItEnv.database != null, "Postgres not reachable; skipping integration test")
    return ItEnv.database!!.dsl
}

fun seedUser(dsl: DSLContext): UUID =
    dsl.insertInto(USERS)
        .set(USERS.GITHUB_ID, Random.nextLong(1, Long.MAX_VALUE))
        .set(USERS.GITHUB_LOGIN, "itest-" + UUID.randomUUID())
        .returning(USERS.ID)
        .fetchOne()!!
        .get(USERS.ID)

fun deleteUser(dsl: DSLContext, userId: UUID) {
    dsl.deleteFrom(USERS).where(USERS.ID.eq(userId)).execute()
}

fun cleanupGameVersion(dsl: DSLContext, gameVersion: Long) {
    dsl.deleteFrom(FIELD_ANNOTATIONS).where(FIELD_ANNOTATIONS.GAME_VERSION.eq(gameVersion)).execute()
    dsl.deleteFrom(PACKET_SCHEMAS).where(PACKET_SCHEMAS.GAME_VERSION.eq(gameVersion)).execute()
    dsl.deleteFrom(SCHEMA_DIRTY_QUEUE).where(SCHEMA_DIRTY_QUEUE.GAME_VERSION.eq(gameVersion)).execute()
}

fun bearer(userId: UUID): String = "Bearer " + ItEnv.jwt.issueAccessToken(userId)

fun randomGameVersion(): Long = Random.nextLong(1, 4_294_967_295L)
