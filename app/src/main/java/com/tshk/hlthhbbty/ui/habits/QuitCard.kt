package com.tshk.hlthhbbty.ui.habits

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassDialog
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.formatDuration
import kotlinx.coroutines.delay

/** Карточка вредной привычки: сколько времени держитесь. */
@Composable
fun QuitCard(habit: HabitEntity, onRelapse: () -> Unit, onEdit: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val p = Liquid.palette
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val since = habit.quitSince ?: now
    GlassCard(modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HabitBadge(habit.emoji, habit.colorIndex)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Без: ${habit.title}", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                Text("Держитесь уже", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
            }
        }
        VSpace(10.dp)
        Text(formatDuration(now - since), style = MaterialTheme.typography.headlineMedium, color = habitColor(habit.colorIndex))
        VSpace(12.dp)
        GlassButton("Сорвался", { confirm = true }, Modifier.fillMaxWidth().height(46.dp), style = GlassButtonStyle.Secondary)
    }
    if (confirm) {
        GlassDialog(
            onDismiss = { confirm = false },
            title = "Начать отсчёт заново?",
            confirmText = "Да, заново",
            confirmStyle = GlassButtonStyle.Danger,
            onConfirm = { confirm = false; onRelapse() },
        ) { Text("Ничего страшного — главное не сдаваться. Счётчик обнулится.", color = p.textSecondary) }
    }
}
