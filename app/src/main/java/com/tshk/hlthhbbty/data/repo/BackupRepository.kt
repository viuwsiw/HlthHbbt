package com.tshk.hlthhbbty.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.tshk.hlthhbbty.data.db.AppDatabase
import com.tshk.hlthhbbty.data.db.DayNoteEntity
import com.tshk.hlthhbbty.data.db.FreezeEntity
import com.tshk.hlthhbbty.data.db.HabitCheckEntity
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.data.db.WaterLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Экспорт и импорт данных пользователя в JSON-файл (без пароля). */
class BackupRepository(private val context: Context, private val db: AppDatabase) {

    suspend fun export(userId: Long, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = db.userDao().findById(userId) ?: error("Пользователь не найден")
            val habits = db.habitDao().allForUser(userId)
            val root = JSONObject()
                .put("format", "hlth-hbbty")
                .put("version", 2)
                .put("exportedAt", System.currentTimeMillis())
                .put("profile", JSONObject()
                    .put("displayName", user.displayName)
                    .put("age", user.age ?: JSONObject.NULL)
                    .put("gender", user.gender?.name ?: JSONObject.NULL)
                    .put("waterGoalMl", user.waterGoalMl))
                .put("habits", JSONArray(habits.map { h ->
                    JSONObject()
                        .put("id", h.id).put("title", h.title).put("emoji", h.emoji)
                        .put("colorIndex", h.colorIndex).put("goalPerDay", h.goalPerDay)
                        .put("createdAt", h.createdAt).put("archived", h.archived)
                        .put("scheduleMask", h.scheduleMask).put("reminderMinutes", h.reminderMinutes ?: JSONObject.NULL)
                        .put("kind", h.kind).put("quitSince", h.quitSince ?: JSONObject.NULL)
                        .put("healthMetric", h.healthMetric ?: JSONObject.NULL)
                        .put("healthTarget", h.healthTarget ?: JSONObject.NULL)
                        .put("focusMinutes", h.focusMinutes ?: JSONObject.NULL)
                }))
                .put("checks", JSONArray(db.habitDao().allChecks(userId).map {
                    JSONObject().put("habitId", it.habitId).put("day", it.epochDay).put("count", it.count)
                }))
                .put("freezes", JSONArray(db.habitDao().allFreezes(userId).map {
                    JSONObject().put("habitId", it.habitId).put("day", it.epochDay)
                }))
                .put("water", JSONArray(db.waterDao().all(userId).map {
                    JSONObject().put("ml", it.amountMl).put("day", it.epochDay).put("ts", it.timestamp)
                }))
                .put("notes", JSONArray(db.noteDao().all(userId).map {
                    JSONObject().put("day", it.epochDay).put("mood", it.mood).put("note", it.note)
                }))
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) }
                ?: error("Не удалось открыть файл")
        }
    }

    /** Заменяет привычки, воду и заметки текущего пользователя данными из файла. */
    suspend fun import(userId: Long, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Не удалось открыть файл")
            val root = JSONObject(text)
            require(root.optString("format") == "hlth-hbbty") { "Это не файл HLTH HBTy" }
            var imported = 0
            db.withTransaction {
                val habitDao = db.habitDao()
                habitDao.deleteAllForUser(userId)
                db.waterDao().deleteAll(userId)
                db.noteDao().deleteAll(userId)

                root.optJSONObject("profile")?.let { p ->
                    db.userDao().findById(userId)?.let { u ->
                        db.userDao().update(u.copy(
                            waterGoalMl = p.optInt("waterGoalMl", u.waterGoalMl),
                        ))
                    }
                }
                val idMap = HashMap<Long, Long>()
                root.optJSONArray("habits")?.forEachObject { h ->
                    val newId = habitDao.insert(HabitEntity(
                        userId = userId,
                        title = h.getString("title"),
                        emoji = h.optString("emoji", "✅"),
                        colorIndex = h.optInt("colorIndex"),
                        goalPerDay = h.optInt("goalPerDay", 1),
                        createdAt = h.optLong("createdAt", System.currentTimeMillis()),
                        archived = h.optBoolean("archived"),
                        scheduleMask = h.optInt("scheduleMask", 127),
                        reminderMinutes = h.optIntOrNull("reminderMinutes"),
                        kind = h.optString("kind", "GOOD"),
                        quitSince = h.optLongOrNull("quitSince"),
                        healthMetric = h.optStringOrNull("healthMetric"),
                        healthTarget = h.optIntOrNull("healthTarget"),
                        focusMinutes = h.optIntOrNull("focusMinutes"),
                    ))
                    idMap[h.getLong("id")] = newId
                    imported++
                }
                val checks = mutableListOf<HabitCheckEntity>()
                root.optJSONArray("checks")?.forEachObject { c ->
                    idMap[c.getLong("habitId")]?.let { checks += HabitCheckEntity(it, c.getLong("day"), c.getInt("count")) }
                }
                habitDao.insertChecks(checks)
                val freezes = mutableListOf<FreezeEntity>()
                root.optJSONArray("freezes")?.forEachObject { f ->
                    idMap[f.getLong("habitId")]?.let { freezes += FreezeEntity(it, f.getLong("day")) }
                }
                habitDao.insertFreezes(freezes)
                val water = mutableListOf<WaterLogEntity>()
                root.optJSONArray("water")?.forEachObject { w ->
                    water += WaterLogEntity(userId = userId, amountMl = w.getInt("ml"), epochDay = w.getLong("day"), timestamp = w.optLong("ts"))
                }
                db.waterDao().insertAll(water)
                root.optJSONArray("notes")?.forEachObject { n ->
                    db.noteDao().upsert(DayNoteEntity(userId, n.getLong("day"), n.optInt("mood"), n.optString("note")))
                }
            }
            imported
        }
    }
}

private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) block(getJSONObject(i))
}

private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else optString(key)
