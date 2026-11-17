package com.tshk.hlthhbbty.messenger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tshk.hlthhbbty.MainActivity
import com.tshk.hlthhbbty.R

/**
 * Держит соединение с сервером мессенджера, пока приложение свёрнуто, —
 * чтобы уведомления о сообщениях приходили сразу (без Google-пушей).
 */
class MessengerLinkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Мессенджер на связи", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                description = "Фоновое соединение для мгновенных уведомлений о сообщениях"
            })
        }
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle("Мессенджер на связи")
            .setContentText("Уведомления о сообщениях приходят сразу")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        runCatching { ServiceCompat.startForeground(this, ID, n, type) }.onFailure { stopSelf() }
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "messenger_link"
        private const val ID = 7999

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MessengerLinkService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, MessengerLinkService::class.java)) }
        }
    }
}
