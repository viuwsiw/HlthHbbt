package com.tshk.hlthhbbty.data

import com.tshk.hlthhbbty.data.db.DayNoteEntity
import com.tshk.hlthhbbty.data.db.DayTotal
import com.tshk.hlthhbbty.data.db.FreezeEntity
import com.tshk.hlthhbbty.data.db.HabitCheckEntity
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.util.createdDay
import com.tshk.hlthhbbty.util.habitBestStreak
import com.tshk.hlthhbbty.util.scheduledOn

data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val current: Int,
    val target: Int,
) {
    val unlocked get() = current >= target
    val progress get() = (current.toFloat() / target).coerceIn(0f, 1f)
}

/** Входные данные для расчёта достижений и статистики. */
data class HistoryData(
    val today: Long,
    val habits: List<HabitEntity>,
    val checks: List<HabitCheckEntity>,
    val freezes: List<FreezeEntity>,
    val water: List<DayTotal>,
    val waterGoal: Int,
    val notes: List<DayNoteEntity>,
) {
    val goodHabits = habits.filter { !it.isQuit }

    val doneByHabit: Map<Long, Set<Long>> = goodHabits.associate { h ->
        h.id to checks.filter { it.habitId == h.id && it.count >= h.goalPerDay }.map { it.epochDay }.toSet()
    }
    val frozenByHabit: Map<Long, Set<Long>> = freezes.groupBy { it.habitId }.mapValues { e -> e.value.map { it.epochDay }.toSet() }

    /** Доля выполненных запланированных привычек за день, null — ничего не было запланировано. */
    fun completion(day: Long): Float? {
        val planned = goodHabits.filter { it.createdDay() <= day && it.scheduledOn(day) }
        if (planned.isEmpty()) return null
        return planned.count { day in doneByHabit[it.id].orEmpty() } / planned.size.toFloat()
    }

    fun bestStreakEver(): Int = goodHabits.maxOfOrNull { h ->
        habitBestStreak(doneByHabit[h.id].orEmpty(), frozenByHabit[h.id].orEmpty(), h.scheduleMask, h.createdDay(), today)
    } ?: 0
}

object Achievements {
    fun compute(d: HistoryData): List<Achievement> {
        val best = d.bestStreakEver()
        val totalDone = d.doneByHabit.values.sumOf { it.size }
        val waterGoalDays = d.water.count { it.total >= d.waterGoal }
        val waterLiters = d.water.sumOf { it.total } / 1000
        val firstDay = d.goodHabits.minOfOrNull { it.createdDay() } ?: d.today
        val perfectDays = (maxOf(firstDay, d.today - 365)..d.today).count { d.completion(it) == 1f }
        val quitDays = d.habits.filter { it.isQuit }.maxOfOrNull {
            ((System.currentTimeMillis() - (it.quitSince ?: System.currentTimeMillis())) / 86_400_000L).toInt()
        } ?: 0
        val moodNotes = d.notes.count { it.mood > 0 }
        return listOf(
            Achievement("first_step", "🌱", "Первый шаг", "Выполните привычку впервые", totalDone, 1),
            Achievement("streak_3", "🔥", "Разогрев", "Серия из 3 дней", best, 3),
            Achievement("streak_7", "⚡", "Неделя силы", "Серия из 7 дней", best, 7),
            Achievement("streak_30", "🏆", "Месяц дисциплины", "Серия из 30 дней", best, 30),
            Achievement("streak_100", "💎", "Сотня", "Серия из 100 дней", best, 100),
            Achievement("perfect_day", "⭐", "Идеальный день", "Выполните все привычки за день", perfectDays, 1),
            Achievement("perfect_7", "🌟", "Семь звёзд", "7 идеальных дней", perfectDays, 7),
            Achievement("water_1", "💧", "Водный баланс", "Выполните цель по воде", waterGoalDays, 1),
            Achievement("water_7", "🌊", "Гидратация", "Цель по воде 7 дней", waterGoalDays, 7),
            Achievement("water_100", "🐳", "Сто литров", "Выпейте 100 литров воды", waterLiters, 100),
            Achievement("habits_5", "🧩", "Коллекционер", "Заведите 5 привычек", d.habits.size, 5),
            Achievement("checks_100", "💯", "Сотня отметок", "100 выполнений привычек", totalDone, 100),
            Achievement("quit_7", "🕊️", "Свобода", "7 дней без вредной привычки", quitDays, 7),
            Achievement("quit_30", "🦅", "Новая жизнь", "30 дней без вредной привычки", quitDays, 30),
            Achievement("notes_7", "📔", "Дневник", "Отметьте настроение 7 раз", moodNotes, 7),
        )
    }
}
