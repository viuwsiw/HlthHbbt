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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tshk.hlthhbbty.spotify.Playback
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.isAppInstalled
import com.tshk.hlthhbbty.util.openUrl
import kotlinx.coroutines.delay

val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun CoverImage(url: String?, size: Dp, corner: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(corner)).background(SpotifyGreen.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, tint = SpotifyGreen, modifier = Modifier.size(size * 0.45f))
    }
}

/** Позиция трека, плавно обновляемая между запросами к API. */
@Composable
fun rememberPosition(pb: Playback?): Long {
    var pos by remember { mutableLongStateOf(pb?.positionNow() ?: 0L) }
    LaunchedEffect(pb) {
        while (true) {
            pos = pb?.positionNow() ?: 0L
            if (pb?.isPlaying != true) break
            delay(500)
        }
    }
    return pos
}

@Composable
fun MiniPlayer(pb: Playback, onToggle: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = Liquid.palette
    val track = pb.track ?: return
    val pos = rememberPosition(pb)
    GlassCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(track.image, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artists, style = MaterialTheme.typography.labelMedium, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GlassIconButton(if (pb.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Пауза/Играть", onToggle, size = 42.dp, tint = SpotifyGreen)
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Rounded.SkipNext, "Следующий", onNext, size = 42.dp)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(SpotifyGreen.copy(alpha = 0.18f))) {
            val frac = if (track.durationMs > 0) pos / track.durationMs.toFloat() else 0f
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(50)).background(SpotifyGreen))
        }
    }
}

fun Context.openSpotifyApp() {
    if (isAppInstalled("com.spotify.music")) {
        packageManager.getLaunchIntentForPackage("com.spotify.music")?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Toast.makeText(this, "Включите любой трек в Spotify и вернитесь — плеер подхватит его", Toast.LENGTH_LONG).show()
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
                SpotifyEvent.OpenSpotifyApp -> context.openSpotifyApp()
                is SpotifyEvent.Message -> Toast.makeText(context, e.text, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
