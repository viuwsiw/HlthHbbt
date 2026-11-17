package com.tshk.hlthhbbty.ui.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.UserAvatar
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.glass.appear
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.daysWord
import com.tshk.hlthhbbty.util.memberSince

/** Публичная страница пользователя: видны только поля, разрешённые его настройками конфиденциальности. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserProfileScreen(onBack: () -> Unit, onMessage: (String) -> Unit, vm: UserProfileViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val p = Liquid.palette
    val profile by vm.profile.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Профиль", onBack) }
        val u = profile
        if (u == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (error != null) Text(error!!, color = p.textSecondary) else CircularProgressIndicator(color = p.accent)
                }
            }
            return@LazyColumn
        }
        item {
            GlassCard(Modifier.fillMaxWidth().appear(0), contentPadding = PaddingValues(24.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    UserAvatar(u.avatar.ifBlank { null }, u.displayName, 100.dp)
                    VSpace(12.dp)
                    Text(u.displayName, style = MaterialTheme.typography.headlineSmall, color = p.textPrimary)
                    Text("@${u.username}", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                    if (u.bio.isNotBlank()) {
                        VSpace(8.dp)
                        Text(u.bio, style = MaterialTheme.typography.bodyMedium, color = p.textPrimary, textAlign = TextAlign.Center)
                    }
                    VSpace(10.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip("с ${memberSince(u.createdAt * 1000)}", leading = "📅")
                        if (u.isContact) GlassChip("в контактах", leading = "⭐")
                    }
                    if (!u.isSelf) {
                        VSpace(16.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton("Написать", { onMessage(u.username) }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.Chat, enabled = u.publicKey != null)
                            GlassButton(if (u.isContact) "Убрать" else "В контакты", vm::toggleContact, Modifier.weight(1f),
                                style = GlassButtonStyle.Secondary, icon = if (u.isContact) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd)
                        }
                        if (u.publicKey == null) {
                            VSpace(6.dp)
                            Text("Пользователь ещё не включил шифрование", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                        }
                    }
                }
            }
        }
        val f = u.fields
        if (f.length() == 0) {
            item {
                Text("🔒 Пользователь скрыл статистику привычек", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
            }
        }
        f.optJSONObject("streak")?.let { s ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.appear(1)) {
                    val cur = s.optInt("current")
                    Stat("🔥", "$cur", "${daysWord(cur)} подряд", Modifier.weight(1f))
                    Stat("🏆", "${s.optInt("best")}", "рекорд", Modifier.weight(1f))
                }
            }
        }
        f.optJSONObject("water")?.let { w ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.appear(2)) {
                    Stat("💧", "%.1f л".format(w.optInt("todayMl") / 1000f), "сегодня", Modifier.weight(1f))
                    Stat("📊", "%.1f л".format(w.optInt("avg7Ml") / 1000f), "в среднем", Modifier.weight(1f))
                }
            }
        }
        f.optJSONArray("habits")?.let { arr ->
            item {
                GlassCard(Modifier.fillMaxWidth().appear(3)) {
                    SectionTitle("Привычки")
                    VSpace(10.dp)
                    for (i in 0 until arr.length()) {
                        val h = arr.getJSONObject(i)
                        val week = h.optJSONArray("week")
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(h.optString("emoji"), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(10.dp))
                            Text(h.optString("title"), style = MaterialTheme.typography.bodyMedium, color = p.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                            if (week != null) for (d in 0 until week.length()) {
                                Box(Modifier.padding(start = 3.dp).size(10.dp).clip(CircleShape)
                                    .background(if (week.optBoolean(d)) p.accent else p.textSecondary.copy(alpha = 0.18f)))
                            }
                        }
                    }
                }
            }
        }
        f.optJSONObject("achievements")?.let { a ->
            item {
                GlassCard(Modifier.fillMaxWidth().appear(4)) {
                    SectionTitle("Достижения") { Text("${a.optInt("unlocked")} / ${a.optInt("total")}", color = p.accent, style = MaterialTheme.typography.labelLarge) }
                    VSpace(10.dp)
                    val list = a.optJSONArray("list")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (list != null) for (i in 0 until list.length()) {
                            val it = list.getJSONObject(i)
                            GlassChip(it.optString("title"), leading = it.optString("emoji"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(emoji: String, value: String, label: String, modifier: Modifier) {
    val p = Liquid.palette
    GlassCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
        }
    }
}
