package com.tshk.hlthhbbty.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Что играет сейчас в любом музыкальном приложении. */
data class MediaState(
    val packageName: String,
    val appName: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val artUri: String?,
    val durationMs: Long,
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    val isPlaying: Boolean,
) {
    fun positionNow(): Long {
        val p = if (isPlaying) positionMs + ((SystemClock.elapsedRealtime() - positionUpdatedAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * Универсальный плеер на системных медиасессиях Android: управляет музыкой в Spotify,
 * Яндекс Музыке, YouTube Music и др. без входа в аккаунты. Нужен «Доступ к уведомлениям».
 */
class MediaRepository(private val context: Context) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _hasAccess = MutableStateFlow(checkAccess())
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val _state = MutableStateFlow<MediaState?>(null)
    val state: StateFlow<MediaState?> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var listening = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Если заиграло другое приложение — переключаемся на него.
            pickController()
        }
        override fun onSessionDestroyed() = pickController()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pickController() }

    fun checkAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Вызывать при возвращении в приложение: подхватывает выданный доступ. */
    fun refresh() {
        _hasAccess.value = checkAccess()
        if (_hasAccess.value) start() else stop()
    }

    private fun start() {
        if (!listening) {
            runCatching { manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler) }
                .onSuccess { listening = true }
        }
        pickController()
    }

    private fun stop() {
        if (listening) runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        listening = false
        controller?.unregisterCallback(controllerCallback)
        controller = null
        _state.value = null
    }

    private fun pickController() {
        val sessions = runCatching { manager.getActiveSessions(component) }.getOrDefault(emptyList())
            .filter { it.packageName != context.packageName }
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.metadata != null }
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) {
            _state.value = null
            return
        }
        val pb = c.playbackState
        val appName = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault(c.packageName)
        _state.value = MediaState(
            packageName = c.packageName,
            appName = appName,
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().ifBlank { md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty() },
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty().ifBlank { md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty() },
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            artUri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI),
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = pb?.position ?: 0L,
            positionUpdatedAt = pb?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = pb?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            isPlaying = pb?.state == PlaybackState.STATE_PLAYING || pb?.state == PlaybackState.STATE_BUFFERING,
        )
    }

    fun togglePlay() {
        val c = controller ?: return
        if (_state.value?.isPlaying == true) c.transportControls.pause() else c.transportControls.play()
    }
    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()
    fun seek(ms: Long) = controller?.transportControls?.seekTo(ms)

    /** Открыть само музыкальное приложение. */
    fun openApp() {
        val pkg = _state.value?.packageName ?: return
        context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
