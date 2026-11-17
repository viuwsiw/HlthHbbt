package com.tshk.hlthhbbty.ui.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.messenger.LinkState
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.ReportScrollEnd
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.UserAvatar
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.glass.appear
import com.tshk.hlthhbbty.ui.theme.Liquid
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun chatTime(ms: Long): String {
    val dt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return if (dt.toLocalDate() == LocalDate.now()) dt.format(DateTimeFormatter.ofPattern("HH:mm"))
    else dt.format(DateTimeFormatter.ofPattern("d.MM"))
}

@Composable
fun ChatsScreen(
    contentPadding: PaddingValues,
    onOpenChat: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    vm: ChatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val p = Liquid.palette
    val account by vm.account.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val server by vm.serverUrl.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val localUsername by vm.localUsername.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    ReportScrollEnd(listState)

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear(0)) {
                Column(Modifier.weight(1f)) {
                    Text("Чаты", style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (color, label) = when (state) {
                            LinkState.ONLINE -> p.accent to "в сети"
                            LinkState.CONNECTING -> p.textSecondary to "подключение…"
                            LinkState.OFFLINE -> p.danger to "нет связи"
                            LinkState.NOT_LINKED -> p.textSecondary to "не подключено"
                        }
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                        Spacer(Modifier.width(10.dp))
                        androidx.compose.material3.Icon(Icons.Rounded.Lock, null, tint = p.textSecondary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("сквозное шифрование", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                    }
                }
            }
        }

        when {
            server.isBlank() -> item { ServerSetupCard(onSave = vm::setServer) }
            account == null -> item { LinkCard(localUsername, busy, error, onLink = vm::link) }
            else -> {
                item {
                    GlassTextField(query, { vm.query.value = it }, "Поиск по логину", Modifier.appear(1), leadingIcon = Icons.Rounded.Search)
                }
                if (query.isNotBlank()) {
                    if (searching) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) } }
                    else if (results.isEmpty()) item { Hint("Никого не нашли. Логин ищется с начала: @anton") }
                    itemsIndexed(results, key = { _, u -> "u_" + u.username }) { i, u ->
                        PersonRow(u.avatar, u.displayName, "@${u.username}", null, false, Modifier.appear(i)) { onOpenUser(u.username) }
                    }
                } else {
                    if (conversations.isEmpty()) {
                        item { Hint("Пока нет переписок. Найдите друга по логину и откройте его страницу.") }
                    }
                    itemsIndexed(conversations, key = { _, c -> "c_" + c.row.peer }) { i, c ->
                        PersonRow(
                            avatar = "", name = "@" + c.row.peer,
                            subtitle = (if (c.row.lastOutgoing) "Вы: " else "") + c.row.lastText,
                            time = chatTime(c.row.lastAt), unread = c.unread, modifier = Modifier.appear(i + 2),
                        ) { onOpenChat(c.row.peer) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Liquid.palette.textSecondary, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp))
}

@Composable
fun PersonRow(avatar: String, name: String, subtitle: String, time: String?, unread: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = Liquid.palette
    GlassCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(avatar.ifBlank { null }, name.removePrefix("@"), 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (unread) p.textPrimary else p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (time != null) Text(time, style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                if (unread) {
                    VSpace(4.dp)
                    Box(Modifier.size(10.dp).clip(CircleShape).background(p.accent))
                }
            }
        }
    }
}

@Composable
private fun LinkCard(username: String?, busy: Boolean, error: String?, onLink: (String) -> Unit) {
    val p = Liquid.palette
    var password by rememberSaveable { mutableStateOf("") }
    GlassCard(Modifier.fillMaxWidth().appear(1)) {
        Text("💬", fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        VSpace(8.dp)
        Text("Подключите мессенджер", style = MaterialTheme.typography.titleMedium, color = p.textPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
        VSpace(6.dp)
        Text(
            "Ваш профиль @${username ?: "…"} появится на сервере HLTH HBTy. Для подтверждения введите пароль от приложения.",
            style = MaterialTheme.typography.bodySmall, color = p.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        VSpace(14.dp)
        GlassTextField(password, { password = it }, "Пароль", isPassword = true, imeAction = ImeAction.Done, onImeAction = { onLink(password) }, error = error)
        VSpace(12.dp)
        GlassButton("Подключить", { onLink(password) }, Modifier.fillMaxWidth(), loading = busy, enabled = password.length >= 6)
    }
}

@Composable
private fun ServerSetupCard(onSave: (String) -> Unit) {
    val p = Liquid.palette
    var url by rememberSaveable { mutableStateOf("") }
    GlassCard(Modifier.fillMaxWidth().appear(1)) {
        Text("Сервер мессенджера не подключён", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
        VSpace(6.dp)
        Text("Укажите адрес сервера HLTH HBTy (выдаёт разработчик).", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        VSpace(12.dp)
        GlassTextField(url, { url = it.trim() }, "https://…", leadingIcon = Icons.Rounded.Dns, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, onImeAction = { onSave(url) })
        VSpace(12.dp)
        GlassButton("Сохранить", { onSave(url) }, Modifier.fillMaxWidth(), enabled = url.length > 4)
    }
}
