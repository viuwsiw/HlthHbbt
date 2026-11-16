package com.tshk.hlthhbbty.ui.integrations

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.data.Integrations
import com.tshk.hlthhbbty.data.MusicService
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassSurface
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.openUrl
import com.tshk.hlthhbbty.util.showMessage

/**
 * Блок музыки для главной и таймера фокуса: плейлисты Spotify играют прямо в приложении
 * (после входа), мини-плеер управляет воспроизведением. Яндекс Музыка — ссылками.
 */
@Composable
fun MusicSection(
    onOpenIntegrations: () -> Unit,
    onOpenPlayer: () -> Unit,
    title: String = "Музыка для привычек",
    vm: SpotifyViewModel = viewModel(key = "music_section", factory = AppViewModelProvider.Factory),
) {
    val p = Liquid.palette
    val context = LocalContext.current
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    SpotifyEvents(vm)

    val playSpotify: (String) -> Unit = { uri ->
        when {
            !vm.configured -> context.showMessage("Spotify", "Spotify не настроен в этой сборке")
            !loggedIn -> {
                context.showMessage("Spotify", "Войдите в Spotify, чтобы слушать музыку прямо в приложении")
                onOpenIntegrations()
            }
            else -> vm.play(contextUri = uri)
        }
    }

    Column {
        SectionTitle(title, Modifier.padding(top = 4.dp)) {
            Text(
                "Все",
                color = p.accent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpenIntegrations).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        VSpace(10.dp)
        val pb = playback
        if (loggedIn && pb?.track != null) {
            MiniPlayer(pb, vm::togglePlay, vm::next, onOpenPlayer)
            VSpace(12.dp)
        }
        // Сетка в две колонки — без горизонтальной прокрутки.
        val tiles = buildList<@Composable (Modifier) -> Unit> {
            Integrations.playlists.filter { it.service == MusicService.SPOTIFY }.forEach { pl ->
                val uri = "spotify:playlist:" + pl.url.substringAfterLast("/").substringBefore("?")
                add { m ->
                    MusicTile(
                        cover = { Image(painterResource(pl.cover), null, it, contentScale = ContentScale.Crop) },
                        title = pl.title,
                        subtitle = if (loggedIn) "▶ Spotify" else "Spotify · войти",
                        badgeColor = SpotifyGreen,
                        onClick = { playSpotify(uri) },
                        modifier = m,
                    )
                }
            }
            if (loggedIn) playlists.take(4).forEach { pl ->
                add { m ->
                    MusicTile(
                        cover = { CoverImage(pl.image, 140.dp, 16.dp, it) },
                        title = pl.name,
                        subtitle = "▶ Мой плейлист",
                        badgeColor = SpotifyGreen,
                        onClick = { playSpotify(pl.uri) },
                        modifier = m,
                    )
                }
            }
            Integrations.playlists.filter { it.service == MusicService.YANDEX }.forEach { pl ->
                add { m ->
                    MusicTile(
                        cover = { Image(painterResource(pl.cover), null, it, contentScale = ContentScale.Crop) },
                        title = pl.title,
                        subtitle = "Яндекс Музыка ↗",
                        badgeColor = Color(0xFFFFCC00),
                        onClick = { context.openUrl(pl.url) },
                        modifier = m,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { tile -> tile(Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MusicTile(
    cover: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Liquid.palette
    GlassSurface(modifier, shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Column(Modifier.padding(10.dp)) {
            Box {
                cover(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp).size(12.dp).clip(RoundedCornerShape(50)).background(badgeColor)
                )
            }
            VSpace(8.dp)
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = p.textSecondary, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}
