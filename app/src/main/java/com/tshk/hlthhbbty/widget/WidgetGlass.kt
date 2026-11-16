package com.tshk.hlthhbbty.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
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

/**
 * Подложка виджета «жидкое стекло»: аврора в цветах акцента, полупрозрачная панель,
 * световой ободок и блик сверху. Glance не умеет размывать, поэтому стекло рисуется картинкой.
 */
fun glassBackground(p: LiquidPalette, widthPx: Int, heightPx: Int, cornerPx: Float): Bitmap {
    val w = widthPx.coerceIn(100, 1400)
    val h = heightPx.coerceIn(100, 1400)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    val clip = android.graphics.Path().apply { addRoundRect(rect, cornerPx, cornerPx, android.graphics.Path.Direction.CW) }
    c.clipPath(clip)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Фон + мягкие пятна
    paint.color = p.base.toArgb()
    c.drawRect(rect, paint)
    val spots = listOf(0.15f to 0.2f, 0.85f to 0.15f, 0.55f to 0.95f, 0.05f to 0.9f)
    spots.forEachIndexed { i, (fx, fy) ->
        val col = p.blobs[i % p.blobs.size].toArgb()
        val r = maxOf(w, h) * 0.75f
        paint.shader = RadialGradient(fx * w, fy * h, r, intArrayOf(col and 0x00FFFFFF or (0xD0 shl 24), col and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRect(rect, paint)
    }
    paint.shader = null
    // Стеклянная панель
    paint.color = p.glassTint.toArgb()
    c.drawRect(rect, paint)
    // Блик сверху
    paint.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, p.sheen.toArgb(), 0x00FFFFFF, Shader.TileMode.CLAMP)
    c.drawRect(rect, paint)
    // Световой ободок
    paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
        intArrayOf(p.rimBright.toArgb(), p.rimFaint.toArgb(), p.rimBright.copy(alpha = p.rimBright.alpha * 0.5f).toArgb()), null, Shader.TileMode.CLAMP)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    val inset = RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
    c.drawRoundRect(inset, cornerPx, cornerPx, paint)
    return bmp
}
