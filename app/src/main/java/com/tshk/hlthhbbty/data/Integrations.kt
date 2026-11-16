package com.tshk.hlthhbbty.data

import androidx.annotation.DrawableRes
import com.tshk.hlthhbbty.R

enum class MusicService(val title: String, val packageName: String) {
    SPOTIFY("Spotify", "com.spotify.music"),
    YANDEX("Яндекс Музыка", "ru.yandex.music"),
}

data class Playlist(
    val title: String,
    val subtitle: String,
    val service: MusicService,
    val url: String,
    @DrawableRes val cover: Int,
)

object Integrations {
    val playlists = listOf(
        Playlist("Утренний заряд", "Для зарядки и прогулки", MusicService.SPOTIFY,
            "https://open.spotify.com/playlist/37i9dQZF1DX889U0CL85jj", R.drawable.cover_spotify_1),
        Playlist("Спокойствие", "Для отдыха и сна", MusicService.SPOTIFY,
            "https://open.spotify.com/playlist/37i9dQZF1DXbXD9pMSZomS", R.drawable.cover_spotify_2),
        Playlist("Плейлист дня", "Подборка от редакции", MusicService.YANDEX,
            "https://music.yandex.ru/users/music-blog/playlists/2467", R.drawable.cover_yandex_1),
        Playlist("Фокус", "Чтение и концентрация", MusicService.YANDEX,
            "https://music.yandex.ru/users/music-blog/playlists/2561", R.drawable.cover_yandex_2),
    )
}
