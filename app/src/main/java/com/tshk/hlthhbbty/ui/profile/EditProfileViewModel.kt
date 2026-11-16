package com.tshk.hlthhbbty.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.db.Gender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileState(
    val loaded: Boolean = false,
    val userId: Long = 0,
    val username: String = "",
    val name: String = "",
    val age: String = "",
    val gender: Gender? = null,
    val avatar: String? = null,
    val waterGoalMl: Int = 2000,
    val saving: Boolean = false,
    val importing: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val saved: Boolean = false,
)

class EditProfileViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(EditProfileState())
    val state: StateFlow<EditProfileState> = _state.asStateFlow()

    /** Аватар, сохранённый в базе: временные файлы, отличные от него, удаляем. */
    private var originalAvatar: String? = null

    init {
        viewModelScope.launch {
            val id = c.auth.currentUserId.filterNotNull().first()
            val user = c.users.observe(id).filterNotNull().first()
            originalAvatar = user.avatar
            _state.value = EditProfileState(
                loaded = true,
                userId = user.id,
                username = user.username,
                name = user.displayName,
                age = user.age?.toString().orEmpty(),
                gender = user.gender,
                avatar = user.avatar ?: "preset:0",
                waterGoalMl = user.waterGoalMl,
            )
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v.take(40), errors = it.errors - "name") }
    fun onAge(v: String) = _state.update { it.copy(age = v.filter(Char::isDigit).take(3), errors = it.errors - "age") }
    fun onGender(g: Gender) = _state.update { it.copy(gender = g) }
    fun onWaterGoal(ml: Int) = _state.update { it.copy(waterGoalMl = ml) }

    fun onPreset(avatar: String) {
        discardTemp(_state.value.avatar)
        _state.update { it.copy(avatar = avatar) }
    }

    fun onPhotoPicked(uri: Uri) {
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            val imported = c.users.importAvatar(_state.value.userId, uri)
            if (imported != null) discardTemp(_state.value.avatar)
            _state.update { it.copy(importing = false, avatar = imported ?: it.avatar) }
        }
    }

    fun save() {
        val s = _state.value
        val age = s.age.toIntOrNull()
        val errors = buildMap {
            if (s.name.isBlank()) put("name", "Введите имя")
            if (s.age.isNotEmpty() && (age == null || age !in 5..120)) put("age", "Возраст от 5 до 120")
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            c.users.updateProfile(s.userId, s.name, age, s.gender, s.avatar, s.waterGoalMl)
            originalAvatar = s.avatar
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    private fun discardTemp(avatar: String?) {
        if (avatar != null && avatar != originalAvatar && avatar.startsWith("file:")) {
            viewModelScope.launch { c.users.discardAvatar(avatar) }
        }
    }

    override fun onCleared() {
        // Не сохранили — подчищаем загруженное фото.
        val current = _state.value.avatar
        if (!_state.value.saved && current != originalAvatar && current?.startsWith("file:") == true) {
            runCatching { java.io.File(current.removePrefix("file:")).delete() }
        }
    }
}
