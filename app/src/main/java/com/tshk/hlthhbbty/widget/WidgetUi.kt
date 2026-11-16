package com.tshk.hlthhbbty.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import com.tshk.hlthhbbty.MainActivity

/** Стеклянная подложка на весь виджет под точный размер. */
@Composable
fun GlassPanel(c: WidgetColors, density: Float, content: @Composable () -> Unit) {
    val size = LocalSize.current
    val bg = glassBackground(c, (size.width.value * density).toInt(), (size.height.value * density).toInt(), 30f * density)
    Box(GlanceModifier.fillMaxSize()) {
        Image(ImageProvider(bg), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        content()
    }
}

/** Круглая стеклянная кнопка с иконкой. */
@Composable
fun GlassCircle(c: WidgetColors, icon: Int, label: String, action: Action, size: Dp = 52.dp, tint: androidx.compose.ui.graphics.Color = c.icon) {
    Box(
        GlanceModifier.size(size).cornerRadius(size / 2).background(ColorProvider(c.circle)).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), label, GlanceModifier.size(size * 0.46f), colorFilter = ColorFilter.tint(ColorProvider(tint)))
    }
}

/** Открыть приложение на нужном экране (см. MainActivity.EXTRA_WIDGET_ROUTE). */
fun openRoute(context: Context, route: String?): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.EXTRA_WIDGET_ROUTE, route)
        .setData(android.net.Uri.parse("hlthhbbty://widget/${route ?: "home"}")) // разные data — разные PendingIntent
