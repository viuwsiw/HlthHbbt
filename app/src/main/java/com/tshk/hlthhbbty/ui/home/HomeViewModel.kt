package com.tshk.hlthhbbty.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.data.db.UserEntity
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitToday(val habit: HabitEntity, val count: Int) {
    val done get() = count >= habit.goalPerDay
}

data class HomeState(
    val user: UserEntity? = null,
    val waterMl: Int = 0,
    val habits: List<HabitToday> = emptyList(),
    val day: Long = today(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val userId = c.auth.currentUserId.filterNotNull()

    val state: StateFlow<HomeState> = userId.flatMapLatest { id ->
        val day = today()
        combine(
            c.users.observe(id),
            c.water.observeToday(id, day),
            c.habits.observeHabits(id),
            c.habits.observeChecks(id, day, day),
        ) { user, water, habits, checks ->
            val counts = checks.associate { it.habitId to it.count }
            HomeState(user, water, habits.map { HabitToday(it, counts[it.id] ?: 0) }, day)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun addWater(ml: Int) = viewModelScope.launch { c.water.add(userId.first(), ml) }
    fun undoWater() = viewModelScope.launch { c.water.undoLast(userId.first()) }
    fun tapHabit(habit: HabitEntity) = viewModelScope.launch { c.habits.tap(habit, today()) }
}
