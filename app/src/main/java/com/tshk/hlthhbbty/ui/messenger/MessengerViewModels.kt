package com.tshk.hlthhbbty.ui.messenger

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.ChatMessageEntity
import com.tshk.hlthhbbty.data.db.ConversationRow
import com.tshk.hlthhbbty.messenger.ApiException
import com.tshk.hlthhbbty.messenger.Privacy
import com.tshk.hlthhbbty.messenger.RemoteProfile
import com.tshk.hlthhbbty.messenger.RemoteUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConversationItem(val row: ConversationRow, val unread: Boolean)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ChatsViewModel(private val c: AppContainer) : ViewModel() {
    private val m = c.messenger
    val account = m.store.account
    val state = m.state
    val serverUrl = m.store.serverUrl

    val conversations: StateFlow<List<ConversationItem>> = account.flatMapLatest { acc ->
        if (acc == null) flowOf(emptyList())
        else m.conversations(acc.username).map { rows ->
            rows.map { ConversationItem(it, !it.lastOutgoing && it.maxServerId > m.store.lastRead(it.peer)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<RemoteUser>>(emptyList())
    val results: StateFlow<List<RemoteUser>> = _results.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val localUsername: StateFlow<String?> = c.auth.currentUserId
        .flatMapLatest { id -> if (id == null) flowOf(null) else c.users.observe(id) }
        .map { it?.username }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            query.debounce(300).distinctUntilChanged().collect { q ->
                if (q.trim().removePrefix("@").length < 2 || account.value == null) { _results.value = emptyList(); return@collect }
                _searching.value = true
                _results.value = runCatching { m.search(q) }.getOrElse { emptyList() }
                _searching.value = false
            }
        }
        viewModelScope.launch { if (account.value != null) runCatching { m.sync() } }
    }

    fun setServer(url: String) = m.store.setServerUrl(url)

    fun link(password: String) = viewModelScope.launch {
        val id = c.auth.currentUserId.filterNotNull().first()
        val user = c.users.observe(id).filterNotNull().first()
        _busy.value = true
        _error.value = null
        try {
            m.link(user.username, password, user.displayName)
        } catch (e: ApiException) {
            _error.value = e.message
        } finally {
            _busy.value = false
        }
    }

    fun refresh() = viewModelScope.launch { runCatching { m.sync() } }
}

class ChatViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val m = c.messenger
    val peer: String = handle.get<String>("peer").orEmpty()
    val me = m.me

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> = m.store.account.flatMapLatest { acc ->
        if (acc == null) flowOf(emptyList()) else m.chat(acc.username, peer)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _profile = MutableStateFlow<RemoteProfile?>(null)
    val profile: StateFlow<RemoteProfile?> = _profile.asStateFlow()
    private val _safety = MutableStateFlow<String?>(null)
    val safety: StateFlow<String?> = _safety.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        m.openChat = peer
        viewModelScope.launch {
            _profile.value = runCatching { m.profile(peer) }.getOrNull()
            _safety.value = runCatching { m.safetyCode(peer) }.getOrNull()
        }
        viewModelScope.launch {
            messages.collect { list -> list.maxOfOrNull { it.serverId }?.let { m.markRead(peer, it) } }
        }
    }

    fun send(text: String) = viewModelScope.launch {
        _error.value = null
        try { m.send(peer, text) } catch (e: ApiException) { _error.value = e.message }
    }

    fun clearError() { _error.value = null }

    override fun onCleared() {
        if (m.openChat == peer) m.openChat = null
    }
}

class UserProfileViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val m = c.messenger
    val username: String = handle.get<String>("username").orEmpty()
    private val _profile = MutableStateFlow<RemoteProfile?>(null)
    val profile: StateFlow<RemoteProfile?> = _profile.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _error.value = null
        runCatching { m.profile(username) }.onSuccess { _profile.value = it }.onFailure { _error.value = it.message }
    }

    fun toggleContact() = viewModelScope.launch {
        val p = _profile.value ?: return@launch
        runCatching { if (p.isContact) m.removeContact(username) else m.addContact(username) }
        load()
    }
}

class PrivacyViewModel(private val c: AppContainer) : ViewModel() {
    private val m = c.messenger
    val privacy: StateFlow<Privacy> = m.store.privacy
    val linked = m.store.account
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    init { m.me?.let { m.store.loadPrivacy(it) } }

    fun update(transform: (Privacy) -> Privacy) = viewModelScope.launch {
        _saving.value = true
        runCatching { m.savePrivacy(transform(privacy.value)) }
        _saving.value = false
    }
}
