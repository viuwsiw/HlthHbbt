package com.tshk.hlthhbbty.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tshk.hlthhbbty.HlthApp
import com.tshk.hlthhbbty.MainActivity
import com.tshk.hlthhbbty.R
import com.tshk.hlthhbbty.reminders.Notifications
import java.util.concurrent.TimeUnit

/** Раз в сутки проверяет GitHub и присылает уведомление о новой версии. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val c = (applicationContext as HlthApp).container
        if (!c.settings.current().autoUpdate) return Result.success()
        val info = c.updates.check().getOrNull() ?: return Result.success()
        if (!Notifications.canPost(applicationContext)) return Result.success()
        val open = PendingIntent.getActivity(
            applicationContext, 7,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_ACHIEVEMENTS)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle("Доступна версия ${info.versionName}")
            .setContentText("Нажмите, чтобы обновить HLTH HBTy")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(9000, n)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("update_check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
