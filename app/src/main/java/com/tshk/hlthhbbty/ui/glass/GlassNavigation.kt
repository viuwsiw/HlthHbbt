package com.tshk.hlthhbbty.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tshk.hlthhbbty.ui.theme.Liquid

data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Плавающая стеклянная панель навигации с «перетекающей» каплей выбора.
 * Если передан [player] (что-то играет), над вкладками появляется ручка: свайп вверх
 * раскрывает панель, и под разделителем показывается мини-плеер; свайп вниз — сворачивает.
 */
@Composable
fun GlassBottomBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    player: (@Composable () -> Unit)? = null,
) {
    val p = Liquid.palette
    val shape = RoundedCornerShape(36.dp)
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (player == null && expanded) expanded = false
    val canExpand = player != null

    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
            .glass(shape, blur = 34.dp)
            .pointerInput(canExpand) {
                if (!canExpand) return@pointerInput
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -40f) expanded = true
                        else if (total > 40f) expanded = false
                    },
                ) { change, dy -> change.consume(); total += dy }
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        if (canExpand) {
            // Ручка-подсказка: что-то играет, можно потянуть вверх
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(remember { MutableInteractionSource() }, null) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(50)).background(p.textSecondary.copy(alpha = 0.45f)))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(60.dp)) {
            val slot = maxWidth / items.size
            val offset by animateDpAsState(
                targetValue = slot * selectedIndex,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                label = "drop",
            )
            Box(
                Modifier
                    .offset(x = offset)
                    .width(slot)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(30.dp))
                    .background(p.accent.copy(alpha = if (p.isDark) 0.28f else 0.22f))
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (p.isDark) 0.12f else 0.5f), 0.6f to Color.Transparent))
                    }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                items.forEachIndexed { i, item ->
                    val selected = i == selectedIndex
                    val color by animateColorAsState(if (selected) p.accent else p.textSecondary, label = "c")
                    Column(
                        Modifier
                            .width(slot)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(30.dp))
                            .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(if (selected) item.selectedIcon else item.icon, item.label, tint = color, modifier = Modifier.size(24.dp))
                        Text(item.label, color = color, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (expanded && player != null) {
            androidx.compose.material3.HorizontalDivider(
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                color = p.textSecondary.copy(alpha = 0.25f),
            )
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) { player() }
        }
    }
}
