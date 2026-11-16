package com.tshk.hlthhbbty.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.focus.FocusTimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FocusViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val habitId = handle.get<String>("id")?.toLongOrNull() ?: -1L
    private val timer = c.focusTimer

    private val _habit = MutableStateFlow<HabitEntity?>(null)
    val habit: StateFlow<HabitEntity?> = _habit.asStateFlow()

    /** Выбранная длительность (минуты) до старта. */
    private val _minutes = MutableStateFlow(25)
    val minutes: StateFlow<Int> = _minutes.asStateFlow()

    val timerState: StateFlow<FocusTimerState?> = timer.state

    init {
        timer.reload()
        viewModelScope.launch {
            val h = c.habits.find(habitId)
            _habit.value = h
            _minutes.value = h?.focusMinutes ?: 25
        }
    }

    /** Таймер на экране относится к этой привычке? */
    fun isMine(s: FocusTimerState?) = s != null && s.habitId == habitId

    fun setMinutes(m: Int) { _minutes.value = m.coerceIn(1, 240) }

    fun start() {
        val h = _habit.value ?: return
        timer.start(h, _minutes.value)
        // Запоминаем выбранное время как длительность по умолчанию для привычки.
        if (h.focusMinutes != _minutes.value) viewModelScope.launch { c.habits.setFocusMinutes(h, _minutes.value) }
    }

    fun pause() = timer.pause()
    fun resume() = timer.resume()
    fun stop() = timer.stop()
    fun dismissFinished() = timer.dismissFinished()
}
