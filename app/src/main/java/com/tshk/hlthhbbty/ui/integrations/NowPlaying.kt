package com.tshk.hlthhbbty.ui.integrations

import android.graphics.Bitmap
import com.tshk.hlthhbbty.media.MediaState
import com.tshk.hlthhbbty.spotify.Playback

enum class PlayerSource { SYSTEM, SPOTIFY_API }

/** Единое «Сейчас играет»: системная медиасессия (любое приложение) или Spotify Web API. */
data class NowPlaying(
    val source: PlayerSource,
    val appName: String,
    val title: String,
    val artist: String,
    val artBitmap: Bitmap?,
    val artUrl: String?,
    val durationMs: Long,
    val isPlaying: Boolean,
    val shuffle: Boolean?,
    private val position: () -> Long,
    val key: Any,
) {
    fun positionNow() = position()
}

fun MediaState.toNowPlaying() = NowPlaying(
    source = PlayerSource.SYSTEM,
    appName = appName,
    title = title,
    artist = artist,
    artBitmap = art,
    artUrl = artUri,
    durationMs = durationMs,
    isPlaying = isPlaying,
    shuffle = null,
    position = ::positionNow,
    key = this,
)

fun Playback.toNowPlaying(): NowPlaying? {
    val t = track ?: return null
    return NowPlaying(
        source = PlayerSource.SPOTIFY_API,
        appName = device?.name?.let { "Spotify · $it" } ?: "Spotify",
        title = t.name,
        artist = t.artists,
        artBitmap = null,
        artUrl = t.image,
        durationMs = t.durationMs,
        isPlaying = isPlaying,
        shuffle = shuffle,
        position = ::positionNow,
        key = this,
    )
}
