package com.tshk.hlthhbbty.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SpotifyUser(val name: String, val product: String, val avatar: String?) {
    val isPremium get() = product == "premium"
}

data class SpotifyPlaylist(val id: String, val name: String, val owner: String, val image: String?, val tracks: Int) {
    val uri get() = "spotify:playlist:$id"
}

data class SpotifyTrack(val uri: String, val name: String, val artists: String, val album: String, val image: String?, val durationMs: Long)

data class SpotifyDevice(val id: String, val name: String, val type: String, val active: Boolean)

data class Playback(
    val track: SpotifyTrack?,
    val isPlaying: Boolean,
    val progressMs: Long,
    val shuffle: Boolean,
    val device: SpotifyDevice?,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    /** Текущая позиция с учётом времени, прошедшего с запроса. */
    fun positionNow(): Long {
        val dur = track?.durationMs ?: 0
        val p = if (isPlaying) progressMs + (System.currentTimeMillis() - fetchedAt) else progressMs
        return p.coerceIn(0, dur)
    }
}

sealed class SpotifyException(message: String) : Exception(message) {
    class NoActiveDevice : SpotifyException("Нет активного устройства")
    class PremiumRequired : SpotifyException("Для управления воспроизведением нужен Spotify Premium")
    class Unauthorized : SpotifyException("Нужно войти в Spotify заново")
    class NotRegistered(raw: String) : SpotifyException("Spotify не пускает этот аккаунт: его нет в User Management приложения (или нужен повторный вход). Ответ Spotify: «$raw»")
    class Http(code: Int, msg: String) : SpotifyException("Spotify ($code): $msg")
}

/** Минимальный клиент Spotify Web API на HttpURLConnection. */
class SpotifyApi(private val auth: SpotifyAuth) {

    suspend fun me(): SpotifyUser {
        val j = get("/me")!!
        return SpotifyUser(
            j.optString("display_name").ifBlank { j.optString("id") },
            j.optString("product"),
            j.optJSONArray("images")?.firstImage(),
        )
    }

    suspend fun myPlaylists(): List<SpotifyPlaylist> =
        get("/me/playlists?limit=50")?.optJSONArray("items").objects().map { it.toPlaylist() }

    suspend fun playlist(id: String): SpotifyPlaylist? = runCatching { get("/playlists/$id?fields=id,name,owner(display_name),images,tracks(total)")?.toPlaylist() }.getOrNull()

    suspend fun playlistTracks(id: String): List<SpotifyTrack> =
        get("/playlists/$id/tracks?limit=100&fields=items(track(uri,name,duration_ms,artists(name),album(name,images)))")
            ?.optJSONArray("items").objects().mapNotNull { it.optJSONObject("track")?.toTrack() }

    suspend fun playback(): Playback? {
        val j = get("/me/player?additional_types=track") ?: return null
        return Playback(
            track = j.optJSONObject("item")?.toTrack(),
            isPlaying = j.optBoolean("is_playing"),
            progressMs = j.optLong("progress_ms"),
            shuffle = j.optBoolean("shuffle_state"),
            device = j.optJSONObject("device")?.toDevice(),
        )
    }

    suspend fun devices(): List<SpotifyDevice> = get("/me/player/devices")?.optJSONArray("devices").objects().map { it.toDevice() }

    suspend fun play(contextUri: String? = null, trackUri: String? = null, deviceId: String? = null) {
        val body = JSONObject()
        if (contextUri != null) body.put("context_uri", contextUri)
        if (contextUri != null && trackUri != null) body.put("offset", JSONObject().put("uri", trackUri))
        if (contextUri == null && trackUri != null) body.put("uris", JSONArray().put(trackUri))
        send("PUT", "/me/player/play" + (deviceId?.let { "?device_id=$it" } ?: ""), if (body.length() > 0) body else null)
    }

