package com.tshk.hlthhbbty.ui.integrations

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.data.Integrations
import com.tshk.hlthhbbty.data.MusicService
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.openUrl

@Composable
fun IntegrationsScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onPlayer: () -> Unit,
    onPlaylist: (String) -> Unit,
    vm: SpotifyViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val p = Liquid.palette
    val context = LocalContext.current
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val mediaAccess by vm.mediaAccess.collectAsStateWithLifecycle()
    SpotifyEvents(vm)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Интеграции", onBack, subtitle = "Музыка и здоровье") }

        // ---------- Сейчас играет (любое приложение) ----------
        item {
            val np = nowPlaying
            when {
                np != null -> MiniPlayer(np, vm::togglePlay, vm::next, onPlayer)
                !mediaAccess -> MediaAccessCard(vm)
            }
        }

        // ---------- Spotify ----------
        item {
            SectionTitle("Spotify", Modifier.padding(top = 8.dp)) {
                if (loggedIn) GlassChip(user?.let { if (it.isPremium) "Premium" else "Free" } ?: "Вход…", leading = "🟢")
            }
        }
        when {
            !vm.configured -> item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Spotify ещё не настроен в этой сборке", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                    VSpace(6.dp)
                    Text("Разработчику нужно указать Client ID из Spotify Dashboard.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
            !loggedIn -> item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎧", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Плеер прямо в приложении", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                            Text("Войдите в Spotify — появятся ваши плейлисты и управление музыкой.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        }
                    }
                    VSpace(14.dp)
                    GlassButton("Войти через Spotify", vm::login, Modifier.fillMaxWidth())
                }
            }
            else -> {
                item { SectionTitle("Для привычек") }
                items(Integrations.playlists.filter { it.service == MusicService.SPOTIFY }, key = { it.url }) { pl ->
                    val id = pl.url.substringAfterLast("/").substringBefore("?")
                    PlaylistRow(
                        cover = { Image(painterResource(pl.cover), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop) },
                        title = pl.title, subtitle = pl.subtitle,
                        onPlay = { vm.play(contextUri = "spotify:playlist:$id") },
                        onClick = { vm.play(contextUri = "spotify:playlist:$id"); onPlayer() },
                    )
                }
                if (playlists.isNotEmpty()) {
                    item { SectionTitle("Мои плейлисты") }
                    items(playlists, key = { it.id }) { pl ->
                        PlaylistRow(
                            cover = { CoverImage(pl.image, 56.dp, 14.dp) },
                            title = pl.name, subtitle = "${pl.tracks} треков · ${pl.owner}",
                            onPlay = { vm.play(contextUri = pl.uri) },
                            onClick = { onPlaylist(pl.id) },
                        )
                    }
                }
                item {
                    GlassButton("Выйти из Spotify", vm::logout, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary)
                }
            }
        }

        // ---------- Яндекс Музыка ----------
        item {
            SectionTitle("Яндекс Музыка", Modifier.padding(top = 8.dp))
        }
        item {
            Text(
                "У Яндекс Музыки нет публичного API, поэтому плейлисты открываются в приложении Яндекса.",
                style = MaterialTheme.typography.bodySmall, color = p.textSecondary, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        items(Integrations.playlists.filter { it.service == MusicService.YANDEX }, key = { it.url }) { pl ->
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), onClick = { context.openUrl(pl.url) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(pl.cover), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pl.title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                        Text(pl.subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Открыть", tint = p.accent)
                }
            }
        }

        // ---------- Здоровье ----------
        item { SectionTitle("Здоровье", Modifier.padding(top = 8.dp)) }
        item {
            GlassCard(Modifier.fillMaxWidth(), onClick = onSettings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("❤️", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Health Connect", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                        Text("Шаги и сон, автоотметка привычек. Подключение — в настройках.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    cover: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onClick: () -> Unit,
) {
    val p = Liquid.palette
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            cover()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            com.tshk.hlthhbbty.ui.glass.GlassIconButton(Icons.Rounded.PlayArrow, "Играть", onPlay, size = 42.dp, tint = SpotifyGreen)
        }
    }
}
