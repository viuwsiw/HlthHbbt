package com.tshk.hlthhbbty.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.data.db.Gender
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassButtonStyle
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.GlassSegmented
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.PresetAvatars
import com.tshk.hlthhbbty.ui.glass.SectionTitle
import com.tshk.hlthhbbty.ui.glass.UserAvatar
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.glass.presetAvatar
import com.tshk.hlthhbbty.ui.theme.Liquid

/**
 * Экран профиля-анкеты. [onboarding] = true — первый вход после регистрации
 * (без кнопки «назад»), иначе — редактирование.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    onboarding: Boolean,
    onDone: () -> Unit,
    vm: EditProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = Liquid.palette
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onPhotoPicked(uri)
    }
    LaunchedEffect(s.saved) { if (s.saved && !onboarding) onDone() }

    if (!s.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        VSpace(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!onboarding) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onDone)
                Box(Modifier.size(14.dp))
            }
            Column {
                Text(
                    if (onboarding) "Расскажите о себе" else "Редактирование",
                    style = MaterialTheme.typography.headlineMedium,
                    color = p.textPrimary,
                )
                if (onboarding) {
                    Text(
                        "Это поможет подобрать цели. Всё можно поменять позже.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textSecondary,
                    )
                }
            }
        }
        VSpace(20.dp)

        GlassCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                UserAvatar(s.avatar, s.name, 120.dp)
                if (s.importing) CircularProgressIndicator(color = p.accent)
            }
            VSpace(16.dp)
            GlassButton(
                "Загрузить фото",
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                style = GlassButtonStyle.Secondary,
                icon = Icons.Rounded.PhotoLibrary,
                modifier = Modifier.fillMaxWidth(),
            )
            VSpace(16.dp)
            Text("или выберите аватар", style = MaterialTheme.typography.labelLarge, color = p.textSecondary)
            VSpace(10.dp)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 6,
            ) {
                PresetAvatars.forEachIndexed { i, res ->
                    val value = presetAvatar(i)
                    val selected = s.avatar == value
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) p.accent else p.rimFaint, CircleShape)
                            .clickable { vm.onPreset(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(res), null, Modifier.fillMaxSize().padding(3.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        if (selected) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).padding(3.dp), contentAlignment = Alignment.BottomEnd) {
                                androidx.compose.material3.Icon(
                                    Icons.Rounded.Check, null, tint = Color.White,
                                    modifier = Modifier.size(16.dp).clip(CircleShape).padding(1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        VSpace(16.dp)

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Основное")
            VSpace(12.dp)
            GlassTextField(s.name, vm::onName, "Имя", leadingIcon = Icons.Rounded.Person, error = s.errors["name"])
            VSpace(12.dp)
            GlassTextField(
                s.age, vm::onAge, "Возраст",
                leadingIcon = Icons.Rounded.Cake, keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done, error = s.errors["age"],
            )
            VSpace(12.dp)
            GlassTextField(s.bio, vm::onBio, "О себе", singleLine = false, imeAction = ImeAction.Default)
            Text("${s.bio.length} / 160 · видно на вашей странице в мессенджере", style = MaterialTheme.typography.labelSmall,
                color = p.textSecondary, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
            VSpace(16.dp)
            Text("Пол", style = MaterialTheme.typography.labelLarge, color = p.textSecondary)
            VSpace(8.dp)
            GlassSegmented(
                options = listOf("Мужской", "Женский"),
                selectedIndex = when (s.gender) { Gender.MALE -> 0; Gender.FEMALE -> 1; null -> -1 },
                onSelect = { vm.onGender(if (it == 0) Gender.MALE else Gender.FEMALE) },
            )
        }
        VSpace(16.dp)

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Цель по воде") {
                Text("${s.waterGoalMl} мл", style = MaterialTheme.typography.titleMedium, color = p.water)
            }
            VSpace(4.dp)
            Text(
                "Рекомендуется около 30 мл на 1 кг веса в день.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary,
            )
            Slider(
                value = s.waterGoalMl.toFloat(),
                onValueChange = { vm.onWaterGoal((it / 250).toInt() * 250) },
                valueRange = 1000f..4000f,
                steps = 11,
                colors = SliderDefaults.colors(
                    thumbColor = p.water,
                    activeTrackColor = p.water,
                    inactiveTrackColor = p.water.copy(alpha = 0.2f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }
        VSpace(20.dp)
        GlassButton(
            if (onboarding) "Готово, поехали!" else "Сохранить",
            onClick = vm::save,
            loading = s.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(32.dp)
    }
}
