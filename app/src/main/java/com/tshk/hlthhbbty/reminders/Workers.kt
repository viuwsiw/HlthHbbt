package com.tshk.hlthhbbty.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tshk.hlthhbbty.HlthApp
import com.tshk.hlthhbbty.util.scheduledOn
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.flow.first
import java.time.LocalTime

private val Context.container get() = (applicationContext as HlthApp).container

class HabitReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val habit = c.habits.find(inputData.getLong(KEY_HABIT, -1)) ?: return Result.success()
        val userId = c.auth.currentUserId.first()
        if (userId == habit.userId) {
            val day = today()
            if (habit.scheduledOn(day) && c.habits.countFor(habit.id, day) < habit.goalPerDay) {
                Notifications.showHabit(applicationContext, habit.id, habit.title, habit.emoji)
            }
            c.reminders.scheduleHabit(habit) // на следующий день
        }
        return Result.success()
    }

    companion object {
        const val KEY_HABIT = "habit_id"
    }
}

class WaterReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val settings = c.settings.current()
        val userId = c.auth.currentUserId.first() ?: return Result.success()
        val hour = LocalTime.now().hour
        if (hour in settings.waterFromHour until settings.waterToHour) {
            val user = c.users.observe(userId).first() ?: return Result.success()
            val current = c.water.todayOnce(userId)
            if (current < user.waterGoalMl) Notifications.showWater(applicationContext, current, user.waterGoalMl)
        }
        return Result.success()
    }
}

class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val userId = c.auth.currentUserId.first() ?: return Result.success()
        runCatching { c.health.syncHabits(userId) }
        return Result.success()
    }
}
