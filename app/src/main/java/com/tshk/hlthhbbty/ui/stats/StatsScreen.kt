package com.tshk.hlthhbbty.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.habits.HabitBadge
import com.tshk.hlthhbbty.ui.habits.habitColor
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.shortWeekday

@Composable
fun StatsScreen(
    contentPadding: PaddingValues,
    vm: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = Liquid.palette

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Статистика", style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
                Text("Последние 7 и 30 дней", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Средне воды", "%.1f л".format(s.waterWeek.average() / 1000), "в день", Modifier.weight(1f))
                StatTile("Идеальных дней", "${s.perfectDays7}", "из 7", Modifier.weight(1f))
                StatTile("Всего воды", "%.0f л".format(s.waterAllTimeMl / 1000f), "за всё время", Modifier.weight(1f))
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Вода за неделю") {
                    Text("цель ${s.waterGoal} мл", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                }
                VSpace(16.dp)
                BarChart(
                    values = s.waterWeek.map { it.toFloat() },
                    goal = s.waterGoal.toFloat(),
                    labels = (6 downTo 0).map { shortWeekday(s.day - it) },
                    colors = listOf(p.water, p.waterDeep),
                )
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Выполнение привычек")
                VSpace(16.dp)
                BarChart(
                    values = s.completionWeek.map { it * 100 },
                    goal = 100f,
                    labels = (6 downTo 0).map { shortWeekday(s.day - it) },
                    colors = listOf(p.accent, p.accentSecondary),
                    max = 100f,
                )
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Год привычек")
                VSpace(4.dp)
                Text("Чем ярче клетка — тем больше привычек выполнено", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                VSpace(12.dp)
                Heatmap(s.heatmap, s.heatmapStart)
            }
        }
        item { MoodStats(s) }
        if (s.habitStats.isNotEmpty()) {
            item { SectionTitle("По привычкам за 30 дней", Modifier.padding(top = 4.dp)) }
            items(s.habitStats, key = { it.habit.id }) { st ->
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HabitBadge(st.habit.emoji, st.habit.colorIndex, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(st.habit.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Серия: ${st.streak} · рекорд: ${st.best}", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                        }
                        Text("${(st.rate30 * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = habitColor(st.habit.colorIndex))
                    }
                    VSpace(10.dp)
                    val color = habitColor(st.habit.colorIndex)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color.copy(alpha = 0.15f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(st.rate30.coerceIn(0f, 1f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    val p = Liquid.palette
    GlassCard(modifier, shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        VSpace(4.dp)
        Text(value, style = MaterialTheme.typography.titleLarge, color = p.textPrimary, maxLines = 1)
        Text(hint, style = MaterialTheme.typography.labelSmall, color = p.textSecondary, maxLines = 1)
    }
}

@Composable
private fun BarChart(
    values: List<Float>,
    goal: Float,
    labels: List<String>,
    colors: List<Color>,
    max: Float? = null,
) {
    val p = Liquid.palette
    val top = max ?: (maxOf(values.maxOrNull() ?: 0f, goal) * 1.15f).coerceAtLeast(1f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values) {
        anim.snapTo(0f)
        anim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = values.size
            val slot = size.width / n
            val barW = slot * 0.5f
            val goalY = size.height * (1 - goal / top)
            drawLine(
                color = p.textSecondary.copy(alpha = 0.5f),
                start = Offset(0f, goalY), end = Offset(size.width, goalY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
            values.forEachIndexed { i, v ->
                val h = (v / top).coerceIn(0f, 1f) * size.height * anim.value
                val x = slot * i + (slot - barW) / 2
                // «стеклянная» подложка столбца
                drawRoundRect(
                    color = if (p.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(x, 0f), size = Size(barW, size.height),
                    cornerRadius = CornerRadius(barW / 2),
                )
                if (h > 0f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(colors, startY = size.height - h, endY = size.height),
                        topLeft = Offset(x, size.height - h), size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2),
                    )
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), startX = x, endX = x + barW * 0.6f),
                        topLeft = Offset(x, size.height - h), size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2),
                    )
                }
            }
        }
        VSpace(8.dp)
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = p.textSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}


@Composable
private fun Heatmap(values: List<Float?>, start: Long) {
    val p = Liquid.palette
    val scroll = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(values.size) { scroll.scrollTo(scroll.maxValue) }
    val weeks = (values.size + 6) / 7
    Row(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(end = 6.dp)) {
            com.tshk.hlthhbbty.util.WeekdayShort.forEachIndexed { i, d ->
                Box(Modifier.height(13.dp), contentAlignment = Alignment.CenterStart) {
                    Text(if (i % 2 == 0) d else "", style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)), color = p.textSecondary)
                }
            }
        }
        Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (w in 0 until weeks) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (d in 0 until 7) {
                        val idx = w * 7 + d
                        val v = values.getOrNull(idx)
                        val exists = idx < values.size
                        Box(
                            Modifier
                                .size(13.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        !exists -> Color.Transparent
                                        v == null || v == 0f -> p.textSecondary.copy(alpha = 0.10f)
                                        else -> p.accent.copy(alpha = 0.25f + 0.75f * v)
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodStats(s: StatsState) {
    val p = Liquid.palette
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle("Настроение за неделю")
        VSpace(12.dp)
        Row(Modifier.fillMaxWidth()) {
            s.moodWeek.forEachIndexed { i, m ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (m > 0) com.tshk.hlthhbbty.data.repo.MoodEmoji[m - 1] else "·", style = MaterialTheme.typography.titleLarge, color = p.textSecondary)
                    Text(shortWeekday(s.day - (6 - i)), style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
            }
        }
        if (s.moodGood != null && s.moodOther != null) {
            VSpace(12.dp)
            val diff = s.moodGood - s.moodOther
            Text(
                if (diff > 0.05f) "В дни, когда вы выполняете привычки, настроение выше на %.1f балла 💪".format(diff)
                else "Связь настроения и привычек пока не видна — продолжайте отмечать.",
                style = MaterialTheme.typography.bodyMedium, color = p.textPrimary,
            )
        } else {
            VSpace(8.dp)
            Text("Отмечайте настроение на главном экране — здесь появится аналитика.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        }
    }
}
