package com.tshk.hlthhbbty.media

import android.service.notification.NotificationListenerService

/**
 * Пустой слушатель уведомлений. Нужен только для того, чтобы Android разрешил
 * приложению видеть активные медиасессии (что играет в Spotify, Яндекс Музыке и т.д.).
 * Содержимое уведомлений не читается и никуда не передаётся.
 */
class MediaListenerService : NotificationListenerService()
