package bytedex.build

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

fun Project.configureOpenApiGeneration() {
    val spec = rootProject.layout.projectDirectory.file("openapi.yaml")
    val generatedRoot = layout.projectDirectory.dir("src/openapi")
    val generatedKotlin = generatedRoot.dir("src/main/kotlin")

    tasks.named<GenerateTask>("openApiGenerate") {
        inputSpec.set(spec.asFile.path)
        outputDir.set(generatedRoot.asFile.path)
        cleanupOutput.set(true)
        generateApiTests.set(false)
        generateModelTests.set(false)
        generateApiDocumentation.set(false)
        generateModelDocumentation.set(false)
        inputs.file(spec)
        outputs.dir(generatedRoot)
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.getByType<SourceSetContainer>().named("main") {
            java.srcDir(generatedKotlin)
        }
        tasks.withType<KotlinCompile>().configureEach {
            dependsOn("openApiGenerate")
        }
    }
}
