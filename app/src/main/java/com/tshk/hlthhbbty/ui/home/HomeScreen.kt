package com.tshk.hlthhbbty.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.PlayArrow
import com.tshk.hlthhbbty.ui.habits.QuitCard
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.R
import com.tshk.hlthhbbty.data.Integrations
import com.tshk.hlthhbbty.data.Playlist
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.GlassSurface
import com.tshk.hlthhbbty.ui.glass.ProgressRing
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.UserAvatar
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.glass.appear
import com.tshk.hlthhbbty.ui.habits.HabitBadge
import com.tshk.hlthhbbty.ui.habits.HabitCheck
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.greeting
import com.tshk.hlthhbbty.util.openUrl

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenProfile: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onFocus: (com.tshk.hlthhbbty.data.db.HabitEntity) -> Unit,
    onWalk: (com.tshk.hlthhbbty.data.db.HabitEntity) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = Liquid.palette
    val user = s.user ?: return
    val tips = stringArrayResource(R.array.tips)
    val tip = tips[(s.day % tips.size).toInt()]
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refreshHealth()
        onPauseOrDispose {}
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    com.tshk.hlthhbbty.ui.glass.ReportScrollEnd(listState)
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Приветствие + совет дня мелким шрифтом
        item {
            Column(Modifier.appear(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting() + ",", style = MaterialTheme.typography.titleMedium, color = p.textSecondary)
                    Text(
                        user.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = p.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassSurface(shape = androidx.compose.foundation.shape.CircleShape, onClick = onOpenProfile) {
                    UserAvatar(user.avatar, user.displayName, 54.dp)
                }
            }
            VSpace(6.dp)
            Text("💡 $tip", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
            }
        }

        // Главное: вода, шаги, сон
        item { Box(Modifier.appear(1)) { WaterCard(s.waterMl, user.waterGoalMl, vm::addWater, vm::undoWater) } }
        item {
            Box(Modifier.appear(2)) {
            val h = s.health
            if (h != null) HealthCard(h)
            else GlassCard(Modifier.fillMaxWidth(), onClick = onOpenSettings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👟😴", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Шаги и сон", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                        Text("Подключите Health Connect в настройках", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.textSecondary)
                }
            }
            }
        }

        // Раскрывашка с остальным
        item {
            val done = s.habits.count { it.done }
            val rotation by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
            GlassCard(Modifier.fillMaxWidth().appear(3), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), onClick = { expanded = !expanded }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (expanded) "Свернуть" else "Привычки, настроение, музыка", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                        Text("Привычки сегодня: $done из ${s.habits.size}", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                    }
                    Icon(
                        Icons.Rounded.KeyboardArrowDown, null, tint = p.accent,
                        modifier = Modifier.graphicsLayer { rotationZ = rotation },
                    )
                }
            }
        }

        if (expanded) {
            item {
                val done = s.habits.count { it.done }
                GlassCard(Modifier.fillMaxWidth().appear(0)) {
                    SectionTitle("Привычки на сегодня") {
                        Text("$done из ${s.habits.size}", style = MaterialTheme.typography.labelLarge, color = p.accent)
                    }
                    VSpace(12.dp)
                    if (s.habits.isEmpty()) {
                        Text("На сегодня привычек нет.", color = p.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        VSpace(12.dp)
                        GlassButton("Добавить привычку", onOpenHabits, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.Add)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            s.habits.forEach { h ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HabitBadge(h.habit.emoji, h.habit.colorIndex, 42.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(h.habit.title, color = p.textPrimary, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        when {
                                            h.habit.activity != null && h.done -> Text("Выполнено ✓", color = p.accent, style = MaterialTheme.typography.labelMedium)
                                            h.habit.activity != null -> Text("Карта, шаги и секундомер", color = p.textSecondary, style = MaterialTheme.typography.labelMedium)
                                            h.habit.healthMetric == "BEDTIME" -> Text("Отметится сама по телефону", color = p.textSecondary, style = MaterialTheme.typography.labelMedium)
                                            h.habit.focusMinutes != null && h.done -> Text("Выполнено ✓", color = p.accent, style = MaterialTheme.typography.labelMedium)
                                            h.habit.focusMinutes != null -> Text("Таймер фокуса", color = p.textSecondary, style = MaterialTheme.typography.labelMedium)
                                            h.habit.goalPerDay > 1 -> Text("${h.count} из ${h.habit.goalPerDay} раз", color = p.textSecondary, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                    if (h.habit.activity != null) {
                                        GlassChip(if (h.done) "Ещё" else "Начать", leading = "🗺", onClick = { onWalk(h.habit) })
                                    } else if (h.habit.healthMetric == "BEDTIME") {
                                        // Автоотметка: только показываем состояние
                                        HabitCheck(h.count, h.habit.goalPerDay, h.habit.colorIndex, {})
                                    } else if (h.habit.focusMinutes != null) {
                                        // Привычки с таймером — только кнопка, ведущая в «Фокус»
                                        GlassChip(if (h.done) "Ещё раз" else "Начать", leading = "⏱", onClick = { onFocus(h.habit) })
                                    } else {
                                        HabitCheck(h.count, h.habit.goalPerDay, h.habit.colorIndex, { vm.tapHabit(h.habit) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            items(s.quits, key = { "q" + it.id }) { q -> Box(Modifier.appear(1)) { QuitCard(q, onRelapse = { vm.relapse(q) }) } }
            item { Box(Modifier.appear(2)) { MoodCard(s.note, vm::saveNote) } }
            item { Box(Modifier.appear(3)) { com.tshk.hlthhbbty.ui.integrations.MusicSection(onOpenIntegrations, onOpenPlayer) } }
        }
    }
}

@Composable
fun WaterCard(waterMl: Int, goalMl: Int, onAdd: (Int) -> Unit, onUndo: () -> Unit) {
    val p = Liquid.palette
    val progress = if (goalMl > 0) waterMl / goalMl.toFloat() else 0f
    val shownMl by androidx.compose.animation.core.animateIntAsState(waterMl, androidx.compose.animation.core.tween(700), label = "ml")
    val bounce = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    var lastMl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(waterMl) }
    androidx.compose.runtime.LaunchedEffect(waterMl) {
        if (waterMl > lastMl) {
            bounce.snapTo(0.9f)
            bounce.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 400f))
        }
        lastMl = waterMl
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress, listOf(p.water, p.waterDeep, p.accentSecondary), size = 128.dp, stroke = 13.dp,
                modifier = Modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.WaterDrop, null, tint = p.water, modifier = Modifier.size(22.dp))
                    Text("${(progress * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.titleLarge, color = p.textPrimary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Вода", style = MaterialTheme.typography.titleMedium, color = p.textSecondary)
                Text(
                    "%.2f л".format(shownMl / 1000f),
                    style = MaterialTheme.typography.headlineMedium,
                    color = p.textPrimary,
                )
                Text(
                    if (waterMl >= goalMl) "Цель выполнена 🎉" else "из %.1f л · осталось %d мл".format(goalMl / 1000f, goalMl - waterMl),
                    style = MaterialTheme.typography.labelMedium,
                    color = p.textSecondary,
                )
            }
        }
        VSpace(16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(150, 250, 500).forEach { ml ->
                GlassChip("+$ml", leading = "💧", onClick = { onAdd(ml) })
            }
            Spacer(Modifier.weight(1f))
            if (waterMl > 0) GlassIconButton(Icons.AutoMirrored.Rounded.Undo, "Отменить", onUndo, size = 40.dp, tint = p.textSecondary)
        }
    }
}

@Composable
fun PlaylistTile(playlist: Playlist, modifier: Modifier = Modifier) {
    val p = Liquid.palette
    val context = LocalContext.current
    GlassSurface(modifier.width(160.dp), shape = RoundedCornerShape(24.dp), onClick = { context.openUrl(playlist.url) }) {
        Column(Modifier.padding(10.dp)) {
            Image(
                painterResource(playlist.cover),
                null,
                Modifier
                    .fillMaxWidth()
                    .size(140.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )
            VSpace(8.dp)
            Text(playlist.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playlist.service.title, style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
        }
    }
}
