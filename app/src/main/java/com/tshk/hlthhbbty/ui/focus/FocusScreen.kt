package com.tshk.hlthhbbty.ui.focus

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.data.Integrations
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.ProgressRing
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.habits.habitColor
import com.tshk.hlthhbbty.ui.home.PlaylistTile
import com.tshk.hlthhbbty.ui.theme.Liquid

@Composable
fun FocusScreen(onBack: () -> Unit, vm: FocusViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = Liquid.palette
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(s.running) {
        view.keepScreenOn = s.running
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(s.finished) {
        if (s.finished) {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        }
    }
    val habit = s.habit
    val color = habit?.let { habitColor(it.colorIndex) } ?: p.accent
    val sec = (s.remainingMs + 999) / 1000

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 12.dp)) { ScreenHeader("Фокус", onBack, subtitle = habit?.let { "${it.emoji} ${it.title}" }) }
        VSpace(28.dp)
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(28.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = if (s.totalMs > 0) 1f - s.remainingMs / s.totalMs.toFloat() else 0f,
                    colors = listOf(color, p.accentSecondary, color),
                    size = 240.dp, stroke = 18.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (s.finished) "🎉" else "%02d:%02d".format(sec / 60, sec % 60),
                            fontSize = 52.sp, fontWeight = FontWeight.Bold, color = p.textPrimary,
                        )
                        Text(
                            when {
                                s.finished -> "Готово! Привычка отмечена"
                                s.running -> "Сосредоточьтесь"
                                else -> "Пауза"
                            },
                            style = MaterialTheme.typography.labelLarge, color = p.textSecondary, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            VSpace(24.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton("Сброс", vm::reset, Modifier.weight(1f), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.Replay)
                when {
                    s.finished -> GlassButton("Закрыть", onBack, Modifier.weight(1.4f))
                    s.running -> GlassButton("Пауза", vm::pause, Modifier.weight(1.4f), icon = Icons.Rounded.Pause)
                    else -> GlassButton("Старт", vm::start, Modifier.weight(1.4f), icon = Icons.Rounded.PlayArrow)
                }
            }
        }
        VSpace(20.dp)
        SectionTitle("Музыка для концентрации")
        VSpace(10.dp)
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Integrations.playlists) { PlaylistTile(it) }
        }
    }
}
