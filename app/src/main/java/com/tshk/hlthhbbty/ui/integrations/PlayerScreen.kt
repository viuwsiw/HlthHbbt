package com.tshk.hlthhbbty.ui.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid

@Composable
fun PlayerScreen(onBack: () -> Unit, vm: SpotifyViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val p = Liquid.palette
    val pb by vm.playback.collectAsStateWithLifecycle()
    SpotifyEvents(vm)
    val track = pb?.track
    val pos = rememberPosition(pb)
    var dragging by remember { mutableStateOf<Float?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        VSpace(12.dp)
        ScreenHeader("Сейчас играет", onBack, subtitle = pb?.device?.let { "на устройстве: ${it.name}" })
        VSpace(20.dp)
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
            if (track == null) {
                Text("Ничего не играет", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                VSpace(6.dp)
                Text(
                    "Выберите плейлист на экране интеграций или включите трек в приложении Spotify.",
                    style = MaterialTheme.typography.bodyMedium, color = p.textSecondary,
                )
                VSpace(14.dp)
                GlassButton("Играть", { vm.togglePlay() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
                return@GlassCard
            }
            CoverImage(track.image, 320.dp, 24.dp, Modifier.fillMaxWidth().aspectRatio(1f))
            VSpace(18.dp)
            Text(track.name, style = MaterialTheme.typography.headlineSmall, color = p.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artists, style = MaterialTheme.typography.titleMedium, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            VSpace(10.dp)
            Slider(
                value = dragging ?: pos.toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { vm.seek(it.toLong()) }; dragging = null },
                valueRange = 0f..track.durationMs.coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen, inactiveTrackColor = SpotifyGreen.copy(alpha = 0.2f)),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatMs((dragging?.toLong()) ?: pos), style = MaterialTheme.typography.labelSmall, color = p.textSecondary, modifier = Modifier.weight(1f))
                Text(formatMs(track.durationMs), style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
            }
            VSpace(12.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.Shuffle, "Перемешать", { vm.shuffle(!(pb?.shuffle ?: false)) }, size = 44.dp,
                    tint = if (pb?.shuffle == true) SpotifyGreen else p.textSecondary)
                GlassIconButton(Icons.Rounded.SkipPrevious, "Предыдущий", vm::previous, size = 54.dp)
                GlassIconButton(if (pb?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Играть/Пауза", vm::togglePlay, size = 72.dp, tint = SpotifyGreen)
                GlassIconButton(Icons.Rounded.SkipNext, "Следующий", vm::next, size = 54.dp)
                Spacer(Modifier.width(44.dp))
            }
        }
        VSpace(12.dp)
        Text(
            "Звук воспроизводится через Spotify. Управление требует Spotify Premium.",
            style = MaterialTheme.typography.labelSmall, color = p.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SpotifyPlaylistScreen(onBack: () -> Unit, onPlayer: () -> Unit, vm: SpotifyViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val p = Liquid.palette
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val pb by vm.playback.collectAsStateWithLifecycle()
    SpotifyEvents(vm)
    val playlist = playlists.firstOrNull { it.id == vm.playlistId }
    val contextUri = "spotify:playlist:${vm.playlistId}"

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(playlist?.name ?: "Плейлист", onBack, subtitle = playlist?.let { "${it.tracks} треков" }) }
        item {
            GlassButton("Слушать", { vm.play(contextUri); onPlayer() }, Modifier.fillMaxWidth().padding(top = 6.dp), icon = Icons.Rounded.PlayArrow)
        }
        pb?.let { item { if (it.track != null) MiniPlayer(it, vm::togglePlay, vm::next, onPlayer) } }
        when (val list = tracks) {
            null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SpotifyGreen) } }
            else -> {
                if (list.isEmpty()) item { Text("Треки не загрузились", color = p.textSecondary) }
                items(list, key = { it.uri + it.name }) { t ->
                    val current = pb?.track?.uri == t.uri
                    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp), onClick = { vm.play(contextUri, t.uri) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverImage(t.image, 46.dp, 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleSmall, color = if (current) SpotifyGreen else p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artists, style = MaterialTheme.typography.labelMedium, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formatMs(t.durationMs), style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
