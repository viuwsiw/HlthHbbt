package com.tshk.hlthhbbty.data.repo

import android.content.Context
import android.net.Uri
import com.tshk.hlthhbbty.data.db.Gender
import com.tshk.hlthhbbty.data.db.UserDao
import com.tshk.hlthhbbty.data.db.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class UserRepository(
    private val context: Context,
    private val userDao: UserDao,
) {
    fun observe(userId: Long): Flow<UserEntity?> = userDao.observe(userId)

    suspend fun updateProfile(
        userId: Long,
        displayName: String,
        age: Int?,
        gender: Gender?,
        avatar: String?,
        waterGoalMl: Int,
    ) {
        val user = userDao.findById(userId) ?: return
        val oldAvatar = user.avatar
        userDao.update(
            user.copy(
                displayName = displayName.trim(),
                age = age,
                gender = gender,
                avatar = avatar,
                waterGoalMl = waterGoalMl,
                profileCompleted = true,
            )
        )
        if (oldAvatar != avatar) deleteAvatarFile(oldAvatar)
    }

    /** Копирует выбранное фото во внутреннюю память и возвращает значение для поля avatar. */
    suspend fun importAvatar(userId: Long, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "avatars").apply { mkdirs() }
            val file = File(dir, "user_${userId}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            "file:${file.absolutePath}"
        }.getOrNull()
    }

    /** Удаляет временный файл аватара, если пользователь передумал. */
    suspend fun discardAvatar(avatar: String?) = withContext(Dispatchers.IO) { deleteAvatarFile(avatar) }

    suspend fun deleteUserFiles(user: UserEntity?) = withContext(Dispatchers.IO) { deleteAvatarFile(user?.avatar) }

    private fun deleteAvatarFile(avatar: String?) {
        if (avatar != null && avatar.startsWith("file:")) {
            runCatching { File(avatar.removePrefix("file:")).delete() }
        }
    }
}
