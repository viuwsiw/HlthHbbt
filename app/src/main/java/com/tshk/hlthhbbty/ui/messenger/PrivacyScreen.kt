package com.tshk.hlthhbbty.ui.messenger

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.messenger.Privacy
import com.tshk.hlthhbbty.messenger.Visibility
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassSegmented
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid

/** Конфиденциальность страницы в мессенджере: кто видит какую статистику привычек. */
@Composable
fun PrivacyScreen(onBack: () -> Unit, onPreview: (String) -> Unit, vm: PrivacyViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val p = Liquid.palette
    val privacy by vm.privacy.collectAsStateWithLifecycle()
    val linked by vm.linked.collectAsStateWithLifecycle()
    var bio by rememberSaveable(privacy.bio) { mutableStateOf(privacy.bio) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp),
    ) {
        VSpace(12.dp)
        ScreenHeader("Конфиденциальность", onBack, subtitle = "Ваша страница в мессенджере")
        VSpace(16.dp)
        if (linked == null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Сначала подключите мессенджер во вкладке «Чаты».", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
            }
            return@Column
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Находить меня по логину", style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
                    Text("Если выключить — в поиске вас не будет, написать смогут только по ссылке на логин", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                }
                Switch(privacy.discoverable, { on -> vm.update { it.copy(discoverable = on) } }, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
            }
            VSpace(14.dp)
            GlassTextField(bio, { bio = it.take(160) }, "О себе", singleLine = false, imeAction = ImeAction.Default)
            VSpace(8.dp)
            GlassButton("Сохранить «О себе»", { vm.update { it.copy(bio = bio) } }, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary, enabled = bio != privacy.bio)
        }
        VSpace(14.dp)
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Кто видит статистику")
            Text("«Контакты» — те, кого вы добавили в контакты. Поля «Никто» вообще не отправляются на сервер.",
                style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
            VSpace(12.dp)
            VisRow("🔥 Серии и рекорд", privacy.streak) { v -> vm.update { it.copy(streak = v) } }
            VisRow("🏆 Достижения", privacy.achievements) { v -> vm.update { it.copy(achievements = v) } }
            VisRow("✅ Привычки и неделя", privacy.habits) { v -> vm.update { it.copy(habits = v) } }
            VisRow("💧 Вода", privacy.water) { v -> vm.update { it.copy(water = v) } }
        }
        VSpace(14.dp)
        GlassButton("Посмотреть мою страницу", { linked?.username?.let(onPreview) }, Modifier.fillMaxWidth(), style = GlassButtonStyle.Secondary)
        VSpace(32.dp)
    }
}

@Composable
private fun VisRow(title: String, value: Visibility, onChange: (Visibility) -> Unit) {
    val p = Liquid.palette
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
        VSpace(6.dp)
        GlassSegmented(Visibility.entries.map { it.title }, value.ordinal, { onChange(Visibility.entries[it]) })
    }
}

