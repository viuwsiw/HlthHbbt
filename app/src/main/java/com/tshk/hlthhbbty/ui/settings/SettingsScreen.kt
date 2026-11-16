package com.tshk.hlthhbbty.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.data.ThemeMode
import com.tshk.hlthhbbty.health.HealthStatus
import com.tshk.hlthhbbty.reminders.Notifications
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.GlassSegmented
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.AccentOptions
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.openUrl
import java.time.LocalDate

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val healthGranted by vm.healthGranted.collectAsStateWithLifecycle()
    val p = Liquid.palette
    val context = LocalContext.current
    var notifAllowed by remember { mutableStateOf(Notifications.canPost(context)) }

    LifecycleResumeEffect(Unit) {
        notifAllowed = Notifications.canPost(context)
        vm.refreshHealth()
        onPauseOrDispose {}
    }
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.consumeMessage() }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifAllowed = it }
    val healthLauncher = rememberLauncherForActivityResult(vm.healthContract()) { vm.onHealthResult(it) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
    ) {
        VSpace(12.dp)
        ScreenHeader("Настройки", onBack)
        VSpace(20.dp)

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Оформление")
            VSpace(12.dp)
            GlassSegmented(ThemeMode.entries.map { it.title }, s.themeMode.ordinal, { vm.setTheme(ThemeMode.entries[it]) })
            VSpace(16.dp)
            Text("Акцентный цвет", style = MaterialTheme.typography.labelLarge, color = p.textSecondary)
            VSpace(10.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AccentOptions.forEachIndexed { i, a ->
                    val (c1, c2) = if (p.isDark) a.dark else a.light
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(c1, c2)))
                                .border(if (i == s.accentIndex) 3.dp else 0.dp, if (i == s.accentIndex) p.textPrimary else c1, CircleShape)
                                .clickable { vm.setAccent(i) }
                        )
                        Text(a.title, style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                    }
                }
            }
        }
        VSpace(16.dp)

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Уведомления")
            if (!notifAllowed) {
                VSpace(8.dp)
                Text("Уведомления выключены — напоминания не будут приходить.", style = MaterialTheme.typography.bodySmall, color = p.danger)
                VSpace(10.dp)
                GlassButton("Разрешить уведомления", {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Notifications)
            }
            VSpace(12.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("💧 Напоминать пить воду", style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
                    Text("Только пока цель не выполнена", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
                Switch(s.waterReminders, { on ->
                    vm.setWaterReminders(on)
                    if (on && !notifAllowed && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
            }
            if (s.waterReminders) {
                VSpace(12.dp)
                Text("Каждые", style = MaterialTheme.typography.labelLarge, color = p.textSecondary)
                VSpace(6.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 4).forEach { h -> GlassChip("$h ч", selected = s.waterIntervalHours == h, onClick = { vm.setWaterInterval(h) }) }
                }
                VSpace(12.dp)
                Text("С ${s.waterFromHour}:00 до ${s.waterToHour}:00", style = MaterialTheme.typography.labelLarge, color = p.textSecondary)
                RangeSlider(
                    value = s.waterFromHour.toFloat()..s.waterToHour.toFloat(),
                    onValueChange = { r -> vm.setWaterHours(r.start.toInt(), r.endInclusive.toInt().coerceAtLeast(r.start.toInt() + 1)) },
                    valueRange = 5f..24f,
                    steps = 18,
                    colors = SliderDefaults.colors(thumbColor = p.water, activeTrackColor = p.water, inactiveTrackColor = p.water.copy(alpha = 0.2f)),
                )
            }
            VSpace(8.dp)
            Text("Напоминания для отдельных привычек настраиваются в самой привычке.", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
        }
        VSpace(16.dp)

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Health Connect")
            VSpace(6.dp)
            Text(
                "Шаги и сон подтягиваются автоматически, а привычки с автоцелью отмечаются сами.",
                style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
            )
            VSpace(12.dp)
            when {
                vm.healthStatus == HealthStatus.UNAVAILABLE -> Text("Health Connect недоступен на этом устройстве.", color = p.textSecondary, style = MaterialTheme.typography.bodyMedium)
                vm.healthStatus == HealthStatus.NEEDS_UPDATE -> GlassButton("Установить Health Connect", {
                    context.openUrl("market://details?id=com.google.android.apps.healthdata")
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Favorite)
                s.healthConnect && healthGranted -> {
                    GlassChip("Подключено", leading = "✅")
                    VSpace(10.dp)
                    GlassButton("Отключить", vm::disconnectHealth, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary)
                }
                else -> GlassButton("Подключить", { healthLauncher.launch(vm.healthPermissions) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Favorite)
            }
        }
        VSpace(16.dp)

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Данные")
            VSpace(6.dp)
            Text("Сохраните прогресс в файл, чтобы перенести его на новый телефон.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            VSpace(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Экспорт", { exportLauncher.launch("hlth-hbty-${LocalDate.now()}.json") }, Modifier.weight(1f), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.Upload)
                GlassButton("Импорт", { importLauncher.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.Download)
            }
            VSpace(8.dp)
            Text("Импорт заменит текущие привычки, воду и заметки.", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
        }
        VSpace(32.dp)
    }
}

