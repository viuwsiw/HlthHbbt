package com.tshk.hlthhbbty.ui.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tshk.hlthhbbty.messenger.MessengerRepository
import com.tshk.hlthhbbty.messenger.Presence
import com.tshk.hlthhbbty.ui.theme.Liquid
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val StatusOnline = Color(0xFF22C55E)
val StatusDnd = Color(0xFFEF4444)
val StatusAway = Color(0xFFF59E0B)

fun statusColor(status: String?): Color = when (status) {
    "ONLINE" -> StatusOnline
    "DND" -> StatusDnd
    else -> StatusAway
}

fun statusLabel(p: Presence?): String = when (p?.status) {
    "ONLINE" -> "В сети"
    "DND" -> "Не беспокоить"
    null -> "Вне чата"
    else -> {
        if (p.lastSeen <= 0) "Вне чата"
        else {
            val dt = Instant.ofEpochSecond(p.lastSeen).atZone(ZoneId.systemDefault())
            val time = if (dt.toLocalDate() == LocalDate.now()) dt.format(DateTimeFormatter.ofPattern("HH:mm"))
            else dt.format(DateTimeFormatter.ofPattern("d.MM HH:mm"))
            "Вне чата · был(а) в $time"
        }
    }
}

/** Цветная точка статуса с обводкой под цвет фона. */
@Composable
fun StatusDot(status: String?, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(statusColor(status))
            .border(2.dp, Liquid.palette.base, CircleShape)
    )
}

/** Пока экран мессенджера на виду — пользователь «в сети»; ушёл — автоматически «вне чата». */
@Composable
fun MessengerPresence(repo: MessengerRepository) {
    LifecycleResumeEffect(Unit) {
        repo.enterMessenger()
        onPauseOrDispose { repo.leaveMessenger() }
    }
}
