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
        provideContent { GlanceTheme { Content(data, density) } }
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
    private fun Content(d: WidgetData, density: Float) {
        val c = widgetColors(d.palette)
        val context = androidx.glance.LocalContext.current
        val text = ColorProvider(c.text)
        val sub = ColorProvider(c.sub)
        GlassPanel(c, density) {
            Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                if (!d.loggedIn) {
                    Text("HLTH HBTy", style = TextStyle(color = text, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                    Text("Войдите в приложение", style = TextStyle(color = sub, fontSize = 12.sp))
                    return@Column
                }
                // «Пилюля» с водой и кнопкой +250
                Row(
                    GlanceModifier.fillMaxWidth().height(50.dp).cornerRadius(25.dp).background(ColorProvider(c.pill))
                        .padding(start = 16.dp, end = 6.dp)
                        .clickable(androidx.glance.appwidget.action.actionStartActivity(openRoute(context, null))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.glance.Image(androidx.glance.ImageProvider(com.tshk.hlthhbbty.R.drawable.ic_w_water), null, GlanceModifier.size(20.dp),
                        colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(c.accent)))
                    Spacer(GlanceModifier.width(10.dp))
                    Text("%.2f / %.1f л".format(d.waterMl / 1000f, d.goalMl / 1000f),
                        style = TextStyle(color = text, fontWeight = FontWeight.Medium, fontSize = 15.sp), modifier = GlanceModifier.defaultWeight())
                    Box(
                        GlanceModifier.height(38.dp).cornerRadius(19.dp).background(ColorProvider(c.circle))
                            .padding(horizontal = 14.dp)
                            .clickable(actionRunCallback<AddWaterAction>()),
                        contentAlignment = Alignment.Center,
                    ) { Text("+250", style = TextStyle(color = text, fontWeight = FontWeight.Bold, fontSize = 13.sp)) }
                }
                Spacer(GlanceModifier.height(10.dp))
                if (d.habits.isEmpty()) {
                    Text("На сегодня привычек нет", style = TextStyle(color = sub, fontSize = 12.sp), modifier = GlanceModifier.padding(start = 6.dp))
                }
                d.habits.take(4).forEach { h ->
                    Row(
                        GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(habitKey to h.id))),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Круглая стеклянная отметка
                        Box(
                            GlanceModifier.size(36.dp).cornerRadius(18.dp)
                                .background(ColorProvider(if (h.done) c.accent else c.circle)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (h.done) Text("✓", style = TextStyle(color = ColorProvider(c.onAccent), fontSize = 15.sp, fontWeight = FontWeight.Bold))
                            else Text(h.emoji, style = TextStyle(fontSize = 15.sp))
                        }
                        Spacer(GlanceModifier.width(10.dp))
                        Text(h.title, maxLines = 1, style = TextStyle(color = if (h.done) sub else text, fontSize = 14.sp), modifier = GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = runCatching {
            HabitsWidget().updateAll(context)
            QuickWidget().updateAll(context)
        }
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
