package org.openmmo.bytedex.api.server

import io.ktor.http.Url

private fun env(name: String, default: String? = null): String =
    System.getenv(name)?.takeIf(String::isNotBlank)
        ?: default
        ?: error("missing required env var $name")

private val loopbackHosts = setOf("localhost", "127.0.0.1", "::1", "[::1]")

class Config(
    val dbUrl: String = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/bytedex"),
    val dbUser: String = env("POSTGRES_USER", "bytedex"),
    val dbPassword: String = env("POSTGRES_PASSWORD"),
    val jwtSecret: String = env("JWT_SIGNING_SECRET"),
    val jwtIssuer: String = env("JWT_ISSUER", "bytedex"),
    val accessTokenTtlSeconds: Long = 15 * 60,
    val refreshTokenTtlSeconds: Long = 30L * 24 * 60 * 60,
    val githubClientId: String = env("GITHUB_OAUTH_CLIENT_ID", ""),
    val githubClientSecret: String = env("GITHUB_OAUTH_CLIENT_SECRET", ""),
    val appUrl: String = env("APP_URL", "http://localhost:3000").trimEnd('/'),
    val oauthRedirectAllowlist: List<String> =
        env("OAUTH_REDIRECT_ALLOWLIST", "")
            .split(",").map(String::trim).filter(String::isNotEmpty),
    val rateLimitRequests: Int = env("RATE_LIMIT_REQUESTS", "600").toInt(),
    val rateLimitWindowSeconds: Long = env("RATE_LIMIT_WINDOW_SECONDS", "60").toLong(),
    val authRateLimitRequests: Int = env("AUTH_RATE_LIMIT_REQUESTS", "60").toInt(),
    val authRateLimitWindowSeconds: Long = env("AUTH_RATE_LIMIT_WINDOW_SECONDS", "60").toLong(),
) {
    val frontendRedirectUri: String = "$appUrl/auth/callback"

    fun isRedirectUriAllowed(uri: String): Boolean {
        if (uri == frontendRedirectUri || uri in oauthRedirectAllowlist) return true
        val parsed = runCatching { Url(uri) }.getOrNull() ?: return false
        if (parsed.protocol.name !in setOf("http", "https")) return false
        return parsed.host in loopbackHosts || parsed.host.endsWith(".localhost")
    }
}
