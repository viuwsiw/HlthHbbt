package com.tshk.hlthhbbty.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tshk.hlthhbbty.HlthApp
import com.tshk.hlthhbbty.R
import com.tshk.hlthhbbty.util.greeting
import com.tshk.hlthhbbty.util.scheduledOn
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.flow.first

private data class QuickData(val loggedIn: Boolean, val summary: String, val colors: WidgetColors)

/**
 * Виджет «Быстрые действия» в стиле стеклянной панели: сверху «пилюля» со сводкой дня,
 * снизу круглые кнопки — вода, фокус, прогулка, привычки.
 */
class QuickWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        val density = context.resources.displayMetrics.density.coerceAtMost(2.5f)
        provideContent { GlanceTheme { Content(data, density) } }
    }

    private suspend fun load(context: Context): QuickData {
        val c = (context.applicationContext as HlthApp).container
        val colors = widgetColors(widgetPalette(context, c.settings.current()))
        val userId = c.auth.currentUserId.first() ?: return QuickData(false, "Войдите в HLTH HBTy", colors)
        val user = c.users.observe(userId).first() ?: return QuickData(false, "Войдите в HLTH HBTy", colors)
        val day = today()
        val habits = c.habits.activeOnce(userId).filter { it.scheduledOn(day) }
        val done = habits.count { c.habits.countFor(it.id, day) >= it.goalPerDay }
        val water = c.water.todayOnce(userId)
        val summary = "💧 %.1f / %.1f л  ·  ✅ %d из %d".format(water / 1000f, user.waterGoalMl / 1000f, done, habits.size)
        return QuickData(true, summary, colors)
    }

    @Composable
    private fun Content(d: QuickData, density: Float) {
        val c = d.colors
        val context = LocalContext.current
        GlassPanel(c, density) {
            Column(GlanceModifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                // «Пилюля» со сводкой
                Row(
                    GlanceModifier.fillMaxWidth().height(52.dp).cornerRadius(26.dp).background(ColorProvider(c.pill))
                        .padding(horizontal = 16.dp)
                        .clickable(actionStartActivity(openRoute(context, null))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(ImageProvider(R.drawable.ic_stat_logo), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(ColorProvider(c.icon)))
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(if (d.loggedIn) greeting() else "HLTH HBTy", style = TextStyle(color = ColorProvider(c.sub), fontSize = 11.sp))
                        Text(d.summary, maxLines = 1, style = TextStyle(color = ColorProvider(c.text), fontSize = 14.sp, fontWeight = FontWeight.Medium))
                    }
                }
                Spacer(GlanceModifier.height(12.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        GlassCircle(c, R.drawable.ic_w_water, "+250 мл воды", actionRunCallback<AddWaterAction>(), tint = c.accent)
                    }
                    Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        GlassCircle(c, R.drawable.ic_w_timer, "Фокус", actionStartActivity(openRoute(context, "focus")))
                    }
                    Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        GlassCircle(c, R.drawable.ic_w_walk, "Прогулка", actionStartActivity(openRoute(context, "walk")))
                    }
                    Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        GlassCircle(c, R.drawable.ic_w_check, "Привычки", actionStartActivity(openRoute(context, "habits")))
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = runCatching { QuickWidget().updateAll(context) }
    }
}

class QuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickWidget()
}
