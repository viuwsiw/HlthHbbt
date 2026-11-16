package com.tshk.hlthhbbty.data.repo

import com.tshk.hlthhbbty.data.db.HabitCheckEntity
import com.tshk.hlthhbbty.data.db.HabitDao
import com.tshk.hlthhbbty.data.db.HabitEntity
import kotlinx.coroutines.flow.Flow

class HabitRepository(private val dao: HabitDao) {

    fun observeHabits(userId: Long): Flow<List<HabitEntity>> = dao.observeActive(userId)

    fun observeChecks(userId: Long, fromDay: Long, toDay: Long): Flow<List<HabitCheckEntity>> =
        dao.observeChecks(userId, fromDay, toDay)

    suspend fun add(userId: Long, title: String, emoji: String, colorIndex: Int, goalPerDay: Int) {
        dao.insert(
            HabitEntity(
                userId = userId,
                title = title.trim(),
                emoji = emoji,
                colorIndex = colorIndex,
                goalPerDay = goalPerDay.coerceIn(1, 20),
            )
        )
    }

    suspend fun update(habit: HabitEntity) = dao.update(habit)

    suspend fun delete(habitId: Long) = dao.delete(habitId)

    /** +1 к выполнению за день; после достижения цели — сброс в 0. */
    suspend fun tap(habit: HabitEntity, day: Long) {
        val current = dao.countFor(habit.id, day) ?: 0
        val next = if (current >= habit.goalPerDay) 0 else current + 1
        if (next == 0) dao.deleteCheck(habit.id, day)
        else dao.upsertCheck(HabitCheckEntity(habit.id, day, next))
    }

    suspend fun seedDefaults(userId: Long) {
        listOf(
            Triple("Прогулка на свежем воздухе", "🚶", 0),
            Triple("Чтение 30 минут", "📖", 2),
            Triple("Зарядка", "🤸", 3),
            Triple("Лечь спать до 23:00", "🌙", 4),
        ).forEach { (title, emoji, color) -> add(userId, title, emoji, color, 1) }
    }
}
