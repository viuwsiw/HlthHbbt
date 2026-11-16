package com.tshk.hlthhbbty.bedtime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.data.db.HabitKind
import com.tshk.hlthhbbty.data.repo.HabitRepository
import com.tshk.hlthhbbty.util.createdDay
import com.tshk.hlthhbbty.util.isScheduled
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * «Лечь спать до N:00»: начиная с N:00 привычка считается выполненной, но если телефон
 * разблокировали в N:00 и позже (до 05:00 утра), отметка снимается. Источник — история
 * использования Android (нужен «Доступ к истории использования»).
 */
class BedtimeChecker(private val context: Context, private val habits: HabitRepository) {

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun accessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Пересчитать отметки для сегодняшней и вчерашней ночи. */
    suspend fun evaluate(userId: Long) {
        if (!hasAccess()) return
        val list = habits.activeOnce(userId).filter { it.healthMetric == HabitKind.METRIC_BEDTIME }
        if (list.isEmpty()) return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val nowMs = System.currentTimeMillis()
        list.forEach { h ->
            val threshold = h.healthTarget ?: (23 * 60)
            for (back in 0..1) {
                val day = LocalDate.now().minusDays(back.toLong())
                val epochDay = day.toEpochDay()
                if (epochDay < h.createdDay() || !isScheduled(h.scheduleMask, epochDay)) continue
                val start = day.atTime(threshold / 60, threshold % 60)
                if (now.isBefore(start)) continue // ещё не наступило время отбоя
                val end = minOf(day.plusDays(1).atTime(5, 0).atZone(zone).toInstant().toEpochMilli(), nowMs)
                val used = phoneUsedBetween(start.atZone(zone).toInstant().toEpochMilli(), end)
                habits.setDone(h, epochDay, done = !used)
            }
        }
    }

    /** Был ли телефон разблокирован/включён в интервале. */
    private fun phoneUsedBetween(from: Long, to: Long): Boolean {
        if (to <= from) return false
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return false
        val events = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = e.eventType
            if (Build.VERSION.SDK_INT >= 28) {
                if (type == UsageEvents.Event.KEYGUARD_HIDDEN || type == UsageEvents.Event.SCREEN_INTERACTIVE) return true
            } else if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                return true
            }
        }
        return false
    }

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<BedtimeWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("bedtime_check", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        fun isBedtime(h: HabitEntity) = h.healthMetric == HabitKind.METRIC_BEDTIME
    }
}

class BedtimeWorker(context: Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as com.tshk.hlthhbbty.HlthApp).container
        val userId = c.auth.currentUserId.first() ?: return Result.success()
        runCatching { c.bedtime.evaluate(userId) }
        return Result.success()
    }
}
