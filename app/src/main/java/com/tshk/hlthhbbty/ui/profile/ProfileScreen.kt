package com.tshk.hlthhbbty.ui.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.BuildConfig
import com.tshk.hlthhbbty.data.db.Gender
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassChip
import com.tshk.hlthhbbty.ui.glass.GlassDialog
import com.tshk.hlthhbbty.ui.glass.UserAvatar
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.daysWord
import com.tshk.hlthhbbty.util.memberSince
import com.tshk.hlthhbbty.util.sendFeedback
import com.tshk.hlthhbbty.util.shareText
import com.tshk.hlthhbbty.util.yearsWord

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    onEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onIntegrations: () -> Unit,
    onAchievements: () -> Unit,
    onSettings: () -> Unit,
    vm: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = Liquid.palette
    val context = LocalContext.current
    val user = s.user ?: return
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    UserAvatar(user.avatar, user.displayName, 112.dp)
                    VSpace(14.dp)
                    Text(user.displayName, style = MaterialTheme.typography.headlineSmall, color = p.textPrimary, textAlign = TextAlign.Center)
                    Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                    VSpace(14.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        user.age?.let { GlassChip("$it ${yearsWord(it)}", leading = "🎂") }
                        user.gender?.let { GlassChip(if (it == Gender.MALE) "Мужской" else "Женский", leading = if (it == Gender.MALE) "👨" else "👩") }
                        GlassChip("с ${memberSince(user.createdAt)}", leading = "📅")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("🔥", "${s.bestStreak}", "${daysWord(s.bestStreak)} серия", Modifier.weight(1f))
                MiniStat("✅", "${s.doneToday}/${s.plannedToday}", "сегодня", Modifier.weight(1f))
                MiniStat("💧", "%.0f л".format(s.waterAllTimeMl / 1000f), "всего", Modifier.weight(1f))
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                MenuRow(Icons.Rounded.EmojiEvents, "Достижения · ${s.achievementsUnlocked}/${s.achievementsTotal}", onClick = onAchievements)
                MenuDivider()
                MenuRow(Icons.Rounded.Edit, "Редактировать профиль", onClick = onEditProfile)
                MenuDivider()
                MenuRow(Icons.Rounded.Settings, "Настройки", onClick = onSettings)
                MenuDivider()
                MenuRow(Icons.Rounded.Extension, "Интеграции", onClick = onIntegrations)
                MenuDivider()
                MenuRow(Icons.Rounded.Key, "Сменить пароль", onClick = onChangePassword)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                MenuRow(Icons.Rounded.Share, "Поделиться прогрессом") {
                    context.shareText(
                        "Мой прогресс в HLTH HBTy: серия ${s.bestStreak} ${daysWord(s.bestStreak)}, " +
                            "выпито %.1f л воды. Присоединяйся!".format(s.waterAllTimeMl / 1000f)
                    )
                }
                MenuDivider()
                MenuRow(Icons.Rounded.MailOutline, "Написать разработчику") { context.sendFeedback() }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                MenuRow(Icons.AutoMirrored.Rounded.Logout, "Выйти из аккаунта", tint = p.textPrimary, chevron = false) { confirmLogout = true }
                MenuDivider()
                MenuRow(Icons.Rounded.DeleteForever, "Удалить аккаунт", tint = p.danger, chevron = false) { confirmDelete = true }
            }
        }
        item {
            Text(
                "HLTH HBTy ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = p.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmLogout) {
        GlassDialog(
            onDismiss = { confirmLogout = false },
            title = "Выйти из аккаунта?",
            confirmText = "Выйти",
            onConfirm = { confirmLogout = false; vm.logout() },
        ) {
            Text("Ваши данные останутся на устройстве — войдите снова по логину и паролю.", color = p.textSecondary)
        }
    }
    if (confirmDelete) {
        GlassDialog(
            onDismiss = { confirmDelete = false },
            title = "Удалить аккаунт?",
            confirmText = "Удалить",
            confirmStyle = GlassButtonStyle.Danger,
            onConfirm = { confirmDelete = false; vm.deleteAccount() },
        ) {
            Text("Профиль, привычки и вся история будут удалены без возможности восстановления.", color = p.textSecondary)
        }
    }
}

@Composable
private fun MiniStat(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    val p = Liquid.palette
    GlassCard(modifier, shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, color = p.textPrimary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = p.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    tint: Color = Liquid.palette.textPrimary,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    val p = Liquid.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (tint == p.textPrimary) p.accent else tint)
        }
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = tint, modifier = Modifier.weight(1f))
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.textSecondary)
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(Modifier.padding(start = 60.dp, end = 20.dp).height(1.dp), color = Liquid.palette.rimFaint.copy(alpha = 0.6f))
}
