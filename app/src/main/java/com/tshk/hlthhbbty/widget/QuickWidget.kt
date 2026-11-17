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
import com.tshk.hlthhbbty.util.scheduledOn
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.flow.first

private data class QuickData(
    val loggedIn: Boolean,
    val waterMl: Int = 0,
    val goalMl: Int = 2000,
    val done: Int = 0,
    val total: Int = 0,
    val colors: WidgetColors,
)

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
        val userId = c.auth.currentUserId.first() ?: return QuickData(false, colors = colors)
        val user = c.users.observe(userId).first() ?: return QuickData(false, colors = colors)
        val day = today()
        val habits = c.habits.activeOnce(userId).filter { it.scheduledOn(day) }
        val done = habits.count { c.habits.countFor(it.id, day) >= it.goalPerDay }
        val water = c.water.todayOnce(userId)
        return QuickData(true, water, user.waterGoalMl, done, habits.size, colors)
    }

    @Composable
    private fun Content(d: QuickData, density: Float) {
        val c = d.colors
        val context = LocalContext.current
        val size = androidx.glance.LocalSize.current
        val roomy = size.height >= 165.dp // на высоком виджете — шапка и подписи кнопок
        GlassPanel(c, density) {
            Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (roomy) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(R.drawable.ic_stat_logo), null, GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(ColorProvider(c.sub)))
                        Spacer(GlanceModifier.width(6.dp))
                        Text("HLTH HBTy", style = TextStyle(color = ColorProvider(c.sub), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.defaultWeight())
                        Text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("ru"))),
                            style = TextStyle(color = ColorProvider(c.sub), fontSize = 11.sp))
                    }
                    Spacer(GlanceModifier.height(10.dp))
                }
                // Сводка: кольцо воды + привычки
                Row(
                    GlanceModifier.fillMaxWidth().clickable(actionStartActivity(openRoute(context, null))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val frac = if (d.goalMl > 0) d.waterMl.toFloat() / d.goalMl else 0f
                    Box(GlanceModifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Image(ImageProvider(progressRing(c, (46 * density).toInt(), frac)), null, GlanceModifier.size(46.dp))
                        Image(ImageProvider(R.drawable.ic_w_water), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(ColorProvider(c.accent)))
                    }
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text("Вода", style = TextStyle(color = ColorProvider(c.sub), fontSize = 11.sp))
                        Text(if (d.loggedIn) "%.1f из %.1f л".format(d.waterMl / 1000f, d.goalMl / 1000f) else "Войдите в приложение",
                            maxLines = 1, style = TextStyle(color = ColorProvider(c.text), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    }
                    if (d.loggedIn) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Привычки", style = TextStyle(color = ColorProvider(c.sub), fontSize = 11.sp))
                            Text("${d.done} из ${d.total}", style = TextStyle(color = ColorProvider(c.text), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Action(c, R.drawable.ic_w_water, "Вода", actionRunCallback<AddWaterAction>(), roomy, c.accent)
                    Action(c, R.drawable.ic_w_timer, "Фокус", actionStartActivity(openRoute(context, "focus")), roomy)
                    Action(c, R.drawable.ic_w_route, "Прогулка", actionStartActivity(openRoute(context, "walk")), roomy)
                    Action(c, R.drawable.ic_w_check, "Привычки", actionStartActivity(openRoute(context, "habits")), roomy)
                }
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.Action(
        c: WidgetColors, icon: Int, label: String, action: androidx.glance.action.Action, showLabel: Boolean,
        tint: androidx.compose.ui.graphics.Color = c.icon,
    ) {
        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            GlassCircle(c, icon, label, action, size = 44.dp, tint = tint)
            if (showLabel) {
                Spacer(GlanceModifier.height(4.dp))
                Text(label, maxLines = 1, style = TextStyle(color = ColorProvider(c.sub), fontSize = 10.sp))
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
