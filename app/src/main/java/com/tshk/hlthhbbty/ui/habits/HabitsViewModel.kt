package com.tshk.hlthhbbty.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.util.currentStreak
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitOverview(
    val habit: HabitEntity,
    val todayCount: Int,
    /** Последние 7 дней, от старого к сегодняшнему: выполнено ли. */
    val week: List<Boolean>,
    val streak: Int,
)

data class HabitsState(val loaded: Boolean = false, val habits: List<HabitOverview> = emptyList(), val day: Long = today())

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModel(private val c: AppContainer) : ViewModel() {
    private val userId = c.auth.currentUserId.filterNotNull()

    val state: StateFlow<HabitsState> = userId.flatMapLatest { id ->
        val day = today()
        combine(c.habits.observeHabits(id), c.habits.observeChecks(id, day - 365, day)) { habits, checks ->
            val byHabit = checks.groupBy { it.habitId }
            HabitsState(
                loaded = true,
                day = day,
                habits = habits.map { h ->
                    val doneDays = byHabit[h.id].orEmpty().filter { it.count >= h.goalPerDay }.map { it.epochDay }.toSet()
                    HabitOverview(
                        habit = h,
                        todayCount = byHabit[h.id]?.firstOrNull { it.epochDay == day }?.count ?: 0,
                        week = (6 downTo 0).map { (day - it) in doneDays },
                        streak = currentStreak(doneDays, day),
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsState())

    fun tap(habit: HabitEntity) = viewModelScope.launch { c.habits.tap(habit, today()) }

    fun save(existing: HabitEntity?, title: String, emoji: String, colorIndex: Int, goal: Int) = viewModelScope.launch {
        if (existing == null) c.habits.add(userId.first(), title, emoji, colorIndex, goal)
        else c.habits.update(existing.copy(title = title.trim(), emoji = emoji, colorIndex = colorIndex, goalPerDay = goal))
    }

    fun delete(habit: HabitEntity) = viewModelScope.launch { c.habits.delete(habit.id) }
}
