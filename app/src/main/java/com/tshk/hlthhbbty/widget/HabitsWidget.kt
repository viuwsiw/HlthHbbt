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
private data class WidgetData(val loggedIn: Boolean, val waterMl: Int, val goalMl: Int, val habits: List<WidgetHabit>)

private val habitKey = Key<Long>("habit_id")

/** Стеклянный виджет: вода и привычки на сегодня, отметка прямо с рабочего стола. */
class HabitsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(data) } }
    }

    private suspend fun load(context: Context): WidgetData {
        val c = (context.applicationContext as HlthApp).container
        val userId = c.auth.currentUserId.first() ?: return WidgetData(false, 0, 0, emptyList())
        val user = c.users.observe(userId).first() ?: return WidgetData(false, 0, 0, emptyList())
        val day = today()
        val habits = c.habits.activeOnce(userId).filter { it.scheduledOn(day) }.map {
            WidgetHabit(it.id, it.title, it.emoji, c.habits.countFor(it.id, day) >= it.goalPerDay)
        }
        return WidgetData(true, c.water.todayOnce(userId), user.waterGoalMl, habits)
    }

    @Composable
    private fun Content(d: WidgetData) {
        val text = ColorProvider(Color(0xFF0D2A21))
        val sub = ColorProvider(Color(0xFF3F6157))
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(ColorProvider(Color(0xCCEFFAF4)))
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (!d.loggedIn) {
                Text("HLTH HBTy", style = TextStyle(color = text, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Text("Войдите в приложение", style = TextStyle(color = sub, fontSize = 12.sp))
                return@Column
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("💧 %.2f / %.1f л".format(d.waterMl / 1000f, d.goalMl / 1000f),
                    style = TextStyle(color = text, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    modifier = GlanceModifier.defaultWeight())
                Box(
                    GlanceModifier.cornerRadius(14.dp).background(ColorProvider(Color(0xFF3AA8FF)))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clickable(actionRunCallback<AddWaterAction>()),
                ) { Text("+250", style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 13.sp)) }
            }
            Spacer(GlanceModifier.height(10.dp))
            if (d.habits.isEmpty()) {
                Text("На сегодня привычек нет", style = TextStyle(color = sub, fontSize = 12.sp))
            }
            d.habits.take(5).forEach { h ->
                Row(
                    GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(habitKey to h.id))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(h.emoji, style = TextStyle(fontSize = 15.sp))
                    Spacer(GlanceModifier.width(8.dp))
                    Text(h.title, maxLines = 1, style = TextStyle(color = text, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
                    Box(
                        GlanceModifier.size(22.dp).cornerRadius(11.dp)
                            .background(ColorProvider(if (h.done) Color(0xFF17B077) else Color(0x3317B077))),
                        contentAlignment = Alignment.Center,
                    ) { if (h.done) Text("✓", style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
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
