package org.openmmo.bytedex.api.server

import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class GitHubOAuthTest {
    private val config = Config()
    private val callbackUrl = "http://localhost:8080/auth/github/callback"
    private val clientRedirect = "http://localhost:3000/auth/callback"

    @Test
    fun `complete rejects an unknown state`() {
        val oauth = GitHubOAuth(config)
        assertFailsWith<ProblemException> {
            runBlocking { oauth.complete("any-code", "never-issued", callbackUrl) }
        }
    }

    @Test
    fun `complete rejects state past its ttl`() {
        val oauth = GitHubOAuth(config, stateTtlMillis = 0)
        val authorizeUrl = oauth.begin(clientRedirect, callbackUrl)
        val state = Regex("[?&]state=([^&]+)").find(authorizeUrl)!!.groupValues[1]
        Thread.sleep(5)
        assertFailsWith<ProblemException> {
            runBlocking { oauth.complete("any-code", state, callbackUrl) }
        }
    }
}
