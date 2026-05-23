import com.github.gradle.node.yarn.task.YarnTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.node.gradle)
    id("org.openapi.generator")
    base
}

node {
    download = true
    version = "22.13.0"
    yarnVersion = "1.22.22"
}

// TypeScript axios client generated from the shared OpenAPI spec.
// Output lives in src/openapi (git-ignored) and is regenerated on every build.
val openApiSpec = rootProject.layout.projectDirectory.file("openapi.yaml")
val openApiOut = layout.projectDirectory.dir("src/openapi")

tasks.named<GenerateTask>("openApiGenerate") {
    generatorName = "typescript-axios"
    inputSpec = openApiSpec.asFile.path
    outputDir = openApiOut.asFile.path
    generateApiTests = false
    generateModelTests = false
    generateApiDocumentation = false
    generateModelDocumentation = false
    apiPackage = "api"
    modelPackage = "models"
    configOptions = mapOf(
        "supportsES6" to "true",
        "withSeparateModelsAndApi" to "true",
        "useSingleRequestParameter" to "true",
        "enumPropertyNaming" to "UPPERCASE",
    )
    inputs.file(openApiSpec)
    outputs.dir(openApiOut)
}

val nextLint = tasks.register<YarnTask>("nextLint") {
    dependsOn(tasks.named("yarn"), tasks.named("openApiGenerate"))
    yarnCommand = listOf("run", "lint")
}

val nextBuild = tasks.register<YarnTask>("nextBuild") {
    dependsOn(tasks.named("yarn"), tasks.named("openApiGenerate"))
    yarnCommand = listOf("run", "build")
    inputs.dir("src")
    inputs.files("package.json", "yarn.lock", "next.config.mjs", "tsconfig.json")
    outputs.dir(layout.buildDirectory.dir("next"))
}

// Live preview: runs the Next.js dev server with hot reload. This is a
// long-running, interactive task - it never "completes", so it declares no
// outputs and is excluded from the build cache and up-to-date checks.
tasks.register<YarnTask>("nextDev") {
    group = "application"
    description = "Runs the Next.js dev server with hot reload for live preview."
    dependsOn(tasks.named("yarn"), tasks.named("openApiGenerate"))
    yarnCommand = listOf("run", "dev")
    outputs.upToDateWhen { false }
}

tasks.named("check") { dependsOn(nextLint) }
tasks.named("assemble") { dependsOn(nextBuild) }
