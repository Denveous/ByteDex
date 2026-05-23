import org.jooq.meta.jaxb.Logging

buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:12.6.1")
        classpath("org.postgresql:postgresql:42.7.4")
    }
}

plugins {
    id("bytedex.kotlin-conventions")
    id("bytedex.openapi-server-conventions")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.ktor)
    alias(libs.plugins.flyway)
    alias(libs.plugins.jooq.codegen)
}

dependencies {
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.logback.classic)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jooq)

    jooqCodegen(libs.postgresql)

    testImplementation(libs.ktor.server.test.host)
}

application {
    mainClass = "org.openmmo.bytedex.api.server.ApplicationKt"
}

val migrations = layout.projectDirectory.dir("src/main/resources/db/migration")
val jooqGeneratedDir = layout.projectDirectory.dir("src/jooq")

fun dbProp(env: String, default: String): String = System.getenv(env) ?: default

val dbUrl = dbProp("DATABASE_URL", "jdbc:postgresql://localhost:5432/bytedex")
val dbUser = dbProp("POSTGRES_USER", "bytedex")
val dbPassword = dbProp("POSTGRES_PASSWORD", "bytedex_local_dev")

flyway {
    url = dbUrl
    user = dbUser
    password = dbPassword
    locations = arrayOf("filesystem:${migrations.asFile.path}")
    cleanDisabled = false
}

jooq {
    configuration {
        logging = Logging.WARN
        jdbc {
            driver = "org.postgresql.Driver"
            url = dbUrl
            user = dbUser
            password = dbPassword
        }
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                excludes = "flyway_schema_history"
            }
            generate {
                isImplicitJoinPathsToMany = false
            }
            target {
                packageName = "org.openmmo.bytedex.api.jooq"
                directory = jooqGeneratedDir.asFile.path
            }
        }
    }
}

tasks.named<org.jooq.codegen.gradle.CodegenTask>("jooqCodegen") {
    dependsOn("flywayMigrate")
    inputs.dir(migrations).withPropertyName("migrations")
}
listOf("compileJava", "compileKotlin").forEach { task ->
    tasks.named(task) { mustRunAfter("jooqCodegen") }
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

testing.suites.register("integrationTest", JvmTestSuite::class) {
    useJUnitJupiter()
    dependencies { implementation(project()) }
    targets.all {
        testTask.configure {
            description = "Runs integration tests."
            mustRunAfter("jooqCodegen")
        }
    }
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations["implementation"], configurations["testImplementation"])
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations["runtimeOnly"], configurations["testRuntimeOnly"])
}

kotlin.target.compilations.named("integrationTest") {
    associateWith(kotlin.target.compilations.named("main").get())
}

tasks.matching { it.name == "sonarlintIntegrationTest" }.configureEach { enabled = false }
