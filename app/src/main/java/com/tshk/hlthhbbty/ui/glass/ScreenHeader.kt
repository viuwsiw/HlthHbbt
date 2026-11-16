package com.tshk.hlthhbbty.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tshk.hlthhbbty.ui.theme.Liquid

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    val p = Liquid.palette
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onBack)
            Box(Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
        }
        action()
    }
}
