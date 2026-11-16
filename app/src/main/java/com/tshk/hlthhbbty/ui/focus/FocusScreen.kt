package com.tshk.hlthhbbty.ui.focus

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.ProgressRing
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.habits.habitColor
import com.tshk.hlthhbbty.ui.theme.Liquid
import kotlinx.coroutines.delay

private val Presets = listOf(5, 10, 15, 20, 25, 30, 45, 60, 90)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(onBack: () -> Unit, onIntegrations: () -> Unit, onPlayer: () -> Unit, vm: FocusViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val p = Liquid.palette
    val habit by vm.habit.collectAsStateWithLifecycle()
    val minutes by vm.minutes.collectAsStateWithLifecycle()
    val timer by vm.timerState.collectAsStateWithLifecycle()
    val color = habit?.let { habitColor(it.colorIndex) } ?: p.accent
    val mine = vm.isMine(timer)
    val t = timer

    // Тикер только для отображения; сам отсчёт ведёт служба.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(t?.running) {
        while (t?.running == true) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 12.dp)) { ScreenHeader("Фокус", onBack, subtitle = habit?.let { "${it.emoji} ${it.title}" }) }
        VSpace(20.dp)

        // Идёт таймер другой привычки
        if (t != null && !mine && !t.finished) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Уже идёт таймер: ${t.emoji} ${t.title}", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                VSpace(10.dp)
                GlassButton("Остановить его", vm::stop, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.Stop)
            }
            VSpace(16.dp)
        }

        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
            val active = mine && t != null
            val remaining = if (active) t!!.remainingMs(now) else minutes * 60_000L
            val total = if (active) t!!.totalMs else minutes * 60_000L
            val sec = (remaining + 999) / 1000
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = if (active && total > 0) 1f - remaining / total.toFloat() else 0f,
                    colors = listOf(color, p.accentSecondary, color),
                    size = 230.dp, stroke = 18.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (active && t!!.finished) "🎉" else "%02d:%02d".format(sec / 60, sec % 60),
                            fontSize = 50.sp, fontWeight = FontWeight.Bold, color = p.textPrimary,
                        )
                        Text(
                            when {
                                !active -> "Настройте время"
                                t!!.finished -> "Готово! Привычка отмечена"
                                t.running -> "Таймер идёт в фоне"
                                else -> "Пауза"
                            },
                            style = MaterialTheme.typography.labelLarge, color = p.textSecondary, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            VSpace(20.dp)
            if (!active) {
                // Гибкая настройка времени
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.Rounded.Remove, "Меньше", { vm.setMinutes(minutes - 5) }, size = 44.dp)
                    Text("$minutes мин", style = MaterialTheme.typography.headlineSmall, color = p.textPrimary,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    GlassIconButton(Icons.Rounded.Add, "Больше", { vm.setMinutes(minutes + 5) }, size = 44.dp)
                }
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { vm.setMinutes(it.toInt()) },
                    valueRange = 1f..120f,
                    colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = color.copy(alpha = 0.2f)),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Presets.forEach { m -> GlassChip("$m", selected = minutes == m, onClick = { vm.setMinutes(m) }) }
                }
                VSpace(20.dp)
                GlassButton("Начать", vm::start, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow, enabled = habit != null && (t == null || t.finished))
            } else if (t!!.finished) {
                GlassButton("Закрыть", { vm.dismissFinished(); onBack() }, Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton("Стоп", vm::stop, Modifier.weight(1f), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.Stop)
                    if (t.running) GlassButton("Пауза", vm::pause, Modifier.weight(1.4f), icon = Icons.Rounded.Pause)
                    else GlassButton("Продолжить", vm::resume, Modifier.weight(1.4f), icon = Icons.Rounded.PlayArrow)
                }
                VSpace(10.dp)
                Text(
                    "Можно свернуть или закрыть приложение — таймер продолжит идти, он виден в уведомлениях.",
                    style = MaterialTheme.typography.labelSmall, color = p.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        VSpace(20.dp)
        com.tshk.hlthhbbty.ui.integrations.MusicSection(onIntegrations, onPlayer, title = "Музыка для концентрации")
        VSpace(24.dp)
        Spacer(Modifier.width(1.dp))
    }
}
