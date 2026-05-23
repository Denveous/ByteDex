plugins {
    id("bytedex.kotlin-conventions")
    id("bytedex.openapi-client-conventions")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":proxy"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.ktor.client)
    implementation(libs.logback.classic)

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "org.openmmo.bytedex.app.MainKt"
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(21)
        }.get().metadata.installationPath.asFile.absolutePath
        buildTypes.release.proguard {
            optimize = false
            configurationFiles.from(project.file("compose-desktop.pro"))
        }
    }
}

val copyAgentJar by tasks.registering(Copy::class) {
    dependsOn(":agent:shadowJar")
    from(project(":agent").layout.buildDirectory.file("libs/bytedex-agent.jar"))
    into(layout.buildDirectory.dir("generated/resources/agent"))
}

val copyAgentKeys by tasks.registering(Copy::class) {
    dependsOn(":agent:generateKeypairs")
    from(project(":agent").layout.buildDirectory.file("generated/resources/keys/bytedex-keys.json"))
    into(layout.buildDirectory.dir("generated/resources/keys"))
}

val copyDnsConfig by tasks.registering(Copy::class) {
    dependsOn(":agent:copyDnsConfig")
    from(project(":agent").layout.buildDirectory.file("generated/resources/dns/bytedex-dns.json"))
    into(layout.buildDirectory.dir("generated/resources/dns"))
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/resources/agent"))
    resources.srcDir(layout.buildDirectory.dir("generated/resources/keys"))
    resources.srcDir(layout.buildDirectory.dir("generated/resources/dns"))
}

tasks.named("processResources") {
    dependsOn(copyAgentJar, copyAgentKeys, copyDnsConfig)
}
