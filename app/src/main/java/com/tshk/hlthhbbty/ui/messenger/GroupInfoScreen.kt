package com.tshk.hlthhbbty.ui.messenger

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassDialog
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.UserAvatar
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid

/** Информация о группе: участники (до 5), переименование, добавление/удаление, выход, вложения. */
@Composable
fun GroupInfoScreen(onBack: () -> Unit, onOpenUser: (String) -> Unit, onLeft: () -> Unit, vm: GroupInfoViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val p = Liquid.palette
    val group by vm.group.collectAsStateWithLifecycle()
    val shared by vm.shared.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    var leaveOpen by rememberSaveable { mutableStateOf(false) }
    val g = group
    val isOwner = g?.owner == vm.me

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(g?.name ?: "Группа", onBack, subtitle = "${g?.members?.size ?: 0} из 5 участников") }
        if (g == null) return@LazyColumn
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Участники", style = MaterialTheme.typography.titleMedium, color = p.textPrimary, modifier = Modifier.weight(1f))
                    if (isOwner && g.members.size < 5) GlassIconButton(Icons.Rounded.PersonAdd, "Добавить", { addOpen = true }, size = 40.dp, tint = p.accent)
                }
                VSpace(8.dp)
                g.members.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(m.avatar.ifBlank { null }, m.displayName.ifBlank { m.username }, 40.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.displayName.ifBlank { m.username } + if (m.username == g.owner) " · создатель" else "",
                                        style = MaterialTheme.typography.bodyMedium, color = p.textPrimary, maxLines = 1)
                                    Text("@${m.username}", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                                }
                            }
                        }
                        if (m.username != vm.me) {
                            GlassButton("Профиль", { onOpenUser(m.username) }, style = GlassButtonStyle.Secondary)
                            if (isOwner) {
                                Spacer(Modifier.width(6.dp))
                                GlassIconButton(Icons.Rounded.PersonRemove, "Удалить", { vm.remove(m.username) }, size = 40.dp, tint = p.danger)
                            }
                        }
                    }
                }
            }
        }
        if (error != null) item { Text(error!!, color = p.danger, style = MaterialTheme.typography.labelMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Переименовать", { renameOpen = true }, Modifier.weight(1f), style = GlassButtonStyle.Secondary)
                GlassButton("Выйти", { leaveOpen = true }, Modifier.weight(1f), style = GlassButtonStyle.Danger)
            }
        }
        item { SharedContent(shared) { vm.mediaFile(it) } }
    }

    if (addOpen) {
        var login by rememberSaveable { mutableStateOf("") }
        GlassDialog(onDismiss = { addOpen = false }, title = "Добавить участника", confirmText = "Добавить",
            confirmEnabled = login.length >= 3, onConfirm = { vm.add(login); addOpen = false }) {
            GlassTextField(login, { login = it.trim().removePrefix("@").lowercase() }, "Логин")
        }
    }
    if (renameOpen) {
        var name by rememberSaveable { mutableStateOf(g?.name.orEmpty()) }
        GlassDialog(onDismiss = { renameOpen = false }, title = "Название группы", confirmText = "Сохранить",
            confirmEnabled = name.isNotBlank(), onConfirm = { vm.rename(name); renameOpen = false }) {
            GlassTextField(name, { name = it.take(40) }, "Название")
        }
    }
    if (leaveOpen) {
        GlassDialog(onDismiss = { leaveOpen = false }, title = "Выйти из группы?", confirmText = "Выйти", confirmStyle = GlassButtonStyle.Danger,
            onConfirm = { leaveOpen = false; vm.leave(onLeft) }) {
            Text("Переписка группы удалится с этого устройства.", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        }
    }
}
