package com.tshk.hlthhbbty.messenger

import android.content.Context
import com.tshk.hlthhbbty.data.Achievements
import com.tshk.hlthhbbty.data.HistoryData
import com.tshk.hlthhbbty.data.db.ChatDao
import com.tshk.hlthhbbty.data.db.ChatMessageEntity
import com.tshk.hlthhbbty.data.db.UserEntity
import com.tshk.hlthhbbty.util.habitStreak
import com.tshk.hlthhbbty.util.scheduledOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class LinkState { NOT_LINKED, CONNECTING, ONLINE, OFFLINE }

/** Новое входящее сообщение — для уведомления. */
data class IncomingEvent(val peer: String, val text: String)

/**
 * Мессенджер: аккаунт на сервере, E2E-шифрование, синхронизация, мгновенная доставка по WebSocket.
 * Сервер видит только шифротекст и метаданные (кто, кому, когда).
 */
class MessengerRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val chatDao: ChatDao,
    private val localUser: suspend () -> UserEntity?,
    private val history: suspend (Long) -> HistoryData,
) {
    val store = MessengerStore(context)
    val crypto = E2eCrypto(context)
    val api = MessengerApi({ store.serverUrl.value }, { store.account.value?.token })

    private val _state = MutableStateFlow(if (store.account.value == null) LinkState.NOT_LINKED else LinkState.OFFLINE)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    /** Входящие сообщения (для уведомлений, пока приложение запущено). */
    val incoming = MutableStateFlow<IncomingEvent?>(null)
    /** Открытый сейчас чат — по нему не показываем уведомления. */
    @Volatile var openChat: String? = null

    private val keyCache = HashMap<String, String>()

    /** Статусы собеседников (обновляются мгновенно по WebSocket). */
    private val _presence = MutableStateFlow<Map<String, Presence>>(emptyMap())
    val presence: StateFlow<Map<String, Presence>> = _presence.asStateFlow()
    val myStatus get() = store.myStatus

    // Сколько экранов мессенджера сейчас открыто: >0 — «в сети», 0 — «вне чата».
    private var activeScreens = 0
    private var presenceJob: Job? = null
    private val syncLock = Mutex()
    private var socket: WebSocket? = null
    private var wsJob: Job? = null
    @Volatile private var wantSocket = false

    val me: String? get() = store.account.value?.username

    fun conversations(owner: String) = chatDao.observeConversations(owner)
    fun chat(owner: String, peer: String): Flow<List<ChatMessageEntity>> = chatDao.observeChat(owner, peer)
    fun unread(owner: String, peer: String) = chatDao.observeUnread(owner, peer, store.lastRead(peer))

    /** Запуск при старте приложения: аккаунт должен совпадать с локальным пользователем. */
    fun start() = scope.launch {
        val local = localUser()
        val acc = store.account.value
        if (acc != null && (local == null || local.username != acc.username)) {
            unlink(logoutRemote = false)
            return@launch
        }
        if (acc != null) {
            store.loadPrivacy(acc.username)
            connect()
            runCatching { sync() }
            runCatching { pushProfile() }
        }
    }

    /**
     * Подключить локальный аккаунт к серверу. Вызывается при входе/регистрации (пароль известен)
     * или вручную из раздела «Чаты». Если логин на сервере свободен — регистрирует, иначе входит.
     */
    suspend fun link(username: String, password: String, displayName: String) {
        _state.value = LinkState.CONNECTING
        val token = try {
            try {
                api.register(username, password, displayName)
            } catch (e: ApiException) {
                if (e.error == "username_taken") api.login(username, password) else throw e
            }
        } catch (e: ApiException) {
            _state.value = if (store.account.value == null) LinkState.NOT_LINKED else LinkState.OFFLINE
            if (e.error == "bad_credentials") throw ApiException(e.code, e.error, "Логин @$username на сервере уже занят другим пользователем или пароль не совпадает")
            throw e
        }
        store.setAccount(MessengerAccount(username, token))
        store.loadPrivacy(username)
        api.putKey(crypto.publicKey(username))
        runCatching { api.setStatus(store.myStatus.value) }
        runCatching { pushProfile() }
        connect()
        runCatching { sync() }
    }

    /** Фоновая привязка после входа/регистрации — без ошибок для пользователя. */
    fun linkQuietly(username: String, password: String, displayName: String) = scope.launch {
        if (store.serverUrl.value.isBlank()) return@launch
        if (store.account.value?.username == username) return@launch
        runCatching { link(username, password, displayName) }
    }

    suspend fun unlink(logoutRemote: Boolean = true) {
        disconnect()
        if (logoutRemote) api.logout()
        store.setAccount(null)
        _state.value = LinkState.NOT_LINKED
        keyCache.clear()
    }

    suspend fun deleteRemoteAccount() {
        val acc = store.account.value ?: return
        runCatching { api.deleteAccount() }
        chatDao.deleteAll(acc.username)
        unlink(logoutRemote = false)
    }

    // ---------- сообщения ----------

    private suspend fun peerKey(peer: String, refresh: Boolean = false): String? {
        if (!refresh) keyCache[peer]?.let { return it }
        val key = api.profile(peer).publicKey ?: return null
        keyCache[peer] = key
        return key
    }

    suspend fun send(peer: String, text: String) {
        val acc = store.account.value ?: throw ApiException(0, "not_linked", "Мессенджер не подключён")
        val clean = text.trim().take(4000)
        if (clean.isEmpty()) return
        val theirKey = peerKey(peer, refresh = true) ?: throw ApiException(0, "no_key", "@$peer ещё не включил шифрование — попросите его открыть «Мессенджер»")
        val myKey = crypto.publicKey(acc.username)
        val payload = JSONObject().put("v", 1).put("text", clean).toString()
        val clientId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        chatDao.upsert(ChatMessageEntity(clientId, acc.username, peer, true, clean, now))
        val (id, createdAt) = api.send(
            to = peer,
            ctRecipient = crypto.encrypt(theirKey, payload, acc.username, peer),
            ctSender = crypto.encrypt(myKey, payload, acc.username, peer),
            clientId = clientId,
        )
        chatDao.upsert(ChatMessageEntity(clientId, acc.username, peer, true, clean, createdAt, id))
        if (id > store.lastMessageId) store.setLastRead(peer, id)
    }

    /** Догружает всё новое с сервера. */
    suspend fun sync(): Int = syncLock.withLock {
        val acc = store.account.value ?: return 0
        var total = 0
        while (true) {
            val batch = try { api.messages(store.lastMessageId) } catch (e: ApiException) {
                if (e.code == 401) unlink(logoutRemote = false)
                throw e
            }
            if (batch.isEmpty()) break
            batch.forEach { process(acc.username, it) }
            store.lastMessageId = batch.maxOf { it.id }
            total += batch.size
            if (batch.size < 500) break
        }
        total
    }

    private suspend fun process(me: String, m: RemoteMessage) {
        val outgoing = m.from == me
        val peer = if (outgoing) m.to else m.from
        val text = crypto.decrypt(me, m.ciphertext, m.from, m.to)
            ?.let { runCatching { JSONObject(it).optString("text") }.getOrNull() }
        chatDao.upsert(
            ChatMessageEntity(
                clientId = m.clientId, owner = me, peer = peer, outgoing = outgoing,
                text = text ?: "🔒 Не удалось расшифровать (сообщение для другого ключа)",
                createdAt = m.createdAt, serverId = m.id, readable = text != null,
            )
        )
        if (outgoing) store.setLastRead(peer, m.id)
        else if (openChat == peer) store.setLastRead(peer, m.id)
        else if (m.id > store.notifiedId) {
            store.notifiedId = m.id
            // «Не беспокоить» — без уведомлений
            if (store.myStatus.value != "DND") incoming.value = IncomingEvent(peer, text ?: "Новое сообщение")
        }
    }

    fun markRead(peer: String, maxServerId: Long) = store.setLastRead(peer, maxServerId)

    // ---------- WebSocket ----------

    private fun connect() {
        wantSocket = true
        if (wsJob?.isActive == true) return
        wsJob = scope.launch {
            var backoff = 1000L
            while (wantSocket && store.account.value != null) {
                val url = api.wsUrl() ?: break
                val closed = MutableStateFlow(false)
                _state.value = LinkState.CONNECTING
                socket = api.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        _state.value = LinkState.ONLINE
                        backoff = 1000L
                        scope.launch {
                            runCatching { sync() }
                            if (activeScreens > 0) runCatching { api.setPresence(true) }
                        }
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val acc = store.account.value ?: return
                        runCatching {
                            val o = JSONObject(text)
                            if (o.optString("type") == "presence") {
                                val name = o.optString("username")
                                MessengerApi.parsePresence(o)?.let { p -> _presence.value = _presence.value + (name to p) }
                            } else if (o.optString("type") == "message") {
                                val m = MessengerApi.parseMessage(o.getJSONObject("message"))
                                scope.launch {
                                    process(acc.username, m)
                                    if (m.id > store.lastMessageId) store.lastMessageId = m.id
                                }
                            }
                        }
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.value = true }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (response?.code == 401) scope.launch { unlink(logoutRemote = false) }
                        closed.value = true
                    }
                })
                while (!closed.value && wantSocket) delay(500)
                if (!wantSocket) break
                _state.value = LinkState.OFFLINE
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000)
            }
        }
    }

    private fun disconnect() {
        wantSocket = false
        socket?.close(1000, null)
        socket = null
        wsJob?.cancel()
    }

    // ---------- статусы ----------

    /** Экран мессенджера открыт. Пока открыт хотя бы один — «в сети» (с подтверждением раз в минуту). */
    fun enterMessenger() {
        activeScreens++
        if (activeScreens == 1) {
            presenceJob?.cancel()
            presenceJob = scope.launch {
                while (true) {
                    if (store.account.value != null) runCatching { api.setPresence(true) }
                    delay(60_000)
                }
            }
        }
    }

    /** Экран мессенджера закрыт. Если ни одного не осталось — через пару секунд «вне чата». */
    fun leaveMessenger() {
        activeScreens = (activeScreens - 1).coerceAtLeast(0)
        if (activeScreens == 0) {
            presenceJob?.cancel()
            presenceJob = scope.launch {
                delay(2_000) // переход между экранами мессенджера не считается выходом
                if (activeScreens == 0 && store.account.value != null) runCatching { api.setPresence(false) }
            }
        }
    }

    suspend fun setMyStatus(status: String) {
        store.setMyStatus(status)
        runCatching { api.setStatus(status) }
    }

    suspend fun refreshPresence(usernames: List<String>) {
        val fresh = runCatching { api.presence(usernames) }.getOrNull() ?: return
        _presence.value = _presence.value + fresh
    }

    fun rememberPresence(username: String, p: Presence?) {
        if (p != null) _presence.value = _presence.value + (username to p)
    }

    // ---------- профиль и конфиденциальность ----------

    suspend fun savePrivacy(p: Privacy) {
        val acc = store.account.value ?: return
        store.savePrivacy(acc.username, p)
        pushProfile()
    }

    /** Публикует профиль. Поля с видимостью «Никто» не отправляются на сервер вовсе. */
    suspend fun pushProfile() {
        store.account.value ?: return
        val user = localUser() ?: return
        val p = store.privacy.value
        val h = history(user.id)
        val day = h.today
        val fields = JSONObject()
        fun put(name: String, vis: Visibility, value: () -> Any) {
            if (vis != Visibility.NOBODY) fields.put(name, JSONObject().put("vis", vis.name).put("value", value()))
        }
        put("streak", p.streak) {
            val current = h.goodHabits.maxOfOrNull { habitStreak(h.doneByHabit[it.id].orEmpty(), h.frozenByHabit[it.id].orEmpty(), it.scheduleMask, day) } ?: 0
            JSONObject().put("current", current).put("best", h.bestStreakEver())
        }
        put("achievements", p.achievements) {
            val list = Achievements.compute(h)
            JSONObject().put("unlocked", list.count { it.unlocked }).put("total", list.size)
                .put("list", JSONArray(list.filter { it.unlocked }.map { JSONObject().put("emoji", it.emoji).put("title", it.title) }))
        }
        put("habits", p.habits) {
            JSONArray(h.goodHabits.map { habit ->
                val done = h.doneByHabit[habit.id].orEmpty()
                JSONObject().put("title", habit.title).put("emoji", habit.emoji)
                    .put("week", JSONArray((6 downTo 0).map { (day - it) in done }))
                    .put("today", habit.scheduledOn(day) && day in done)
            })
        }
        put("water", p.water) {
            val byDay = h.water.associate { it.epochDay to it.total }
            JSONObject().put("todayMl", byDay[day] ?: 0).put("goalMl", h.waterGoal)
                .put("avg7Ml", (0..6).sumOf { byDay[day - it] ?: 0 } / 7)
        }
        api.putProfile(
            JSONObject()
                .put("displayName", user.displayName)
                .put("avatar", user.avatar?.takeIf { it.startsWith("preset:") } ?: "")
                .put("bio", p.bio)
                .put("discoverable", p.discoverable)
                .put("fields", fields)
        )
    }

    // ---------- поиск, профили, контакты ----------

    suspend fun search(q: String) = api.search(q).also { list -> list.forEach { rememberPresence(it.username, it.presence) } }
    suspend fun profile(username: String) = api.profile(username).also { rememberPresence(it.username, it.presence) }
    suspend fun addContact(username: String) = api.addContact(username)
    suspend fun removeContact(username: String) = api.removeContact(username)
    suspend fun contacts() = api.contacts()

    suspend fun safetyCode(peer: String): String? {
        val acc = store.account.value ?: return null
        val theirs = peerKey(peer) ?: return null
        return crypto.safetyCode(crypto.publicKey(acc.username), theirs)
    }
}
