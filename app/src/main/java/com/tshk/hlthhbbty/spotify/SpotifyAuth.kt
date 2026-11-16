package com.tshk.hlthhbbty.spotify

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tshk.hlthhbbty.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.spotifyStore by preferencesDataStore(name = "spotify")

object SpotifyConfig {
    val clientId: String = BuildConfig.SPOTIFY_CLIENT_ID
    const val REDIRECT_URI = "hlthhbbty://spotify-callback"
    val isConfigured get() = clientId.isNotBlank()
    val scopes = listOf(
        "user-read-private",
        "user-read-playback-state",
        "user-modify-playback-state",
        "user-read-currently-playing",
        "playlist-read-private",
        "playlist-read-collaborative",
    )
}

/** Авторизация Spotify по Authorization Code + PKCE: без client secret в приложении. */
class SpotifyAuth(private val context: Context) {
    private object K {
        val access = stringPreferencesKey("access_token")
        val refresh = stringPreferencesKey("refresh_token")
        val expires = longPreferencesKey("expires_at")
        val verifier = stringPreferencesKey("pkce_verifier")
        val state = stringPreferencesKey("pkce_state")
    }

    private val refreshLock = Mutex()

    val isLoggedIn: Flow<Boolean> = context.spotifyStore.data.map { it[K.refresh] != null }

    /** Готовит PKCE и возвращает URL страницы входа Spotify. */
    suspend fun buildLoginUri(): Uri {
        val verifier = randomString(64)
        val state = randomString(16)
        context.spotifyStore.edit {
            it[K.verifier] = verifier
            it[K.state] = state
        }
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", SpotifyConfig.clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", SpotifyConfig.REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SpotifyConfig.scopes.joinToString(" "))
            .build()
    }

    /** Обрабатывает возврат из браузера: hlthhbbty://spotify-callback?code=…&state=… */
    suspend fun handleRedirect(uri: Uri): Result<Unit> = runCatching {
        uri.getQueryParameter("error")?.let { error(if (it == "access_denied") "Вход отменён" else it) }
        val code = uri.getQueryParameter("code") ?: error("Нет кода авторизации")
        val prefs = context.spotifyStore.data.first()
        require(uri.getQueryParameter("state") == prefs[K.state]) { "Неверный state" }
        val verifier = prefs[K.verifier] ?: error("Нет PKCE verifier")
        val json = tokenRequest(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to SpotifyConfig.REDIRECT_URI,
            "client_id" to SpotifyConfig.clientId,
            "code_verifier" to verifier,
        )
        save(json)
        context.spotifyStore.edit {
            it.remove(K.verifier)
            it.remove(K.state)
        }
    }

    /** Действующий access token; при необходимости обновляет его. null — нужно войти заново. */
    suspend fun accessToken(forceRefresh: Boolean = false): String? = refreshLock.withLock {
        val prefs = context.spotifyStore.data.first()
        val access = prefs[K.access]
        val expires = prefs[K.expires] ?: 0L
        if (!forceRefresh && access != null && System.currentTimeMillis() < expires - 60_000) return access
        val refresh = prefs[K.refresh] ?: return null
        return runCatching {
            val json = tokenRequest(
                "grant_type" to "refresh_token",
                "refresh_token" to refresh,
                "client_id" to SpotifyConfig.clientId,
            )
            save(json)
            json.getString("access_token")
        }.getOrElse { e ->
            if (e is SpotifyAuthException) logout()
            null
        }
    }

    suspend fun logout() {
        context.spotifyStore.edit { it.clear() }
    }

    private suspend fun save(json: JSONObject) {
        context.spotifyStore.edit {
            it[K.access] = json.getString("access_token")
            it[K.expires] = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000
            json.optString("refresh_token").takeIf { r -> r.isNotBlank() }?.let { r -> it[K.refresh] = r }
        }
    }

    private suspend fun tokenRequest(vararg params: Pair<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val body = params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val conn = (URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code == 400 || code == 401) throw SpotifyAuthException(text)
            if (code !in 200..299) error("Spotify: HTTP $code")
            JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun randomString(len: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = SecureRandom()
        return (1..len).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }
}

class SpotifyAuthException(message: String) : Exception(message)
