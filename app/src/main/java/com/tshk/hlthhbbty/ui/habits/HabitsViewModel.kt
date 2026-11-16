package com.tshk.hlthhbbty.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.util.freezeAvailable
import com.tshk.hlthhbbty.util.habitStreak
import com.tshk.hlthhbbty.util.isScheduled
import com.tshk.hlthhbbty.util.scheduledOn
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DayMark { DONE, MISSED, FROZEN, OFF, PENDING }

data class HabitOverview(
    val habit: HabitEntity,
    val todayCount: Int,
    /** Последние 7 дней, от старого к сегодняшнему. */
    val week: List<DayMark>,
    val streak: Int,
    val scheduledToday: Boolean,
    /** Вчера пропущено, а заморозка на этой неделе не использована. */
    val canFreezeYesterday: Boolean,
)

data class HabitsState(
    val loaded: Boolean = false,
    val habits: List<HabitOverview> = emptyList(),
    val quits: List<HabitEntity> = emptyList(),
    val day: Long = today(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModel(private val c: AppContainer) : ViewModel() {

    val state: StateFlow<HabitsState> = c.auth.currentUserId.filterNotNull().flatMapLatest { id ->
        val day = today()
        combine(
            c.habits.observeHabits(id),
            c.habits.observeChecks(id, day - 400, day),
            c.habits.observeFreezes(id, day - 400, day),
        ) { habits, checks, freezes ->
            val checksBy = checks.groupBy { it.habitId }
            val freezesBy = freezes.groupBy { it.habitId }.mapValues { e -> e.value.map { it.epochDay }.toSet() }
            HabitsState(
                loaded = true,
                day = day,
                quits = habits.filter { it.isQuit },
                habits = habits.filter { !it.isQuit }.map { h ->
                    val done = checksBy[h.id].orEmpty().filter { it.count >= h.goalPerDay }.map { it.epochDay }.toSet()
                    val frozen = freezesBy[h.id].orEmpty()
                    val yesterday = day - 1
                    HabitOverview(
                        habit = h,
                        todayCount = checksBy[h.id]?.firstOrNull { it.epochDay == day }?.count ?: 0,
                        week = (6 downTo 0).map { back ->
                            val d = day - back
                            when {
                                d in done -> DayMark.DONE
                                d in frozen -> DayMark.FROZEN
                                !isScheduled(h.scheduleMask, d) -> DayMark.OFF
                                d == day -> DayMark.PENDING
                                else -> DayMark.MISSED
                            }
                        },
                        streak = habitStreak(done, frozen, h.scheduleMask, day),
                        scheduledToday = h.scheduledOn(day),
                        canFreezeYesterday = isScheduled(h.scheduleMask, yesterday) && yesterday !in done &&
                            yesterday !in frozen && freezeAvailable(frozen, day),
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsState())

    fun tap(habit: HabitEntity) = viewModelScope.launch { c.habits.tap(habit, today()) }
    fun freezeYesterday(habit: HabitEntity) = viewModelScope.launch { c.habits.freeze(habit.id, today() - 1) }
    fun relapse(habit: HabitEntity) = viewModelScope.launch { c.habits.relapse(habit) }
}
