plugins {
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.node.gradle) apply false
}

allprojects {
    group = "org.openmmo.bytedex"
    version = "0.1.0"
}
