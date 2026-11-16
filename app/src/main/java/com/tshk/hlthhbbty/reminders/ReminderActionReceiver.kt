package com.tshk.hlthhbbty.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tshk.hlthhbbty.HlthApp
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Кнопки в уведомлениях и перезапуск напоминаний после перезагрузки. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as HlthApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val userId = c.auth.currentUserId.first()
                when (intent.action) {
                    ACTION_DONE -> {
                        val id = intent.getLongExtra(EXTRA_ID, -1)
                        c.habits.find(id)?.takeIf { it.userId == userId }?.let { c.habits.complete(it, today()) }
                        Notifications.cancel(context, Notifications.habitId(id))
                    }
                    ACTION_WATER -> {
                        if (userId != null) c.water.add(userId, intent.getLongExtra(EXTRA_ID, 250).toInt())
                        Notifications.cancel(context, Notifications.WATER_ID)
                    }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        if (userId != null) c.reminders.rescheduleAll(c.habits.activeOnce(userId), c.settings.current())
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.tshk.hlthhbbty.HABIT_DONE"
        const val ACTION_WATER = "com.tshk.hlthhbbty.ADD_WATER"
        const val EXTRA_ID = "id"
    }
}
