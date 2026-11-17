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

/** Лимиты (совпадают с сервером). */
const val MAX_CHARS = 5000
const val MAX_MEDIA_BYTES = 30L * 1024 * 1024

/** Новое входящее сообщение — для уведомления. title — имя чата, sender — автор (для групп). */
data class IncomingEvent(val peer: String, val text: String, val title: String = "@$peer", val sender: String = peer)

const val MAX_VOICE_MS = 5 * 60_000L

fun isGroup(peer: String) = peer.startsWith("g:")
fun groupId(peer: String) = peer.removePrefix("g:").toLongOrNull() ?: 0L

/**
 * Мессенджер: аккаунт на сервере, E2E-шифрование, синхронизация, мгновенная доставка по WebSocket.
 * Сервер видит только шифротекст и метаданные (кто, кому, когда).
 */
class MessengerRepository(
    private val context: Context,
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

    /** Входящие сообщения (для уведомлений). Поток без склейки — каждое событие доходит. */
    val incoming = kotlinx.coroutines.flow.MutableSharedFlow<IncomingEvent>(extraBufferCapacity = 32)

    private val _groups = MutableStateFlow<Map<Long, GroupInfo>>(emptyMap())
    val groups: StateFlow<Map<Long, GroupInfo>> = _groups.asStateFlow()
    val pins get() = store.pins
    val peerRead get() = store.peerRead
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
            loadLocalState()
            connect()
            runCatching { sync() }
            runCatching { pushProfile() }
        }
    }

    /** Вход/регистрация прошли на сервере — подключаем мессенджер этим токеном. */
    suspend fun onSignedIn(account: MessengerAccount) {
        store.setAccount(account)
        store.loadPrivacy(account.username)
        loadLocalState()
        _state.value = LinkState.CONNECTING
        scope.launch {
            runCatching { api.putKey(crypto.publicKey(account.username)) }
            runCatching { api.setStatus(store.myStatus.value) }
            runCatching { pushProfile() }
            connect()
            runCatching { sync() }
        }
    }

    /** Привязка по паролю (резервный путь, если автоматический перенос не прошёл). */
    suspend fun link(username: String, password: String, displayName: String) {
        _state.value = LinkState.CONNECTING
        val token = try {
            try {
                api.register(username, password, displayName, store.deviceId)
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

    private fun loadLocalState() {
        store.loadPins()
        store.loadPeerRead()
        _groups.value = runCatching {
            val arr = JSONArray(store.groupsJson)
            (0 until arr.length()).map { MessengerApi.parseGroup(arr.getJSONObject(it)) }.associateBy { it.id }
        }.getOrDefault(emptyMap())
    }

    suspend fun refreshGroups() {
        val list = runCatching { api.groups() }.getOrNull() ?: return
        _groups.value = list.associateBy { it.id }
        store.groupsJson = JSONArray(list.map { g ->
            JSONObject().put("id", g.id).put("name", g.name).put("owner", g.owner).put("members", JSONArray(g.members.map { m ->
                JSONObject().put("username", m.username).put("displayName", m.displayName)
                    .put("avatar", if (m.avatar.startsWith("remote:")) "photo:" + m.avatar.substringAfterLast(':') else m.avatar)
                    .put("publicKey", m.publicKey ?: "")
            }))
        }).toString()
    }

    fun group(peer: String): GroupInfo? = if (isGroup(peer)) _groups.value[groupId(peer)] else null

    /** Название чата для списка и уведомлений. */
    fun chatTitle(peer: String): String = group(peer)?.name ?: "@$peer"

    suspend fun createGroup(name: String, members: List<String>): GroupInfo {
        val g = api.createGroup(name.trim(), members)
        refreshGroups()
        return g
    }

    suspend fun renameGroup(id: Long, name: String) { api.renameGroup(id, name.trim()); refreshGroups() }
    suspend fun addGroupMember(id: Long, username: String) { api.addGroupMember(id, username.trim().removePrefix("@").lowercase()); refreshGroups() }
    suspend fun removeGroupMember(id: Long, username: String) {
        api.removeGroupMember(id, username)
        if (username == me) {
            store.account.value?.let { chatDao.deleteConversation(it.username, "g:$id") }
            togglePin("g:$id", false)
        }
        refreshGroups()
    }

    fun togglePin(peer: String, pin: Boolean = peer !in store.pins.value) {
        store.setPins(if (pin) store.pins.value + peer else store.pins.value - peer)
        onPrivacyChanged?.invoke() // закрепления входят в облачную копию
    }

    /** Удалить диалог: у себя или у всех участников. */
    suspend fun clearConversation(peer: String, forAll: Boolean) {
        val acc = store.account.value ?: return
        api.clearConversation(peer, forAll)
        chatDao.deleteConversation(acc.username, peer)
        togglePin(peer, false)
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
        val clean = text.trim().take(MAX_CHARS)
        if (clean.isEmpty()) return
        sendPayload(peer, clean, null)
    }

    /** Отправка: для себя («Избранное») — своим ключом, иначе — ключом собеседника и копия себе. */
    private suspend fun sendPayload(peer: String, text: String, media: JSONObject?) {
        val acc = store.account.value ?: throw ApiException(0, "not_linked", "Мессенджер не подключён")
        if (isGroup(peer)) return sendGroupPayload(acc.username, peer, text, media)
        val self = peer == acc.username
        val myKey = crypto.publicKey(acc.username)
        val theirKey = if (self) myKey else peerKey(peer, refresh = true)
            ?: throw ApiException(0, "no_key", "@$peer ещё не включил шифрование — попросите его открыть «Мессенджер»")
        val payload = JSONObject().put("v", 1).put("text", text).apply { if (media != null) put("media", media) }.toString()
        val clientId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val kind = if (media != null) "media" else "text"
        val localMedia = media?.toString()
        chatDao.upsert(ChatMessageEntity(clientId, acc.username, peer, true, text, now, kind = kind, media = localMedia, sender = acc.username))
        throttle()
        val ct = crypto.encrypt(theirKey, payload, acc.username, peer)
        val (id, createdAt) = api.send(
            to = peer,
            ctRecipient = ct,
            ctSender = if (self) ct else crypto.encrypt(myKey, payload, acc.username, peer),
            clientId = clientId,
            mediaIds = listOfNotNull(media?.optString("id")),
        )
        chatDao.upsert(ChatMessageEntity(clientId, acc.username, peer, true, text, createdAt, id, kind = kind, media = localMedia, sender = acc.username))
        store.setLastRead(peer, id)
    }

    /** Групповое сообщение: шифруется отдельно ключом каждого участника (и своим). */
    private suspend fun sendGroupPayload(me: String, peer: String, text: String, media: JSONObject?) {
        var g = group(peer) ?: run { refreshGroups(); group(peer) } ?: throw ApiException(404, "no_group", "Группа не найдена")
        val payload = JSONObject().put("v", 1).put("text", text).apply { if (media != null) put("media", media) }.toString()
        val clientId = UUID.randomUUID().toString()
        val kind = if (media != null) "media" else "text"
        val localMedia = media?.toString()
        chatDao.upsert(ChatMessageEntity(clientId, me, peer, true, text, System.currentTimeMillis(), kind = kind, media = localMedia, sender = me))
        throttle()
        var attempt = 0
        while (true) {
            val cts = HashMap<String, String>()
            for (m in g.members) {
                val key = if (m.username == me) crypto.publicKey(me) else (m.publicKey ?: peerKey(m.username, refresh = true))
                    ?: throw ApiException(0, "no_key", "@${m.username} ещё не включил шифрование")
                cts[m.username] = crypto.encrypt(key, payload, me, peer)
            }
            try {
                val (id, createdAt) = api.sendGroup(g.id, clientId, cts, listOfNotNull(media?.optString("id")))
                chatDao.upsert(ChatMessageEntity(clientId, me, peer, true, text, createdAt, id, kind = kind, media = localMedia, sender = me))
                store.setLastRead(peer, id)
                return
            } catch (e: ApiException) {
                if (e.error != "members_changed" || attempt++ > 0) throw e
                refreshGroups()
                g = group(peer) ?: throw e
            }
        }
    }

    /** Голосовое сообщение (AAC до 5 минут): шифруется как файл, в сообщении — пометка voice и длительность. */
    suspend fun sendVoice(peer: String, file: java.io.File, durationMs: Long) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val bytes = file.readBytes()
        if (bytes.size > MAX_MEDIA_BYTES) throw ApiException(413, "file_too_large", "Голосовое больше 30 МБ")
        val (cipher, key, nonce) = crypto.encryptFile(bytes)
        val id = api.uploadMedia(cipher)
        java.io.File(mediaDir, "$id.m4a").writeBytes(bytes)
        file.delete()
        val media = JSONObject().put("id", id).put("key", key).put("nonce", nonce).put("mime", "audio/mp4")
            .put("name", "voice.m4a").put("size", bytes.size).put("voice", true).put("duration", durationMs.coerceAtMost(MAX_VOICE_MS))
        sendPayload(peer, "", media)
    }

    /** Входящее голосовое впервые прослушано — отправителю уйдёт «Прослушано». */
    fun markListened(m: ChatMessageEntity) {
        if (m.outgoing || m.listened || m.serverId <= 0) return
        scope.launch {
            chatDao.markListened(m.owner, listOf(m.serverId))
            runCatching { api.listened(m.serverId) }
        }
    }

    private val sendTimes = ArrayDeque<Long>()
    private val throttleLock = Mutex()

    /** Сервер принимает не больше 2 сообщений/файлов за 5 секунд — третье ждёт своей очереди. */
    private suspend fun throttle() = throttleLock.withLock {
        while (sendTimes.size >= 2) {
            val wait = sendTimes.first() + 5_100 - System.currentTimeMillis()
            if (wait <= 0) sendTimes.removeFirst() else delay(wait)
        }
        sendTimes.addLast(System.currentTimeMillis())
    }

    // ---------- медиа (сквозное шифрование файлов) ----------

    private val mediaDir get() = java.io.File(context.cacheDir, "chat_media").apply { mkdirs() }

    /** Отправить фото/видео/GIF/файл: шифруем на устройстве, загружаем шифротекст, ключ — внутри сообщения. */
    suspend fun sendMedia(peer: String, uri: android.net.Uri, caption: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cr = context.contentResolver
        var name = "file"
        var size = -1L
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(android.provider.OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        if (size > MAX_MEDIA_BYTES) throw ApiException(413, "file_too_large", "Файл больше 30 МБ")
        val bytes = cr.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_MEDIA_BYTES) throw ApiException(413, "file_too_large", "Файл больше 30 МБ")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: throw ApiException(0, "read", "Не удалось прочитать файл")
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val (cipher, key, nonce) = crypto.encryptFile(bytes)
        val id = api.uploadMedia(cipher)
        // Своя копия — сразу в кэш, чтобы показать без скачивания.
        java.io.File(mediaDir, id + extFor(mime, name)).writeBytes(bytes)
        val media = JSONObject().put("id", id).put("key", key).put("nonce", nonce).put("mime", mime).put("name", name).put("size", bytes.size)
        sendPayload(peer, caption.trim().take(MAX_CHARS), media)
    }

    /** Расшифрованный файл вложения (скачивается при первом открытии). */
    suspend fun mediaFile(media: JSONObject): java.io.File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val id = media.getString("id")
        val file = java.io.File(mediaDir, id + extFor(media.optString("mime"), media.optString("name")))
        if (!file.exists()) {
            val plain = crypto.decryptFile(api.downloadMedia(id), media.getString("key"), media.getString("nonce"))
            file.writeBytes(plain)
        }
        file
    }

    private fun extFor(mime: String, name: String): String {
        val fromName = name.substringAfterLast('.', "").takeIf { it.length in 1..5 }
        val fromMime = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        return "." + (fromName ?: fromMime ?: "bin")
    }

    suspend fun limits() = api.limits()

    /** Догружает всё новое с сервера. */
    suspend fun sync(): Int = syncLock.withLock {
        val acc = store.account.value ?: return 0
        var total = 0
        if (_groups.value.isEmpty()) refreshGroups()
        while (true) {
            val page = try { api.messages(store.lastMessageId) } catch (e: ApiException) {
                if (e.code == 401) unlink(logoutRemote = false)
                throw e
            }
            page.messages.forEach { process(acc.username, it) }
            if (page.cursor > store.lastMessageId) store.lastMessageId = page.cursor
            total += page.messages.size
            if (!page.more) break
        }
        runCatching { applyReceipts(acc.username) }
        total
    }

    private suspend fun applyReceipts(me: String) {
        val (reads, listened) = api.receipts()
        reads.forEach { store.setPeerRead(it.conv, it.upTo) }
        if (listened.isNotEmpty()) listened.chunked(500).forEach { chatDao.markListened(me, it) }
    }

    private suspend fun process(me: String, m: RemoteMessage) {
        val outgoing = m.from == me
        val peer = when {
            m.group > 0 -> "g:${m.group}"
            outgoing -> m.to
            else -> m.from
        }
        if (m.group > 0 && _groups.value[m.group] == null) refreshGroups()
        val payload = crypto.decrypt(me, m.ciphertext, m.from, m.to)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val text = payload?.optString("text")
        val media = payload?.optJSONObject("media")
        chatDao.upsert(
            ChatMessageEntity(
                clientId = m.clientId, owner = me, peer = peer, outgoing = outgoing,
                text = if (payload == null) "🔒 Не удалось расшифровать (сообщение для другого ключа)" else text.orEmpty(),
                createdAt = m.createdAt, serverId = m.id, readable = payload != null,
                kind = if (media != null) "media" else "text", media = media?.toString(),
                sender = m.from,
                listened = chatDao.byClientId(me, m.clientId)?.listened ?: false,
            )
        )
        if (outgoing) store.setLastRead(peer, m.id)
        else if (openChat == peer) store.setLastRead(peer, m.id)
        else if (m.id > store.notifiedId) {
            store.notifiedId = m.id
            // «Не беспокоить» — без уведомлений
            val preview = when {
                media?.optBoolean("voice") == true -> "🎤 Голосовое сообщение"
                media != null -> "📎 " + (text?.takeIf { it.isNotBlank() } ?: media.optString("name", "Вложение"))
                else -> text ?: "Новое сообщение"
            }
            if (store.myStatus.value != "DND" && peer != me) incoming.tryEmit(IncomingEvent(peer, preview, chatTitle(peer), m.from))
        }
    }

    /** Чат открыт и сообщения видны — отмечаем прочитанным (у собеседника появятся две галочки). */
    fun markRead(peer: String, maxServerId: Long) {
        store.setLastRead(peer, maxServerId)
        if (maxServerId > store.sentRead(peer) && peer != me) {
            store.setSentRead(peer, maxServerId)
            scope.launch { runCatching { api.read(peer, maxServerId) } }
        }
    }

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
                            when (o.optString("type")) {
                                "read" -> store.setPeerRead(o.optString("conv"), o.optLong("upTo"))
                                "listened" -> scope.launch { chatDao.markListened(acc.username, listOf(o.optLong("id"))) }
                                "cleared" -> scope.launch { chatDao.deleteUpTo(acc.username, o.optString("conv"), o.optLong("upTo")) }
                                "group" -> scope.launch { refreshGroups() }
                            }
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

    private var lastShared: String? = null
    private var shareJob: Job? = null

    /** Поделиться тем, что играет (пустой title — ничего не играет). С задержкой, чтобы не дёргать сервер на каждом треке. */
    fun shareNowPlaying(title: String, artist: String, app: String, vis: String) {
        val key = "$title|$artist|$app|$vis"
        if (key == lastShared) return
        shareJob?.cancel()
        shareJob = scope.launch {
            delay(1_500)
            if (runCatching { api.setNowPlaying(title, artist, app, vis) }.isSuccess) lastShared = key
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

    var onPrivacyChanged: (() -> Unit)? = null

    suspend fun savePrivacy(p: Privacy) {
        val acc = store.account.value ?: return
        store.savePrivacy(acc.username, p)
        onPrivacyChanged?.invoke()
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
        val avatar = syncAvatar(user.avatar)
        api.putProfile(
            JSONObject()
                .put("displayName", user.displayName)
                .put("avatar", avatar)
                .put("bio", p.bio)
                .put("discoverable", p.discoverable)
                .put("fields", fields)
        )
    }

    /** Фото профиля загружается на сервер один раз (и при смене) — его видят собеседники. */
    private suspend fun syncAvatar(avatar: String?): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        when {
            avatar?.startsWith("file:") == true -> {
                val f = java.io.File(avatar.removePrefix("file:"))
                val key = "${f.absolutePath}:${f.lastModified()}:${f.length()}"
                if (store.uploadedAvatar != key && f.exists()) {
                    val jpeg = runCatching {
                        val src = android.graphics.BitmapFactory.decodeFile(f.absolutePath)
                        val scale = 512f / maxOf(src.width, src.height)
                        val bmp = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
                        java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                    }.getOrNull()
                    if (jpeg != null && runCatching { api.putAvatar(jpeg) }.isSuccess) store.uploadedAvatar = key
                }
                if (store.uploadedAvatar == key) "photo" else ""
            }
            avatar?.startsWith("preset:") == true -> { store.uploadedAvatar = ""; avatar }
            else -> {
                if (store.uploadedAvatar.isNotEmpty()) { runCatching { api.deleteAvatar() }; store.uploadedAvatar = "" }
                ""
            }
        }
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
