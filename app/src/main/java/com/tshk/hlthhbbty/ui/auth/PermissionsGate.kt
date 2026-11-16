package com.tshk.hlthhbbty.ui.auth

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import com.tshk.hlthhbbty.HlthApp
import com.tshk.hlthhbbty.health.HealthStatus
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid

/** Одно разрешение в списке. */
private data class PermItem(
    val key: String,
    val emoji: String,
    val title: String,
    val why: String,
    val required: Boolean,
    val granted: Boolean,
)

private val runtimePermissions: List<String> = buildList {
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
}

private fun Context.has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

private fun collect(context: Context, healthGranted: Boolean): List<PermItem> {
    val c = (context.applicationContext as HlthApp).container
    return buildList {
        add(PermItem("notif", "🔔", "Уведомления", "Напоминания, таймер фокуса, сообщения",
            true, Build.VERSION.SDK_INT < 33 || context.has(Manifest.permission.POST_NOTIFICATIONS)))
        add(PermItem("location", "📍", "Геолокация", "Карта и маршрут прогулок", true, context.has(Manifest.permission.ACCESS_FINE_LOCATION)))
        add(PermItem("activity", "👟", "Физическая активность", "Шаги во время прогулки",
            true, Build.VERSION.SDK_INT < 29 || context.has(Manifest.permission.ACTIVITY_RECOGNITION)))
        add(PermItem("media", "🎛️", "Доступ к уведомлениям", "Плеер для Spotify, Яндекс Музыки и других", true, c.media.checkAccess()))
        add(PermItem("usage", "🌙", "История использования", "Автоотметка «Лечь спать до…»", true, c.bedtime.hasAccess()))
        add(PermItem("install", "⬇️", "Установка обновлений", "Автообновление приложения из GitHub", true, c.updates.canInstall()))
        if (c.health.status() == HealthStatus.AVAILABLE) {
            add(PermItem("health", "❤️", "Health Connect", "Шаги и сон на главном экране", false, healthGranted))
        }
    }
}

/**
 * Обязательный шаг перед регистрацией/входом: все нужные приложению разрешения.
 * [onAllGranted] сообщает, выданы ли все обязательные.
 */
@Composable
fun PermissionsGate(onAllGranted: (Boolean) -> Unit) {
    val p = Liquid.palette
    val context = LocalContext.current
    val c = (context.applicationContext as HlthApp).container
    var tick by remember { mutableIntStateOf(0) }
    var healthGranted by remember { mutableStateOf(false) }
    var chain by remember { mutableStateOf(false) }
    var lastAsked by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val items = remember(tick, healthGranted) { collect(context, healthGranted) }
    val required = items.filter { it.required }
    val done = required.count { it.granted }
    androidx.compose.runtime.LaunchedEffect(done, required.size) { onAllGranted(done == required.size) }

    fun openSettings(key: String) {
        val intent = when (key) {
            "media" -> c.media.accessSettingsIntent()
            "usage" -> c.bedtime.accessIntent()
            "install" -> c.updates.unknownSourcesSettings().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        tick++
        // Цепочку продолжит LifecycleResumeEffect: после системного диалога экран снова в ON_RESUME.
        // Отказали «навсегда» — системный диалог больше не покажется, ведём в настройки приложения.
        val activity = context.findActivity()
        val blocked = result.filterValues { !it }.keys.any { perm ->
            activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }
        if (blocked) openSettings("app")
    }
    val healthLauncher = rememberLauncherForActivityResult(c.health.permissionContract()) { granted ->
        healthGranted = granted.containsAll(c.health.permissions)
        if (healthGranted) scope.launch { c.settings.update { it.copy(healthConnect = true) } }
    }

    fun request(item: PermItem) {
        when (item.key) {
            "notif", "location", "activity" -> runtimeLauncher.launch(runtimePermissions.filter { !context.has(it) }.toTypedArray())
            "health" -> healthLauncher.launch(c.health.permissions)
            else -> openSettings(item.key)
        }
    }

    /** «Выдать все»: по очереди открываем следующее невыданное (продолжается при возврате). */
    fun next() {
        val missing = collect(context, healthGranted).firstOrNull { it.required && !it.granted }
        // Нечего выдавать или пользователь отказал в том же пункте — останавливаем цепочку.
        if (missing == null || missing.key == lastAsked) { chain = false; lastAsked = null; return }
        lastAsked = missing.key
        request(missing)
    }

    LifecycleResumeEffect(Unit) {
        tick++
        scope.launch { healthGranted = c.health.hasPermissions() }
        if (chain) next()
        onPauseOrDispose {}
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Разрешения", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                Text("Без них регистрация и вход недоступны", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
            }
            GlassChip("$done / ${required.size}", leading = if (done == required.size) "✅" else "🔒")
        }
        VSpace(10.dp)
        // Прогресс
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(p.accent.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(if (required.isEmpty()) 1f else done / required.size.toFloat()).height(6.dp)
                .clip(RoundedCornerShape(50)).background(p.accent))
        }
        VSpace(12.dp)
        items.forEach { item -> PermRow(item) { request(item) } }
        if (done < required.size) {
            VSpace(14.dp)
            GlassButton("Выдать все по очереди", { chain = true; lastAsked = null; next() }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PermRow(item: PermItem, onGrant: () -> Unit) {
    val p = Liquid.palette
    val dot by animateColorAsState(if (item.granted) p.accent else Color.Transparent, label = "dot")
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(item.emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title + if (!item.required) " · по желанию" else "", style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
            Text(item.why, style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
        }
        if (item.granted) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(dot), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Выдано", tint = p.onAccent, modifier = Modifier.size(18.dp))
            }
        } else {
            GlassButton("Выдать", onGrant, Modifier.height(40.dp), style = if (item.required) GlassButtonStyle.Primary else GlassButtonStyle.Secondary)
        }
    }
}

