package com.tshk.hlthhbbty.focus

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.tshk.hlthhbbty.HlthApp
import com.tshk.hlthhbbty.MainActivity
import com.tshk.hlthhbbty.R
import com.tshk.hlthhbbty.reminders.Notifications
import com.tshk.hlthhbbty.util.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Служба таймера фокуса: держит постоянное уведомление с обратным отсчётом,
 * работает при свёрнутом и закрытом приложении, по окончании отмечает привычку.
 */
class FocusTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val timer get() = (application as HlthApp).container.focusTimer
    private val finishRunnable = Runnable { finish() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> timer.pause()
            ACTION_RESUME -> timer.resume()
            ACTION_STOP -> timer.stop()
        }
        sync()
        return START_STICKY
    }

    private fun sync() {
        timer.reload()
        handler.removeCallbacks(finishRunnable)
        val s = timer.state.value
        if (s == null || s.finished) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(s), type)
        if (s.running) handler.postDelayed(finishRunnable, s.remainingMs())
    }

    private fun finish() {
        val s = timer.state.value ?: return
        timer.markFinished()
        val c = (application as HlthApp).container
        scope.launch {
            c.habits.find(s.habitId)?.let { c.habits.complete(it, today()) }
        }
        vibrate()
        showDone(s)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(s: FocusTimerState): Notification {
        val running = s.running
        val b = NotificationCompat.Builder(this, Notifications.CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle("${s.emoji} ${s.title}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
        if (running) {
            b.setContentText("Фокус: идёт таймер")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(s.endAt ?: System.currentTimeMillis())
                .setShowWhen(true)
                .addAction(0, "Пауза", action(ACTION_PAUSE, 1))
        } else {
            val left = s.remainingMs() / 1000
            b.setContentText("На паузе · осталось %d:%02d".format(left / 60, left % 60))
                .setShowWhen(false)
                .addAction(0, "Продолжить", action(ACTION_RESUME, 2))
        }
        b.addAction(0, "Стоп", action(ACTION_STOP, 3))
        return b.build()
    }

    @SuppressLint("MissingPermission")
    private fun showDone(s: FocusTimerState) {
        if (!Notifications.canPost(this)) return
        val n = NotificationCompat.Builder(this, Notifications.CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle("🎉 ${s.title} — готово!")
            .setContentText("Таймер завершён, привычка отмечена.")
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(DONE_ID, n)
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1))
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 30,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_FOCUS, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, FocusTimerService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        handler.removeCallbacks(finishRunnable)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SYNC = "focus.sync"
        const val ACTION_PAUSE = "focus.pause"
        const val ACTION_RESUME = "focus.resume"
        const val ACTION_STOP = "focus.stop"
        private const val NOTIFICATION_ID = 4242
        private const val DONE_ID = 4243
    }
}
