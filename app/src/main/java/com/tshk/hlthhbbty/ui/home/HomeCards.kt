package com.tshk.hlthhbbty.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tshk.hlthhbbty.data.db.DayNoteEntity
import com.tshk.hlthhbbty.data.repo.MoodEmoji
import com.tshk.hlthhbbty.health.HealthSnapshot
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.GlassDialog
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid

@Composable
fun MoodCard(note: DayNoteEntity?, onSave: (Int, String) -> Unit) {
    val p = Liquid.palette
    var editNote by rememberSaveable { mutableStateOf(false) }
    val mood = note?.mood ?: 0
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle("Как ваш день?") {
            GlassChip(if (note?.note.isNullOrBlank()) "Заметка" else "Изменить", leading = "📝", onClick = { editNote = true })
        }
        VSpace(12.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MoodEmoji.forEachIndexed { i, e ->
                val selected = mood == i + 1
                val bg by animateColorAsState(if (selected) p.accent.copy(alpha = 0.28f) else Color.Transparent, label = "m")
                val scale by animateFloatAsState(if (selected) 1.15f else if (mood == 0) 1f else 0.85f, label = "s")
                Box(
                    Modifier
                        .size(52.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (mood == 0 || selected) 1f else 0.55f }
                        .clip(CircleShape)
                        .background(bg)
                        .clickable { onSave(i + 1, note?.note.orEmpty()) },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 30.sp) }
            }
        }
        if (!note?.note.isNullOrBlank()) {
            VSpace(10.dp)
            Text("«${note!!.note}»", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
        }
    }
    if (editNote) {
        var text by rememberSaveable { mutableStateOf(note?.note.orEmpty()) }
        var m by rememberSaveable { mutableIntStateOf(mood) }
        GlassDialog(
            onDismiss = { editNote = false },
            title = "Заметка о дне",
            confirmText = "Сохранить",
            onConfirm = { onSave(m, text); editNote = false },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MoodEmoji.forEachIndexed { i, e ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (m == i + 1) p.accent.copy(alpha = 0.3f) else Color.Transparent)
                            .clickable { m = i + 1 },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 24.sp) }
                }
            }
            VSpace(12.dp)
            GlassTextField(text, { text = it.take(500) }, "Что запомнилось?", singleLine = false, imeAction = ImeAction.Default)
        }
    }
}

@Composable
fun HealthCard(h: HealthSnapshot) {
    val p = Liquid.palette
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle("Health Connect") { Text("сегодня", style = MaterialTheme.typography.labelMedium, color = p.textSecondary) }
        VSpace(12.dp)
        Row {
            Stat("👟", "%,d".format(h.steps).replace(',', ' '), "шагов", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Stat("😴", "${h.sleepMinutes / 60} ч ${h.sleepMinutes % 60} м", "сон", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(emoji: String, value: String, label: String, modifier: Modifier) {
    val p = Liquid.palette
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 28.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
        }
    }
}
