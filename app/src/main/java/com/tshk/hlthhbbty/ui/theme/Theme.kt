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
import androidx.compose.ui.graphics.lerp
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

/** Полностью чёрная тема (AMOLED): чистый чёрный фон, едва заметное свечение. */
val BlackPalette = DarkPalette.copy(
    base = Color.Black,
    blobs = listOf(
        Color(0xFF0E4D36), Color(0xFF0A3F45), Color(0xFF2E3D0C),
        Color(0xFF15264F), Color(0xFF2B1B52),
    ),
    glassTint = Color.White.copy(alpha = 0.06f),
    glassTintStrong = Color(0xFF0A0A0A).copy(alpha = 0.92f),
    rimBright = Color.White.copy(alpha = 0.30f),
    rimFaint = Color.White.copy(alpha = 0.04f),
    sheen = Color.White.copy(alpha = 0.06f),
    textPrimary = Color(0xFFF2F2F2),
    textSecondary = Color(0xFF9A9A9A),
)

/** Цвета привычек. */
val HabitColors = listOf(
    Color(0xFF22C55E), Color(0xFF06B6D4), Color(0xFFF59E0B), Color(0xFFEF4444),
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF3B82F6), Color(0xFF84CC16),
)

/** Варианты акцентного цвета: основной и вторичный (для градиентов). */
data class AccentOption(val title: String, val light: Pair<Color, Color>, val dark: Pair<Color, Color>)

val AccentOptions = listOf(
    AccentOption("Мята", Color(0xFF17B077) to Color(0xFF14A3B8), Color(0xFF2FD394) to Color(0xFF2CC3D6)),
    AccentOption("Океан", Color(0xFF2563EB) to Color(0xFF06B6D4), Color(0xFF60A5FA) to Color(0xFF22D3EE)),
    AccentOption("Лаванда", Color(0xFF7C3AED) to Color(0xFFDB2777), Color(0xFFA78BFA) to Color(0xFFF472B6)),
    AccentOption("Закат", Color(0xFFEA580C) to Color(0xFFE11D48), Color(0xFFFB923C) to Color(0xFFFB7185)),
    AccentOption("Лайм", Color(0xFF65A30D) to Color(0xFF0D9488), Color(0xFFA3E635) to Color(0xFF2DD4BF)),
    // Монохромные: в светлой теме — чёрные, в тёмной/чёрной — светлые, чтобы оставаться видимыми.
    AccentOption("AMOLED", Color(0xFF000000) to Color(0xFF262626), Color(0xFFFFFFFF) to Color(0xFFBDBDBD)),
    AccentOption("Графит", Color(0xFF1F1F1F) to Color(0xFF6B6B6B), Color(0xFFE0E0E0) to Color(0xFF8A8A8A)),
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

private fun saturation(c: Color): Float {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    return if (max == 0f) 0f else (max - min) / max
}

/** Фон чуть подкрашен акцентом; у чёрной темы — всегда чистый чёрный. */
private fun backgroundFor(accent: Color, dark: Boolean, black: Boolean): Color = when {
    black -> Color.Black
    dark -> lerp(Color(0xFF080A0C), accent, if (saturation(accent) < 0.15f) 0.03f else 0.07f)
    else -> lerp(Color(0xFFF1F3F5), accent, if (saturation(accent) < 0.15f) 0.04f else 0.09f)
}

/** Пятна «авроры» — оттенки акцентных цветов; у монохромных акцентов — мягкие серые. */
private fun blobsFor(a1: Color, a2: Color, dark: Boolean, black: Boolean): List<Color> {
    val mono = saturation(a1) < 0.15f && saturation(a2) < 0.15f
    val mid = lerp(a1, a2, 0.5f)
    val raw = listOf(a1, a2, mid, lerp(a1, Color.White, 0.3f), lerp(a2, mid, 0.5f))
    return raw.map { c ->
        when {
            black -> lerp(c, Color.Black, if (mono) 0.82f else 0.62f)
            dark -> lerp(c, Color.Black, if (mono) 0.72f else 0.4f)
            else -> lerp(c, Color.White, if (mono) 0.72f else 0.42f)
        }
    }
}

@Composable
fun HlthTheme(dark: Boolean = isSystemInDarkTheme(), accentIndex: Int = 0, black: Boolean = false, content: @Composable () -> Unit) {
    val accent = AccentOptions.getOrElse(accentIndex) { AccentOptions[0] }
    val (a1, a2) = if (dark) accent.dark else accent.light
    val base = if (black) BlackPalette else if (dark) DarkPalette else LightPalette
    val p = base.copy(
        accent = a1,
        accentSecondary = a2,
        base = backgroundFor(a1, dark, black),
        blobs = blobsFor(a1, a2, dark, black),
    )
    val scheme = if (dark || black) {
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
