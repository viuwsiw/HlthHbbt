package com.tshk.hlthhbbty.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.tshk.hlthhbbty.ui.achievements.AchievementsViewModel
import com.tshk.hlthhbbty.ui.focus.FocusViewModel
import com.tshk.hlthhbbty.ui.habits.HabitEditorViewModel
import com.tshk.hlthhbbty.ui.settings.SettingsViewModel
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
        initializer { HabitEditorViewModel(container(), createSavedStateHandle()) }
        initializer { FocusViewModel(container(), createSavedStateHandle()) }
        initializer { AchievementsViewModel(container()) }
        initializer { SettingsViewModel(container()) }
        initializer { com.tshk.hlthhbbty.ui.admin.AdminViewModel(container()) }
        initializer { com.tshk.hlthhbbty.ui.messenger.ChatsViewModel(container()) }
        initializer { com.tshk.hlthhbbty.ui.messenger.ChatViewModel(container(), createSavedStateHandle()) }
        initializer { com.tshk.hlthhbbty.ui.messenger.UserProfileViewModel(container(), createSavedStateHandle()) }
        initializer { com.tshk.hlthhbbty.ui.messenger.PrivacyViewModel(container()) }
        initializer { com.tshk.hlthhbbty.ui.walk.WalkViewModel(container(), createSavedStateHandle()) }
        initializer { com.tshk.hlthhbbty.ui.integrations.SpotifyViewModel(container(), createSavedStateHandle()) }
    }

    private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as HlthApp).container
}
