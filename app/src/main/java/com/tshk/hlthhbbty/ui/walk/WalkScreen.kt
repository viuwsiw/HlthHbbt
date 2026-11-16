package com.tshk.hlthhbbty.ui.walk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.data.db.WalkEntity
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassDialog
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.glass.glass
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.openUrl
import com.tshk.hlthhbbty.util.showMessage
import com.tshk.hlthhbbty.walk.googleMapsRoute
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

private fun formatPace(ms: Long, meters: Double): String {
    if (meters < 50) return "—"
    val secPerKm = (ms / 1000.0) / (meters / 1000.0)
    return "%d:%02d".format((secPerKm / 60).toInt(), (secPerKm % 60).toInt())
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru"))

@Composable
fun WalkScreen(onBack: () -> Unit, vm: WalkViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val p = Liquid.palette
    val context = LocalContext.current
    val session by vm.session.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val habit by vm.habit.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val viewing by vm.viewing.collectAsStateWithLifecycle()
    var follow by rememberSaveable { mutableStateOf(true) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session?.paused, session != null) {
        while (session != null && session?.paused == false) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val location = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (location) vm.start()
        else context.showMessage("Прогулка", "Без доступа к геолокации нельзя построить карту прогулки. Разрешите его в настройках приложения.")
    }
    val startWalk: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted && permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) vm.start()
        else launcher.launch(permissions)
    }

    val s = session
    // Что показываем на карте: текущую прогулку, итог или выбранную из истории.
    val shownPoints = s?.points ?: summary?.points ?: viewing?.second ?: emptyList()
    val tracking = s != null

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Прогулка", onBack, subtitle = habit?.let { "${it.emoji} ${it.title}" } ?: "Карта, шаги и секундомер") }

        item {
            Box(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(28.dp))) {
                OsmMap(
                    points = shownPoints,
                    current = s?.current,
                    follow = tracking && follow,
                    lineColor = p.accent,
                    dark = p.isDark,
                    modifier = Modifier.fillMaxSize(),
                )
                if (tracking) {
                    GlassIconButton(
                        Icons.Rounded.MyLocation, "Следовать за мной", { follow = !follow },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                        tint = if (follow) p.accent else p.textSecondary,
                    )
                }
                if (viewing != null || summary != null) {
                    GlassIconButton(
                        Icons.Rounded.Close, "Закрыть", vm::closeView,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )
                }
                Text(
                    "© OpenStreetMap",
                    style = MaterialTheme.typography.labelSmall, color = p.textSecondary,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).glass(RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // Показатели
        item {
            val shownWalk = summary?.walk ?: viewing?.first
            val elapsed = s?.elapsedMs(now) ?: shownWalk?.durationMs ?: 0L
            val meters = s?.distanceM ?: shownWalk?.distanceM ?: 0.0
            val steps = s?.displaySteps ?: shownWalk?.steps ?: 0
            GlassCard(Modifier.fillMaxWidth()) {
                Text(formatDuration(elapsed), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = p.textPrimary,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(
                    when {
                        s == null && summary != null -> "Прогулка сохранена 🎉"
                        s == null && viewing != null -> viewing!!.first.startedAt.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dateFmt) }
                        s == null -> "Готовы к прогулке"
                        s.paused -> "Пауза"
                        s.current == null -> "Ищем спутники GPS…"
                        else -> "Идёт запись маршрута"
                    },
                    style = MaterialTheme.typography.labelLarge, color = p.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                VSpace(16.dp)
                Row(Modifier.fillMaxWidth()) {
                    Metric("📍", "%.2f".format(meters / 1000), "км", Modifier.weight(1f))
                    Metric("👟", "$steps", if (s?.hasStepSensor == false) "шагов ≈" else "шагов", Modifier.weight(1f))
                    Metric("⚡", formatPace(elapsed, meters), "мин/км", Modifier.weight(1f))
                }
                VSpace(18.dp)
                when {
                    s == null -> GlassButton("Начать прогулку", startWalk, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (s.paused) GlassButton("Продолжить", vm::resume, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow)
                        else GlassButton("Пауза", vm::pause, Modifier.weight(1f), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.Pause)
                        GlassButton("Завершить", { confirmFinish = true }, Modifier.weight(1f), icon = Icons.Rounded.Flag)
                    }
                }
                if (s == null && shownPoints.size >= 2) {
                    VSpace(10.dp)
                    GlassButton("Открыть маршрут в Google Картах", { googleMapsRoute(shownPoints)?.let { context.openUrl(it) }; Unit },
                        Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.Map)
                }
                if (tracking) {
                    VSpace(8.dp)
                    Text("Можно заблокировать телефон — запись продолжится, она видна в уведомлении.",
                        style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
            }
        }

        if (history.isNotEmpty()) {
            item { SectionTitle("История прогулок", Modifier.padding(top = 4.dp)) }
            items(history, key = { it.id }) { w -> WalkRow(w, onClick = { vm.view(w) }, onDelete = { vm.delete(w) }) }
        }
    }

    if (confirmFinish) {
        GlassDialog(
            onDismiss = { confirmFinish = false },
            title = "Завершить прогулку?",
            confirmText = "Сохранить",
            dismissText = "Отмена",
            onConfirm = { confirmFinish = false; vm.finish(save = true) },
        ) {
            Text("Маршрут сохранится в истории, а привычка отметится выполненной.", color = p.textSecondary)
            VSpace(12.dp)
            GlassButton("Не сохранять", { confirmFinish = false; vm.finish(save = false) }, Modifier.fillMaxWidth(), style = GlassButtonStyle.Danger)
        }
    }
}

@Composable
private fun Metric(emoji: String, value: String, label: String, modifier: Modifier) {
    val p = Liquid.palette
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, color = p.textPrimary, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
    }
}

@Composable
private fun WalkRow(w: WalkEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val p = Liquid.palette
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🗺️", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Instant.ofEpochMilli(w.startedAt).atZone(ZoneId.systemDefault()).format(dateFmt), style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                Text("%.2f км · %s · %d шагов".format(w.distanceM / 1000, formatDuration(w.durationMs), w.steps),
                    style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
            }
            GlassIconButton(Icons.Rounded.Delete, "Удалить", onDelete, size = 38.dp, tint = p.textSecondary)
        }
    }
}
