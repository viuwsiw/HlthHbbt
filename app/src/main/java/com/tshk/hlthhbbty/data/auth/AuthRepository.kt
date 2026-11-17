package com.tshk.hlthhbbty.data.auth

import com.tshk.hlthhbbty.data.db.UserDao
import com.tshk.hlthhbbty.data.db.UserEntity
import com.tshk.hlthhbbty.data.repo.HabitRepository
import com.tshk.hlthhbbty.messenger.ApiException
import com.tshk.hlthhbbty.messenger.MessengerAccount
import com.tshk.hlthhbbty.messenger.MessengerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

sealed interface AuthResult {
    data class Success(val userId: Long) : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * Аккаунты живут на сервере: регистрация, вход и смена пароля проверяются там (bcrypt).
 * На устройстве — только сессия и локальные данные привычек, привязанные к логину.
 */
class AuthRepository(
    private val userDao: UserDao,
    private val session: SessionStore,
    private val habits: HabitRepository,
    private val messenger: () -> MessengerRepository,
) {
    val currentUserId: Flow<Long?> = session.currentUserId

    private val m get() = messenger()

    suspend fun register(username: String, password: String, displayName: String): AuthResult =
        withContext(Dispatchers.Default) {
            val login = username.trim().lowercase()
            validateUsername(login)?.let { return@withContext AuthResult.Error(it) }
            validatePassword(password)?.let { return@withContext AuthResult.Error(it) }
            val name = displayName.trim()
            if (name.isEmpty()) return@withContext AuthResult.Error("Введите имя")
            val token = try {
                m.api.register(login, password, name, m.store.deviceId)
            } catch (e: ApiException) {
                return@withContext AuthResult.Error(e.message ?: "Ошибка сервера")
            }
            val local = userDao.findByUsername(login)
            val id = local?.id ?: userDao.insert(localUser(login, password, name)).also { habits.seedDefaults(it) }
            finishSignIn(id, login, token)
            AuthResult.Success(id)
        }

    suspend fun login(username: String, password: String): AuthResult =
        withContext(Dispatchers.Default) {
            val login = username.trim().lowercase()
            var local = userDao.findByUsername(login)
            val token = try {
                m.api.login(login, password)
            } catch (e: ApiException) {
                // Старый аккаунт с этого устройства, ещё не перенесённый на сервер: пароль проверяем
                // по локальному хешу и сразу переносим.
                if (e.error == "bad_credentials" && local != null && !m.store.isMigrated(login) &&
                    PasswordHasher.verify(password.toCharArray(), local.passwordHash, local.passwordSalt)
                ) {
                    try {
                        m.api.register(login, password, local.displayName, m.store.deviceId)
                    } catch (e2: ApiException) {
                        if (e2.error == "username_taken") m.store.setConflict(login)
                        return@withContext AuthResult.Error(
                            if (e2.error == "username_taken") "Логин @$login на сервере занят другим человеком. Зарегистрируйтесь с новым логином — привычки с этого устройства сохранятся."
                            else e2.message ?: "Ошибка сервера"
                        )
                    }
                } else {
                    return@withContext AuthResult.Error(e.message ?: "Ошибка сервера")
                }
            }
            if (local == null) {
                // Первый вход на этом устройстве — заводим локальный профиль из данных сервера.
                val me = runCatching { m.api.meWith(token) }.getOrNull()
                val name = me?.optString("displayName")?.takeIf { it.isNotBlank() } ?: login
                val id = userDao.insert(localUser(login, password, name))
                habits.seedDefaults(id)
                local = userDao.findById(id)
            }
            finishSignIn(local!!.id, login, token)
            AuthResult.Success(local.id)
        }

    private suspend fun finishSignIn(localId: Long, login: String, token: String) {
        m.store.setMigrated(login)
        m.onSignedIn(MessengerAccount(login, token))
        session.set(localId)
    }

    /** Локальная запись нужна для привычек и профиля; пароль проверяет сервер. */
    private fun localUser(login: String, password: String, name: String): UserEntity {
        val hashed = PasswordHasher.hash(password.toCharArray())
        return UserEntity(username = login, passwordHash = hashed.hash, passwordSalt = hashed.salt, displayName = name)
    }

    suspend fun changePassword(userId: Long, current: String, new: String): AuthResult =
        withContext(Dispatchers.Default) {
            val user = userDao.findById(userId) ?: return@withContext AuthResult.Error("Пользователь не найден")
            validatePassword(new)?.let { return@withContext AuthResult.Error(it) }
            try {
                m.api.changePassword(current, new)
            } catch (e: ApiException) {
                return@withContext AuthResult.Error(e.message ?: "Ошибка сервера")
            }
            val hashed = PasswordHasher.hash(new.toCharArray())
            userDao.update(user.copy(passwordHash = hashed.hash, passwordSalt = hashed.salt))
            AuthResult.Success(userId)
        }

    /** Логин занят на сервере другим — переносим аккаунт под новым логином (привычки сохраняются). */
    suspend fun claimNewUsername(userId: Long, newLogin: String, password: String): AuthResult =
        withContext(Dispatchers.Default) {
            val user = userDao.findById(userId) ?: return@withContext AuthResult.Error("Пользователь не найден")
            if (!PasswordHasher.verify(password.toCharArray(), user.passwordHash, user.passwordSalt)) {
                return@withContext AuthResult.Error("Неверный пароль")
            }
            val login = newLogin.trim().lowercase()
            validateUsername(login)?.let { return@withContext AuthResult.Error(it) }
            if (userDao.findByUsername(login) != null) return@withContext AuthResult.Error("Этот логин уже есть на устройстве")
            val token = try {
                m.api.register(login, password, user.displayName, m.store.deviceId)
            } catch (e: ApiException) {
                return@withContext AuthResult.Error(e.message ?: "Ошибка сервера")
            }
            userDao.update(user.copy(username = login))
            finishSignIn(user.id, login, token)
            AuthResult.Success(user.id)
        }

    suspend fun logout() = session.clear()

    suspend fun deleteAccount(userId: Long) {
        userDao.delete(userId)
        session.clear()
    }

    /**
     * Перенос всех аккаунтов с устройства на сервер (по хешу, без пароля).
     * Для текущего пользователя сразу подключает мессенджер.
     */
    suspend fun migrateLocalAccounts(currentId: Long?) {
        if (m.store.serverUrl.value.isBlank()) return
        for (u in userDao.all()) {
            // Уже подключён к серверу в прошлых бетах — это тот же аккаунт.
            if (m.store.account.value?.username == u.username) { m.store.setMigrated(u.username); continue }
            if (m.store.isMigrated(u.username) || m.store.hasConflict(u.username)) continue
            try {
                val token = m.api.importAccount(u.username, u.displayName, u.passwordHash, u.passwordSalt, PasswordHasher.ITERATIONS, m.store.deviceId)
                m.store.setMigrated(u.username)
                if (u.id == currentId) m.onSignedIn(MessengerAccount(u.username, token))
            } catch (e: ApiException) {
                when (e.error) {
                    "username_taken" -> m.store.setConflict(u.username)
                    "network", "no_server" -> return // попробуем при следующем запуске
                }
            }
        }
    }

    companion object {
        private val USERNAME_REGEX = Regex("^[a-z0-9_.]{3,24}$")

        fun validateUsername(login: String): String? = when {
            login.length < 3 -> "Логин — минимум 3 символа"
            login.length > 24 -> "Логин — максимум 24 символа"
            !USERNAME_REGEX.matches(login) -> "Только латиница, цифры, «_» и «.»"
            else -> null
        }

        fun validatePassword(password: String): String? = when {
            password.length < 6 -> "Пароль — минимум 6 символов"
            password.none { it.isDigit() } || password.none { it.isLetter() } -> "Пароль должен содержать буквы и цифры"
            else -> null
        }
    }
}
