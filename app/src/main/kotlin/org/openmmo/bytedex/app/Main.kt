package org.openmmo.bytedex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import org.openmmo.bytedex.app.agent.AgentAttacher
import org.openmmo.bytedex.app.auth.AuthClient
import org.openmmo.bytedex.app.auth.CurrentUser
import org.openmmo.bytedex.app.auth.OAuthLoopback
import org.openmmo.bytedex.app.auth.TokenStore
import org.openmmo.bytedex.app.capture.CaptureService
import org.openmmo.bytedex.app.ui.AttachScreen
import org.openmmo.bytedex.app.ui.CaptureScreen
import org.openmmo.bytedex.app.ui.LoginScreen
import org.openmmo.bytedex.proxy.Proxy
import kotlinx.coroutines.runBlocking

private sealed interface AppState {
    data object Loading : AppState
    data class LoggedOut(val error: String? = null, val busy: Boolean = false) : AppState
    data class LoggedIn(val user: CurrentUser) : AppState
}

private sealed interface HomeState {
    data object PickingTarget : HomeState
    data class Capturing(val pid: String, val gameVersion: Long) : HomeState
}

private val ByteDexDark = darkColorScheme(
    primary = Color(0xFFB7C5FF),
    onPrimary = Color(0xFF14181F),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF14181F),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF1F242C),
    onSurfaceVariant = Color(0xFF9AA3AD),
    error = Color(0xFFEF6F6C),
)

fun main() {
    val tokenStore = TokenStore()
    val captureService = CaptureService(tokenStore)
    val proxy = Proxy(sink = captureService).also { it.start() }
    Runtime.getRuntime().addShutdownHook(
        Thread {
            runCatching { runBlocking { captureService.close() } }
            runCatching { captureService.shutdown() }
            runCatching { proxy.stop() }
        },
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ByteDex",
            state = rememberWindowState(size = DpSize(820.dp, 600.dp)),
        ) {
            MaterialTheme(colorScheme = ByteDexDark) {
                Root(tokenStore = tokenStore, captureService = captureService)
            }
        }
    }
}

@Composable
private fun Root(tokenStore: TokenStore, captureService: CaptureService) {
    val scope = rememberCoroutineScope()
    val auth = remember(tokenStore) { AuthClient(tokenStore) }
    var state: AppState by remember { mutableStateOf(AppState.Loading) }

    LaunchedEffect(Unit) {
        state = if (tokenStore.load() == null) {
            AppState.LoggedOut()
        } else {
            runCatching { auth.me() }
                .fold(
                    onSuccess = { AppState.LoggedIn(it) },
                    onFailure = {
                        tokenStore.clear()
                        AppState.LoggedOut()
                    },
                )
        }
    }

    when (val s = state) {
        AppState.Loading -> SplashSpinner()

        is AppState.LoggedOut -> LoginScreen(
            busy = s.busy,
            error = s.error,
            onSignIn = {
                state = AppState.LoggedOut(busy = true)
                scope.launch {
                    state = runCatching {
                        val code = OAuthLoopback.run()
                        auth.exchangeCode(code)
                        AppState.LoggedIn(auth.me())
                    }.getOrElse {
                        AppState.LoggedOut(error = it.message ?: "Sign-in failed")
                    }
                }
            },
        )

        is AppState.LoggedIn -> Home(
            user = s.user,
            captureService = captureService,
            onSignOut = {
                state = AppState.Loading
                scope.launch {
                    runCatching { captureService.close() }
                    runCatching { auth.logout() }
                    state = AppState.LoggedOut()
                }
            },
        )
    }
}

@Composable
private fun Home(
    user: CurrentUser,
    captureService: CaptureService,
    onSignOut: () -> Unit,
) {
    var home: HomeState by remember { mutableStateOf(HomeState.PickingTarget) }

    when (val h = home) {
        HomeState.PickingTarget -> AttachScreen(
            user = user,
            onSignOut = onSignOut,
            attach = { pid ->
                AgentAttacher.attach(pid).mapCatching { result ->
                    captureService.open(result.gameVersion).getOrThrow()
                    home = HomeState.Capturing(pid = pid, gameVersion = result.gameVersion)
                    result
                }
            },
        )

        is HomeState.Capturing -> CaptureScreen(
            user = user,
            pid = h.pid,
            statsFlow = captureService.stats,
            onSignOut = onSignOut,
            onDetach = {
                captureService.close()
                home = HomeState.PickingTarget
            },
        )
    }
}

@Composable
private fun SplashSpinner() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}
