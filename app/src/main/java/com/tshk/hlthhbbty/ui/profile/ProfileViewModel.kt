package com.tshk.hlthhbbty.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.auth.AuthResult
import com.tshk.hlthhbbty.data.db.UserEntity
import com.tshk.hlthhbbty.util.currentStreak
import com.tshk.hlthhbbty.util.today
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val user: UserEntity? = null,
    val habitsCount: Int = 0,
    val bestStreak: Int = 0,
    val waterAllTimeMl: Int = 0,
    val doneToday: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(private val c: AppContainer) : ViewModel() {
    private val userId = c.auth.currentUserId.filterNotNull()

    val state: StateFlow<ProfileState> = userId.flatMapLatest { id ->
        val day = today()
        combine(
            c.users.observe(id),
            c.habits.observeHabits(id),
            c.habits.observeChecks(id, day - 365, day),
            c.water.observeAllTime(id),
        ) { user, habits, checks, water ->
            val streaks = habits.map { h ->
                currentStreak(checks.filter { it.habitId == h.id && it.count >= h.goalPerDay }.map { it.epochDay }.toSet(), day)
            }
            ProfileState(
                user = user,
                habitsCount = habits.size,
                bestStreak = streaks.maxOrNull() ?: 0,
                waterAllTimeMl = water,
                doneToday = habits.count { h -> checks.any { it.habitId == h.id && it.epochDay == day && it.count >= h.goalPerDay } },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState())

    fun logout() = viewModelScope.launch { c.auth.logout() }

    fun deleteAccount() = viewModelScope.launch {
        val id = userId.first()
        c.users.deleteUserFiles(state.value.user)
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
