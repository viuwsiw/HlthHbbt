package com.tshk.hlthhbbty.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")

fun today(): Long = LocalDate.now().toEpochDay()

fun shortWeekday(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(TextStyle.SHORT, ru)
        .replaceFirstChar { it.titlecase(ru) }

fun memberSince(createdAt: Long): String {
    val date = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val month = date.month.getDisplayName(TextStyle.FULL_STANDALONE, ru)
    return "$month ${date.year}"
}

fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Доброе утро"
    in 12..16 -> "Добрый день"
    in 17..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}

fun yearsWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "лет"
        mod10 == 1 -> "год"
        mod10 in 2..4 -> "года"
        else -> "лет"
    }
}

fun daysWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "дней"
        mod10 == 1 -> "день"
        mod10 in 2..4 -> "дня"
        else -> "дней"
    }
}

