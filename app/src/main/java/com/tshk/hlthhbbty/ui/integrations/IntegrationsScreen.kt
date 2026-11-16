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
    val profileError by vm.profileError.collectAsStateWithLifecycle()
    val mediaAccess by vm.mediaAccess.collectAsStateWithLifecycle()
    SpotifyEvents(vm)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Интеграции", onBack, subtitle = "Музыка и здоровье") }

        // ---------- 1. Плеер (любое приложение на телефоне) ----------
        item { BlockHeader("🎛️", "Плеер", "Управление музыкой из любого приложения — без входа в аккаунты") }
        item {
            val np = nowPlaying
            when {
                np != null -> MiniPlayer(np, vm::togglePlay, vm::next, onPlayer)
                !mediaAccess -> MediaAccessCard(vm)
                else -> GlassCard(Modifier.fillMaxWidth(), onClick = onPlayer) {
                    Text("Сейчас ничего не играет", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                    Text("Включите музыку — плеер подхватит её сам", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
        }

        // ---------- 2. Spotify (API, вход в аккаунт) ----------
        item { BlockDivider() }
        item {
            BlockHeader("🟢", "Spotify", "Вход в аккаунт: ваши плейлисты и запуск музыки без переключения в Spotify") {
                if (loggedIn) when {
                    user != null -> GlassChip(if (user!!.isPremium) "Premium" else "Free", leading = "🟢")
                    profileError != null -> GlassChip("Нет доступа", leading = "🔴")
                    else -> GlassChip("Вход…", leading = "🟡")
                }
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
            !loggedIn -> {
                item { SpotifyAccessNotice(vm) }
                item {
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
            }
            else -> {
                val notRegistered = profileError?.contains("not registered", true) == true ||
                    profileError?.contains("User Management", true) == true
                if (notRegistered) {
                    item { SpotifyNotRegisteredCard(vm) }
                    item { SpotifyAccessNotice(vm) }
                }
                profileError?.takeIf { !notRegistered }?.let { err ->
                    item {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("Spotify не отдал данные аккаунта", style = MaterialTheme.typography.titleSmall, color = p.danger)
                            VSpace(6.dp)
                            Text(err, style = MaterialTheme.typography.bodySmall, color = p.textPrimary)
                            VSpace(8.dp)
                            Text(
                                "Если вы только что добавили email в User Management — выйдите из Spotify и войдите заново. Email должен совпадать с почтой аккаунта Spotify.",
                                style = MaterialTheme.typography.labelSmall, color = p.textSecondary,
                            )
                            VSpace(12.dp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassButton("Повторить", vm::retryProfile, Modifier.weight(1f), style = GlassButtonStyle.Secondary)
                                GlassButton("Войти заново", vm::relogin, Modifier.weight(1f))
                            }
                        }
                    }
                }
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

        // ---------- 3. Яндекс Музыка ----------
        item { BlockDivider() }
        item { BlockHeader("🟡", "Яндекс Музыка", "Публичного API нет: плейлисты открываются в приложении Яндекса, управлять можно из «Плеера»") }
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
        item { BlockDivider() }
        item { BlockHeader("❤️", "Здоровье", null) }
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


@Composable
private fun BlockHeader(emoji: String, title: String, subtitle: String?, action: @Composable () -> Unit = {}) {
    val p = Liquid.palette
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
        }
        action()
    }
}

@Composable
private fun BlockDivider() {
    androidx.compose.material3.HorizontalDivider(
        Modifier.padding(vertical = 6.dp),
        color = Liquid.palette.textSecondary.copy(alpha = 0.18f),
    )
}
