package com.tshk.hlthhbbty.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FocusState(
    val habit: HabitEntity? = null,
    val totalMs: Long = 25 * 60_000L,
    val remainingMs: Long = 25 * 60_000L,
    val running: Boolean = false,
    val finished: Boolean = false,
)

class FocusViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    private val habitId = handle.get<String>("id")?.toLongOrNull() ?: -1L
    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()
    private var ticker: Job? = null

    /** Момент окончания — переживает поворот и пересоздание процесса. */
    private var endAt: Long
        get() = handle["endAt"] ?: 0L
        set(v) { handle["endAt"] = v }

    init {
        viewModelScope.launch {
            val habit = c.habits.find(habitId)
            val total = (habit?.focusMinutes ?: 25) * 60_000L
            val remaining = handle.get<Long>("remaining") ?: total
            _state.value = FocusState(habit, total, remaining)
            if (endAt > 0) start()
        }
    }

    fun start() {
        if (endAt <= 0) endAt = System.currentTimeMillis() + _state.value.remainingMs
        _state.update { it.copy(running = true) }
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                val left = endAt - System.currentTimeMillis()
                if (left <= 0) {
                    finish()
                    break
                }
                _state.update { it.copy(remainingMs = left) }
                handle["remaining"] = left
                delay(250)
            }
        }
    }

    fun pause() {
        ticker?.cancel()
        endAt = 0
        _state.update { it.copy(running = false) }
    }

    fun reset() {
        pause()
        handle["remaining"] = _state.value.totalMs
        _state.update { it.copy(remainingMs = it.totalMs, finished = false) }
    }

    private suspend fun finish() {
        endAt = 0
        handle["remaining"] = 0L
        _state.update { it.copy(remainingMs = 0, running = false, finished = true) }
        _state.value.habit?.let { c.habits.complete(it, today()) }
    }
}
