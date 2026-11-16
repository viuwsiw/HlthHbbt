package com.tshk.hlthhbbty.ui.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tshk.hlthhbbty.ui.glass.liquidPress
import com.tshk.hlthhbbty.ui.theme.HabitColors
import com.tshk.hlthhbbty.ui.theme.Liquid

fun habitColor(index: Int): Color = HabitColors[index.mod(HabitColors.size)]

/** Эмодзи привычки в цветной стеклянной «капле». */
@Composable
fun HabitBadge(emoji: String, colorIndex: Int, size: Dp = 46.dp) {
    val color = habitColor(colorIndex)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.36f))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.25f))))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), RoundedCornerShape(size * 0.36f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.48f).sp)
    }
}

/** Круглая кнопка отметки с прогрессом count/goal. */
@Composable
fun HabitCheck(count: Int, goal: Int, colorIndex: Int, onClick: () -> Unit, size: Dp = 40.dp) {
    val p = Liquid.palette
    val color = habitColor(colorIndex)
    val done = count >= goal
    val fill by animateColorAsState(if (done) color else Color.Transparent, label = "fill")
    val scale by animateFloatAsState(
        if (done) 1f else 0.92f,
        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(size)
            .liquidPress(interaction)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, if (done) Color.White.copy(alpha = 0.6f) else color.copy(alpha = 0.8f), CircleShape)
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.35f), 0.55f to Color.Transparent))
            }
            .clickable(interaction, null, role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            done -> Icon(Icons.Rounded.Check, "Выполнено", tint = Color.White, modifier = Modifier.size(size * 0.55f))
            goal > 1 -> Text("$count/$goal", color = p.textPrimary, style = MaterialTheme.typography.labelSmall)
        }
    }
}
