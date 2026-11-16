package com.tshk.hlthhbbty

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.tshk.hlthhbbty.util.showMessage
import com.tshk.hlthhbbty.util.showSpotifyMessage
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tshk.hlthhbbty.data.AppSettings
import com.tshk.hlthhbbty.data.ThemeMode
import com.tshk.hlthhbbty.ui.AppRoot
import com.tshk.hlthhbbty.ui.theme.HlthTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSpotifyRedirect(intent)
        handleFocusOpen(intent)
        val settingsFlow = (application as HlthApp).container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(AppSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.BLACK -> true
            }
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                )
                onDispose {}
            }
            HlthTheme(dark, settings.accentIndex, black = settings.themeMode == ThemeMode.BLACK) { AppRoot() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSpotifyRedirect(intent)
        handleFocusOpen(intent)
    }

    /** Возврат со страницы входа Spotify: hlthhbbty://spotify-callback?code=… */
    private fun handleSpotifyRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "hlthhbbty" || uri.host != "spotify-callback") return
        intent.data = null
        val c = (application as HlthApp).container
        lifecycleScope.launch {
            c.spotify.handleRedirect(uri)
                .onSuccess { showMessage("Spotify", "Аккаунт подключён — плейлисты и плеер доступны в «Интеграциях»") }
                .onFailure { showSpotifyMessage("Не удалось войти: ${it.message}") }
        }
    }

    private fun handleFocusOpen(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_FOCUS, false) == true) {
            intent.removeExtra(EXTRA_OPEN_FOCUS)
            (application as HlthApp).container.focusTimer.openRequest.value = true
        }
        intent?.getStringExtra(EXTRA_WIDGET_ROUTE)?.let { route ->
            intent.removeExtra(EXTRA_WIDGET_ROUTE)
            (application as HlthApp).container.widgetRoute.value = route
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_WALK, false) == true) {
            intent.removeExtra(EXTRA_OPEN_WALK)
            (application as HlthApp).container.walkOpenRequest.value = true
        }
    }

    companion object {
        const val EXTRA_OPEN_FOCUS = "open_focus"
        const val EXTRA_OPEN_WALK = "open_walk"
        const val EXTRA_WIDGET_ROUTE = "widget_route"
    }
}
