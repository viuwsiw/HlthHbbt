package com.tshk.hlthhbbty

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
    }

    /** Возврат со страницы входа Spotify: hlthhbbty://spotify-callback?code=… */
    private fun handleSpotifyRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "hlthhbbty" || uri.host != "spotify-callback") return
        intent.data = null
        val c = (application as HlthApp).container
        lifecycleScope.launch {
            c.spotify.handleRedirect(uri)
                .onSuccess { Toast.makeText(this@MainActivity, "Spotify подключён", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(this@MainActivity, "Spotify: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
}
