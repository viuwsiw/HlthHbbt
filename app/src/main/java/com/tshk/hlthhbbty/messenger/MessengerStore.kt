package com.tshk.hlthhbbty.messenger

import android.content.Context
import com.tshk.hlthhbbty.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Видимость поля профиля для других пользователей. */
enum class Visibility(val title: String) { ALL("Все"), CONTACTS("Контакты"), NOBODY("Никто") }

/** Настройки конфиденциальности: что о привычках видят другие. */
data class Privacy(
    val discoverable: Boolean = true,
    val bio: String = "",
    val streak: Visibility = Visibility.ALL,
    val achievements: Visibility = Visibility.ALL,
    val habits: Visibility = Visibility.CONTACTS,
    val water: Visibility = Visibility.NOBODY,
)

/** Привязка локального аккаунта к серверу мессенджера. */
data class MessengerAccount(val username: String, val token: String)

/** Локальные данные мессенджера (SharedPreferences, приватно для приложения). */
class MessengerStore(context: Context) {
    private val prefs = context.getSharedPreferences("messenger", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(loadAccount())
    val account: StateFlow<MessengerAccount?> = _account.asStateFlow()

    private val _privacy = MutableStateFlow<Privacy>(Privacy())
    val privacy: StateFlow<Privacy> = _privacy.asStateFlow()

    private val _serverUrl = MutableStateFlow(prefs.getString("server_url", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.MESSENGER_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    fun setServerUrl(url: String) {
        val clean = url.trim().trimEnd('/').let { if (it.isNotEmpty() && !it.startsWith("http")) "https://$it" else it }
        prefs.edit().putString("server_url", clean).apply()
        _serverUrl.value = clean
    }

    fun setAccount(a: MessengerAccount?) {
        prefs.edit().apply {
            if (a == null) { remove("username"); remove("token") } else { putString("username", a.username); putString("token", a.token) }
        }.apply()
        _account.value = a
    }

    private fun loadAccount(): MessengerAccount? {
        val u = prefs.getString("username", null) ?: return null
        val t = prefs.getString("token", null) ?: return null
        return MessengerAccount(u, t)
    }

    fun loadPrivacy(username: String) {
        val raw = prefs.getString("privacy_$username", null)
        _privacy.value = raw?.let { runCatching { parsePrivacy(JSONObject(it)) }.getOrNull() } ?: Privacy()
    }

    fun savePrivacy(username: String, p: Privacy) {
        prefs.edit().putString("privacy_$username", JSONObject()
            .put("discoverable", p.discoverable).put("bio", p.bio)
            .put("streak", p.streak.name).put("achievements", p.achievements.name)
            .put("habits", p.habits.name).put("water", p.water.name).toString()).apply()
        _privacy.value = p
    }

    private fun parsePrivacy(j: JSONObject) = Privacy(
        discoverable = j.optBoolean("discoverable", true),
        bio = j.optString("bio"),
        streak = Visibility.valueOf(j.optString("streak", "ALL")),
        achievements = Visibility.valueOf(j.optString("achievements", "ALL")),
        habits = Visibility.valueOf(j.optString("habits", "CONTACTS")),
        water = Visibility.valueOf(j.optString("water", "NOBODY")),
    )

    var lastMessageId: Long
        get() = prefs.getLong("last_id_${_account.value?.username}", 0)
        set(v) { prefs.edit().putLong("last_id_${_account.value?.username}", v).apply() }

    fun lastRead(peer: String): Long = prefs.getLong("read_${_account.value?.username}_$peer", 0)
    fun setLastRead(peer: String, id: Long) {
        if (id > lastRead(peer)) prefs.edit().putLong("read_${_account.value?.username}_$peer", id).apply()
    }

    private val _myStatus = MutableStateFlow(prefs.getString("my_status", "ONLINE") ?: "ONLINE")
    /** Выбранный вручную статус: ONLINE или DND. */
    val myStatus: StateFlow<String> = _myStatus.asStateFlow()
    fun setMyStatus(s: String) { prefs.edit().putString("my_status", s).apply(); _myStatus.value = s }

    /** Последнее сообщение, о котором уже показано уведомление. */
    var notifiedId: Long
        get() = prefs.getLong("notified_${_account.value?.username}", 0)
        set(v) { prefs.edit().putLong("notified_${_account.value?.username}", v).apply() }
}
