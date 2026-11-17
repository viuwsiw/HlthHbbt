package com.tshk.hlthhbbty.ui.settings

import android.Manifest
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.graphics.graphicsLayer
import com.tshk.hlthhbbty.util.sendFeedback
import com.tshk.hlthhbbty.util.shareText
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
import androidx.compose.foundation.layout.width
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
import com.tshk.hlthhbbty.util.showMessage
import java.time.LocalDate

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
        message?.let { context.showMessage("Настройки", it); vm.consumeMessage() }
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
        // Открыта только одна группа — экран не перегружен.
        var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
        val toggle: (String) -> Unit = { key -> open = if (open == key) null else key }

        SettingsGroup("🎨", "Оформление", "Тема, акцент, фон", open == "look", { toggle("look") }) {
            GlassSegmented(ThemeMode.entries.map { it.title }, s.themeMode.ordinal, { vm.setTheme(ThemeMode.entries[it]) })
            VSpace(16.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Живой фон", style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
                    Text("Плавно движущиеся пятна. Нагружает слабые телефоны", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
                Switch(s.animatedBackground, vm::setAnimatedBackground, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
            }
            VSpace(16.dp)
            Text("Акцентный цвет", style = MaterialTheme.typography.labelLarge, color = p.textSecondary)
            VSpace(10.dp)
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccentOptions.forEachIndexed { i, a ->
                    val (c1, c2) = if (p.isDark) a.dark else a.light
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                        // Кольцо выбора с зазором — видно даже на чёрном образце в тёмной теме.
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(
                                    if (i == s.accentIndex) 2.5.dp else 1.dp,
                                    if (i == s.accentIndex) p.textPrimary else p.textSecondary.copy(alpha = 0.25f),
                                    CircleShape,
                                )
                                .clickable { vm.setAccent(i) }
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(c1, c2)))
                        )
                        Text(a.title, style = MaterialTheme.typography.labelSmall, color = p.textSecondary, maxLines = 1)
                    }
                }
            }
        }
        VSpace(12.dp)
        SettingsGroup("🔔", "Уведомления", if (s.waterReminders) "Вода: каждые ${s.waterIntervalHours} ч" else "Напоминания и вода", open == "notif", { toggle("notif") }) {
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
        VSpace(12.dp)
        SettingsGroup("❤️", "Health Connect", if (s.healthConnect && healthGranted) "Подключено" else "Шаги и сон", open == "health", { toggle("health") }) {
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
        VSpace(12.dp)
        SettingsGroup("💾", "Данные", "Экспорт и импорт", open == "data", { toggle("data") }) {
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
        VSpace(12.dp)
        val upd by vm.updater.state.collectAsStateWithLifecycle()
        SettingsGroup("⬇️", "Обновления", "v${com.tshk.hlthhbbty.BuildConfig.VERSION_NAME} · " + if (s.betaChannel) "бета-канал" else "релизы",
            open == "updates", { toggle("updates") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Проверять автоматически", style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
                    Text("При запуске и раз в сутки, из GitHub", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
                Switch(s.autoUpdate, vm::setAutoUpdate, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
            }
            VSpace(12.dp)
            val status = when (val u = upd) {
                com.tshk.hlthhbbty.update.UpdateState.Checking -> "Проверяем…"
                com.tshk.hlthhbbty.update.UpdateState.UpToDate -> "У вас последняя версия ✓"
                is com.tshk.hlthhbbty.update.UpdateState.Available -> "Доступна версия ${u.info.versionName}"
                is com.tshk.hlthhbbty.update.UpdateState.Downloading -> "Загрузка ${(u.progress * 100).toInt()}%"
                is com.tshk.hlthhbbty.update.UpdateState.Ready -> "Готово к установке"
                is com.tshk.hlthhbbty.update.UpdateState.Failed -> "Ошибка: ${u.message}"
                else -> null
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (upd is com.tshk.hlthhbbty.update.UpdateState.Failed) p.danger else p.textPrimary)
                VSpace(10.dp)
            }
            when (val u = upd) {
                is com.tshk.hlthhbbty.update.UpdateState.Available -> GlassButton("Обновить до ${u.info.versionName}", { vm.updater.download(u.info) }, Modifier.fillMaxWidth())
                else -> GlassButton("Проверить обновления", { vm.updater.check() }, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary,
                    loading = upd is com.tshk.hlthhbbty.update.UpdateState.Checking)
            }
        }
        VSpace(12.dp)
        SettingsGroup("🧪", "Бета-тестирование", if (s.betaChannel) "Вы бета-тестер" else "Ранний доступ к новым версиям", open == "beta", { toggle("beta") }) {
            Text(
                "Бета-версии выходят раньше релизов и приходят тем же автообновлением из GitHub. " +
                    "В них могут быть ошибки — сообщайте о них кнопкой 🐞.",
                style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
            )
            VSpace(12.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Бета-канал", style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
                    Text(if (s.betaChannel) "Получаете беты и релизы — что новее" else "Сейчас: только релизы", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
                Switch(s.betaChannel, vm::setBetaChannel, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
            }
            if (s.betaChannel) {
                VSpace(10.dp)
                Text("Если вернуться на релизы, бета останется установленной, пока не выйдет релиз новее.",
                    style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
            }
        }
        VSpace(12.dp)
        SettingsGroup("ℹ️", "О приложении", "Что нового, обучение, связь", open == "about", { toggle("about") }) {
            ChangelogContent()
            VSpace(14.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🐞 Кнопка багтрекера", style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
                    Text("Сбоку экрана, для отчёта об ошибке", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
                Switch(s.bugButton, vm::setBugButton, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
            }
            VSpace(12.dp)
            GlassButton("Показать обучение ещё раз", vm::replayOnboarding, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary)
            VSpace(10.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Написать нам", { context.sendFeedback() }, Modifier.weight(1f), style = GlassButtonStyle.Secondary)
                GlassButton("Поделиться", { context.shareText("HLTH HBTy — трекер полезных привычек: https://github.com/viuwsiw/HlthHbbt") },
                    Modifier.weight(1f), style = GlassButtonStyle.Secondary)
            }
        }
        VSpace(32.dp)
    }
}

/** Раскрывающаяся группа настроек: заголовок с иконкой и кратким статусом. */
@Composable
private fun SettingsGroup(
    emoji: String,
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val p = Liquid.palette
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
    GlassCard(Modifier.fillMaxWidth().animateContentSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                Text(summary, style = MaterialTheme.typography.labelMedium, color = p.textSecondary, maxLines = 1)
            }
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown, null, tint = p.textSecondary,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), content = content)
        }
    }
}

@Composable
private fun ChangelogContent() {
    val p = Liquid.palette
    var showAll by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val entries = com.tshk.hlthhbbty.data.Changelog.entries
    Column {
        Text("Что нового", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
        VSpace(10.dp)
        (if (showAll) entries else entries.take(1)).forEachIndexed { i, e ->
            if (i > 0) VSpace(14.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.version, style = MaterialTheme.typography.titleSmall, color = if (i == 0) p.accent else p.textPrimary)
                Text("  ·  ${e.title}", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
            }
            VSpace(6.dp)
            e.items.forEach { item ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium, color = p.accent)
                    Text(item, style = MaterialTheme.typography.bodyMedium, color = p.textPrimary)
                }
            }
        }
        VSpace(12.dp)
        GlassButton(
            if (showAll) "Свернуть" else "Все версии",
            { showAll = !showAll }, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary,
        )
    }
}

