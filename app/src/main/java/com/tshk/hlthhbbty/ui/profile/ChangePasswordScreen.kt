package com.tshk.hlthhbbty.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassIconButton
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import com.tshk.hlthhbbty.util.showMessage

@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    vm: ChangePasswordViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = Liquid.palette
    val context = LocalContext.current
    LaunchedEffect(s.done) {
        if (s.done) {
            context.showMessage("Аккаунт", "Пароль изменён")
            onBack()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        VSpace(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onBack)
            Box(Modifier.size(14.dp))
            Text("Смена пароля", style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
        }
        VSpace(20.dp)
        GlassCard(Modifier.fillMaxWidth()) {
            GlassTextField(s.current, vm::onCurrent, "Текущий пароль", leadingIcon = Icons.Rounded.Lock, isPassword = true)
            VSpace(12.dp)
            GlassTextField(s.new, vm::onNew, "Новый пароль", leadingIcon = Icons.Rounded.Password, isPassword = true)
            VSpace(12.dp)
            GlassTextField(
                s.confirm, vm::onConfirm, "Повторите новый пароль",
                leadingIcon = Icons.Rounded.LockReset, isPassword = true,
                imeAction = ImeAction.Done, onImeAction = vm::submit,
            )
            AnimatedVisibility(s.error != null) {
                Text(s.error.orEmpty(), color = p.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            }
            VSpace(8.dp)
            Text("Минимум 6 символов, буквы и цифры.", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
            VSpace(16.dp)
            GlassButton("Сохранить", vm::submit, Modifier.fillMaxWidth(), loading = s.loading)
        }
    }
}
