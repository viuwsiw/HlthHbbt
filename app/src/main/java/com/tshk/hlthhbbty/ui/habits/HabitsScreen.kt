package com.tshk.hlthhbbty.ui.habits

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.data.db.HabitKind
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.daysWord
import com.tshk.hlthhbbty.util.formatMinutesOfDay
import com.tshk.hlthhbbty.util.scheduleLabel
import com.tshk.hlthhbbty.util.shortWeekday

@Composable
fun HabitsScreen(
    contentPadding: PaddingValues,
    onCreate: (kind: String) -> Unit,
    onEdit: (HabitEntity) -> Unit,
    onFocus: (HabitEntity) -> Unit,
    onWalk: (HabitEntity) -> Unit,
    vm: HabitsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = Liquid.palette

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    com.tshk.hlthhbbty.ui.glass.ReportScrollEnd(listState)
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Привычки", style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
                    Text("Нажмите на карточку — больше действий", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                }
                GlassIconButton(Icons.Rounded.Add, "Новая привычка", { onCreate(HabitKind.GOOD) }, size = 52.dp, tint = p.accent)
            }
        }
        if (s.loaded && s.habits.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("🌱", fontSize = 44.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    VSpace(8.dp)
                    Text(
                        "Начните с одной маленькой привычки — так проще закрепить результат.",
                        textAlign = TextAlign.Center, color = p.textSecondary, modifier = Modifier.fillMaxWidth(),
                    )
                    VSpace(16.dp)
                    GlassButton("Создать привычку", { onCreate(HabitKind.GOOD) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
                }
            }
        }
        items(s.habits, key = { it.habit.id }) { h ->
            HabitCard(h, s.day, onTap = { vm.tap(h.habit) }, onEdit = { onEdit(h.habit) },
                onFreeze = { vm.freezeYesterday(h.habit) }, onFocus = { onFocus(h.habit) }, onWalk = { onWalk(h.habit) })
        }
        item {
            SectionTitle("Отказ от вредного", Modifier.padding(top = 8.dp)) {
                GlassChip("Добавить", leading = "➕", onClick = { onCreate(HabitKind.QUIT) })
            }
        }
        if (s.quits.isEmpty()) {
            item {
                Text(
                    "Курение, сладкое, соцсети до сна… Отмечайте, сколько вы держитесь.",
                    style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        items(s.quits, key = { "q" + it.id }) { q -> QuitCard(q, onRelapse = { vm.relapse(q) }, onEdit = { onEdit(q) }) }
    }
}

@Composable
private fun HabitCard(
    h: HabitOverview,
    today: Long,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onFreeze: () -> Unit,
    onFocus: () -> Unit,
    onWalk: () -> Unit,
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
                    style = MaterialTheme.typography.labelMedium, color = p.textSecondary,
                )
            }
            if (h.scheduledToday) HabitCheck(h.todayCount, h.habit.goalPerDay, h.habit.colorIndex, onTap, size = 44.dp)
            else Text("выходной", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
        }
        VSpace(14.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            h.week.forEachIndexed { i, mark ->
                val day = today - (6 - i)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(shortWeekday(day), style = MaterialTheme.typography.labelSmall, color = if (day == today) p.textPrimary else p.textSecondary)
                    VSpace(4.dp)
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                when (mark) {
                                    DayMark.DONE -> color
                                    DayMark.FROZEN -> Color(0xFF7DD3FC)
                                    DayMark.OFF -> Color.Transparent
                                    else -> color.copy(alpha = 0.12f)
                                }
                            )
                            .border(
                                if (day == today || mark == DayMark.OFF) 1.dp else 0.dp,
                                if (mark == DayMark.OFF) p.textSecondary.copy(alpha = 0.25f) else if (day == today) color else Color.Transparent,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (mark == DayMark.FROZEN) Text("❄", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
        if (expanded) {
            VSpace(12.dp)
            Text(
                buildString {
                    append(scheduleLabel(h.habit.scheduleMask))
                    h.habit.reminderMinutes?.let { append(" · 🔔 ${formatMinutesOfDay(it)}") }
                    h.habit.focusMinutes?.let { append(" · ⏱ $it мин") }
                    if (h.habit.healthMetric != null) append(" · ❤️ Health Connect")
                },
                style = MaterialTheme.typography.labelMedium, color = p.textSecondary,
            )
            VSpace(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Изменить", onEdit, Modifier.weight(1f).height(46.dp), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.Edit)
                if (h.habit.activity != null) {
                    GlassButton("Прогулка", onWalk, Modifier.weight(1f).height(46.dp), icon = Icons.Rounded.PlayArrow)
                } else if (h.habit.focusMinutes != null && h.scheduledToday) {
                    GlassButton("Таймер", onFocus, Modifier.weight(1f).height(46.dp), icon = Icons.Rounded.PlayArrow)
                }
            }
            if (h.canFreezeYesterday) {
                VSpace(10.dp)
                GlassButton("Заморозить вчера ❄ (1 раз в неделю)", onFreeze, Modifier.fillMaxWidth().height(46.dp), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.AcUnit)
            }
        }
    }
}


