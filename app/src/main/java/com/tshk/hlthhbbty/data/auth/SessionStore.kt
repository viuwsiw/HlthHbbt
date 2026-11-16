package com.tshk.hlthhbbty.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** Хранит id вошедшего пользователя между запусками приложения. */
class SessionStore(private val context: Context) {
    private val key = longPreferencesKey("current_user_id")

    val currentUserId: Flow<Long?> = context.sessionDataStore.data.map { it[key] }

    suspend fun set(userId: Long) {
        context.sessionDataStore.edit { it[key] = userId }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(key) }
    }
}
