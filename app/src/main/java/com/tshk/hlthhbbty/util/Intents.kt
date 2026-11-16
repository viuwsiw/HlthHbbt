package com.tshk.hlthhbbty.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.tshk.hlthhbbty.BuildConfig

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        showMessage("HLTH HBTy", "Не найдено приложение для открытия ссылки")
    }
}

fun Context.isAppInstalled(packageName: String): Boolean = try {
    packageManager.getPackageInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

fun Context.sendFeedback() {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf("yudinanthony@yandex.ru"))
        putExtra(Intent.EXTRA_SUBJECT, "HLTH HBTy — обращение")
        putExtra(Intent.EXTRA_TEXT, "Версия: ${BuildConfig.VERSION_NAME}\nСуть обращения: ")
    }
    try {
        startActivity(Intent.createChooser(intent, "Написать разработчику"))
    } catch (_: ActivityNotFoundException) {
        showMessage("HLTH HBTy", "Не найдено почтовое приложение")
    }
}

fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, "Поделиться"))
}
