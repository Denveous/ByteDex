import bytedex.build.configureOpenApiGeneration
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("org.openapi.generator")
}

configureOpenApiGeneration()

tasks.named<GenerateTask>("openApiGenerate") {
    generatorName.set("kotlin")
    library.set("jvm-ktor")
    packageName.set("org.openmmo.bytedex.client")
    apiPackage.set("org.openmmo.bytedex.client.api")
    modelPackage.set("org.openmmo.bytedex.client.model")

    configOptions.put("serializationLibrary", "kotlinx_serialization")
}
