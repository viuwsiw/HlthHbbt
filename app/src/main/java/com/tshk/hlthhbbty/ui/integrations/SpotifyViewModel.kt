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
import kotlinx.coroutines.launch

sealed interface SpotifyEvent {
    data class OpenUrl(val uri: Uri) : SpotifyEvent
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

    fun play(contextUri: String? = null, trackUri: String? = null) = launchPlay { repo.play(contextUri, trackUri) }
    fun togglePlay() = launchPlay { repo.togglePlay() }
    fun next() = launchPlay { repo.next() }
    fun previous() = launchPlay { repo.previous() }
    fun seek(ms: Long) = launchPlay { repo.seek(ms) }
    fun shuffle(on: Boolean) = launchPlay { repo.shuffle(on) }

    private fun launchPlay(block: suspend () -> PlayResult) = viewModelScope.launch {
        when (val r = block()) {
            PlayResult.Ok -> Unit
            PlayResult.OpenSpotify -> _events.send(SpotifyEvent.OpenSpotifyApp)
            is PlayResult.Error -> _events.send(SpotifyEvent.Message(r.message))
        }
    }
}
