package org.openmmo.bytedex.app.auth

import com.sun.net.httpserver.HttpServer
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.openmmo.bytedex.app.Config
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

object OAuthLoopback {

    suspend fun run(): String = withContext(Dispatchers.IO) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        val redirectUri = "http://127.0.0.1:$port/callback"

        val deferred = CompletableDeferred<String>()
        server.createContext("/callback") { exchange ->
            try {
                val q = exchange.requestURI.rawQuery.orEmpty()
                val params = q.split('&').mapNotNull {
                    val i = it.indexOf('='); if (i < 0) null
                    else java.net.URLDecoder.decode(it.substring(0, i), Charsets.UTF_8) to
                        java.net.URLDecoder.decode(it.substring(i + 1), Charsets.UTF_8)
                }.toMap()

                val code = params["code"]

                val body: String
                if (code != null) {
                    deferred.complete(code)
                    body = HTML_OK
                } else {
                    val err = params["error_description"] ?: params["error"] ?: "missing code"
                    deferred.completeExceptionally(IllegalStateException("OAuth callback: $err"))
                    body = htmlErr(err)
                }
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders["Content-Type"] = listOf("text/html; charset=utf-8")
                exchange.sendResponseHeaders(if (code != null) 200 else 400, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
            }
        }
        server.start()

        try {
            val authUrl = URLBuilder("${Config.apiBaseUrl}/auth/github/login").apply {
                parameters.append("redirect_uri", redirectUri)
            }.buildString()
            openInBrowser(authUrl)
            try {
                withTimeout(TIMEOUT_MS.milliseconds) { deferred.await() }
            } catch (_: TimeoutCancellationException) {
                throw IllegalStateException("Sign-in timed out - please try again.")
            }
        } finally {
            server.stop(1)
        }
    }

    private const val TIMEOUT_MS = 30_000L

    private fun openInBrowser(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                return
            }
        }
        System.err.println("Open this URL in your browser to continue sign-in:\n$url")
    }

    private val HTML_OK = """
        <!doctype html><html><head><meta charset="utf-8"><title>ByteDex</title>
        <style>html,body{height:100%;margin:0;font-family:system-ui,sans-serif;background:#0b0d10;color:#e6e8eb}
        .b{display:flex;align-items:center;justify-content:center;height:100%;flex-direction:column;gap:8px}
        .t{font-size:18px;font-weight:600}.s{font-size:13px;color:#9aa3ad}</style></head>
        <body><div class="b"><div class="t">Signed in to ByteDex.</div>
        <div class="s">You can close this tab.</div></div></body></html>
    """.trimIndent()

    private fun htmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun htmlErr(msg: String) = """
        <!doctype html><html><head><meta charset="utf-8"><title>ByteDex - error</title>
        <style>html,body{height:100%;margin:0;font-family:system-ui,sans-serif;background:#0b0d10;color:#e6e8eb}
        .b{display:flex;align-items:center;justify-content:center;height:100%;flex-direction:column;gap:8px}
        .t{font-size:18px;font-weight:600;color:#ef6f6c}.s{font-size:13px;color:#9aa3ad}</style></head>
        <body><div class="b"><div class="t">Sign-in failed.</div>
        <div class="s">${htmlEscape(msg)}</div></div></body></html>
    """.trimIndent()
}
