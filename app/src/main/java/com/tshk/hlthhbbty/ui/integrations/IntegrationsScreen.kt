package com.tshk.hlthhbbty.ui.integrations

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tshk.hlthhbbty.data.Integrations
import com.tshk.hlthhbbty.data.MusicService
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.isAppInstalled
import com.tshk.hlthhbbty.util.openUrl

@Composable
fun IntegrationsScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val p = Liquid.palette
    val context = LocalContext.current
    val installed = remember { MusicService.entries.associateWith { context.isAppInstalled(it.packageName) } }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onBack)
                Box(Modifier.size(14.dp))
                Column {
                    Text("Интеграции", style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
                    Text("Музыка под ваши привычки", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                }
            }
        }
        MusicService.entries.forEach { service ->
            item(key = service.name) {
                SectionTitle(service.title, Modifier.padding(top = 8.dp)) {
                    GlassChip(if (installed[service] == true) "Установлено" else "Откроется в браузере", leading = if (installed[service] == true) "✅" else "🌐")
                }
            }
            items(Integrations.playlists.filter { it.service == service }, key = { it.url }) { pl ->
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), onClick = { context.openUrl(pl.url) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painterResource(pl.cover), null,
                            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pl.title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                            Text(pl.subtitle, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        }
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Открыть", tint = p.accent)
                    }
                }
            }
        }
        item {
            SectionTitle("Здоровье", Modifier.padding(top = 8.dp))
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), onClick = onSettings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("❤️", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Health Connect", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                        Text("Шаги и сон, автоотметка привычек. Подключение — в настройках.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                }
            }
        }
    }
}
