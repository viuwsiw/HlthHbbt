package com.tshk.hlthhbbty.ui.glass

import androidx.compose.animation.animateColorAsState
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

/** Плавающая стеклянная панель навигации с «перетекающей» каплей выбора. */
@Composable
fun GlassBottomBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Liquid.palette
    val shape = RoundedCornerShape(36.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .glass(shape, blur = 34.dp)
            .padding(6.dp)
    ) {
        val slot = maxWidth / items.size
        val offset by animateDpAsState(
            targetValue = slot * selectedIndex,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "drop",
        )
        // Капля выбранного пункта
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
}
