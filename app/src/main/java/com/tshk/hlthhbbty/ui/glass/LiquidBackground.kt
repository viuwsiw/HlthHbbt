package com.tshk.hlthhbbty.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.tshk.hlthhbbty.ui.theme.Liquid
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Состояние размытия фона; null — стекло рисуется без blur (например, в отдельном окне). */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

private data class Blob(val color: Int, val cx: Float, val cy: Float, val r: Float, val ax: Float, val ay: Float, val speed: Float, val phase: Float)

private val blobs = listOf(
    Blob(0, 0.15f, 0.12f, 0.62f, 0.10f, 0.06f, 1f, 0f),
    Blob(1, 0.90f, 0.28f, 0.58f, 0.08f, 0.10f, 0.8f, 1.3f),
    Blob(2, 0.30f, 0.62f, 0.55f, 0.12f, 0.08f, 1.2f, 2.1f),
    Blob(3, 0.85f, 0.85f, 0.65f, 0.09f, 0.07f, 0.9f, 3.4f),
    Blob(4, 0.05f, 0.95f, 0.50f, 0.07f, 0.09f, 1.1f, 4.6f),
)

/**
 * Живой фон-аврора из мягких цветовых пятен. Служит источником для стеклянного размытия:
 * все GlassSurface внутри [content] размывают именно его.
 */
@Composable
fun LiquidBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = Liquid.palette
    val hazeState = remember { HazeState() }
    // Статичный фон: размытие считается один раз и кэшируется.
    // Анимация — ~15 кадров/с: движение плавное и медленное, а нагрузка в разы ниже, чем на 60/120 Гц.
    var t by remember { mutableFloatStateOf(0.6f) }
    if (animated) {
        LaunchedEffect(Unit) {
            val start = System.nanoTime()
            while (true) {
                val seconds = (System.nanoTime() - start) / 1e9f
                t = 0.6f + (seconds / 26f * 2 * PI.toFloat()) % (2 * PI.toFloat())
                delay(66)
            }
        }
    }
    Box(modifier.fillMaxSize().background(palette.base)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            val w = size.width
            val h = size.height
            val alpha = if (palette.isDark) 0.75f else 0.85f
            blobs.forEach { b ->
                val center = Offset(
                    (b.cx + b.ax * sin(t * b.speed + b.phase)) * w,
                    (b.cy + b.ay * cos(t * b.speed * 0.9f + b.phase)) * h,
                )
                val radius = b.r * maxOf(w, h) * (0.92f + 0.08f * sin(t * 2 + b.phase))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.blobs[b.color].copy(alpha = alpha), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            content()
        }
    }
}
