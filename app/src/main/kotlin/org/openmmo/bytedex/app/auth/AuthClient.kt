package org.openmmo.bytedex.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.openmmo.bytedex.app.Config

@Serializable
data class CurrentUser(
    val id: String,
    val githubLogin: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val createdAt: String,
)

@Serializable
private data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Int,
)

@Serializable
private data class RefreshRequest(val refreshToken: String)

@Serializable
private data class ExchangeRequest(val code: String)

class AuthClient(
    private val tokenStore: TokenStore,
    private val baseUrl: String = Config.apiBaseUrl,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        expectSuccess = false
    }

    suspend fun me(): CurrentUser = authedGet("/users/me")

    suspend fun logout() {
        val tokens = tokenStore.load() ?: return
        runCatching {
            http.post("$baseUrl/auth/logout") {
                header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
            }
        }
        tokenStore.clear()
    }

    suspend fun exchangeCode(code: String) {
        val res = http.post("$baseUrl/auth/exchange") {
            contentType(ContentType.Application.Json)
            setBody(ExchangeRequest(code))
        }
        if (!res.status.isSuccess()) error("code exchange failed: ${res.status}")
        saveTokens(res.body())
    }

    private suspend inline fun <reified T> authedGet(path: String): T {
        val first = http.get("$baseUrl$path") { bearer() }
        val res: HttpResponse = if (first.status == HttpStatusCode.Unauthorized) {
            refreshTokensOrThrow()
            http.get("$baseUrl$path") { bearer() }
        } else first
        if (!res.status.isSuccess()) error("GET $path failed: ${res.status}")
        return res.body()
    }

    private suspend fun refreshTokensOrThrow() {
        val current = tokenStore.load() ?: error("not logged in")
        val res = http.post("$baseUrl/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(current.refreshToken))
        }
        if (!res.status.isSuccess()) {
            tokenStore.clear()
            error("refresh failed: ${res.status}")
        }
        saveTokens(res.body())
    }

    private fun saveTokens(t: TokenResponse) {
        tokenStore.save(
            Tokens(
                accessToken = t.accessToken,
                refreshToken = t.refreshToken,
                expiresAt = System.currentTimeMillis() + t.expiresIn * 1_000L,
            ),
        )
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer() {
        val tokens = tokenStore.load() ?: return
        header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
    }
}
