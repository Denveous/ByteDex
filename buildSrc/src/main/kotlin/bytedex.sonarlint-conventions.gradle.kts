import name.remal.gradle_plugins.sonarlint.SonarLint

plugins {
    id("name.remal.sonarlint")
}

sonarLint {
    failOnSeverity("ERROR")
    rules {
        // jOOQ's DSL uses get()/set(), so the indexed-accessor hint is noise.
        disable("kotlin:S6518")
        // Dispatchers.IO is correct for the blocking JDBC calls here.
        disable("kotlin:S6310")
        // Suspending Ktor handler extensions are the standard pattern.
        disable("kotlin:S6312")
        // Local accumulator lists are idiomatic.
        disable("kotlin:S6524")
        // The agent must catch Throwable so instrumentation errors can't crash the host JVM.
        disable("java:S1181")
        // KeyGen is a CLI tool, so System.out is i.O.
        disable("java:S106")
    }
}

tasks.withType<SonarLint>().configureEach {
    exclude {
        val path = it.file.invariantSeparatorsPath
        "/src/jooq/" in path || "/src/openapi/" in path
    }
}
