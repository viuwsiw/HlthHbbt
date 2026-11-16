package com.tshk.hlthhbbty.ui.integrations

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.spotify.PlayResult
import com.tshk.hlthhbbty.spotify.SpotifyConfig
import com.tshk.hlthhbbty.spotify.SpotifyTrack
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SpotifyEvent {
    data class OpenUrl(val uri: Uri) : SpotifyEvent
    /** Открыть плейлист в приложении Spotify (если API не дал запустить). */
    data class OpenInSpotify(val contextUri: String, val reason: String?) : SpotifyEvent
    data object OpenSpotifyApp : SpotifyEvent
    data class Message(val text: String) : SpotifyEvent
}

class SpotifyViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val repo = c.spotify
    val configured = SpotifyConfig.isConfigured
    val loggedIn = repo.isLoggedIn
    val user = repo.user
    val playlists = repo.playlists
    val playback = repo.playback

    // Универсальный системный плеер
    private val media = c.media
    val mediaAccess = media.hasAccess
    /** Системная медиасессия приоритетнее: управление работает без Premium. */
    val nowPlaying: StateFlow<NowPlaying?> = combine(media.state, repo.playback) { m, pb ->
        m?.toNowPlaying() ?: pb?.toNowPlaying()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    fun refreshMedia() = media.refresh()
    fun mediaAccessIntent() = media.accessSettingsIntent()

    /** Для экрана плейлиста. */
    val playlistId: String? = handle.get<String>("playlistId")
    private val _tracks = MutableStateFlow<List<SpotifyTrack>?>(null)
    val tracks: StateFlow<List<SpotifyTrack>?> = _tracks.asStateFlow()

    private val _events = Channel<SpotifyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (repo.user.value == null) repo.loadProfile()
            playlistId?.let { _tracks.value = repo.tracks(it) }
        }
        viewModelScope.launch {
            repo.lastError.collect { e -> if (e != null) { _events.send(SpotifyEvent.Message(e)); repo.consumeError() } }
        }
    }

    fun login() = viewModelScope.launch { _events.send(SpotifyEvent.OpenUrl(repo.loginUri())) }
    fun logout() = viewModelScope.launch { repo.logout() }

    /** Запуск плейлиста: через API, а если нельзя (нет Premium и т.п.) — открываем в Spotify. */
    fun play(contextUri: String? = null, trackUri: String? = null) = viewModelScope.launch {
        if (!loggedIn.value) {
            contextUri?.let { _events.send(SpotifyEvent.OpenInSpotify(it, null)) }
            return@launch
        }
        when (val r = repo.play(contextUri, trackUri)) {
            PlayResult.Ok -> Unit
            PlayResult.OpenSpotify -> _events.send(SpotifyEvent.OpenInSpotify(contextUri ?: "spotify:", null))
            is PlayResult.Error -> if (contextUri != null) _events.send(SpotifyEvent.OpenInSpotify(contextUri, r.message))
            else _events.send(SpotifyEvent.Message(r.message))
        }
    }

    private val systemActive get() = nowPlaying.value?.source == PlayerSource.SYSTEM
    fun togglePlay() = if (systemActive) { media.togglePlay(); Unit } else { launchPlay { repo.togglePlay() }; Unit }
    fun next() = if (systemActive) { media.next(); Unit } else { launchPlay { repo.next() }; Unit }
    fun previous() = if (systemActive) { media.previous(); Unit } else { launchPlay { repo.previous() }; Unit }
    fun seek(ms: Long) = if (systemActive) { media.seek(ms); Unit } else { launchPlay { repo.seek(ms) }; Unit }
    fun shuffle(on: Boolean) = launchPlay { repo.shuffle(on) }

    private fun launchPlay(block: suspend () -> PlayResult) = viewModelScope.launch {
        when (val r = block()) {
            PlayResult.Ok -> Unit
            PlayResult.OpenSpotify -> _events.send(SpotifyEvent.OpenSpotifyApp)
            is PlayResult.Error -> _events.send(SpotifyEvent.Message(r.message))
        }
    }
}
