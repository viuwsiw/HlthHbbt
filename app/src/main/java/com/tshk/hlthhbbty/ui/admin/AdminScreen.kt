package com.tshk.hlthhbbty.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.BuildConfig
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassDialog
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.update.UpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class AdminViewModel(private val c: AppContainer) : ViewModel() {
    private val api = c.messenger.api
    private val _stats = MutableStateFlow<JSONObject?>(null)
    val stats: StateFlow<JSONObject?> = _stats.asStateFlow()
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    private val _channels = MutableStateFlow<Map<String, UpdateInfo?>>(emptyMap())
    val channels: StateFlow<Map<String, UpdateInfo?>> = _channels.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _wiped = MutableStateFlow(false)
    val wiped: StateFlow<Boolean> = _wiped.asStateFlow()

    init {
        viewModelScope.launch { _channels.value = c.updates.channels() }
        viewModelScope.launch {
            while (true) { refresh(); delay(5_000) } // живой мониторинг, пока панель открыта
        }
    }

    suspend fun refresh() {
        runCatching { api.adminStats() }.onSuccess { _stats.value = it; _error.value = null }.onFailure { _error.value = it.message }
        runCatching { api.adminLogs(150) }.onSuccess { _logs.value = it.reversed() }
    }

    fun refreshNow() = viewModelScope.launch { refresh(); _channels.value = c.updates.channels() }

    fun wipe() = viewModelScope.launch {
        runCatching { api.adminWipe() }.onSuccess { _wiped.value = true; refresh() }.onFailure { _error.value = it.message }
    }
}

private fun bytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f ГБ".format(b / (1L shl 30).toFloat())
    b >= 1L shl 20 -> "%.1f МБ".format(b / (1L shl 20).toFloat())
    b >= 1L shl 10 -> "%.0f КБ".format(b / 1024f)
    else -> "$b Б"
}

private fun uptime(sec: Long): String {
    val d = sec / 86400; val h = (sec / 3600) % 24; val m = (sec / 60) % 60
    return if (d > 0) "${d} д ${h} ч" else if (h > 0) "${h} ч ${m} мин" else "${m} мин"
}

/** Админ-панель сервера (только для @developer). */
@Composable
fun AdminScreen(onBack: () -> Unit, vm: AdminViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val p = Liquid.palette
    val s by vm.stats.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val wiped by vm.wiped.collectAsStateWithLifecycle()
    var wipeStep by rememberSaveable { mutableStateOf(0) }
    var confirmText by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("Админ-панель", onBack, subtitle = "Сервер HLTH HBTy · обновляется каждые 5 с") {
                GlassIconButton(Icons.Rounded.Refresh, "Обновить", vm::refreshNow)
            }
        }
        error?.let { item { Text(it, color = p.danger, style = MaterialTheme.typography.bodyMedium) } }
        if (wiped) item { Text("✅ Все данные сервера сброшены", color = p.accent, style = MaterialTheme.typography.titleSmall) }
        val st = s
        if (st != null) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Сервер") { Text("🟢 работает", color = p.accent, style = MaterialTheme.typography.labelLarge) }
                    VSpace(8.dp)
                    KV("Аптайм", uptime(st.optLong("uptimeSec")))
                    KV("Онлайн сейчас", "${st.optInt("online")}")
                    KV("Сессий", "${st.optInt("sessions")}")
                    KV("Go", st.optString("goVersion"))
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Данные")
                    VSpace(8.dp)
                    KV("Пользователей", "${st.optInt("users")} (+${st.optInt("usersToday")} за сутки)")
                    KV("Сообщений", "${st.optLong("messages")} (+${st.optInt("messagesToday")} за сутки)")
                    KV("Файлов", "${st.optInt("mediaFiles")} · ${bytes(st.optLong("mediaBytes"))}")
                    KV("Облачных копий", "${st.optInt("backups")}")
                    KV("База данных", bytes(st.optLong("dbBytes")))
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Ресурсы")
                    VSpace(8.dp)
                    val load = st.optJSONArray("load")
                    val cpus = st.optInt("cpus").coerceAtLeast(1)
                    val l1 = load?.optDouble(0) ?: 0.0
                    Bar("Процессор (нагрузка ${"%.2f".format(l1)} / $cpus ядер)", (l1 / cpus).toFloat())
                    val total = st.optLong("sysMemTotal"); val avail = st.optLong("sysMemAvail")
                    Bar("Память ${bytes(total - avail)} из ${bytes(total)}", if (total > 0) (total - avail) / total.toFloat() else 0f)
                    val dt = st.optLong("diskTotal"); val df = st.optLong("diskFree")
                    Bar("Диск ${bytes(dt - df)} из ${bytes(dt)}", if (dt > 0) (dt - df) / dt.toFloat() else 0f)
                    KV("Процесс сервера", "${bytes(st.optLong("procMemBytes"))} · ${st.optInt("goroutines")} горутин")
                }
            }
        } else if (error == null) {
            item { Text("Загрузка…", color = p.textSecondary) }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Обновления приложения")
                VSpace(8.dp)
                KV("Установлено здесь", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                KV("Релиз (update.json)", channels["release"]?.let { "${it.versionName} (${it.versionCode})" } ?: "—")
                KV("Бета (update-beta.json)", channels["beta"]?.let { "${it.versionName} (${it.versionCode})" } ?: "—")
                KV("GitHub Release", channels["githubRelease"]?.versionName ?: "нет")
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Логи") { Text("${logs.size}", color = p.textSecondary, style = MaterialTheme.typography.labelMedium) }
                VSpace(8.dp)
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp))
                        .background(p.textSecondary.copy(alpha = 0.08f)).verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()).padding(10.dp),
                ) {
                    if (logs.isEmpty()) Text("Пусто", color = p.textSecondary, fontSize = 11.sp)
                    logs.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = p.textPrimary, softWrap = false) }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Экстренно")
                VSpace(6.dp)
                Text("Удаляет ВСЕ аккаунты (кроме администратора), сообщения, файлы, контакты и облачные копии. Отменить нельзя.",
                    style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                VSpace(12.dp)
                GlassButton("Сбросить все данные сервера", { wipeStep = 1 }, Modifier.fillMaxWidth(), style = GlassButtonStyle.Danger, icon = Icons.Rounded.Warning)
            }
        }
        item { Box(Modifier.height(24.dp)) }
    }

    if (wipeStep == 1) {
        GlassDialog(
            onDismiss = { wipeStep = 0; confirmText = "" },
            title = "Сбросить ВСЁ?",
            confirmText = "Сбросить",
            confirmStyle = GlassButtonStyle.Danger,
            confirmEnabled = confirmText == "СБРОСИТЬ ВСЁ",
            onConfirm = { vm.wipe(); wipeStep = 0; confirmText = "" },
        ) {
            Text("Чтобы подтвердить, напишите: СБРОСИТЬ ВСЁ", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
            VSpace(10.dp)
            GlassTextField(confirmText, { confirmText = it }, "Подтверждение")
        }
    }
}

@Composable
private fun KV(k: String, v: String) {
    val p = Liquid.palette
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, modifier = Modifier.weight(1f))
        Text(v, style = MaterialTheme.typography.bodyMedium, color = p.textPrimary)
    }
}

@Composable
private fun Bar(label: String, frac: Float) {
    val p = Liquid.palette
    val f = frac.coerceIn(0f, 1f)
    val color = when { f > 0.85f -> p.danger; f > 0.6f -> androidx.compose.ui.graphics.Color(0xFFF59E0B); else -> p.accent }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.textPrimary)
        VSpace(4.dp)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(f).height(8.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}
