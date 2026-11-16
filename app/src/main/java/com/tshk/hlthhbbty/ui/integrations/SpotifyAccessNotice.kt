package com.tshk.hlthhbbty.ui.integrations

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tshk.hlthhbbty.BuildConfig
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.showMessage

private const val DEV_EMAIL = "yudinanthony@yandex.ru"

/**
 * Плашка-уведомление: пока приложение в режиме разработки Spotify, доступ выдаётся вручную.
 * Пользователь вводит email своего Spotify-аккаунта, имя берётся из профиля приложения.
 */
@Composable
fun SpotifyAccessNotice(vm: SpotifyViewModel, modifier: Modifier = Modifier) {
    val p = Liquid.palette
    val context = LocalContext.current
    val name by vm.profileName.collectAsStateWithLifecycle()
    val requested by vm.requestedEmail.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf(requested.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(requested == null) }
    val noticeShape = RoundedCornerShape(18.dp)

    GlassCard(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(p.accent.copy(alpha = 0.14f), noticeShape)
                .border(1.dp, p.accent.copy(alpha = 0.45f), noticeShape)
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("ℹ️", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Text(
                "Авторизация в Spotify временно доступна через согласование с разработчиком. Для этого направьте нам:",
                style = MaterialTheme.typography.bodyMedium, color = p.textPrimary,
            )
        }
        VSpace(14.dp)
        if (!editing && requested != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✅", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Заявка отправлена", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
                    Text("$requested · как только вас добавят, нажмите «Войти через Spotify».", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
            VSpace(10.dp)
            GlassButton("Изменить email", { editing = true }, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary)
            return@GlassCard
        }
        // Имя — из профиля, только для чтения
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(Icons.Rounded.Person, null, tint = p.textSecondary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Имя (из профиля)", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                Text(name.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
            }
        }
        VSpace(12.dp)
        GlassTextField(
            value = email,
            onValueChange = { email = it.trim(); error = null },
            label = "email (как в аккаунте Spotify)",
            leadingIcon = Icons.Rounded.AlternateEmail,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Send,
            error = error,
        )
        VSpace(14.dp)
        GlassButton("Отправить заявку", {
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                error = "Введите корректный email"
            } else if (context.sendAccessRequest(name, email)) {
                vm.markAccessRequested(email)
                editing = false
            }
        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Send)
    }
}

private fun Context.sendAccessRequest(name: String, email: String): Boolean {
    val body = buildString {
        appendLine("Здравствуйте! Прошу добавить мой аккаунт Spotify в HLTH HBTy.")
        appendLine()
        appendLine("Имя: ${name.ifBlank { "—" }}")
        appendLine("Email Spotify: $email")
        appendLine()
        appendLine("Версия приложения: ${BuildConfig.VERSION_NAME}")
    }
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(DEV_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "HLTH HBTy — доступ к Spotify: $email")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try {
        startActivity(Intent.createChooser(intent, "Отправить заявку").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        showMessage("Spotify", "Не найдено почтовое приложение. Напишите на $DEV_EMAIL: имя и email вашего аккаунта Spotify.")
        false
    }
}
