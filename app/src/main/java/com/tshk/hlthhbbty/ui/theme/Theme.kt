package com.tshk.hlthhbbty.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Палитра «жидкого стекла»: фон-аврора, стекло и текст. */
@Immutable
data class LiquidPalette(
    val isDark: Boolean,
    val base: Color,
    val blobs: List<Color>,
    val glassTint: Color,
    val glassTintStrong: Color,
    val rimBright: Color,
    val rimFaint: Color,
    val sheen: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentSecondary: Color,
    val onAccent: Color,
    val danger: Color,
    val water: Color,
    val waterDeep: Color,
)

val LightPalette = LiquidPalette(
    isDark = false,
    base = Color(0xFFE9F7EF),
    blobs = listOf(
        Color(0xFF8CE8B8), Color(0xFF5ED3C8), Color(0xFFD8F57E),
        Color(0xFFA3CCFF), Color(0xFFC9BBFF),
    ),
    glassTint = Color.White.copy(alpha = 0.34f),
    glassTintStrong = Color.White.copy(alpha = 0.72f),
    rimBright = Color.White.copy(alpha = 0.95f),
    rimFaint = Color.White.copy(alpha = 0.18f),
    sheen = Color.White.copy(alpha = 0.45f),
    textPrimary = Color(0xFF0D2A21),
    textSecondary = Color(0xFF3F6157),
    accent = Color(0xFF17B077),
    accentSecondary = Color(0xFF14A3B8),
    onAccent = Color.White,
    danger = Color(0xFFE5484D),
    water = Color(0xFF3AA8FF),
    waterDeep = Color(0xFF2470E8),
)

val DarkPalette = LiquidPalette(
    isDark = true,
    base = Color(0xFF06110E),
    blobs = listOf(
        Color(0xFF1B8C62), Color(0xFF0F7782), Color(0xFF5E7F17),
        Color(0xFF2A4C9C), Color(0xFF5938A8),
    ),
    glassTint = Color.White.copy(alpha = 0.08f),
    glassTintStrong = Color(0xFF14231F).copy(alpha = 0.82f),
    rimBright = Color.White.copy(alpha = 0.42f),
    rimFaint = Color.White.copy(alpha = 0.05f),
    sheen = Color.White.copy(alpha = 0.10f),
    textPrimary = Color(0xFFEAFBF3),
    textSecondary = Color(0xFFA6C4BA),
    accent = Color(0xFF2FD394),
    accentSecondary = Color(0xFF2CC3D6),
    onAccent = Color(0xFF032016),
    danger = Color(0xFFFF6B6F),
    water = Color(0xFF58B8FF),
    waterDeep = Color(0xFF3B82F6),
)

/** Цвета привычек. */
val HabitColors = listOf(
    Color(0xFF22C55E), Color(0xFF06B6D4), Color(0xFFF59E0B), Color(0xFFEF4444),
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF3B82F6), Color(0xFF84CC16),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object Liquid {
    val palette: LiquidPalette
        @Composable get() = LocalPalette.current
}

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun HlthTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accentSecondary,
            background = p.base, surface = p.base, onBackground = p.textPrimary,
            onSurface = p.textPrimary, onSurfaceVariant = p.textSecondary, error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accentSecondary,
            background = p.base, surface = p.base, onBackground = p.textPrimary,
            onSurface = p.textPrimary, onSurfaceVariant = p.textSecondary, error = p.danger,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

val Typography.caption: TextStyle get() = labelMedium
