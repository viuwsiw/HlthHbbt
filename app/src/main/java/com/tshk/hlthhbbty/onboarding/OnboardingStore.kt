package com.tshk.hlthhbbty.onboarding

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Показано ли обучение — отдельно для каждого пользователя на этом устройстве. */
class OnboardingStore(context: Context) {
    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    private val _seen = MutableStateFlow(prefs.getStringSet("seen", emptySet())!!.toSet())
    val seen: StateFlow<Set<String>> = _seen.asStateFlow()

    fun isSeen(userId: Long) = userId.toString() in _seen.value

    fun markSeen(userId: Long) = save(_seen.value + userId.toString())

    /** Показать заново (новая регистрация или кнопка в настройках). */
    fun reset(userId: Long) = save(_seen.value - userId.toString())

    private fun save(set: Set<String>) {
        _seen.value = set
        prefs.edit().putStringSet("seen", set).apply()
    }
}
