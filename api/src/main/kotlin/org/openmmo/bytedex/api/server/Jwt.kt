package org.openmmo.bytedex.api.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import java.util.Date
import java.util.UUID

const val JWT_REALM = "bytedex"

class JwtService(private val config: Config) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    fun issueAccessToken(userId: UUID): String =
        JWT.create()
            .withIssuer(config.jwtIssuer)
            .withSubject(userId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + config.accessTokenTtlSeconds * 1000))
            .sign(algorithm)

    fun configure(auth: AuthenticationConfig) {
        auth.jwt(JWT_REALM) {
            realm = JWT_REALM
            verifier(JWT.require(algorithm).withIssuer(config.jwtIssuer).build())
            validate { credential ->
                credential.payload.subject?.let { JWTPrincipal(credential.payload) }
            }
            challenge { _, _ ->
                throw ProblemException(
                    HttpStatusCode.Unauthorized, "Unauthorized", "Missing or invalid credentials",
                )
            }
        }
    }
}

fun Application.installAuth(jwt: JwtService) {
    install(Authentication) { jwt.configure(this) }
}

fun ApplicationCall.currentUserId(): UUID =
    UUID.fromString(principal<JWTPrincipal>()!!.subject)
