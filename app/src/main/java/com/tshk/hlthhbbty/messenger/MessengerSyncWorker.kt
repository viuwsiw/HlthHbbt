package com.tshk.hlthhbbty.messenger

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tshk.hlthhbbty.HlthApp
import java.util.concurrent.TimeUnit

/** Проверка новых сообщений, когда приложение закрыто (раз в 15 минут — минимум Android). */
class MessengerSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val m = (applicationContext as HlthApp).container.messenger
        if (m.store.account.value == null) return Result.success()
        runCatching { m.sync() }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<MessengerSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("messenger_sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
