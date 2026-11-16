package com.tshk.hlthhbbty.data.repo

import com.tshk.hlthhbbty.data.db.FreezeEntity
import com.tshk.hlthhbbty.data.db.HabitCheckEntity
import com.tshk.hlthhbbty.data.db.HabitDao
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.data.db.HabitKind
import kotlinx.coroutines.flow.Flow

/** Поля формы привычки. */
data class HabitDraft(
    val title: String,
    val emoji: String,
    val colorIndex: Int = 0,
    val goalPerDay: Int = 1,
    val scheduleMask: Int = HabitKind.EVERY_DAY,
    val reminderMinutes: Int? = null,
    val kind: String = HabitKind.GOOD,
    val healthMetric: String? = null,
    val healthTarget: Int? = null,
    val focusMinutes: Int? = null,
)

/** Колбэки на изменения: обновить виджет, перепланировать напоминания. */
interface HabitChangeListener {
    suspend fun onHabitSaved(habit: HabitEntity) {}
    suspend fun onHabitDeleted(habitId: Long) {}
    suspend fun onProgressChanged() {}
}

class HabitRepository(private val dao: HabitDao) {
    var listener: HabitChangeListener? = null

    fun observeHabits(userId: Long): Flow<List<HabitEntity>> = dao.observeActive(userId)

    fun observeChecks(userId: Long, fromDay: Long, toDay: Long): Flow<List<HabitCheckEntity>> =
        dao.observeChecks(userId, fromDay, toDay)

    fun observeFreezes(userId: Long, fromDay: Long, toDay: Long): Flow<List<FreezeEntity>> =
        dao.observeFreezes(userId, fromDay, toDay)

    suspend fun find(id: Long) = dao.findById(id)

    suspend fun activeOnce(userId: Long) = dao.activeOnce(userId)

    suspend fun countFor(habitId: Long, day: Long) = dao.countFor(habitId, day) ?: 0

    suspend fun add(userId: Long, d: HabitDraft) {
        val habit = HabitEntity(
            userId = userId,
            title = d.title.trim(),
            emoji = d.emoji,
            colorIndex = d.colorIndex,
            goalPerDay = d.goalPerDay.coerceIn(1, 20),
            scheduleMask = d.scheduleMask.takeIf { it != 0 } ?: HabitKind.EVERY_DAY,
            reminderMinutes = d.reminderMinutes,
            kind = d.kind,
            quitSince = if (d.kind == HabitKind.QUIT) System.currentTimeMillis() else null,
            healthMetric = d.healthMetric,
            healthTarget = d.healthTarget,
            focusMinutes = d.focusMinutes,
        )
        val id = dao.insert(habit)
        listener?.onHabitSaved(habit.copy(id = id))
        listener?.onProgressChanged()
    }

    suspend fun update(existing: HabitEntity, d: HabitDraft) {
        val habit = existing.copy(
            title = d.title.trim(),
            emoji = d.emoji,
            colorIndex = d.colorIndex,
            goalPerDay = d.goalPerDay.coerceIn(1, 20),
            scheduleMask = d.scheduleMask.takeIf { it != 0 } ?: HabitKind.EVERY_DAY,
            reminderMinutes = d.reminderMinutes,
            kind = d.kind,
            quitSince = if (d.kind == HabitKind.QUIT) existing.quitSince ?: System.currentTimeMillis() else null,
            healthMetric = d.healthMetric,
            healthTarget = d.healthTarget,
            focusMinutes = d.focusMinutes,
        )
        dao.update(habit)
        listener?.onHabitSaved(habit)
        listener?.onProgressChanged()
    }

    suspend fun delete(habitId: Long) {
        dao.delete(habitId)
        listener?.onHabitDeleted(habitId)
        listener?.onProgressChanged()
    }

    /** +1 к выполнению за день; после достижения цели — сброс в 0. */
    suspend fun tap(habit: HabitEntity, day: Long) {
        val current = dao.countFor(habit.id, day) ?: 0
        val next = if (current >= habit.goalPerDay) 0 else current + 1
        if (next == 0) dao.deleteCheck(habit.id, day)
        else dao.upsertCheck(HabitCheckEntity(habit.id, day, next))
        listener?.onProgressChanged()
    }

    /** Отметить выполненным полностью (из уведомления, таймера или Health Connect). */
    suspend fun complete(habit: HabitEntity, day: Long) {
        if ((dao.countFor(habit.id, day) ?: 0) >= habit.goalPerDay) return
        dao.upsertCheck(HabitCheckEntity(habit.id, day, habit.goalPerDay))
        listener?.onProgressChanged()
    }

    suspend fun setFocusMinutes(habit: HabitEntity, minutes: Int) {
        dao.update(habit.copy(focusMinutes = minutes))
    }

    suspend fun freeze(habitId: Long, day: Long) {
        dao.insertFreeze(FreezeEntity(habitId, day))
        listener?.onProgressChanged()
    }

    /** Срыв в вредной привычке — отсчёт начинается заново. */
    suspend fun relapse(habit: HabitEntity) {
        dao.update(habit.copy(quitSince = System.currentTimeMillis()))
        listener?.onProgressChanged()
    }

    suspend fun seedDefaults(userId: Long) {
        listOf(
            HabitDraft("Прогулка на свежем воздухе", "🚶", 0),
            HabitDraft("Чтение 30 минут", "📖", 2, focusMinutes = 30),
            HabitDraft("Зарядка", "🤸", 3, reminderMinutes = 8 * 60),
            HabitDraft("Лечь спать до 23:00", "🌙", 4),
        ).forEach { add(userId, it) }
    }
}
