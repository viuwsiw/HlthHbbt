package com.tshk.hlthhbbty.util

import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.data.db.HabitKind
import java.time.LocalDate
import java.time.temporal.IsoFields

val WeekdayShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

fun isScheduled(mask: Int, epochDay: Long): Boolean {
    val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1 // 0 = понедельник
    return mask and (1 shl dow) != 0
}

fun HabitEntity.scheduledOn(epochDay: Long) = !isQuit && isScheduled(scheduleMask, epochDay)

fun HabitEntity.createdDay(): Long =
    java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

fun scheduleLabel(mask: Int): String = when {
    mask and HabitKind.EVERY_DAY == HabitKind.EVERY_DAY -> "Каждый день"
    mask == 0b0011111 -> "По будням"
    mask == 0b1100000 -> "По выходным"
    else -> WeekdayShort.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(", ")
}

/**
 * Текущая серия с учётом расписания и заморозок: незапланированные и замороженные дни
 * не рвут серию. Сегодняшний день, если ещё не выполнен, тоже не рвёт.
 */
fun habitStreak(done: Set<Long>, frozen: Set<Long>, mask: Int, today: Long = today(), limit: Int = 400): Int {
    var day = today
    if (day !in done) day--
    var streak = 0
    var steps = 0
    while (steps++ < limit) {
        when {
            day in done -> streak++
            !isScheduled(mask, day) || day in frozen -> Unit
            else -> break
        }
        day--
    }
    return streak
}

fun habitBestStreak(done: Set<Long>, frozen: Set<Long>, mask: Int, from: Long, to: Long): Int {
    var best = 0
    var run = 0
    for (day in from..to) {
        when {
            day in done -> { run++; best = maxOf(best, run) }
            !isScheduled(mask, day) || day in frozen -> Unit
            day == to -> Unit // сегодня ещё можно успеть
            else -> run = 0
        }
    }
    return best
}

/** Заморозку можно использовать раз в ISO-неделю. */
fun freezeAvailable(frozen: Set<Long>, today: Long = today()): Boolean {
    val d = LocalDate.ofEpochDay(today)
    val week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) to d.get(IsoFields.WEEK_BASED_YEAR)
    return frozen.none {
        val f = LocalDate.ofEpochDay(it)
        (f.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) to f.get(IsoFields.WEEK_BASED_YEAR)) == week
    }
}

fun formatDuration(millis: Long): String {
    val totalMin = (millis / 60_000).coerceAtLeast(0)
    val days = totalMin / (60 * 24)
    val hours = (totalMin / 60) % 24
    val mins = totalMin % 60
    return when {
        days > 0 -> "$days ${daysWord(days.toInt())} $hours ч"
        hours > 0 -> "$hours ч $mins мин"
        else -> "$mins мин"
    }
}

fun formatMinutesOfDay(m: Int) = "%02d:%02d".format(m / 60, m % 60)
