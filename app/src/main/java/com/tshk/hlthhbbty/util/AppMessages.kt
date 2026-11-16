package com.tshk.hlthhbbty.util

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tshk.hlthhbbty.MainActivity
import com.tshk.hlthhbbty.R
import com.tshk.hlthhbbty.reminders.Notifications

/** Кнопка в уведомлении, открывающая ссылку. */
data class MessageAction(val label: String, val url: String)

/**
 * Сообщение приложения в панель уведомлений: полный текст (раскрывается), заголовок,
 * необязательная кнопка-ссылка. Без разрешения на уведомления — длинный Toast.
 */
@SuppressLint("MissingPermission")
fun Context.showMessage(title: String, text: String, action: MessageAction? = null) {
    if (!Notifications.canPost(this)) {
        Toast.makeText(this, "$title: $text", Toast.LENGTH_LONG).show()
        return
    }
    val open = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_MESSAGES)
        .setSmallIcon(R.drawable.ic_stat_leaf)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(open)
        .setAutoCancel(true)
    if (action != null) {
        val pi = PendingIntent.getActivity(
            this, action.url.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(0, action.label, pi)
    }
    // Одинаковые сообщения заменяют друг друга, а не копятся.
    NotificationManagerCompat.from(this).notify(20_000 + (title + text).hashCode() % 10_000, builder.build())
}

/** Ошибки Spotify с подсказкой, что делать. */
fun Context.showSpotifyMessage(text: String) {
    val action = when {
        text.contains("User Management", true) || text.contains("не добавлен", true) ->
            MessageAction("Открыть Spotify Dashboard", "https://developer.spotify.com/dashboard")
        text.contains("Premium", true) -> MessageAction("Spotify Premium", "https://www.spotify.com/premium/")
        else -> null
    }
    showMessage("Spotify", text, action)
}
