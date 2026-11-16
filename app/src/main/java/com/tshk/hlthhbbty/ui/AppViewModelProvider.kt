package com.tshk.hlthhbbty.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.HlthApp
import com.tshk.hlthhbbty.ui.auth.AuthViewModel
import com.tshk.hlthhbbty.ui.habits.HabitsViewModel
import com.tshk.hlthhbbty.ui.home.HomeViewModel
import com.tshk.hlthhbbty.ui.profile.ChangePasswordViewModel
import com.tshk.hlthhbbty.ui.profile.EditProfileViewModel
import com.tshk.hlthhbbty.ui.profile.ProfileViewModel
import com.tshk.hlthhbbty.ui.stats.StatsViewModel

object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { RootViewModel(container()) }
        initializer { AuthViewModel(container()) }
        initializer { HomeViewModel(container()) }
        initializer { HabitsViewModel(container()) }
        initializer { StatsViewModel(container()) }
        initializer { ProfileViewModel(container()) }
        initializer { EditProfileViewModel(container()) }
        initializer { ChangePasswordViewModel(container()) }
    }

    private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as HlthApp).container
}
