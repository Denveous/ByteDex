package org.openmmo.bytedex.api.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class Database private constructor(
    private val dataSource: HikariDataSource,
) : AutoCloseable by dataSource {

    constructor(config: Config) : this(
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.dbUrl
                username = config.dbUser
                password = config.dbPassword
                maximumPoolSize = 10
            },
        ),
    )

    val dsl: DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)

    fun migrate() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
