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
    const val CHANNEL_FOCUS = "focus_timer"
    const val CHANNEL_CHAT = "chat"
    const val WATER_ID = 1

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_HABITS, "Привычки", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Напоминания о привычках" },
            NotificationChannel(CHANNEL_WATER, "Вода", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Напоминания попить воды" },
            NotificationChannel(CHANNEL_ACHIEVEMENTS, "Достижения", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_CHAT, "Сообщения", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Новые сообщения в мессенджере" },
            NotificationChannel(CHANNEL_FOCUS, "Таймер фокуса", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Идущий таймер фокуса"; setShowBadge(false) },
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
            .setSmallIcon(R.drawable.ic_stat_logo)
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
            .setSmallIcon(R.drawable.ic_stat_logo)
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
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle("$emoji Новое достижение!")
            .setContentText(title)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(5000 + id, n)
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun showChat(context: Context, peer: String, text: String, title: String = "@$peer", sender: String = peer) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, peer.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_WIDGET_ROUTE, "chat:$peer")
                .setData(android.net.Uri.parse("hlthhbbty://chat/$peer")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(title)
            .setContentText(if (peer.startsWith("g:")) "@$sender: $text" else text)
            .setStyle(
                NotificationCompat.MessagingStyle(androidx.core.app.Person.Builder().setName("Вы").build())
                    .setConversationTitle(if (peer.startsWith("g:")) title else null)
                    .setGroupConversation(peer.startsWith("g:"))
                    .addMessage(text, System.currentTimeMillis(), androidx.core.app.Person.Builder().setName(if (peer.startsWith("g:")) "@$sender" else title).build())
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(7000 + (peer.hashCode() and 0xFFF), n)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
