package com.tshk.hlthhbbty.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.tshk.hlthhbbty.data.AppSettings
import com.tshk.hlthhbbty.data.ThemeMode
import com.tshk.hlthhbbty.ui.theme.LiquidPalette
import com.tshk.hlthhbbty.ui.theme.buildPalette

/** Палитра виджета по настройкам пользователя (тема + акцент). */
fun widgetPalette(context: Context, s: AppSettings): LiquidPalette {
    val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val dark = when (s.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    return buildPalette(dark, s.themeMode == ThemeMode.BLACK, s.accentIndex)
}

/** Цвета стеклянного виджета: тонировка поверх обоев, «пилюли», кнопки, текст. */
data class WidgetColors(
    val panel: Int,
    val pill: androidx.compose.ui.graphics.Color,
    val circle: androidx.compose.ui.graphics.Color,
    val text: androidx.compose.ui.graphics.Color,
    val sub: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
    val onAccent: androidx.compose.ui.graphics.Color,
    val dark: Boolean,
)

fun widgetColors(p: LiquidPalette): WidgetColors {
    val dark = p.isDark
    val white = androidx.compose.ui.graphics.Color.White
    val black = androidx.compose.ui.graphics.Color.Black
    // Тонировка с лёгким оттенком фона темы — сквозь неё видны обои, как на iOS.
    val tint = androidx.compose.ui.graphics.lerp(if (dark) black else white, p.base, 0.35f)
    return WidgetColors(
        panel = tint.copy(alpha = if (dark) 0.55f else 0.45f).toArgb(),
        pill = (if (dark) white else white).copy(alpha = if (dark) 0.10f else 0.45f),
        circle = (if (dark) white else white).copy(alpha = if (dark) 0.12f else 0.50f),
        text = if (dark) white else p.textPrimary,
        sub = if (dark) white.copy(alpha = 0.7f) else p.textSecondary,
        icon = if (dark) white else p.textPrimary,
        accent = p.accent,
        onAccent = p.onAccent,
        dark = dark,
    )
}

/**
 * Подложка «жидкое стекло»: полупрозрачная тонированная панель со скруглением,
 * мягкий блик сверху и световой ободок. Обои рабочего стола видны насквозь.
 */
fun glassBackground(c: WidgetColors, widthPx: Int, heightPx: Int, cornerPx: Float): Bitmap {
    val w = widthPx.coerceIn(100, 1400)
    val h = heightPx.coerceIn(100, 1400)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val rect = RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = c.panel
    canvas.drawRoundRect(rect, cornerPx, cornerPx, paint)
    // Блик сверху
    paint.shader = LinearGradient(0f, 0f, 0f, h * 0.45f, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    canvas.drawRoundRect(rect, cornerPx, cornerPx, paint)
    // Световой ободок: ярче сверху-слева, гаснет к низу
    paint.shader = LinearGradient(0f, 0f, w * 0.6f, h.toFloat(),
        intArrayOf(0x88FFFFFF.toInt(), 0x1AFFFFFF, 0x44FFFFFF), null, Shader.TileMode.CLAMP)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f
    canvas.drawRoundRect(rect, cornerPx, cornerPx, paint)
    return bmp
}

/** Кольцо прогресса для виджета (Glance не рисует дуги — делаем картинкой). */
fun progressRing(c: WidgetColors, sizePx: Int, fraction: Float): Bitmap {
    val s = sizePx.coerceIn(48, 400)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val stroke = s * 0.1f
    val r = RectF(stroke / 2 + 1, stroke / 2 + 1, s - stroke / 2 - 1, s - stroke / 2 - 1)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    paint.color = c.circle.toArgb()
    canvas.drawArc(r, 0f, 360f, false, paint)
    if (fraction > 0f) {
        paint.color = c.accent.toArgb()
        canvas.drawArc(r, -90f, 360f * fraction.coerceIn(0f, 1f), false, paint)
    }
    return bmp
}

/** Тонкая полоса прогресса картинкой: ровно по ширине, со скруглёнными концами. */
fun progressBar(c: WidgetColors, widthPx: Int, heightPx: Int, fraction: Float): Bitmap {
    val w = widthPx.coerceIn(20, 1400)
    val h = heightPx.coerceIn(2, 40)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = c.circle.toArgb()
    canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, paint)
    if (fraction > 0f) {
        paint.color = c.accent.toArgb()
        canvas.drawRoundRect(RectF(0f, 0f, maxOf(h.toFloat(), w * fraction.coerceIn(0f, 1f)), h.toFloat()), h / 2f, h / 2f, paint)
    }
    return bmp
}
