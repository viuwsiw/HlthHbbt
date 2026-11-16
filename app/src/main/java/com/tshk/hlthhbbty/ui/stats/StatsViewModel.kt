package com.tshk.hlthhbbty.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.util.createdDay
import com.tshk.hlthhbbty.util.habitBestStreak
import com.tshk.hlthhbbty.util.habitStreak
import com.tshk.hlthhbbty.util.isScheduled
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

data class HabitStat(val habit: HabitEntity, val rate30: Float, val streak: Int, val best: Int)

data class StatsState(
    val loaded: Boolean = false,
    val day: Long = today(),
    val waterGoal: Int = 2000,
    val waterWeek: List<Int> = List(7) { 0 },
    val waterAllTimeMl: Int = 0,
    val perfectDays7: Int = 0,
    val habitStats: List<HabitStat> = emptyList(),
    val completionWeek: List<Float> = List(7) { 0f },
    /** Последние 371 день (53 недели), значение — доля выполнения или null. */
    val heatmap: List<Float?> = emptyList(),
    val heatmapStart: Long = 0,
    val moodWeek: List<Int> = List(7) { 0 },
    /** Среднее настроение в дни с ≥80% выполнения и в остальные дни. */
    val moodGood: Float? = null,
    val moodOther: Float? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<StatsState> = c.auth.currentUserId.filterNotNull().flatMapLatest { id ->
        c.history.observe(id).map { h ->
            val day = h.today
            val week = (6 downTo 0).map { day - it }
            val waterByDay = h.water.associate { it.epochDay to it.total }
            val completion = week.map { h.completion(it) ?: 0f }
            // календарь начинается с понедельника, 53 недели назад
            val todayDow = java.time.LocalDate.ofEpochDay(day).dayOfWeek.value - 1
            val start = day - todayDow - 52 * 7
            val moodByDay = h.notes.associate { it.epochDay to it.mood }
            val withMood = h.notes.filter { it.mood > 0 }
            val good = withMood.filter { (h.completion(it.epochDay) ?: 0f) >= 0.8f }
            val other = withMood - good.toSet()
            StatsState(
                loaded = true,
                day = day,
                waterGoal = h.waterGoal,
                waterWeek = week.map { waterByDay[it] ?: 0 },
                waterAllTimeMl = h.water.sumOf { it.total },
                perfectDays7 = week.count { h.completion(it) == 1f },
                completionWeek = completion,
                heatmap = (start..day).map { h.completion(it) },
                heatmapStart = start,
                moodWeek = week.map { moodByDay[it] ?: 0 },
                moodGood = good.takeIf { it.isNotEmpty() }?.map { it.mood }?.average()?.toFloat(),
                moodOther = other.takeIf { it.isNotEmpty() }?.map { it.mood }?.average()?.toFloat(),
                habitStats = h.goodHabits.map { habit ->
                    val done = h.doneByHabit[habit.id].orEmpty()
                    val frozen = h.frozenByHabit[habit.id].orEmpty()
                    val planned = (0 until 30).map { day - it }
                        .filter { it >= h.createdDayOf(habit) && isScheduled(habit.scheduleMask, it) }
                    HabitStat(
                        habit = habit,
                        rate30 = if (planned.isEmpty()) 0f else planned.count { it in done } / planned.size.toFloat(),
                        streak = habitStreak(done, frozen, habit.scheduleMask, day),
                        best = habitBestStreak(done, frozen, habit.scheduleMask, h.createdDayOf(habit), day),
                    )
                }.sortedByDescending { it.rate30 },
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())
}
