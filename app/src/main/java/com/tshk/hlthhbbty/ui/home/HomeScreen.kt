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
import androidx.compose.material.icons.rounded.PlayArrow
import com.tshk.hlthhbbty.ui.habits.QuitCard
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    com.tshk.hlthhbbty.ui.glass.BetaBadge(Modifier.padding(bottom = 6.dp))
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
        }

        item { WaterCard(s.waterMl, user.waterGoalMl, vm::addWater, vm::undoWater) }

        s.health?.let { h -> item { HealthCard(h) } }

        item {
            val done = s.habits.count { it.done }
            GlassCard(Modifier.fillMaxWidth()) {
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
                                    if (h.habit.goalPerDay > 1) {
                                        Text("${h.count} из ${h.habit.goalPerDay} раз", color = p.textSecondary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                if (h.habit.focusMinutes != null && !h.done) {
                                    GlassIconButton(Icons.Rounded.PlayArrow, "Таймер", { onFocus(h.habit) }, size = 38.dp, tint = p.accent)
                                    Spacer(Modifier.width(8.dp))
                                }
                                HabitCheck(h.count, h.habit.goalPerDay, h.habit.colorIndex, { vm.tapHabit(h.habit) })
                            }
                        }
                    }
                }
            }
        }

        items(s.quits, key = { "q" + it.id }) { q -> QuitCard(q, onRelapse = { vm.relapse(q) }) }

        item { MoodCard(s.note, vm::saveNote) }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💡", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(10.dp))
                    Text("Совет дня", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                }
                VSpace(8.dp)
                Text(tip, style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
            }
        }

        item {
            SectionTitle("Музыка для привычек", Modifier.padding(top = 4.dp)) {
                Text(
                    "Все",
                    color = p.accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenIntegrations)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Integrations.playlists) { PlaylistTile(it) }
            }
        }
    }
}

@Composable
fun WaterCard(waterMl: Int, goalMl: Int, onAdd: (Int) -> Unit, onUndo: () -> Unit) {
    val p = Liquid.palette
    val progress = if (goalMl > 0) waterMl / goalMl.toFloat() else 0f
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress, listOf(p.water, p.waterDeep, p.accentSecondary), size = 128.dp, stroke = 13.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.WaterDrop, null, tint = p.water, modifier = Modifier.size(22.dp))
                    Text("${(progress * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.titleLarge, color = p.textPrimary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Вода", style = MaterialTheme.typography.titleMedium, color = p.textSecondary)
                Text(
                    "%.2f л".format(waterMl / 1000f),
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
