package com.tshk.hlthhbbty.ui.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tshk.hlthhbbty.spotify.SpotifyConfig
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.openUrl

/**
 * Spotify ответил «The user is not registered for this application»: аккаунт, под которым
 * выполнен вход, не добавлен в User Management приложения с нашим Client ID.
 */
@Composable
fun SpotifyNotRegisteredCard(vm: SpotifyViewModel) {
    val p = Liquid.palette
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Spotify не пускает этот аккаунт", style = MaterialTheme.typography.titleMedium, color = p.danger)
        VSpace(6.dp)
        Text(
            "Ответ Spotify: «The user is not registered for this application». Пока приложение в режиме разработки, " +
                "войти могут только аккаунты из списка User Management.",
            style = MaterialTheme.typography.bodySmall, color = p.textPrimary,
        )
        VSpace(10.dp)
        Text("Что проверить:", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
        listOf(
            "Вы вошли тем аккаунтом Spotify, который добавлен. Нажмите «Войти другим аккаунтом» — на странице Spotify будет «Это не вы?».",
            "В User Management указан email именно этого аккаунта (spotify.com → Аккаунт → Email). При входе через Google/Apple почта может отличаться.",
            "Аккаунт добавлен в приложение с Client ID ${SpotifyConfig.clientId.take(8)}…",
        ).forEachIndexed { i, t ->
            Row(Modifier.padding(top = 6.dp)) {
                Text("${i + 1}.  ", style = MaterialTheme.typography.bodySmall, color = p.accent)
                Text(t, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            }
        }
        VSpace(14.dp)
        GlassButton("Войти другим аккаунтом", vm::relogin, Modifier.fillMaxWidth(), icon = Icons.Rounded.SwitchAccount)
        VSpace(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Повторить", vm::retryProfile, Modifier.weight(1f), style = GlassButtonStyle.Secondary)
            GlassButton("Dashboard", { context.openUrl("https://developer.spotify.com/dashboard") }, Modifier.weight(1f),
                style = GlassButtonStyle.Secondary, icon = Icons.AutoMirrored.Rounded.OpenInNew)
        }
    }
}
