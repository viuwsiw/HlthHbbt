package com.tshk.hlthhbbty.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.util.bestStreak
import com.tshk.hlthhbbty.util.currentStreak
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class HabitStat(val habit: HabitEntity, val rate30: Float, val streak: Int, val best: Int)

data class StatsState(
    val loaded: Boolean = false,
    val day: Long = today(),
    val waterGoal: Int = 2000,
    /** Вода за последние 7 дней, от старого к сегодняшнему. */
    val waterWeek: List<Int> = List(7) { 0 },
    val waterAllTimeMl: Int = 0,
    val perfectDays7: Int = 0,
    val habitStats: List<HabitStat> = emptyList(),
    /** Доля выполненных привычек по дням за 7 дней. */
    val completionWeek: List<Float> = List(7) { 0f },
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<StatsState> = c.auth.currentUserId.filterNotNull().flatMapLatest { id ->
        val day = today()
        combine(
            c.users.observe(id),
            c.water.observeTotals(id, day - 6, day),
            c.water.observeAllTime(id),
            c.habits.observeHabits(id),
            c.habits.observeChecks(id, day - 365, day),
        ) { user, waterTotals, allTime, habits, checks ->
            val waterByDay = waterTotals.associate { it.epochDay to it.total }
            val week = (6 downTo 0).map { day - it }
            val doneByHabit = habits.associate { h ->
                h.id to checks.filter { it.habitId == h.id && it.count >= h.goalPerDay }.map { it.epochDay }.toSet()
            }
            val completion = week.map { d ->
                val active = habits.filter { java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() <= d }
                if (active.isEmpty()) 0f else active.count { d in doneByHabit[it.id].orEmpty() } / active.size.toFloat()
            }
            StatsState(
                loaded = true,
                day = day,
                waterGoal = user?.waterGoalMl ?: 2000,
                waterWeek = week.map { waterByDay[it] ?: 0 },
                waterAllTimeMl = allTime,
                perfectDays7 = completion.count { it >= 1f },
                completionWeek = completion,
                habitStats = habits.map { h ->
                    val done = doneByHabit[h.id].orEmpty()
                    HabitStat(
                        habit = h,
                        rate30 = (0 until 30).count { (day - it) in done } / 30f,
                        streak = currentStreak(done, day),
                        best = bestStreak(done),
                    )
                }.sortedByDescending { it.rate30 },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())
}
