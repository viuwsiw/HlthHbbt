package com.tshk.hlthhbbty.messenger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, val error: String, message: String) : Exception(message)

data class RemoteUser(val username: String, val displayName: String, val avatar: String, val presence: Presence? = null)

/** Статус пользователя: ONLINE — в сети, DND — не беспокоить, AWAY — вне чата (автоматически). */
data class Presence(val status: String, val lastSeen: Long, val nowPlaying: Track? = null)

/** Что сейчас играет у пользователя. */
data class Track(val title: String, val artist: String, val app: String)

data class RemoteProfile(
    val username: String,
    val displayName: String,
    val avatar: String,
    val bio: String,
    val createdAt: Long,
    val fields: JSONObject,
    val publicKey: String?,
    val isContact: Boolean,
    val isSelf: Boolean,
    val presence: Presence? = null,
    val manualStatus: String? = null,
)

data class RemoteMessage(val id: Long, val from: String, val to: String, val ciphertext: String, val clientId: String, val createdAt: Long, val group: Long = 0)

data class MessagesPage(val messages: List<RemoteMessage>, val cursor: Long, val more: Boolean)

data class GroupMember(val username: String, val displayName: String, val avatar: String, val publicKey: String?)

/** Групповой чат (до 5 участников). Ключ диалога — "g:<id>". */
data class GroupInfo(val id: Long, val name: String, val owner: String, val members: List<GroupMember>) {
    val conv get() = "g:$id"
}

data class ReadReceipt(val conv: String, val by: String, val upTo: Long)

/** "photo:<ts>" с сервера → "remote:<логин>:<ts>": аватар загружается по ссылке с авторизацией. */
fun remoteAvatar(username: String, avatar: String): String =
    if (avatar.startsWith("photo:")) "remote:$username:${avatar.removePrefix("photo:")}" else avatar

