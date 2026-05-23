package org.openmmo.bytedex.api.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.openmmo.bytedex.api.model.TokenResponse
import org.openmmo.bytedex.api.jooq.Tables.REFRESH_TOKENS
import org.openmmo.bytedex.api.jooq.Tables.USERS
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val rng = SecureRandom()
private val urlB64 = Base64.getUrlEncoder().withoutPadding()

private fun randomToken(bytes: Int = 32): String =
    ByteArray(bytes).also(rng::nextBytes).let(urlB64::encodeToString)

private fun sha256(value: String): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

class Users(private val dsl: DSLContext) {
    fun upsertFromGitHub(githubId: Long, login: String, name: String?, avatarUrl: String?): UUID =
        dsl.insertInto(USERS)
            .set(USERS.GITHUB_ID, githubId)
            .set(USERS.GITHUB_LOGIN, login)
            .set(USERS.DISPLAY_NAME, name)
            .set(USERS.AVATAR_URL, avatarUrl)
            .set(USERS.LAST_SEEN_AT, now())
            .onConflict(USERS.GITHUB_ID)
            .doUpdate()
            .set(USERS.GITHUB_LOGIN, login)
            .set(USERS.DISPLAY_NAME, name)
            .set(USERS.AVATAR_URL, avatarUrl)
            .set(USERS.LAST_SEEN_AT, now())
            .returning(USERS.ID)
            .fetchOne()!!
            .get(USERS.ID)
}

private sealed interface RotateOutcome {
    data class Ok(val userId: UUID, val refreshToken: String) : RotateOutcome
    data object Invalid : RotateOutcome
}

// Refresh tokens are single-use. Presenting an already-rotated or revoked token means it leaked, so the whole family is revoked.
class RefreshTokens(private val dsl: DSLContext, private val config: Config) {

    fun issue(userId: UUID, familyId: UUID = UUID.randomUUID()): String {
        val raw = randomToken()
        dsl.insertInto(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.USER_ID, userId)
            .set(REFRESH_TOKENS.TOKEN_HASH, sha256(raw))
            .set(REFRESH_TOKENS.FAMILY_ID, familyId)
            .set(REFRESH_TOKENS.EXPIRES_AT, now().plusSeconds(config.refreshTokenTtlSeconds))
            .execute()
        return raw
    }

    data class Rotated(val userId: UUID, val refreshToken: String)

    // Row lock plus single transaction serializes concurrent refreshes, so the second one sees the replaced row and is treated as reuse rather than forking the family.
    fun rotate(rawToken: String): Rotated {
        val outcome = dsl.transactionResult { cfg ->
            val tx = cfg.dsl()
            val row = tx.selectFrom(REFRESH_TOKENS)
                .where(REFRESH_TOKENS.TOKEN_HASH.eq(sha256(rawToken)))
                .forUpdate()
                .fetchOne() ?: return@transactionResult RotateOutcome.Invalid

            val reused = row.get(REFRESH_TOKENS.REVOKED_AT) != null ||
                row.get(REFRESH_TOKENS.REPLACED_BY_ID) != null
            if (reused) {
                // Returned rather than thrown so the family revocation commits with the transaction instead of rolling back.
                tx.update(REFRESH_TOKENS)
                    .set(REFRESH_TOKENS.REVOKED_AT, now())
                    .where(REFRESH_TOKENS.FAMILY_ID.eq(row.get(REFRESH_TOKENS.FAMILY_ID)))
                    .and(REFRESH_TOKENS.REVOKED_AT.isNull)
                    .execute()
                return@transactionResult RotateOutcome.Invalid
            }
            if (row.get(REFRESH_TOKENS.EXPIRES_AT).isBefore(now())) {
                return@transactionResult RotateOutcome.Invalid
            }

            val userId = row.get(REFRESH_TOKENS.USER_ID)
            val familyId = row.get(REFRESH_TOKENS.FAMILY_ID)
            val newRaw = randomToken()
            val newId = tx.insertInto(REFRESH_TOKENS)
                .set(REFRESH_TOKENS.USER_ID, userId)
                .set(REFRESH_TOKENS.TOKEN_HASH, sha256(newRaw))
                .set(REFRESH_TOKENS.FAMILY_ID, familyId)
                .set(REFRESH_TOKENS.EXPIRES_AT, now().plusSeconds(config.refreshTokenTtlSeconds))
                .returning(REFRESH_TOKENS.ID)
                .fetchOne()!!
                .get(REFRESH_TOKENS.ID)
            tx.update(REFRESH_TOKENS)
                .set(REFRESH_TOKENS.REVOKED_AT, now())
                .set(REFRESH_TOKENS.REPLACED_BY_ID, newId)
                .where(REFRESH_TOKENS.ID.eq(row.get(REFRESH_TOKENS.ID)))
                .execute()
            RotateOutcome.Ok(userId, newRaw)
        }
        return when (outcome) {
            is RotateOutcome.Ok -> Rotated(outcome.userId, outcome.refreshToken)
            RotateOutcome.Invalid -> throw unauthorized()
        }
    }

    fun revokeAllForUser(userId: UUID) {
        dsl.update(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.REVOKED_AT, now())
            .where(REFRESH_TOKENS.USER_ID.eq(userId))
            .and(REFRESH_TOKENS.REVOKED_AT.isNull)
            .execute()
    }

    private fun unauthorized() =
        ProblemException(HttpStatusCode.Unauthorized, "Unauthorized", "Invalid refresh token")
}

@Serializable
private data class GitHubToken(@SerialName("access_token") val accessToken: String? = null)

@Serializable
private data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

private class PendingAuth(
    val codeVerifier: String,
    val clientRedirectUri: String,
    val createdAt: Long = System.currentTimeMillis(),
)

