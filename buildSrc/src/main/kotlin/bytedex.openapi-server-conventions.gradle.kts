import bytedex.build.configureOpenApiGeneration
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("org.openapi.generator")
}

configureOpenApiGeneration()

tasks.named<GenerateTask>("openApiGenerate") {
    generatorName.set("kotlin-server")
    library.set("ktor")
    packageName.set("org.openmmo.bytedex.api")
    apiPackage.set("org.openmmo.bytedex.api")
    modelPackage.set("org.openmmo.bytedex.api.model")

    ignoreFileOverride.set(layout.projectDirectory.file(".openapi-generator-ignore").asFile.path)

    typeMappings.put("UUID", "kotlin.String")
    typeMappings.put("URI", "kotlin.String")

    configOptions.put("featureAutoHead", "false")
    configOptions.put("featureConditionalHeaders", "false")
    configOptions.put("featureCompression", "false")
    configOptions.put("featureHSTS", "false")
    configOptions.put("featureCORS", "false")
    configOptions.put("featureMetrics", "false")
    configOptions.put("featureResources", "false")
    configOptions.put("featureLocations", "false")
}
