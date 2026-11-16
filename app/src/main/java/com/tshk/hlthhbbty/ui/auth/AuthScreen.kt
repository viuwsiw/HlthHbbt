package com.tshk.hlthhbbty.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.R
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassButton
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.GlassSegmented
import com.tshk.hlthhbbty.ui.glass.GlassSurface
import com.tshk.hlthhbbty.ui.glass.GlassTextField
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid

@Composable
fun AuthScreen(vm: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    val p = Liquid.palette
    val focus = LocalFocusManager.current
    val submit = { focus.clearFocus(); vm.submit() }

    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VSpace(40.dp)
            GlassSurface(Modifier.size(96.dp), shape = RoundedCornerShape(30.dp)) {
                Image(
                    painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(64.dp).clip(RoundedCornerShape(18.dp)),
                )
            }
            VSpace(20.dp)
            Text("HLTH HBTy", style = MaterialTheme.typography.displaySmall, color = p.textPrimary)
            VSpace(6.dp)
            Text(
                "Полезные привычки — каждый день",
                style = MaterialTheme.typography.bodyLarge,
                color = p.textSecondary,
                textAlign = TextAlign.Center,
            )
            VSpace(28.dp)
            GlassCard(Modifier.fillMaxWidth().animateContentSize()) {
                GlassSegmented(
                    options = listOf("Вход", "Регистрация"),
                    selectedIndex = if (s.isRegister) 1 else 0,
                    onSelect = { vm.setMode(it == 1) },
                )
                VSpace(20.dp)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedVisibility(s.isRegister) {
                        GlassTextField(
                            s.name, vm::onName, "Как к вам обращаться",
                            leadingIcon = Icons.Rounded.Person, error = s.fieldErrors["name"],
                        )
                    }
                    GlassTextField(
                        s.username, vm::onUsername, "Логин",
                        leadingIcon = Icons.Rounded.AlternateEmail, error = s.fieldErrors["username"],
                    )
                    GlassTextField(
                        s.password, vm::onPassword, "Пароль",
                        leadingIcon = Icons.Rounded.Lock, isPassword = true, error = s.fieldErrors["password"],
                        imeAction = if (s.isRegister) ImeAction.Next else ImeAction.Done,
                        onImeAction = { if (!s.isRegister) submit() else focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                    )
                    AnimatedVisibility(s.isRegister) {
                        GlassTextField(
                            s.confirm, vm::onConfirm, "Повторите пароль",
                            leadingIcon = Icons.Rounded.LockReset, isPassword = true, error = s.fieldErrors["confirm"],
                            imeAction = ImeAction.Done, onImeAction = submit,
                        )
                    }
                }
                AnimatedVisibility(s.error != null) {
                    Text(
                        s.error.orEmpty(),
                        color = p.danger,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 14.dp, start = 4.dp),
                    )
                }
                VSpace(20.dp)
                GlassButton(
                    text = if (s.isRegister) "Создать аккаунт" else "Войти",
                    onClick = submit,
                    loading = s.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            VSpace(16.dp)
            Text(
                "Данные хранятся только на этом устройстве. Пароль хранится в виде защищённого хеша.",
                style = MaterialTheme.typography.labelMedium,
                color = p.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            VSpace(40.dp)
        }
    }
}