    suspend fun pause() = send("PUT", "/me/player/pause")
    suspend fun next() = send("POST", "/me/player/next")
    suspend fun previous() = send("POST", "/me/player/previous")
    suspend fun seek(ms: Long) = send("PUT", "/me/player/seek?position_ms=$ms")
    suspend fun shuffle(on: Boolean) = send("PUT", "/me/player/shuffle?state=$on")
    suspend fun transfer(deviceId: String, play: Boolean = true) =
        send("PUT", "/me/player", JSONObject().put("device_ids", JSONArray().put(deviceId)).put("play", play))

    // --- HTTP ---

    private suspend fun get(path: String): JSONObject? = request("GET", path, null)?.let { if (it.isBlank()) null else JSONObject(it) }

    private suspend fun send(method: String, path: String, body: JSONObject? = null) {
        request(method, path, body)
    }

    private suspend fun request(method: String, path: String, body: JSONObject?, retried: Boolean = false): String? {
        val token = auth.accessToken() ?: throw SpotifyException.Unauthorized()
        val (code, text) = withContext(Dispatchers.IO) {
            val conn = (URL("https://api.spotify.com/v1$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Authorization", "Bearer $token")
                if (body != null || method == "PUT" || method == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            try {
                if (conn.doOutput) conn.outputStream.use { it.write((body?.toString() ?: "").toByteArray()) }
                val c = conn.responseCode
                c to ((if (c in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty())
            } finally {
                conn.disconnect()
            }
        }
        return when {
            code == 204 -> null
            code in 200..299 -> text
            code == 401 && !retried -> {
                auth.accessToken(forceRefresh = true) ?: throw SpotifyException.Unauthorized()
                request(method, path, body, retried = true)
            }
            code == 401 -> throw SpotifyException.Unauthorized()
            code == 404 && text.contains("NO_ACTIVE_DEVICE") -> throw SpotifyException.NoActiveDevice()
            code == 404 && path.startsWith("/me/player") -> throw SpotifyException.NoActiveDevice()
            code == 403 -> throw forbidden(text)
            else -> throw SpotifyException.Http(code, errorMessage(text))
        }
    }
}

/** Текст ошибки Spotify: JSON {"error":{"message","reason"}} или обычный текст. */
private fun errorMessage(text: String): String =
    runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: text.trim().take(200)

private fun forbidden(text: String): SpotifyException {
    val reason = runCatching { JSONObject(text).optJSONObject("error")?.optString("reason") }.getOrNull().orEmpty()
    val msg = errorMessage(text)
    return when {
        reason == "PREMIUM_REQUIRED" || msg.contains("premium", true) -> SpotifyException.PremiumRequired()
        msg.contains("not be registered", true) || msg.contains("developer.spotify.com", true) ->
            SpotifyException.NotRegistered(msg)
        msg.contains("Restriction violated", true) || reason == "UNKNOWN" ->
            SpotifyException.Http(403, "Spotify не разрешил это действие (${msg.ifBlank { "ограничение" }})")
        else -> SpotifyException.Http(403, msg.ifBlank { "доступ запрещён" })
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray.firstImage(): String? = objects().firstOrNull()?.optString("url")?.takeIf { it.isNotBlank() }

private fun JSONObject.toPlaylist() = SpotifyPlaylist(
    id = getString("id"),
    name = optString("name"),
    owner = optJSONObject("owner")?.optString("display_name").orEmpty(),
    image = optJSONArray("images")?.firstImage(),
    tracks = optJSONObject("tracks")?.optInt("total") ?: 0,
)

private fun JSONObject.toTrack() = SpotifyTrack(
    uri = optString("uri"),
    name = optString("name"),
    artists = optJSONArray("artists").objects().joinToString(", ") { it.optString("name") },
    album = optJSONObject("album")?.optString("name").orEmpty(),
    image = optJSONObject("album")?.optJSONArray("images")?.firstImage(),
    durationMs = optLong("duration_ms"),
)

private fun JSONObject.toDevice() = SpotifyDevice(optString("id"), optString("name"), optString("type"), optBoolean("is_active"))
