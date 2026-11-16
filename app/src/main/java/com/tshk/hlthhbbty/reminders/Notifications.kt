package com.tshk.hlthhbbty.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tshk.hlthhbbty.MainActivity
import com.tshk.hlthhbbty.R

object Notifications {
    const val CHANNEL_HABITS = "habits"
    const val CHANNEL_WATER = "water"
    const val CHANNEL_ACHIEVEMENTS = "achievements"
    const val CHANNEL_MESSAGES = "messages"
    const val WATER_ID = 1

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_HABITS, "Привычки", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Напоминания о привычках" },
            NotificationChannel(CHANNEL_WATER, "Вода", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Напоминания попить воды" },
            NotificationChannel(CHANNEL_ACHIEVEMENTS, "Достижения", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_MESSAGES, "Сообщения приложения", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Ошибки, подсказки и статусы (Spotify, обновления и т.д.)" },
        ))
    }

    fun canPost(context: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String, id: Int, extra: Long = 0) = PendingIntent.getBroadcast(
        context, id,
        Intent(context, ReminderActionReceiver::class.java).setAction(action).putExtra(ReminderActionReceiver.EXTRA_ID, extra),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun habitId(habitId: Long) = 1000 + habitId.toInt()

    @android.annotation.SuppressLint("MissingPermission")
    fun showHabit(context: Context, habitId: Long, title: String, emoji: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle("$emoji $title")
            .setContentText("Самое время — не прерывайте серию!")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .addAction(0, "Выполнено ✓", action(context, ReminderActionReceiver.ACTION_DONE, habitId(habitId), habitId))
            .build()
        NotificationManagerCompat.from(context).notify(habitId(habitId), n)
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun showWater(context: Context, currentMl: Int, goalMl: Int) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_WATER)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle("💧 Пора попить воды")
            .setContentText("Сегодня: $currentMl из $goalMl мл")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .addAction(0, "+250 мл", action(context, ReminderActionReceiver.ACTION_WATER, WATER_ID, 250))
            .build()
        NotificationManagerCompat.from(context).notify(WATER_ID, n)
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun showAchievement(context: Context, id: Int, title: String, emoji: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_ACHIEVEMENTS)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle("$emoji Новое достижение!")
            .setContentText(title)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(5000 + id, n)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
