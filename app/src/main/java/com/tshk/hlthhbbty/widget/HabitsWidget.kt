package com.tshk.hlthhbbty.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
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
import androidx.glance.action.ActionParameters.Key
import com.tshk.hlthhbbty.HlthApp
import com.tshk.hlthhbbty.MainActivity
import com.tshk.hlthhbbty.util.scheduledOn
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.flow.first

private data class WidgetHabit(val id: Long, val title: String, val emoji: String, val done: Boolean)
private data class WidgetData(
    val loggedIn: Boolean,
    val waterMl: Int,
    val goalMl: Int,
    val habits: List<WidgetHabit>,
    val palette: com.tshk.hlthhbbty.ui.theme.LiquidPalette,
)

private val habitKey = Key<Long>("habit_id")

/** Стеклянный виджет: вода и привычки на сегодня, отметка прямо с рабочего стола. */
class HabitsWidget : GlanceAppWidget() {

    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        val density = context.resources.displayMetrics.density.coerceAtMost(2.5f)
        provideContent {
            val size = androidx.glance.LocalSize.current
            val bg = glassBackground(data.palette, (size.width.value * density).toInt(), (size.height.value * density).toInt(), 28f * density)
            GlanceTheme { Content(data, bg) }
        }
    }

    private suspend fun load(context: Context): WidgetData {
        val c = (context.applicationContext as HlthApp).container
        val palette = widgetPalette(context, c.settings.current())
        val userId = c.auth.currentUserId.first() ?: return WidgetData(false, 0, 0, emptyList(), palette)
        val user = c.users.observe(userId).first() ?: return WidgetData(false, 0, 0, emptyList(), palette)
        val day = today()
        val habits = c.habits.activeOnce(userId).filter { it.scheduledOn(day) }.map {
            WidgetHabit(it.id, it.title, it.emoji, c.habits.countFor(it.id, day) >= it.goalPerDay)
        }
        return WidgetData(true, c.water.todayOnce(userId), user.waterGoalMl, habits, palette)
    }

    @Composable
    private fun Content(d: WidgetData, bg: android.graphics.Bitmap) {
        val p = d.palette
        val text = ColorProvider(p.textPrimary)
        val sub = ColorProvider(p.textSecondary)
        val accent = p.accent
        Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>())) {
            androidx.glance.Image(
                provider = androidx.glance.ImageProvider(bg),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = androidx.glance.layout.ContentScale.FillBounds,
            )
            Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                if (!d.loggedIn) {
                    Text("HLTH HBTy", style = TextStyle(color = text, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                    Text("Войдите в приложение", style = TextStyle(color = sub, fontSize = 12.sp))
                    return@Column
                }
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text("💧 Вода", style = TextStyle(color = sub, fontSize = 11.sp))
                        Text("%.2f / %.1f л".format(d.waterMl / 1000f, d.goalMl / 1000f),
                            style = TextStyle(color = text, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                    }
                    Box(
                        GlanceModifier.cornerRadius(16.dp).background(ColorProvider(accent))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable(actionRunCallback<AddWaterAction>()),
                    ) { Text("+250", style = TextStyle(color = ColorProvider(p.onAccent), fontWeight = FontWeight.Bold, fontSize = 13.sp)) }
                }
                // Полоска прогресса воды
                Spacer(GlanceModifier.height(8.dp))
                val frac = if (d.goalMl > 0) (d.waterMl.toFloat() / d.goalMl).coerceIn(0f, 1f) else 0f
                Row(GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp).background(ColorProvider(accent.copy(alpha = 0.2f)))) {
                    if (frac > 0f) Box(GlanceModifier.height(6.dp).width((frac * 280).dp).cornerRadius(3.dp).background(ColorProvider(accent))) {}
                }
                Spacer(GlanceModifier.height(10.dp))
                if (d.habits.isEmpty()) {
                    Text("На сегодня привычек нет", style = TextStyle(color = sub, fontSize = 12.sp))
                }
                d.habits.take(5).forEach { h ->
                    Row(
                        GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
                            .cornerRadius(12.dp).background(ColorProvider(p.glassTint))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(habitKey to h.id))),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(h.emoji, style = TextStyle(fontSize = 14.sp))
                        Spacer(GlanceModifier.width(8.dp))
                        Text(h.title, maxLines = 1, style = TextStyle(color = text, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
                        Box(
                            GlanceModifier.size(22.dp).cornerRadius(11.dp)
                                .background(ColorProvider(if (h.done) accent else accent.copy(alpha = 0.18f))),
                            contentAlignment = Alignment.Center,
                        ) { if (h.done) Text("✓", style = TextStyle(color = ColorProvider(p.onAccent), fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = runCatching { HabitsWidget().updateAll(context) }
    }
}

class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = (context.applicationContext as HlthApp).container
        val habit = parameters[habitKey]?.let { c.habits.find(it) } ?: return
        c.habits.tap(habit, today())
    }
}

class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = (context.applicationContext as HlthApp).container
        val userId = c.auth.currentUserId.first() ?: return
        c.water.add(userId, 250)
    }
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}
