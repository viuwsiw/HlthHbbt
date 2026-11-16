package com.tshk.hlthhbbty.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Общее для всего приложения состояние обновления. */
class UpdateController(private val repo: UpdateRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Версия, которую пользователь отложил в этой сессии — повторно не навязываем. */
    private val _dismissed = MutableStateFlow<String?>(null)
    val dismissed: StateFlow<String?> = _dismissed.asStateFlow()

    private var job: Job? = null

    fun check(silent: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        job = scope.launch {
            if (!silent) _state.value = UpdateState.Checking
            repo.check()
                .onSuccess { info -> _state.value = if (info != null) UpdateState.Available(info) else if (silent) UpdateState.Idle else UpdateState.UpToDate }
                .onFailure { if (!silent) _state.value = UpdateState.Failed(it.message ?: "Нет соединения") }
        }
    }

    fun download(info: UpdateInfo) {
        job?.cancel()
        _state.value = UpdateState.Downloading(info, 0f)
        job = scope.launch {
            runCatching { repo.download(info) { p -> _state.value = UpdateState.Downloading(info, p) } }
                .onSuccess { _state.value = UpdateState.Ready(info, it) }
                .onFailure { _state.value = UpdateState.Failed(it.message ?: "Ошибка загрузки") }
        }
    }

    fun dismiss() {
        when (val s = _state.value) {
            is UpdateState.Available -> _dismissed.value = s.info.versionName
            is UpdateState.Ready -> _dismissed.value = s.info.versionName
            is UpdateState.Downloading -> { job?.cancel(); _dismissed.value = s.info.versionName }
            else -> Unit
        }
        if (_state.value !is UpdateState.Available) _state.value = UpdateState.Idle
    }

    fun reset() { if (_state.value is UpdateState.UpToDate || _state.value is UpdateState.Failed) _state.value = UpdateState.Idle }
}
