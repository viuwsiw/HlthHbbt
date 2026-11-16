package com.tshk.hlthhbbty.ui.habits

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassDialog
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.HabitColors
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.daysWord
import com.tshk.hlthhbbty.util.shortWeekday

private val EmojiChoices = listOf(
    "💧", "🚶", "🏃", "🤸", "🧘", "🚴", "🏊", "💪",
    "📖", "✍️", "🧠", "🎧", "🌙", "😴", "🍎", "🥗",
    "🦷", "💊", "☀️", "🌿", "🚭", "📵", "🙏", "❤️",
)

@Composable
fun HabitsScreen(
    contentPadding: PaddingValues,
    vm: HabitsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = Liquid.palette
    var editing by remember { mutableStateOf<HabitEntity?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<HabitEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Привычки", style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
                    Text(
                        "Нажмите на кружок, чтобы отметить выполнение",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textSecondary,
                    )
                }
                GlassIconButton(Icons.Rounded.Add, "Новая привычка", { creating = true }, size = 52.dp, tint = p.accent)
            }
        }
        if (s.loaded && s.habits.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("🌱", fontSize = 44.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    VSpace(8.dp)
                    Text(
                        "Начните с одной маленькой привычки — так проще закрепить результат.",
                        textAlign = TextAlign.Center,
                        color = p.textSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VSpace(16.dp)
                    GlassButton("Создать привычку", { creating = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
                }
            }
        }
        items(s.habits, key = { it.habit.id }) { h ->
            HabitCard(
                h, s.day,
                onTap = { vm.tap(h.habit) },
                onEdit = { editing = h.habit },
                onDelete = { deleting = h.habit },
            )
        }
    }

    if (creating || editing != null) {
        HabitEditorDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { title, emoji, color, goal ->
                vm.save(editing, title, emoji, color, goal)
                creating = false; editing = null
            },
        )
    }
    deleting?.let { habit ->
        GlassDialog(
            onDismiss = { deleting = null },
            title = "Удалить привычку?",
            confirmText = "Удалить",
            confirmStyle = GlassButtonStyle.Danger,
            onConfirm = { vm.delete(habit); deleting = null },
        ) {
            Text("«${habit.title}» и вся её история будут удалены.", color = p.textSecondary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCard(
    h: HabitOverview,
    today: Long,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = Liquid.palette
    var expanded by rememberSaveable(h.habit.id) { mutableStateOf(false) }
    val color = habitColor(h.habit.colorIndex)
    GlassCard(Modifier.fillMaxWidth().animateContentSize(), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HabitBadge(h.habit.emoji, h.habit.colorIndex)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(h.habit.title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (h.streak > 0) "🔥 ${h.streak} ${daysWord(h.streak)} подряд" else "Серия ещё не началась",
                    style = MaterialTheme.typography.labelMedium,
                    color = p.textSecondary,
                )
            }
            HabitCheck(h.todayCount, h.habit.goalPerDay, h.habit.colorIndex, onTap, size = 44.dp)
        }
        VSpace(14.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            h.week.forEachIndexed { i, done ->
                val day = today - (6 - i)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(shortWeekday(day), style = MaterialTheme.typography.labelSmall, color = if (day == today) p.textPrimary else p.textSecondary)
                    VSpace(4.dp)
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (done) color else color.copy(alpha = 0.12f))
                            .border(if (day == today) 1.5.dp else 0.dp, if (day == today) color else color.copy(alpha = 0f), CircleShape)
                    )
                }
            }
        }
        if (expanded) {
            VSpace(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Изменить", onEdit, Modifier.weight(1f).height(46.dp), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.Edit)
                GlassButton("Удалить", onDelete, Modifier.weight(1f).height(46.dp), style = GlassButtonStyle.Danger, icon = Icons.Rounded.Delete)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitEditorDialog(
    initial: HabitEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, emoji: String, color: Int, goal: Int) -> Unit,
) {
    val p = Liquid.palette
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var emoji by rememberSaveable { mutableStateOf(initial?.emoji ?: EmojiChoices[1]) }
    var color by rememberSaveable { mutableIntStateOf(initial?.colorIndex ?: 0) }
    var goal by rememberSaveable { mutableIntStateOf(initial?.goalPerDay ?: 1) }

    GlassDialog(
        onDismiss = onDismiss,
        title = if (initial == null) "Новая привычка" else "Изменить привычку",
        confirmText = "Сохранить",
        confirmEnabled = title.isNotBlank(),
        onConfirm = { onSave(title, emoji, color, goal) },
    ) {
        GlassTextField(title, { title = it.take(40) }, "Название")
        VSpace(14.dp)
        Text("Иконка", style = MaterialTheme.typography.labelLarge, color = p.textSecondary)
        VSpace(8.dp)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 8,
        ) {
            EmojiChoices.forEach { e ->
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (e == emoji) habitColor(color).copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { emoji = e },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 20.sp) }
            }
        }
        VSpace(14.dp)
        Text("Цвет", style = MaterialTheme.typography.labelLarge, color = p.textSecondary)
        VSpace(8.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HabitColors.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (i == color) 3.dp else 0.dp, if (i == color) p.textPrimary else c, CircleShape)
                        .clickable { color = i }
                )
            }
        }
        VSpace(14.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Раз в день", style = MaterialTheme.typography.labelLarge, color = p.textSecondary)
                Text("$goal", style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
            }
            GlassIconButton(Icons.Rounded.Remove, "Меньше", { goal = (goal - 1).coerceAtLeast(1) }, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            GlassIconButton(Icons.Rounded.Add, "Больше", { goal = (goal + 1).coerceAtMost(20) }, size = 40.dp)
        }
    }
}
