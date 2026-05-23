package org.openmmo.bytedex.app

object Config {
    val apiBaseUrl: String = System.getenv("BYTEDEX_API_URL") ?: "http://localhost:8080"
}
