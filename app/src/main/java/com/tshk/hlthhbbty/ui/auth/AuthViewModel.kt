package com.tshk.hlthhbbty.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.auth.AuthRepository
import com.tshk.hlthhbbty.data.auth.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isRegister: Boolean = false,
    val name: String = "",
    val username: String = "",
    val password: String = "",
    val confirm: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
)

class AuthViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setMode(register: Boolean) = _state.update { it.copy(isRegister = register, error = null, fieldErrors = emptyMap()) }
    fun onName(v: String) = _state.update { it.copy(name = v, fieldErrors = it.fieldErrors - "name") }
    fun onUsername(v: String) = _state.update { it.copy(username = v.trim(), fieldErrors = it.fieldErrors - "username") }
    fun onPassword(v: String) = _state.update { it.copy(password = v, fieldErrors = it.fieldErrors - "password") }
    fun onConfirm(v: String) = _state.update { it.copy(confirm = v, fieldErrors = it.fieldErrors - "confirm") }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val errors = buildMap {
            if (s.isRegister) {
                if (s.name.isBlank()) put("name", "Введите имя")
                AuthRepository.validateUsername(s.username.lowercase())?.let { put("username", it) }
                AuthRepository.validatePassword(s.password)?.let { put("password", it) }
                if (s.confirm != s.password) put("confirm", "Пароли не совпадают")
            } else {
                if (s.username.isBlank()) put("username", "Введите логин")
                if (s.password.isEmpty()) put("password", "Введите пароль")
            }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, error = null) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = if (s.isRegister) c.auth.register(s.username, s.password, s.name)
            else c.auth.login(s.username, s.password)
            _state.update {
                when (result) {
                    is AuthResult.Success -> {
                        if (s.isRegister) c.onboarding.reset(result.userId)
                        viewModelScope.launch { c.settings.update { st -> st.copy(autoUpdate = true) } }
                        AuthUiState()
                    }
                    is AuthResult.Error -> it.copy(loading = false, error = result.message)
                }
            }
        }
    }
}