/** HTTP-клиент сервера мессенджера. */
class MessengerApi(private val baseUrl: () -> String, private val token: () -> String?) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    private suspend fun call(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            val base = baseUrl().trimEnd('/')
            if (base.isBlank()) throw ApiException(0, "no_server", "Сервер мессенджера не настроен")
            val req = Request.Builder().url("$base/v1$path").apply {
                if (auth) token()?.let { header("Authorization", "Bearer $it") }
                when (method) {
                    "GET" -> get()
                    "DELETE" -> delete()
                    else -> method(method, (body ?: JSONObject()).toString().toRequestBody(json))
                }
            }.build()
            val resp = try { http.newCall(req).execute() } catch (e: java.io.IOException) {
                throw ApiException(0, "network", "Нет связи с сервером")
            }
            resp.use { r ->
                val text = r.body?.string().orEmpty()
                val obj = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                if (!r.isSuccessful) throw ApiException(r.code, obj.optString("error"), obj.optString("message").ifBlank { "Ошибка сервера (${r.code})" })
                obj
            }
        }

    suspend fun health() = call("GET", "/health", auth = false)

    suspend fun register(username: String, password: String, displayName: String, deviceId: String): String =
        call("POST", "/auth/register", JSONObject().put("username", username).put("password", password)
            .put("displayName", displayName).put("deviceId", deviceId), auth = false).getString("token")

    suspend fun login(username: String, password: String): String =
        call("POST", "/auth/login", JSONObject().put("username", username).put("password", password), auth = false).getString("token")

    /** Перенос аккаунта, созданного на устройстве до появления сервера (передаётся хеш, не пароль). */
    suspend fun importAccount(username: String, displayName: String, hash: String, salt: String, iterations: Int, deviceId: String): String =
        call("POST", "/auth/import", JSONObject().put("username", username).put("displayName", displayName)
            .put("hash", hash).put("salt", salt).put("iterations", iterations).put("deviceId", deviceId), auth = false).getString("token")

    /** Логин с явным токеном (до того, как он сохранён). */
    suspend fun meWith(token: String): JSONObject = withContext(Dispatchers.IO) {
        val base = baseUrl().trimEnd('/')
        val req = Request.Builder().url("$base/v1/me").header("Authorization", "Bearer $token").get().build()
        try {
            http.newCall(req).execute().use { r -> JSONObject(r.body?.string().orEmpty()) }
        } catch (e: java.io.IOException) {
            throw ApiException(0, "network", "Нет связи с сервером")
        }
    }

    suspend fun changePassword(current: String, new: String) =
        call("PUT", "/me/password", JSONObject().put("current", current).put("new", new))

    suspend fun limits() = call("GET", "/me/limits")

    // ---------- админ-панель (только для developer) ----------
    suspend fun adminStats() = call("GET", "/admin/stats")
    suspend fun adminLogs(n: Int = 200): List<String> {
        val arr = call("GET", "/admin/logs?n=$n").optJSONArray("lines") ?: JSONArray()
        return (0 until arr.length()).map { arr.getString(it) }
    }
    suspend fun adminWipe() = call("POST", "/admin/wipe", JSONObject().put("confirm", "СБРОСИТЬ ВСЁ"))

    /** Загрузка уже зашифрованного файла. */
    suspend fun uploadMedia(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val base = baseUrl().trimEnd('/')
        val req = Request.Builder().url("$base/v1/media")
            .header("Authorization", "Bearer ${token()}")
            .post(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val longClient = http.newBuilder().writeTimeout(5, TimeUnit.MINUTES).readTimeout(2, TimeUnit.MINUTES).build()
        val resp = try { longClient.newCall(req).execute() } catch (e: java.io.IOException) {
            throw ApiException(0, "network", "Не удалось загрузить файл: нет связи")
        }
        resp.use { r ->
            val obj = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrElse { JSONObject() }
            if (!r.isSuccessful) throw ApiException(r.code, obj.optString("error"), obj.optString("message").ifBlank { "Ошибка загрузки (${r.code})" })
            obj.getString("id")
        }
    }

    suspend fun downloadMedia(id: String): ByteArray = withContext(Dispatchers.IO) {
        val base = baseUrl().trimEnd('/')
        val req = Request.Builder().url("$base/v1/media/$id").header("Authorization", "Bearer ${token()}").get().build()
        val longClient = http.newBuilder().readTimeout(5, TimeUnit.MINUTES).build()
        val resp = try { longClient.newCall(req).execute() } catch (e: java.io.IOException) {
            throw ApiException(0, "network", "Нет связи с сервером")
        }
        resp.use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "download", if (r.code == 404) "Файл удалён с сервера (хранится 30 дней)" else "Ошибка загрузки (${r.code})")
            r.body!!.bytes()
        }
    }

    suspend fun logout() = runCatching { call("POST", "/auth/logout") }

    suspend fun deleteAccount() = call("DELETE", "/me")

    suspend fun putKey(publicKey: String) = call("PUT", "/me/key", JSONObject().put("publicKey", publicKey))

    suspend fun putProfile(body: JSONObject) = call("PUT", "/me/profile", body)

    suspend fun search(q: String): List<RemoteUser> =
        call("GET", "/users/search?q=" + URLEncoder.encode(q, "UTF-8")).optJSONArray("users").users()

    suspend fun profile(username: String): RemoteProfile {
        val j = call("GET", "/users/" + URLEncoder.encode(username, "UTF-8"))
        return RemoteProfile(
            j.getString("username"), j.optString("displayName"), remoteAvatar(j.getString("username"), j.optString("avatar")), j.optString("bio"),
            j.optLong("createdAt"), j.optJSONObject("fields") ?: JSONObject(),
            j.optString("publicKey").takeIf { it.isNotBlank() }, j.optBoolean("isContact"), j.optBoolean("isSelf"),
            parsePresence(j), j.optString("manualStatus").takeIf { it.isNotBlank() },
        )
    }

    suspend fun setStatus(status: String) = call("PUT", "/me/status", JSONObject().put("status", status))
    suspend fun setNowPlaying(title: String, artist: String, app: String, vis: String) =
        call("PUT", "/me/nowplaying", JSONObject().put("title", title).put("artist", artist).put("app", app).put("vis", vis))
    suspend fun setPresence(active: Boolean) = call("PUT", "/me/presence", JSONObject().put("active", active))
    suspend fun presence(usernames: List<String>): Map<String, Presence> {
        if (usernames.isEmpty()) return emptyMap()
        val obj = call("GET", "/presence?u=" + URLEncoder.encode(usernames.take(100).joinToString(","), "UTF-8")).optJSONObject("presence") ?: return emptyMap()
        return obj.keys().asSequence().mapNotNull { k -> parsePresence(obj.optJSONObject(k))?.let { k to it } }.toMap()
    }

    suspend fun contacts(): List<RemoteUser> = call("GET", "/contacts").optJSONArray("contacts").users()
    suspend fun addContact(username: String) = call("PUT", "/contacts/$username")
    suspend fun removeContact(username: String) = call("DELETE", "/contacts/$username")

    suspend fun send(to: String, ctRecipient: String, ctSender: String, clientId: String, mediaIds: List<String> = emptyList()): Pair<Long, Long> {
        val j = call("POST", "/messages", JSONObject().put("to", to).put("ctRecipient", ctRecipient).put("ctSender", ctSender)
            .put("clientId", clientId).put("mediaIds", JSONArray(mediaIds)))
        return j.getLong("id") to j.getLong("createdAt")
    }

    suspend fun messages(since: Long): MessagesPage {
        val j = call("GET", "/messages?since=$since")
        val arr = j.optJSONArray("messages") ?: JSONArray()
        val list = (0 until arr.length()).map { parseMessage(arr.getJSONObject(it)) }
        val cursor = if (j.has("cursor")) j.optLong("cursor") else (list.maxOfOrNull { it.id } ?: since)
        return MessagesPage(list, cursor, if (j.has("more")) j.optBoolean("more") else list.size >= 500)
    }

    // ---------- группы ----------
    suspend fun groups(): List<GroupInfo> {
        val arr = call("GET", "/groups").optJSONArray("groups") ?: JSONArray()
        return (0 until arr.length()).map { parseGroup(arr.getJSONObject(it)) }
    }
    suspend fun createGroup(name: String, members: List<String>): GroupInfo =
        parseGroup(call("POST", "/groups", JSONObject().put("name", name).put("members", JSONArray(members))))
    suspend fun renameGroup(id: Long, name: String) = call("PUT", "/groups/$id", JSONObject().put("name", name))
    suspend fun addGroupMember(id: Long, username: String) = call("POST", "/groups/$id/members", JSONObject().put("username", username))
    suspend fun removeGroupMember(id: Long, username: String) = call("DELETE", "/groups/$id/members/$username")
    suspend fun sendGroup(id: Long, clientId: String, cts: Map<String, String>, mediaIds: List<String>): Pair<Long, Long> {
        val j = call("POST", "/groups/$id/messages", JSONObject().put("clientId", clientId).put("cts", JSONObject(cts as Map<*, *>)).put("mediaIds", JSONArray(mediaIds)))
        return j.getLong("id") to j.getLong("createdAt")
    }

    // ---------- прочитано / прослушано / удаление ----------
    suspend fun read(conv: String, upTo: Long) = call("POST", "/read", JSONObject().put("conv", conv).put("upTo", upTo))
    suspend fun listened(id: Long) = call("POST", "/messages/$id/listened")
    suspend fun receipts(): Pair<List<ReadReceipt>, List<Long>> {
        val j = call("GET", "/receipts")
        val r = j.optJSONArray("reads") ?: JSONArray()
        val l = j.optJSONArray("listened") ?: JSONArray()
        return (0 until r.length()).map { r.getJSONObject(it).let { o -> ReadReceipt(o.optString("conv"), o.optString("by"), o.optLong("upTo")) } } to
            (0 until l.length()).map { l.getLong(it) }
    }
    suspend fun clearConversation(conv: String, all: Boolean) = call("DELETE", "/conversations/" + URLEncoder.encode(conv, "UTF-8") + if (all) "?all=1" else "")

    // ---------- фото профиля ----------
    suspend fun putAvatar(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl().trimEnd('/') + "/v1/me/avatar")
            .header("Authorization", "Bearer ${token()}")
            .put(jpeg.toRequestBody("image/jpeg".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val obj = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrElse { JSONObject() }
            if (!r.isSuccessful) throw ApiException(r.code, obj.optString("error"), obj.optString("message").ifBlank { "Не удалось загрузить фото" })
            obj.optString("avatar")
        }
    }
    suspend fun deleteAvatar() = call("DELETE", "/me/avatar")
    fun avatarUrl(username: String): String = baseUrl().trimEnd('/') + "/v1/users/" + URLEncoder.encode(username, "UTF-8") + "/avatar"
    fun authToken(): String? = token()

    fun wsUrl(): String? {
        val base = baseUrl().trimEnd('/')
        val t = token() ?: return null
        if (base.isBlank()) return null
        return base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/v1/ws?token=" + URLEncoder.encode(t, "UTF-8")
    }

    private fun JSONArray?.users(): List<RemoteUser> =
        if (this == null) emptyList() else (0 until length()).map {
            val o = getJSONObject(it)
            RemoteUser(o.getString("username"), o.optString("displayName"), remoteAvatar(o.getString("username"), o.optString("avatar")), parsePresence(o))
        }

    companion object {
        fun parsePresence(o: JSONObject?): Presence? =
            o?.optString("status")?.takeIf { it.isNotBlank() }?.let {
                val np = o.optJSONObject("nowPlaying")?.let { t -> Track(t.optString("title"), t.optString("artist"), t.optString("app")) }
                Presence(it, o.optLong("lastSeen"), np?.takeIf { t -> t.title.isNotBlank() })
            }

        fun parseMessage(o: JSONObject) = RemoteMessage(
            o.getLong("id"), o.getString("from"), o.getString("to"), o.getString("ciphertext"), o.getString("clientId"), o.getLong("createdAt"),
            o.optLong("group"),
        )

        fun parseGroup(o: JSONObject): GroupInfo {
            val arr = o.optJSONArray("members") ?: JSONArray()
            return GroupInfo(o.getLong("id"), o.optString("name"), o.optString("owner"), (0 until arr.length()).map {
                val m = arr.getJSONObject(it)
                val un = m.getString("username")
                GroupMember(un, m.optString("displayName"), remoteAvatar(un, m.optString("avatar")), m.optString("publicKey").takeIf { k -> k.isNotBlank() })
            })
        }
    }
}
