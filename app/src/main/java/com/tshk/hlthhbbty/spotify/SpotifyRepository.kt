package com.tshk.hlthhbbty.spotify

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface PlayResult {
    data object Ok : PlayResult
    /** Нет активного устройства — нужно открыть приложение Spotify. */
    data object OpenSpotify : PlayResult
    data class Error(val message: String) : PlayResult
}

/** Состояние Spotify на уровне процесса: вход, профиль, плейлисты и текущее воспроизведение. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyRepository(context: Context, scope: CoroutineScope) {
    val auth = SpotifyAuth(context)
    val api = SpotifyApi(auth)

    val isLoggedIn: StateFlow<Boolean> = auth.isLoggedIn.stateIn(scope, SharingStarted.Eagerly, false)

    private val _user = MutableStateFlow<SpotifyUser?>(null)
    val user: StateFlow<SpotifyUser?> = _user.asStateFlow()

    private val _playlists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val playlists: StateFlow<List<SpotifyPlaylist>> = _playlists.asStateFlow()

    private val _profileError = MutableStateFlow<String?>(null)
    /** Ошибка загрузки профиля — показывается в разделе Spotify, без уведомлений. */
    val profileError: StateFlow<String?> = _profileError.asStateFlow()
    private var lastProfileLoad = 0L

    private val refreshTick = MutableStateFlow(0)
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Опрос плеера идёт только пока есть подписчики (открыт экран с плеером). */
    val playback: StateFlow<Playback?> = combine(isLoggedIn, refreshTick) { logged, _ -> logged }
        .flatMapLatest { logged ->
            if (!logged) flowOf(null) else pollPlayback()
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(2_000), null)

    private fun pollPlayback(): Flow<Playback?> = flow {
        while (true) {
            emit(runCatching { api.playback() }.getOrNull())
            delay(3_000)
        }
    }

    suspend fun loginUri(): Uri = auth.buildLoginUri()

    suspend fun handleRedirect(uri: Uri): Result<Unit> = auth.handleRedirect(uri).onSuccess { loadProfile(force = true) }

    /** Загружает профиль и плейлисты. Без force — не чаще раза в минуту и только если ещё не загружено. */
    suspend fun loadProfile(force: Boolean = false) {
        if (!force && (_user.value != null || System.currentTimeMillis() - lastProfileLoad < 60_000)) return
        lastProfileLoad = System.currentTimeMillis()
        if (!isLoggedIn.value && auth.accessToken() == null) return
        runCatching { api.me() }
            .onSuccess {
                _user.value = it
                _profileError.value = null
                runCatching { _playlists.value = api.myPlaylists() }
            }
            .onFailure {
                if (it is SpotifyException.Unauthorized) _user.value = null
                _profileError.value = it.message
            }
    }

    suspend fun tracks(playlistId: String): List<SpotifyTrack> = runCatching { api.playlistTracks(playlistId) }.getOrElse { emptyList() }

    suspend fun play(contextUri: String? = null, trackUri: String? = null): PlayResult {
        val result = try {
            api.play(contextUri, trackUri)
            PlayResult.Ok
        } catch (e: SpotifyException.NoActiveDevice) {
            // Пробуем разбудить любое доступное устройство, предпочитая телефон.
            val device = runCatching { api.devices() }.getOrDefault(emptyList())
                .sortedByDescending { it.type.equals("Smartphone", true) }.firstOrNull()
            if (device == null) PlayResult.OpenSpotify
            else runCatching { api.play(contextUri, trackUri, device.id); PlayResult.Ok }
                .getOrElse { PlayResult.Error(it.message ?: "Ошибка") }
        } catch (e: Exception) {
            PlayResult.Error(e.message ?: "Ошибка")
        }
        refreshSoon()
        return result
    }

    suspend fun togglePlay(): PlayResult {
        val pb = playback.value
        return if (pb?.isPlaying == true) action { api.pause() } else play()
    }

    suspend fun next() = action { api.next() }
    suspend fun previous() = action { api.previous() }
    suspend fun seek(ms: Long) = action { api.seek(ms) }
    suspend fun shuffle(on: Boolean) = action { api.shuffle(on) }

    private suspend fun action(block: suspend () -> Unit): PlayResult {
        val r = try {
            block()
            PlayResult.Ok
        } catch (e: SpotifyException.NoActiveDevice) {
            PlayResult.OpenSpotify
        } catch (e: Exception) {
            PlayResult.Error(e.message ?: "Ошибка")
        }
        refreshSoon()
        return r
    }

    private suspend fun refreshSoon() {
        delay(350)
        refreshTick.value++
    }

    private fun onError(e: Throwable) {
        _lastError.value = e.message
        if (e is SpotifyException.Unauthorized) {
            _user.value = null
        }
    }

    fun consumeError() { _lastError.value = null }

    suspend fun logout() {
        auth.logout()
        _user.value = null
        _playlists.value = emptyList()
        _profileError.value = null
        lastProfileLoad = 0L
    }
}
