package com.tshk.hlthhbbty.ui.glass

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.tshk.hlthhbbty.ui.theme.Liquid

/**
 * Диалог в стиле стекла. Диалог — отдельное окно, поэтому вместо Haze
 * используется системное размытие заднего плана (Android 12+).
 */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String = "Отмена",
    confirmStyle: GlassButtonStyle = GlassButtonStyle.Primary,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BlurBehindWindow()
        CompositionLocalProvider(LocalHazeState provides null) {
            GlassCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Liquid.palette.textPrimary)
                VSpace(12.dp)
                content()
                VSpace(20.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(dismissText, onDismiss, Modifier.weight(1f), style = GlassButtonStyle.Secondary)
                    GlassButton(confirmText, onConfirm, Modifier.weight(1f), style = confirmStyle, enabled = confirmEnabled)
                }
            }
        }
    }
}

@Composable
fun BlurBehindWindow(radius: Int = 48) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        window.setDimAmount(0.25f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radius }
        }
    }
}

@Composable
fun GlassDialogColumn(content: @Composable ColumnScope.() -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
