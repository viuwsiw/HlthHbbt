package com.tshk.hlthhbbty.ui.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.data.db.HabitKind
import com.tshk.hlthhbbty.data.repo.HabitDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitEditorState(
    val loaded: Boolean = false,
    val existing: HabitEntity? = null,
    val draft: HabitDraft = HabitDraft(title = "", emoji = "🚶"),
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

class HabitEditorViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val habitId: Long = handle.get<String>("id")?.toLongOrNull() ?: handle.get<Long>("id") ?: -1L
    private val startKind: String = handle.get<String>("kind") ?: HabitKind.GOOD

    private val _state = MutableStateFlow(HabitEditorState())
    val state: StateFlow<HabitEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val habit = if (habitId > 0) c.habits.find(habitId) else null
            _state.value = if (habit != null) {
                HabitEditorState(
                    loaded = true, existing = habit,
                    draft = HabitDraft(
                        title = habit.title, emoji = habit.emoji, colorIndex = habit.colorIndex,
                        goalPerDay = habit.goalPerDay, scheduleMask = habit.scheduleMask,
                        reminderMinutes = habit.reminderMinutes, kind = habit.kind,
                        healthMetric = habit.healthMetric, healthTarget = habit.healthTarget,
                        focusMinutes = habit.focusMinutes,
                        activity = habit.activity,
                    ),
                )
            } else {
                HabitEditorState(
                    loaded = true,
                    draft = if (startKind == HabitKind.QUIT) HabitDraft("", "🚭", 3, kind = HabitKind.QUIT)
                    else HabitDraft("", "🚶"),
                )
            }
        }
    }

    fun hasUsageAccess() = c.bedtime.hasAccess()
    fun usageAccessIntent() = c.bedtime.accessIntent()

    fun edit(transform: (HabitDraft) -> HabitDraft) = _state.update { it.copy(draft = transform(it.draft)) }

    fun save() {
        val s = _state.value
        if (s.draft.title.isBlank()) return
        viewModelScope.launch {
            if (s.existing == null) c.habits.add(c.auth.currentUserId.filterNotNull().first(), s.draft)
            else c.habits.update(s.existing, s.draft)
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val habit = _state.value.existing ?: return
        viewModelScope.launch {
            c.habits.delete(habit.id)
            _state.update { it.copy(deleted = true) }
        }
    }
}
