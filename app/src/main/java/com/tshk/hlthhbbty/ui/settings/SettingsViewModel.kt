package com.tshk.hlthhbbty.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.AppSettings
import com.tshk.hlthhbbty.data.ThemeMode
import com.tshk.hlthhbbty.health.HealthStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _healthGranted = MutableStateFlow(false)
    val healthGranted: StateFlow<Boolean> = _healthGranted.asStateFlow()

    val healthStatus: HealthStatus = c.health.status()
    val healthPermissions = c.health.permissions
    fun healthContract() = c.health.permissionContract()

    init {
        refreshHealth()
    }

    fun refreshHealth() = viewModelScope.launch { _healthGranted.value = c.health.hasPermissions() }

    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setAccent(i: Int) = update { it.copy(accentIndex = i) }
    fun setAnimatedBackground(on: Boolean) = update { it.copy(animatedBackground = on) }
    /** Бета-канал: включает и кнопку багтрекера, сразу проверяет обновления. */
    fun setBetaChannel(on: Boolean) = viewModelScope.launch {
        c.settings.update { it.copy(betaChannel = on, bugButton = on) }
        c.updater.check()
    }
    fun replayOnboarding() = viewModelScope.launch {
        c.auth.currentUserId.filterNotNull().first().let { c.onboarding.reset(it) }
    }
    fun setBugButton(on: Boolean) = update { it.copy(bugButton = on) }
    fun setAutoUpdate(on: Boolean) = update { it.copy(autoUpdate = on) }
    val updater = c.updater
    fun setWaterReminders(on: Boolean) = update { it.copy(waterReminders = on) }
    fun setWaterInterval(h: Int) = update { it.copy(waterIntervalHours = h) }
    fun setWaterHours(from: Int, to: Int) = update { it.copy(waterFromHour = from, waterToHour = to) }

    fun onHealthResult(granted: Set<String>) = viewModelScope.launch {
        val ok = granted.containsAll(c.health.permissions) || c.health.hasPermissions()
        _healthGranted.value = ok
        c.settings.update { it.copy(healthConnect = ok) }
        if (ok) {
            c.reminders.scheduleHealthSync()
            runCatching { c.health.syncHabits(c.auth.currentUserId.filterNotNull().first()) }
            _message.value = "Health Connect подключён"
        } else {
            _message.value = "Доступ не выдан"
        }
    }

    fun disconnectHealth() = viewModelScope.launch {
        c.settings.update { it.copy(healthConnect = false) }
        c.reminders.cancelHealthSync()
    }

    fun export(uri: Uri) = viewModelScope.launch {
        val r = c.backup.export(c.auth.currentUserId.filterNotNull().first(), uri)
        _message.value = r.fold({ "Данные сохранены" }, { "Ошибка: ${it.message}" })
    }

    fun import(uri: Uri) = viewModelScope.launch {
        val id = c.auth.currentUserId.filterNotNull().first()
        val r = c.backup.import(id, uri)
        _message.value = r.fold({ n -> "Импортировано привычек: $n" }, { "Ошибка: ${it.message}" })
        if (r.isSuccess) c.reminders.rescheduleAll(c.habits.activeOnce(id), c.settings.current())
    }

    fun consumeMessage() { _message.value = null }

    private fun update(t: (AppSettings) -> AppSettings) = viewModelScope.launch { c.settings.update(t) }
}
