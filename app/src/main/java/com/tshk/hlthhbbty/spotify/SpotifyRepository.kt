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
class SpotifyRepository(private val context: Context, scope: CoroutineScope) {
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

    /**
     * Запуск без переключения в приложение Spotify:
     * 1) играем на активном устройстве; 2) если его нет — на доступном (телефон в приоритете);
     * 3) если Spotify не запущен — будим его в фоне медиакнопкой и ждём, пока телефон появится в устройствах.
     */
    suspend fun play(contextUri: String? = null, trackUri: String? = null): PlayResult {
        val result = try {
            api.play(contextUri, trackUri)
            PlayResult.Ok
        } catch (e: SpotifyException.NoActiveDevice) {
            val device = findDevice() ?: run {
                wakeSpotifyInBackground()
                waitForDevice()
            }
            if (device == null) PlayResult.OpenSpotify
            else runCatching { api.play(contextUri, trackUri, device.id); PlayResult.Ok }
                .getOrElse { PlayResult.Error(it.message ?: "Ошибка") }
        } catch (e: Exception) {
            PlayResult.Error(e.message ?: "Ошибка")
        }
        refreshSoon()
        return result
    }

    private suspend fun findDevice(): SpotifyDevice? =
        runCatching { api.devices() }.getOrDefault(emptyList())
            .sortedByDescending { it.type.equals("Smartphone", true) }.firstOrNull()

    private suspend fun waitForDevice(): SpotifyDevice? {
        repeat(10) {
            delay(700)
            findDevice()?.let { return it }
        }
        return null
    }

    /** Отправляет приложению Spotify медиакнопку Play: оно запускается в фоне, без открытия окна. */
    private fun wakeSpotifyInBackground() {
        if (!isSpotifyInstalled()) return
        listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP).forEach { action ->
            val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON)
                .setPackage(SPOTIFY_PACKAGE)
                .putExtra(android.content.Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(action, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
            runCatching { context.sendBroadcast(intent) }
        }
    }

    private fun isSpotifyInstalled() = runCatching { context.packageManager.getPackageInfo(SPOTIFY_PACKAGE, 0); true }.getOrDefault(false)

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

    companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
    }

    suspend fun logout() {
        auth.logout()
        _user.value = null
        _playlists.value = emptyList()
        _profileError.value = null
        lastProfileLoad = 0L
    }
}
