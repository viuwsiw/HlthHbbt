package com.tshk.hlthhbbty.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tshk.hlthhbbty.BuildConfig
import com.tshk.hlthhbbty.ui.glass.BlurBehindWindow
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.LocalHazeState
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.glass.glass
import com.tshk.hlthhbbty.ui.glass.liquidPress
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.showMessage
import kotlin.math.roundToInt

private const val REPO = "viuwsiw/HlthHbbt"
private const val BUG_EMAIL = "yudinanthony@yandex.ru"

/** Маленькая кнопка с божьей коровкой у правого края; тянется вверх-вниз. */
@Composable
fun BoxScope.BugTrackerButton() {
    val density = LocalDensity.current
    var offsetY by rememberSaveable { mutableFloatStateOf(with(density) { 140.dp.toPx() }) }
    var open by rememberSaveable { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .size(width = 38.dp, height = 44.dp)
            .liquidPress(interaction)
            .glass(shape)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, drag ->
                    change.consume()
                    val limit = size.height * 8f
                    offsetY = (offsetY + drag).coerceIn(-limit, limit)
                }
            }
            .clickable(interaction, null) { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Text("🐞", fontSize = 20.sp, modifier = Modifier.padding(start = 2.dp))
    }
    if (open) BugReportDialog(onDismiss = { open = false })
}

@Composable
private fun BugReportDialog(onDismiss: () -> Unit) {
    val p = Liquid.palette
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    var withDevice by rememberSaveable { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BlurBehindWindow()
        androidx.compose.runtime.CompositionLocalProvider(LocalHazeState provides null) {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentPadding = PaddingValues(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🐞", fontSize = 28.sp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("Багтрекер", style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
                        Text("BETA TEST · v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                    }
                }
                VSpace(14.dp)
                GlassTextField(text, { text = it.take(2000) }, "Что случилось?", singleLine = false, imeAction = ImeAction.Default)
                VSpace(10.dp)
                GlassTextField(steps, { steps = it.take(2000) }, "Как повторить (необязательно)", singleLine = false, imeAction = ImeAction.Default)
                VSpace(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { withDevice = !withDevice }) {
                    Checkbox(withDevice, { withDevice = it }, colors = CheckboxDefaults.colors(checkedColor = p.accent))
                    Text("Приложить версию и модель устройства", style = MaterialTheme.typography.bodyMedium, color = p.textPrimary)
                }
                VSpace(14.dp)
                val ready = text.isNotBlank()
                GlassButton("Создать issue на GitHub", {
                    context.openIssue(text, steps, withDevice); onDismiss()
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Code, enabled = ready)
                VSpace(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Отмена", onDismiss, Modifier.weight(1f), style = GlassButtonStyle.Secondary)
                    GlassButton("На почту", {
                        context.mailReport(text, steps, withDevice); onDismiss()
                    }, Modifier.weight(1f), style = GlassButtonStyle.Secondary, icon = Icons.Rounded.MailOutline, enabled = ready)
                }
            }
        }
    }
}

private fun reportBody(text: String, steps: String, withDevice: Boolean): String = buildString {
    appendLine("### Описание")
    appendLine(text.trim())
    if (steps.isNotBlank()) {
        appendLine()
        appendLine("### Как повторить")
        appendLine(steps.trim())
    }
    if (withDevice) {
        appendLine()
        appendLine("### Устройство")
        appendLine("- Версия приложения: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("- Модель: ${Build.MANUFACTURER} ${Build.MODEL}")
    }
}

private fun reportTitle(text: String) = "[Bug] " + text.trim().lineSequence().first().take(70)

private fun Context.openIssue(text: String, steps: String, withDevice: Boolean) {
    val uri = Uri.parse("https://github.com/$REPO/issues/new").buildUpon()
        .appendQueryParameter("title", reportTitle(text))
        .appendQueryParameter("body", reportBody(text, steps, withDevice))
        .appendQueryParameter("labels", "bug")
        .build()
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        showMessage("Багтрекер", "Нет браузера для открытия GitHub")
    }
}

private fun Context.mailReport(text: String, steps: String, withDevice: Boolean) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(BUG_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "HLTH HBTy — " + reportTitle(text))
        putExtra(Intent.EXTRA_TEXT, reportBody(text, steps, withDevice))
    }
    try {
        startActivity(Intent.createChooser(intent, "Отправить отчёт"))
    } catch (_: ActivityNotFoundException) {
        showMessage("Багтрекер", "Не найдено почтовое приложение")
    }
}
