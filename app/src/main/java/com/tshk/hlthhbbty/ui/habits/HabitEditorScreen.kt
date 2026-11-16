package com.tshk.hlthhbbty.ui.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.data.db.HabitKind
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.GlassDialog
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.GlassSegmented
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.HabitColors
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.WeekdayShort
import com.tshk.hlthhbbty.util.formatMinutesOfDay

private val GoodEmoji = listOf(
    "💧", "🚶", "🏃", "🤸", "🧘", "🚴", "🏊", "💪",
    "📖", "✍️", "🧠", "🎧", "🌙", "😴", "🍎", "🥗",
    "🦷", "💊", "☀️", "🌿", "🙏", "❤️", "🎨", "🎸",
)
private val QuitEmoji = listOf("🚭", "🍺", "🍬", "🍔", "📱", "🎮", "☕", "🥤", "💸", "🛋️", "😤", "🌃")
private val FocusOptions = listOf(null, 10, 15, 25, 30, 45, 60)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorScreen(
    onBack: () -> Unit,
    vm: HabitEditorViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = Liquid.palette
    var timeDialog by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(s.saved, s.deleted) { if (s.saved || s.deleted) onBack() }

    if (!s.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }
        return
    }
    val d = s.draft
    val quit = d.kind == HabitKind.QUIT
    val color = habitColor(d.colorIndex)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        VSpace(12.dp)
        ScreenHeader(if (s.existing == null) "Новая привычка" else "Привычка", onBack) {
            if (s.existing != null) GlassIconButton(Icons.Rounded.Delete, "Удалить", { confirmDelete = true }, tint = p.danger)
        }
        VSpace(20.dp)

        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitBadge(d.emoji, d.colorIndex, 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.title.ifBlank { if (quit) "Отказаться от…" else "Название привычки" }, style = MaterialTheme.typography.titleMedium, color = if (d.title.isBlank()) p.textSecondary else p.textPrimary)
                    Text(if (quit) "Вредная привычка" else "Полезная привычка", style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
            VSpace(16.dp)
            GlassSegmented(
                listOf("Полезная", "Вредная"),
                if (quit) 1 else 0,
                { i -> vm.edit { it.copy(kind = if (i == 1) HabitKind.QUIT else HabitKind.GOOD, emoji = if (i == 1) QuitEmoji[0] else GoodEmoji[1]) } },
            )
            VSpace(12.dp)
            GlassTextField(d.title, { t -> vm.edit { it.copy(title = t.take(40)) } }, if (quit) "От чего отказываетесь" else "Название")
        }
        VSpace(16.dp)

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Иконка и цвет")
            VSpace(10.dp)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = 8,
            ) {
                (if (quit) QuitEmoji else GoodEmoji).forEach { e ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (e == d.emoji) color.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable { vm.edit { it.copy(emoji = e) } },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 21.sp) }
                }
            }
            VSpace(14.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HabitColors.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (i == d.colorIndex) 3.dp else 0.dp, if (i == d.colorIndex) p.textPrimary else c, CircleShape)
                            .clickable { vm.edit { it.copy(colorIndex = i) } }
                    )
                }
            }
        }

        AnimatedVisibility(!quit) {
            Column {
                VSpace(16.dp)
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Расписание")
                    VSpace(10.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        WeekdayShort.forEachIndexed { i, label ->
                            val on = d.scheduleMask and (1 shl i) != 0
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (on) color else color.copy(alpha = 0.12f))
                                    .clickable {
                                        vm.edit {
                                            val m = it.scheduleMask xor (1 shl i)
                                            it.copy(scheduleMask = if (m == 0) it.scheduleMask else m)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(label, color = if (on) Color.White else p.textPrimary, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                    VSpace(10.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip("Каждый день", selected = d.scheduleMask == HabitKind.EVERY_DAY, onClick = { vm.edit { it.copy(scheduleMask = HabitKind.EVERY_DAY) } })
                        GlassChip("Будни", selected = d.scheduleMask == 0b0011111, onClick = { vm.edit { it.copy(scheduleMask = 0b0011111) } })
                        GlassChip("Выходные", selected = d.scheduleMask == 0b1100000, onClick = { vm.edit { it.copy(scheduleMask = 0b1100000) } })
                    }
                    VSpace(16.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Раз в день", style = MaterialTheme.typography.labelLarge, color = p.textSecondary)
                            Text("${d.goalPerDay}", style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
                        }
                        GlassIconButton(Icons.Rounded.Remove, "Меньше", { vm.edit { it.copy(goalPerDay = (it.goalPerDay - 1).coerceAtLeast(1)) } }, size = 40.dp)
                        Spacer(Modifier.width(10.dp))
                        GlassIconButton(Icons.Rounded.Add, "Больше", { vm.edit { it.copy(goalPerDay = (it.goalPerDay + 1).coerceAtMost(20)) } }, size = 40.dp)
                    }
                }
                VSpace(16.dp)
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Напоминание", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                            Text(
                                d.reminderMinutes?.let { "В ${formatMinutesOfDay(it)} в дни по расписанию" } ?: "Выключено",
                                style = MaterialTheme.typography.labelMedium, color = p.textSecondary,
                            )
                        }
                        Switch(
                            checked = d.reminderMinutes != null,
                            onCheckedChange = { on -> if (on) timeDialog = true else vm.edit { it.copy(reminderMinutes = null) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = p.accent),
                        )
                    }
                    if (d.reminderMinutes != null) {
                        VSpace(10.dp)
                        GlassButton("Изменить время", { timeDialog = true }, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary)
                    }
                }
                VSpace(16.dp)
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Таймер фокуса")
                    Text("Запускайте таймер прямо из привычки — по окончании она отметится сама.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    VSpace(10.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusOptions.forEach { m ->
                            GlassChip(m?.let { "$it мин" } ?: "Нет", selected = d.focusMinutes == m, onClick = { vm.edit { it.copy(focusMinutes = m) } })
                        }
                    }
                }
                VSpace(16.dp)
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Автоотметка из Health Connect")
                    Text("Привычка отметится сама, когда цель будет достигнута.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    VSpace(10.dp)
                    GlassSegmented(
                        listOf("Нет", "Шаги", "Сон"),
                        when (d.healthMetric) { HabitKind.METRIC_STEPS -> 1; HabitKind.METRIC_SLEEP -> 2; else -> 0 },
                        { i ->
                            vm.edit {
                                when (i) {
                                    1 -> it.copy(healthMetric = HabitKind.METRIC_STEPS, healthTarget = 8000)
                                    2 -> it.copy(healthMetric = HabitKind.METRIC_SLEEP, healthTarget = 7 * 60)
                                    else -> it.copy(healthMetric = null, healthTarget = null)
                                }
                            }
                        },
                    )
                    if (d.healthMetric != null) {
                        val steps = d.healthMetric == HabitKind.METRIC_STEPS
                        val target = d.healthTarget ?: 0
                        VSpace(12.dp)
                        Text(
                            if (steps) "Цель: $target шагов" else "Цель: ${target / 60} ч ${target % 60} мин сна",
                            style = MaterialTheme.typography.titleSmall, color = p.textPrimary,
                        )
                        Slider(
                            value = target.toFloat(),
                            onValueChange = { v ->
                                vm.edit { it.copy(healthTarget = if (steps) (v / 500).toInt() * 500 else (v / 15).toInt() * 15) }
                            },
                            valueRange = if (steps) 2000f..20000f else 300f..600f,
                            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = color.copy(alpha = 0.2f)),
                        )
                        Text("Подключите Health Connect в настройках.", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                    }
                }
            }
        }
        VSpace(20.dp)
        GlassButton("Сохранить", vm::save, Modifier.fillMaxWidth(), enabled = d.title.isNotBlank())
        VSpace(32.dp)
    }

    if (timeDialog) {
        val initial = d.reminderMinutes ?: (9 * 60)
        val state = rememberTimePickerState(initial / 60, initial % 60, is24Hour = true)
        GlassDialog(
            onDismiss = { timeDialog = false },
            title = "Время напоминания",
            confirmText = "Готово",
            onConfirm = {
                vm.edit { it.copy(reminderMinutes = state.hour * 60 + state.minute) }
                timeDialog = false
            },
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = p.accent.copy(alpha = 0.12f),
                        selectorColor = p.accent,
                        timeSelectorSelectedContainerColor = p.accent.copy(alpha = 0.25f),
                        timeSelectorUnselectedContainerColor = p.textSecondary.copy(alpha = 0.1f),
                        timeSelectorSelectedContentColor = p.textPrimary,
                        timeSelectorUnselectedContentColor = p.textPrimary,
                        clockDialSelectedContentColor = Color.White,
                        clockDialUnselectedContentColor = p.textPrimary,
                    ),
                )
            }
        }
    }
    if (confirmDelete) {
        GlassDialog(
            onDismiss = { confirmDelete = false },
            title = "Удалить привычку?",
            confirmText = "Удалить",
            confirmStyle = GlassButtonStyle.Danger,
            onConfirm = { confirmDelete = false; vm.delete() },
        ) { Text("Вся история этой привычки будет удалена.", color = p.textSecondary) }
    }
}
