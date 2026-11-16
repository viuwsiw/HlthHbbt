package com.tshk.hlthhbbty.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tshk.hlthhbbty.AppContainer
import com.tshk.hlthhbbty.data.Achievement
import com.tshk.hlthhbbty.data.Achievements
import com.tshk.hlthhbbty.ui.AppViewModelProvider
import com.tshk.hlthhbbty.ui.glass.GlassCard
import com.tshk.hlthhbbty.ui.glass.ScreenHeader
import com.tshk.hlthhbbty.ui.glass.VSpace
import com.tshk.hlthhbbty.ui.theme.Liquid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsViewModel(c: AppContainer) : ViewModel() {
    val achievements: StateFlow<List<Achievement>> = c.auth.currentUserId.filterNotNull()
        .flatMapLatest { c.history.observe(it) }
        .map { Achievements.compute(it).sortedByDescending { a -> a.unlocked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun AchievementsScreen(onBack: () -> Unit, vm: AchievementsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val list by vm.achievements.collectAsStateWithLifecycle()
    val unlocked = list.count { it.unlocked }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            ScreenHeader("Достижения", onBack, subtitle = "Открыто $unlocked из ${list.size}")
        }
        items(list, key = { it.id }) { AchievementTile(it) }
    }
}

@Composable
private fun AchievementTile(a: Achievement) {
    val p = Liquid.palette
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), contentPadding = PaddingValues(16.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (a.unlocked) Brush.linearGradient(listOf(p.accent.copy(alpha = 0.45f), p.accentSecondary.copy(alpha = 0.3f)))
                        else Brush.linearGradient(listOf(p.textSecondary.copy(alpha = 0.12f), p.textSecondary.copy(alpha = 0.06f)))
                    )
                    .graphicsLayer { alpha = if (a.unlocked) 1f else 0.45f },
                contentAlignment = Alignment.Center,
            ) {
                Text(a.emoji, fontSize = 40.sp, modifier = Modifier.padding(14.dp), textAlign = TextAlign.Center)
            }
            VSpace(10.dp)
            Text(a.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, textAlign = TextAlign.Center)
            Text(a.description, style = MaterialTheme.typography.labelSmall, color = p.textSecondary, textAlign = TextAlign.Center, minLines = 2)
            VSpace(10.dp)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(p.accent.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(a.progress).height(6.dp).clip(RoundedCornerShape(50)).background(p.accent))
            }
            VSpace(4.dp)
            Text(
                if (a.unlocked) "Получено ✓" else "${a.current.coerceAtMost(a.target)} / ${a.target}",
                style = MaterialTheme.typography.labelSmall, color = if (a.unlocked) p.accent else p.textSecondary,
            )
        }
    }
}
