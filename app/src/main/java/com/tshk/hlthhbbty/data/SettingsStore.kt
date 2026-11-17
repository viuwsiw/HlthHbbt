package com.tshk.hlthhbbty.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

enum class ThemeMode(val title: String) { SYSTEM("Авто"), LIGHT("Светлая"), DARK("Тёмная"), BLACK("Чёрная") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentIndex: Int = 0,
    val waterReminders: Boolean = false,
    val waterIntervalHours: Int = 2,
    val waterFromHour: Int = 9,
    val waterToHour: Int = 21,
    val healthConnect: Boolean = false,
    val autoUpdate: Boolean = true,
    val bugButton: Boolean = true,
    val animatedBackground: Boolean = false,
    /** Канал автообновлений: false — релизы, true — беты (и релизы, если они новее). */
    val betaChannel: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private object K {
        val theme = intPreferencesKey("theme")
        val accent = intPreferencesKey("accent")
        val water = booleanPreferencesKey("water_reminders")
        val interval = intPreferencesKey("water_interval")
        val from = intPreferencesKey("water_from")
        val to = intPreferencesKey("water_to")
        val health = booleanPreferencesKey("health_connect")
        val autoUpdate = booleanPreferencesKey("auto_update")
        val bugButton = booleanPreferencesKey("bug_button")
        val animatedBg = booleanPreferencesKey("animated_background")
        val beta = booleanPreferencesKey("beta_channel")
        val seen = stringSetPreferencesKey("seen_achievements")
    }

    suspend fun seenAchievements(): Set<String> = context.settingsDataStore.data.first()[K.seen].orEmpty()

    suspend fun markSeen(ids: Set<String>) {
        context.settingsDataStore.edit { it[K.seen] = it[K.seen].orEmpty() + ids }
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[K.theme] = s.themeMode.ordinal
            prefs[K.accent] = s.accentIndex
            prefs[K.water] = s.waterReminders
            prefs[K.interval] = s.waterIntervalHours
            prefs[K.from] = s.waterFromHour
            prefs[K.to] = s.waterToHour
            prefs[K.health] = s.healthConnect
            prefs[K.autoUpdate] = s.autoUpdate
            prefs[K.bugButton] = s.bugButton
            prefs[K.animatedBg] = s.animatedBackground
            prefs[K.beta] = s.betaChannel
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        themeMode = ThemeMode.entries.getOrElse(this[K.theme] ?: 0) { ThemeMode.SYSTEM },
        accentIndex = this[K.accent] ?: 0,
        waterReminders = this[K.water] ?: false,
        waterIntervalHours = this[K.interval] ?: 2,
        waterFromHour = this[K.from] ?: 9,
        waterToHour = this[K.to] ?: 21,
        healthConnect = this[K.health] ?: false,
        autoUpdate = this[K.autoUpdate] ?: true,
        bugButton = this[K.bugButton] ?: false,
        animatedBackground = this[K.animatedBg] ?: false,
        betaChannel = this[K.beta] ?: false,
    )
}
