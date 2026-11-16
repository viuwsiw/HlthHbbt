package com.tshk.hlthhbbty.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tshk.hlthhbbty.data.AppSettings
import com.tshk.hlthhbbty.data.db.HabitEntity
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class ReminderScheduler(context: Context) {
    private val wm = WorkManager.getInstance(context)

    private fun habitWork(id: Long) = "habit_reminder_$id"

    fun scheduleHabit(habit: HabitEntity) {
        val minutes = habit.reminderMinutes
        if (minutes == null || habit.isQuit || habit.archived) {
            cancelHabit(habit.id)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(minutes / 60, minutes % 60))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = java.time.Duration.between(now, next).toMillis()
        val request = OneTimeWorkRequestBuilder<HabitReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(HabitReminderWorker.KEY_HABIT to habit.id))
            .addTag(TAG)
            .build()
        wm.enqueueUniqueWork(habitWork(habit.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelHabit(habitId: Long) {
        wm.cancelUniqueWork(habitWork(habitId))
    }

    fun scheduleWater(settings: AppSettings) {
        if (!settings.waterReminders) {
            wm.cancelUniqueWork(WATER_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<WaterReminderWorker>(settings.waterIntervalHours.toLong(), TimeUnit.HOURS)
            .setInitialDelay(settings.waterIntervalHours.toLong(), TimeUnit.HOURS)
            .addTag(TAG)
            .build()
        wm.enqueueUniquePeriodicWork(WATER_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleHealthSync() {
        val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(1, TimeUnit.HOURS).addTag(TAG).build()
        wm.enqueueUniquePeriodicWork(HEALTH_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelHealthSync() = wm.cancelUniqueWork(HEALTH_WORK)

    fun rescheduleAll(habits: List<HabitEntity>, settings: AppSettings) {
        habits.forEach(::scheduleHabit)
        scheduleWater(settings)
        if (settings.healthConnect) scheduleHealthSync()
    }

    fun cancelAll() = wm.cancelAllWorkByTag(TAG)

    companion object {
        const val TAG = "hlth_reminders"
        private const val WATER_WORK = "water_reminder"
        private const val HEALTH_WORK = "health_sync"
    }
}