class GitHubOAuth(
    private val config: Config,
    private val stateTtlMillis: Long = 10 * 60 * 1000,
) {
    private val client = HttpClient {
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    private val pending = ConcurrentHashMap<String, PendingAuth>()

    fun begin(clientRedirectUri: String, callbackUrl: String): String {
        val cutoff = System.currentTimeMillis() - stateTtlMillis
        pending.values.removeIf { it.createdAt < cutoff }
        val state = randomToken()
        val verifier = randomToken(64)
        pending[state] = PendingAuth(verifier, clientRedirectUri)
        val challenge = urlB64.encodeToString(sha256(verifier))
        return "https://github.com/login/oauth/authorize" +
            "?client_id=${config.githubClientId}" +
            "&redirect_uri=$callbackUrl" +
            "&scope=read:user" +
            "&state=$state" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"
    }

    data class Result(val user: GitHubAccount, val clientRedirectUri: String)
    data class GitHubAccount(val id: Long, val login: String, val name: String?, val avatarUrl: String?)

    suspend fun complete(code: String, state: String, callbackUrl: String): Result {
        val p = pending.remove(state)
            ?.takeIf { System.currentTimeMillis() - it.createdAt <= stateTtlMillis }
            ?: throw ProblemException(HttpStatusCode.BadRequest, "Bad Request", "Unknown or expired state")
        val token: GitHubToken = client.post("https://github.com/login/oauth/access_token") {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "client_id" to config.githubClientId,
                    "client_secret" to config.githubClientSecret,
                    "code" to code,
                    "redirect_uri" to callbackUrl,
                    "code_verifier" to p.codeVerifier,
                ),
            )
        }.body()
        val accessToken = token.accessToken
            ?: throw ProblemException(HttpStatusCode.Unauthorized, "Unauthorized", "GitHub rejected the code")
        val gh: GitHubUser = client.get("https://api.github.com/user") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }.body()
        return Result(GitHubAccount(gh.id, gh.login, gh.name, gh.avatarUrl), p.clientRedirectUri)
    }
}

@Serializable
private data class RefreshRequest(val refreshToken: String)

@Serializable
private data class ExchangeRequest(val code: String)

// Single-use short-lived codes so tokens never travel in a redirect URL.
private class AuthCodes(private val ttlMillis: Long = 60_000) {
    private class Entry(val tokens: TokenResponse, val createdAt: Long)
    private val codes = ConcurrentHashMap<String, Entry>()

    fun issue(tokens: TokenResponse): String {
        val cutoff = System.currentTimeMillis() - ttlMillis
        codes.values.removeIf { it.createdAt < cutoff }
        val code = randomToken()
        codes[code] = Entry(tokens, System.currentTimeMillis())
        return code
    }

    fun redeem(code: String): TokenResponse? {
        val entry = codes.remove(code) ?: return null
        if (System.currentTimeMillis() - entry.createdAt > ttlMillis) return null
        return entry.tokens
    }
}

fun Route.authRoutes(
    config: Config,
    jwt: JwtService,
    users: Users,
    refreshTokens: RefreshTokens,
    github: GitHubOAuth,
) {
    fun callbackUrl(call: ApplicationCall): String {
        val origin = call.request.origin
        val defaultPort = if (origin.scheme == "https") 443 else 80
        val host = if (origin.serverPort == defaultPort) {
            origin.serverHost
        } else {
            "${origin.serverHost}:${origin.serverPort}"
        }
        return "${origin.scheme}://$host/auth/github/callback"
    }

    fun tokenResponse(userId: UUID, refreshToken: String) = TokenResponse(
        accessToken = jwt.issueAccessToken(userId),
        refreshToken = refreshToken,
        tokenType = TokenResponse.TokenType.Bearer,
        expiresIn = config.accessTokenTtlSeconds.toInt(),
    )

    val authCodes = AuthCodes()
    val rateLimiter = RateLimiter(config.authRateLimitRequests, config.authRateLimitWindowSeconds)

    route("/auth") {
        install(RateLimitPlugin) { limiter = rateLimiter }

        get("/github/login") {
            val redirectUri = call.request.queryParameters["redirect_uri"]
                ?: badRequest("redirect_uri is required")
            if (!config.isRedirectUriAllowed(redirectUri)) {
                badRequest("redirect_uri is not allow-listed")
            }
            call.respondRedirect(github.begin(redirectUri, callbackUrl(call)))
        }

        get("/github/callback") {
            val code = call.request.queryParameters["code"] ?: badRequest("code is required")
            val state = call.request.queryParameters["state"] ?: badRequest("state is required")
            val result = github.complete(code, state, callbackUrl(call))
            val userId = users.upsertFromGitHub(
                result.user.id, result.user.login, result.user.name, result.user.avatarUrl,
            )
            val tokens = tokenResponse(userId, refreshTokens.issue(userId))
            val target = URLBuilder(result.clientRedirectUri).apply {
                parameters.append("code", authCodes.issue(tokens))
            }.buildString()
            call.respondRedirect(target)
        }

        post("/refresh") {
            val body = call.receive<RefreshRequest>()
            val rotated = refreshTokens.rotate(body.refreshToken)
            call.respond(tokenResponse(rotated.userId, rotated.refreshToken))
        }

        post("/exchange") {
            val body = call.receive<ExchangeRequest>()
            val tokens = authCodes.redeem(body.code)
                ?: throw ProblemException(
                    HttpStatusCode.Unauthorized, "Unauthorized", "Unknown or expired code",
                )
            call.respond(tokens)
        }

        authenticate(JWT_REALM) {
            post("/logout") {
                refreshTokens.revokeAllForUser(call.currentUserId())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
