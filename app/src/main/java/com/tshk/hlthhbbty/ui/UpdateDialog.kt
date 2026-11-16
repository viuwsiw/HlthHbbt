package com.tshk.hlthhbbty.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tshk.hlthhbbty.BuildConfig
import com.tshk.hlthhbbty.HlthApp
import com.tshk.hlthhbbty.update.UpdateState
import com.tshk.hlthhbbty.ui.glass.GlassDialog
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Проверка при запуске и стеклянное окно обновления поверх всего приложения. */
@Composable
fun UpdateHost() {
    val context = LocalContext.current
    val c = (context.applicationContext as HlthApp).container
    val updater = c.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val dismissed by updater.dismissed.collectAsStateWithLifecycle()
    val p = Liquid.palette

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        scope.launch { if (c.settings.settings.first().autoUpdate) updater.checkIfStale() }
        onPauseOrDispose {}
    }

    val install: (java.io.File) -> Unit = { file ->
        if (!c.updates.canInstall()) {
            Toast.makeText(context, "Разрешите установку из этого источника и нажмите «Установить» ещё раз", Toast.LENGTH_LONG).show()
            context.startActivity(c.updates.unknownSourcesSettings().addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            try {
                context.startActivity(c.updates.installIntent(file))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Не удалось открыть установщик", Toast.LENGTH_SHORT).show()
            }
        }
    }

    when (val s = state) {
        is UpdateState.Available -> if (dismissed != s.info.versionName) {
            GlassDialog(
                onDismiss = updater::dismiss,
                title = "Доступна версия ${s.info.versionName}",
                confirmText = "Обновить",
                dismissText = "Позже",
                onConfirm = { updater.download(s.info) },
            ) {
                Text("У вас ${BuildConfig.VERSION_NAME}." + (s.info.sizeBytes?.let { " Размер: %.1f МБ.".format(it / 1_048_576f) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                if (s.info.changelog.isNotBlank()) {
                    VSpace(10.dp)
                    Text("Что нового", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                    VSpace(4.dp)
                    Text(
                        s.info.changelog,
                        style = MaterialTheme.typography.bodyMedium, color = p.textPrimary,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
        is UpdateState.Downloading -> if (dismissed != s.info.versionName) {
            GlassDialog(
                onDismiss = updater::dismiss,
                title = "Загрузка ${s.info.versionName}",
                confirmText = "${(s.progress * 100).toInt()}%",
                confirmEnabled = false,
                onConfirm = {},
            ) {
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(p.accent.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(s.progress).height(10.dp).clip(RoundedCornerShape(50)).background(p.accent))
                }
            }
        }
        is UpdateState.Ready -> if (dismissed != s.info.versionName) {
            LaunchedEffect(s.file) { install(s.file) }
            GlassDialog(
                onDismiss = updater::dismiss,
                title = "Обновление скачано",
                confirmText = "Установить",
                onConfirm = { install(s.file) },
            ) { Text("Версия ${s.info.versionName} готова к установке. Данные сохранятся.", color = p.textSecondary) }
        }
        else -> Unit
    }
}
