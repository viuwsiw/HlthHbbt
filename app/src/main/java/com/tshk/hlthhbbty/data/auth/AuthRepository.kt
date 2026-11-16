package com.tshk.hlthhbbty.data.auth

import com.tshk.hlthhbbty.data.db.UserDao
import com.tshk.hlthhbbty.data.db.UserEntity
import com.tshk.hlthhbbty.data.repo.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

sealed interface AuthResult {
    data class Success(val userId: Long) : AuthResult
    data class Error(val message: String) : AuthResult
}

class AuthRepository(
    private val userDao: UserDao,
    private val session: SessionStore,
    private val habits: HabitRepository,
) {
    val currentUserId: Flow<Long?> = session.currentUserId

    suspend fun register(username: String, password: String, displayName: String): AuthResult =
        withContext(Dispatchers.Default) {
            val login = username.trim().lowercase()
            validateUsername(login)?.let { return@withContext AuthResult.Error(it) }
            validatePassword(password)?.let { return@withContext AuthResult.Error(it) }
            val name = displayName.trim()
            if (name.isEmpty()) return@withContext AuthResult.Error("Введите имя")
            if (userDao.findByUsername(login) != null) {
                return@withContext AuthResult.Error("Логин «$login» уже занят")
            }
            val hashed = PasswordHasher.hash(password.toCharArray())
            val id = userDao.insert(
                UserEntity(
                    username = login,
                    passwordHash = hashed.hash,
                    passwordSalt = hashed.salt,
                    displayName = name,
                )
            )
            habits.seedDefaults(id)
            session.set(id)
            AuthResult.Success(id)
        }

    suspend fun login(username: String, password: String): AuthResult =
        withContext(Dispatchers.Default) {
            val login = username.trim().lowercase()
            val user = userDao.findByUsername(login)
            if (user == null || !PasswordHasher.verify(password.toCharArray(), user.passwordHash, user.passwordSalt)) {
                return@withContext AuthResult.Error("Неверный логин или пароль")
            }
            session.set(user.id)
            AuthResult.Success(user.id)
        }

    suspend fun changePassword(userId: Long, current: String, new: String): AuthResult =
        withContext(Dispatchers.Default) {
            val user = userDao.findById(userId) ?: return@withContext AuthResult.Error("Пользователь не найден")
            if (!PasswordHasher.verify(current.toCharArray(), user.passwordHash, user.passwordSalt)) {
                return@withContext AuthResult.Error("Текущий пароль неверный")
            }
            validatePassword(new)?.let { return@withContext AuthResult.Error(it) }
            val hashed = PasswordHasher.hash(new.toCharArray())
            userDao.update(user.copy(passwordHash = hashed.hash, passwordSalt = hashed.salt))
            AuthResult.Success(userId)
        }

    suspend fun logout() = session.clear()

    suspend fun deleteAccount(userId: Long) {
        userDao.delete(userId)
        session.clear()
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
