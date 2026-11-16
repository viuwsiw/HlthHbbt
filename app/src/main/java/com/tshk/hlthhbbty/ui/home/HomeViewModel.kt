package com.tshk.hlthhbbty.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.DayNoteEntity
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.data.db.UserEntity
import com.tshk.hlthhbbty.health.HealthSnapshot
import com.tshk.hlthhbbty.util.scheduledOn
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class HabitToday(val habit: HabitEntity, val count: Int) {
    val done get() = count >= habit.goalPerDay
}

data class HomeState(
    val user: UserEntity? = null,
    val waterMl: Int = 0,
    val habits: List<HabitToday> = emptyList(),
    val quits: List<HabitEntity> = emptyList(),
    val note: DayNoteEntity? = null,
    val health: HealthSnapshot? = null,
    val day: Long = today(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val userId = c.auth.currentUserId.filterNotNull()
    private val health = MutableStateFlow<HealthSnapshot?>(null)

    val state: StateFlow<HomeState> = userId.flatMapLatest { id ->
        val day = today()
        combine(
            combine(c.users.observe(id), c.water.observeToday(id, day)) { u, w -> u to w },
            c.habits.observeHabits(id),
            c.habits.observeChecks(id, day, day),
            c.notes.observe(id, day),
            health,
        ) { (user, water), habits, checks, note, snap ->
            val counts = checks.associate { it.habitId to it.count }
            HomeState(
                user = user,
                waterMl = water,
                habits = habits.filter { it.scheduledOn(day) }.map { HabitToday(it, counts[it.id] ?: 0) },
                quits = habits.filter { it.isQuit },
                note = note,
                health = snap,
                day = day,
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Вызывается при каждом появлении экрана. */
    fun refreshHealth() = viewModelScope.launch {
        if (!c.settings.current().healthConnect) {
            health.value = null
            return@launch
        }
        health.value = runCatching { c.health.syncHabits(userId.first()) }.getOrNull()
    }

    fun addWater(ml: Int) = viewModelScope.launch { c.water.add(userId.first(), ml) }
    fun undoWater() = viewModelScope.launch { c.water.undoLast(userId.first()) }
    fun tapHabit(habit: HabitEntity) = viewModelScope.launch { c.habits.tap(habit, today()) }
    fun relapse(habit: HabitEntity) = viewModelScope.launch { c.habits.relapse(habit) }
    fun saveNote(mood: Int, note: String) = viewModelScope.launch { c.notes.save(userId.first(), today(), mood, note) }
}
