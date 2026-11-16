package com.tshk.hlthhbbty.ui.integrations

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.isAppInstalled
import com.tshk.hlthhbbty.util.openUrl
import com.tshk.hlthhbbty.util.showMessage
import com.tshk.hlthhbbty.util.showSpotifyMessage
import kotlinx.coroutines.delay

val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun CoverImage(url: String?, size: Dp, corner: Dp = 12.dp, modifier: Modifier = Modifier, bitmap: android.graphics.Bitmap? = null) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(corner)).background(SpotifyGreen.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, tint = SpotifyGreen, modifier = Modifier.size(size * 0.45f))
    }
}

/** Позиция трека, плавно обновляемая между событиями плеера. */
@Composable
fun rememberPosition(np: NowPlaying?): Long {
    var pos by remember { mutableLongStateOf(np?.positionNow() ?: 0L) }
    LaunchedEffect(np?.key) {
        while (true) {
            pos = np?.positionNow() ?: 0L
            if (np?.isPlaying != true) break
            delay(500)
        }
    }
    return pos
}

@Composable
fun MiniPlayer(np: NowPlaying, onToggle: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = Liquid.palette
    val pos = rememberPosition(np)
    GlassCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(np.artUrl, 52.dp, bitmap = np.artBitmap)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(np.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(np.artist, np.appName).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GlassIconButton(if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Пауза/Играть", onToggle, size = 42.dp, tint = SpotifyGreen)
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Rounded.SkipNext, "Следующий", onNext, size = 42.dp)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(SpotifyGreen.copy(alpha = 0.18f))) {
            val frac = if (np.durationMs > 0) pos / np.durationMs.toFloat() else 0f
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(50)).background(SpotifyGreen))
        }
    }
}

fun Context.openSpotifyApp() {
    if (isAppInstalled("com.spotify.music")) {
        packageManager.getLaunchIntentForPackage("com.spotify.music")?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        showMessage("Spotify", "Включите любой трек в приложении Spotify и вернитесь — плеер подхватит воспроизведение.")
    } else {
        openUrl("market://details?id=com.spotify.music")
    }
}

fun Context.openLogin(uri: Uri) = openUrl(uri.toString())

fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Обработка событий ViewModel: открыть вход, приложение Spotify, показать сообщение. */
@Composable
fun SpotifyEvents(vm: SpotifyViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is SpotifyEvent.OpenUrl -> context.openLogin(e.uri)
                is SpotifyEvent.OpenInSpotify -> {
                    if (e.reason != null) context.showSpotifyMessage(e.reason + "\nПлейлист открыт в приложении Spotify — управлять музыкой можно из нашего плеера.")
                    context.openInSpotify(e.contextUri)
                }
                SpotifyEvent.OpenSpotifyApp -> context.openSpotifyApp()
                is SpotifyEvent.Message -> context.showSpotifyMessage(e.text)
            }
        }
    }
}


/** Открыть плейлист в приложении Spotify (или в браузере, если его нет). */
fun Context.openInSpotify(contextUri: String) {
    val web = "https://open.spotify.com/" + contextUri.removePrefix("spotify:").replace(':', '/')
    val target = if (isAppInstalled("com.spotify.music")) contextUri else web
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: android.content.ActivityNotFoundException) {
        openUrl(web)
    }
}

/** Карточка-подсказка: включить доступ к медиасессиям для универсального плеера. */
@Composable
fun MediaAccessCard(vm: SpotifyViewModel, modifier: Modifier = Modifier) {
    val p = Liquid.palette
    val context = androidx.compose.ui.platform.LocalContext.current
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎛️", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Плеер для любой музыки", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                Text(
                    "Управляйте Spotify, Яндекс Музыкой и другими прямо отсюда. Включите для HLTH HBTy «Доступ к уведомлениям» — один раз.",
                    style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        com.tshk.hlthhbbty.ui.glass.GlassButton(
            "Включить", { context.startActivity(vm.mediaAccessIntent()) }, Modifier.fillMaxWidth(),
        )
    }
}
