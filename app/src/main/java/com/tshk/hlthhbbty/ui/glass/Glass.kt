package com.tshk.hlthhbbty.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tshk.hlthhbbty.ui.theme.Liquid
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeInputScale

val GlassShape = RoundedCornerShape(28.dp)

/**
 * Базовая поверхность «жидкого стекла»: размытие фона + полупрозрачная заливка,
 * блик сверху и световой ободок. Без HazeState (отдельное окно) — плотная заливка.
 */
@OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)
@Composable
fun Modifier.glass(
    shape: Shape = GlassShape,
    strong: Boolean = false,
    blur: Dp = 28.dp,
    tint: Color? = null,
): Modifier {
    val p = Liquid.palette
    val haze = LocalHazeState.current
    val fill = tint ?: if (strong || haze == null) p.glassTintStrong else p.glassTint
    val style = remember(p, fill, blur) {
        HazeStyle(
            backgroundColor = p.base,
            tint = HazeTint(fill),
            blurRadius = blur,
            noiseFactor = if (p.isDark) 0.05f else 0.035f,
        )
    }
    val rim = remember(p) { Brush.linearGradient(listOf(p.rimBright, p.rimFaint, p.rimFaint, p.rimBright.copy(alpha = p.rimBright.alpha * 0.5f))) }
    val sheen = remember(p) { Brush.verticalGradient(0f to p.sheen, 0.45f to Color.Transparent) }
    return this
        .clip(shape)
        .then(
            if (haze != null) Modifier.hazeEffect(haze, style) {
                // Размытие в половинном разрешении: в ~4 раза меньше пикселей, на глаз неотличимо.
                inputScale = HazeInputScale.Fixed(0.5f)
            } else Modifier.background(fill)
        )
        .drawWithContent {
            drawRect(sheen)
            drawContent()
        }
        .border(BorderStroke(1.dp, rim), shape)
}

/** Пружинное «продавливание» при нажатии — даёт ощущение жидкости. */
@Composable
fun Modifier.liquidPress(interaction: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .then(if (onClick != null) Modifier.liquidPress(interaction) else Modifier)
            .glass(shape, strong)
            .then(
                if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick)
                else Modifier
            ),
        content = content,
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier, shape, onClick = onClick) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

enum class GlassButtonStyle { Primary, Secondary, Danger }

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val p = Liquid.palette
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    val active = enabled && !loading
    val (fill, content) = when (style) {
        GlassButtonStyle.Primary -> Brush.linearGradient(listOf(p.accent, p.accentSecondary)) to p.onAccent
        GlassButtonStyle.Danger -> Brush.linearGradient(listOf(p.danger, p.danger.copy(alpha = 0.85f))) to Color.White
        GlassButtonStyle.Secondary -> null to p.textPrimary
    }
    Box(
        modifier
            .height(54.dp)
            .liquidPress(interaction, active)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .glass(shape)
            .then(if (fill != null) Modifier.background(fill, shape) else Modifier)
            .drawWithContent {
                drawContent()
                // стеклянный блик поверх цветной заливки
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.5f to Color.Transparent))
            }
            .clickable(interaction, null, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = content, strokeWidth = 2.5.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) {
                    Icon(icon, null, tint = content, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, color = content, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color = Liquid.palette.textPrimary,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .liquidPress(interaction)
            .glass(CircleShape)
            .clickable(interaction, null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/** Небольшая стеклянная «таблетка» — для чипов и бейджей. */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = Liquid.palette
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .then(if (onClick != null) Modifier.liquidPress(interaction) else Modifier)
            .glass(shape)
            .then(if (selected) Modifier.background(p.accent.copy(alpha = 0.22f), shape).border(1.5.dp, p.accent, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(interaction, null, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(leading)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = p.textPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

/** Сегментированный переключатель с «перетекающим» индикатором. */
@Composable
fun GlassSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Liquid.palette
    val shape = RoundedCornerShape(50)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .glass(shape)
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size
        val segmentHeight = maxHeight
        val offset by androidx.compose.animation.core.animateDpAsState(
            targetValue = segment * selectedIndex.coerceAtLeast(0),
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        if (selectedIndex >= 0) {
            Box(
                Modifier
                    .offset(x = offset)
                    .width(segment)
                    .height(segmentHeight)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(p.accent, p.accentSecondary)))
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.3f), 0.6f to Color.Transparent))
                    }
            )
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .width(segment)
                        .height(segmentHeight)
                        .clip(shape)
                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (i == selectedIndex) p.onAccent else p.textPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = Liquid.palette.textPrimary,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
