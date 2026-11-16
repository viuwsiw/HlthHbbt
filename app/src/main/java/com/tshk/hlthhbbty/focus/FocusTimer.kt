package com.tshk.hlthhbbty.focus

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.tshk.hlthhbbty.data.db.HabitEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Состояние таймера фокуса. Переживает сворачивание и закрытие приложения. */
data class FocusTimerState(
    val habitId: Long,
    val title: String,
    val emoji: String,
    val totalMs: Long,
    /** Момент окончания, если таймер идёт. */
    val endAt: Long?,
    /** Остаток, если на паузе. */
    val pausedRemainingMs: Long?,
    val finished: Boolean = false,
) {
    val running get() = endAt != null && !finished
    fun remainingMs(now: Long = System.currentTimeMillis()): Long = when {
        finished -> 0
        endAt != null -> (endAt - now).coerceAtLeast(0)
        else -> pausedRemainingMs ?: totalMs
    }
}

/**
 * Единый таймер фокуса на всё приложение: состояние хранится в SharedPreferences,
 * отсчёт ведёт [FocusTimerService] с постоянным уведомлением.
 */
class FocusTimer(private val context: Context) {
    private val prefs = context.getSharedPreferences("focus_timer", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())

    /** Запрос «открыть экран фокуса» (нажатие на уведомление таймера). */
    val openRequest = MutableStateFlow(false)
    val state: StateFlow<FocusTimerState?> = _state.asStateFlow()

    fun start(habit: HabitEntity, minutes: Int) {
        val total = minutes.coerceIn(1, 240) * 60_000L
        save(FocusTimerState(habit.id, habit.title, habit.emoji, total, System.currentTimeMillis() + total, null))
        runService(FocusTimerService.ACTION_SYNC)
    }

    fun pause() {
        val s = _state.value ?: return
        if (!s.running) return
        save(s.copy(endAt = null, pausedRemainingMs = s.remainingMs()))
        runService(FocusTimerService.ACTION_SYNC)
    }

    fun resume() {
        val s = _state.value ?: return
        if (s.running || s.finished) return
        save(s.copy(endAt = System.currentTimeMillis() + (s.pausedRemainingMs ?: s.totalMs), pausedRemainingMs = null))
        runService(FocusTimerService.ACTION_SYNC)
    }

    fun stop() {
        save(null)
        runService(FocusTimerService.ACTION_SYNC)
    }

    /** Вызывается службой по окончании. */
    internal fun markFinished() {
        _state.value?.let { save(it.copy(endAt = null, pausedRemainingMs = 0, finished = true)) }
    }

    fun dismissFinished() {
        if (_state.value?.finished == true) save(null)
    }

    /** Перечитать после действия из уведомления (другой поток/процесс). */
    fun reload() { _state.value = load() }

    private fun runService(action: String) {
        val intent = Intent(context, FocusTimerService::class.java).setAction(action)
        val s = _state.value
        if (s != null && !s.finished) ContextCompat.startForegroundService(context, intent)
        else context.startService(intent)
    }

    private fun save(s: FocusTimerState?) {
        _state.value = s
        prefs.edit().apply {
            if (s == null) clear() else {
                putLong("habitId", s.habitId)
                putString("title", s.title)
                putString("emoji", s.emoji)
                putLong("total", s.totalMs)
                putLong("endAt", s.endAt ?: 0L)
                putLong("paused", s.pausedRemainingMs ?: -1L)
                putBoolean("finished", s.finished)
            }
        }.apply()
    }

    private fun load(): FocusTimerState? {
        if (!prefs.contains("habitId")) return null
        val endAt = prefs.getLong("endAt", 0L).takeIf { it > 0 }
        val paused = prefs.getLong("paused", -1L).takeIf { it >= 0 }
        return FocusTimerState(
            habitId = prefs.getLong("habitId", -1),
            title = prefs.getString("title", "").orEmpty(),
            emoji = prefs.getString("emoji", "⏱").orEmpty(),
            totalMs = prefs.getLong("total", 25 * 60_000L),
            endAt = endAt,
            pausedRemainingMs = paused,
            finished = prefs.getBoolean("finished", false),
        )
    }
}
