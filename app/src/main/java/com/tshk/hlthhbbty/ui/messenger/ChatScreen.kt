package com.tshk.hlthhbbty.ui.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.data.db.ChatMessageEntity
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassDialog
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.GlassSurface
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.UserAvatar
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.glass.glass
import com.tshk.hlthhbbty.ui.theme.Liquid

@Composable
fun ChatScreen(onBack: () -> Unit, onOpenUser: (String) -> Unit, vm: ChatViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val p = Liquid.palette
    val messages by vm.messages.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val safety by vm.safety.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val presenceMap by vm.presence.collectAsStateWithLifecycle()
    val presence = presenceMap[vm.peer]
    MessengerPresence(vm.m)
    var text by rememberSaveable { mutableStateOf("") }
    var showSecurity by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Шапка
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onBack)
            Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onOpenUser(vm.peer) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    UserAvatar(profile?.avatar?.ifBlank { null }, profile?.displayName ?: vm.peer, 40.dp)
                    StatusDot(presence?.status, 12.dp, Modifier.align(Alignment.BottomEnd))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(profile?.displayName ?: "@${vm.peer}", style = MaterialTheme.typography.titleMedium, color = p.textPrimary, maxLines = 1)
                    Text(statusLabel(presence), style = MaterialTheme.typography.labelSmall, color = statusColor(presence?.status), maxLines = 1)
                }
            }
            GlassIconButton(Icons.Rounded.Lock, "Шифрование", { showSecurity = true }, tint = p.accent)
        }
        // Сообщения
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    "🔒 Сообщения защищены сквозным шифрованием. Прочитать их можете только вы и @${vm.peer}.",
                    style = MaterialTheme.typography.labelSmall, color = p.textSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(8.dp).glass(RoundedCornerShape(14.dp)).padding(10.dp),
                )
            }
            items(messages, key = { it.clientId }) { m -> Bubble(m) }
        }
        if (error != null) {
            Text(error!!, color = p.danger, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        // Ввод
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(text, { text = it.take(4000) }, "Сообщение", Modifier.weight(1f), singleLine = false, imeAction = ImeAction.Default)
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.AutoMirrored.Rounded.Send, "Отправить", {
                if (text.isNotBlank()) { vm.send(text); text = "" }
            }, size = 52.dp, tint = p.accent)
        }
    }

    if (showSecurity) {
        GlassDialog(onDismiss = { showSecurity = false }, title = "Сквозное шифрование", confirmText = "Понятно", onConfirm = { showSecurity = false }) {
            Text(
                "Сообщения шифруются на вашем телефоне ключом собеседника (X25519 + AES-256-GCM). Сервер хранит только шифротекст и не может его прочитать.",
                style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
            )
            VSpace(12.dp)
            Text("Код безопасности", style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
            Text(safety ?: "—", style = MaterialTheme.typography.headlineSmall, color = p.accent)
            Text("Сравните код с собеседником при встрече: если совпадает — ключи не подменены.", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
        }
    }
}

@Composable
private fun Bubble(m: ChatMessageEntity) {
    val p = Liquid.palette
    val shape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp,
        bottomStart = if (m.outgoing) 20.dp else 6.dp, bottomEnd = if (m.outgoing) 6.dp else 20.dp,
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .then(
                    if (m.outgoing) Modifier.clip(shape).background(Brush.linearGradient(listOf(p.accent, p.accentSecondary)))
                    else Modifier.glass(shape)
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(m.text, style = MaterialTheme.typography.bodyMedium, color = if (m.outgoing) p.onAccent else if (m.readable) p.textPrimary else p.textSecondary)
            Text(
                chatTime(m.createdAt) + if (m.outgoing) (if (m.serverId > 0) "  ✓" else "  ⏳") else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (m.outgoing) p.onAccent.copy(alpha = 0.75f) else p.textSecondary,
            )
        }
    }
}
