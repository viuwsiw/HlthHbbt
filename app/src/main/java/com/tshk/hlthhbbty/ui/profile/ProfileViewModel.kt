package com.tshk.hlthhbbty.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.auth.AuthResult
import com.tshk.hlthhbbty.data.db.UserEntity
import com.tshk.hlthhbbty.data.Achievements
import com.tshk.hlthhbbty.util.habitStreak
import com.tshk.hlthhbbty.util.scheduledOn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val user: UserEntity? = null,
    val habitsCount: Int = 0,
    val bestStreak: Int = 0,
    val waterAllTimeMl: Int = 0,
    val doneToday: Int = 0,
    val plannedToday: Int = 0,
    val achievementsUnlocked: Int = 0,
    val achievementsTotal: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(private val c: AppContainer) : ViewModel() {
    private val userId = c.auth.currentUserId.filterNotNull()

    val state: StateFlow<ProfileState> = userId.flatMapLatest { id ->
        combine(c.users.observe(id), c.history.observe(id)) { user, h ->
            val day = h.today
            val planned = h.goodHabits.filter { it.scheduledOn(day) }
            val achievements = Achievements.compute(h)
            ProfileState(
                user = user,
                habitsCount = h.habits.size,
                bestStreak = h.goodHabits.maxOfOrNull { habit ->
                    habitStreak(h.doneByHabit[habit.id].orEmpty(), h.frozenByHabit[habit.id].orEmpty(), habit.scheduleMask, day)
                } ?: 0,
                waterAllTimeMl = h.water.sumOf { it.total },
                doneToday = planned.count { day in h.doneByHabit[it.id].orEmpty() },
                plannedToday = planned.size,
                achievementsUnlocked = achievements.count { it.unlocked },
                achievementsTotal = achievements.size,
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState())

    fun logout() = viewModelScope.launch { c.auth.logout() }

    fun deleteAccount() = viewModelScope.launch {
        val id = userId.first()
        c.users.deleteUserFiles(state.value.user)
        c.messenger.deleteRemoteAccount()
        c.auth.deleteAccount(id)
    }
}

data class ChangePasswordState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

class ChangePasswordViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ChangePasswordState())
    val state: StateFlow<ChangePasswordState> = _state.asStateFlow()

    fun onCurrent(v: String) = _state.update { it.copy(current = v, error = null) }
    fun onNew(v: String) = _state.update { it.copy(new = v, error = null) }
    fun onConfirm(v: String) = _state.update { it.copy(confirm = v, error = null) }

    fun submit() {
        val s = _state.value
        if (s.new != s.confirm) {
            _state.update { it.copy(error = "Новые пароли не совпадают") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val id = c.auth.currentUserId.filterNotNull().first()
            when (val r = c.auth.changePassword(id, s.current, s.new)) {
                is AuthResult.Success -> _state.update { ChangePasswordState(done = true) }
                is AuthResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
