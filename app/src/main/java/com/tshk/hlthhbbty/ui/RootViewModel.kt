package com.tshk.hlthhbbty.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.UserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RootState {
    data object Loading : RootState
    data object LoggedOut : RootState
    data class NeedsProfile(val user: UserEntity) : RootState
    data class Ready(val user: UserEntity) : RootState
}

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<RootState> = c.auth.currentUserId
        .flatMapLatest { id ->
            if (id == null) flowOf(RootState.LoggedOut)
            else c.users.observe(id).map { user ->
                when {
                    user == null -> {
                        // Сессия указывает на удалённого пользователя — сбрасываем.
                        viewModelScope.launch { c.auth.logout() }
                        RootState.LoggedOut
                    }
                    !user.profileCompleted -> RootState.NeedsProfile(user)
                    else -> RootState.Ready(user)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)
}
