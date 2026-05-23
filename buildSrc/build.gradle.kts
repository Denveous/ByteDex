plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugins.kotlin.jvm.toMarker())
    implementation(libs.plugins.kotlin.serialization.toMarker())
    implementation(libs.plugins.sonarlint.toMarker())
    implementation(libs.plugins.openapi.generator.toMarker())
    implementation(libs.plugins.kotlin.compose.toMarker())
    implementation(libs.plugins.compose.multiplatform.toMarker())
}

fun Provider<PluginDependency>.toMarker(): Provider<String> =
    map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
