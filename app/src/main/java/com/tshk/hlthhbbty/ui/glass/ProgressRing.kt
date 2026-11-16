package com.tshk.hlthhbbty.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tshk.hlthhbbty.ui.theme.Liquid

@Composable
fun ProgressRing(
    progress: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    stroke: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val p = Liquid.palette
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
        label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(
                color = if (p.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.55f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(s),
            )
            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(colors + colors.first()),
                    startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(s, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}
