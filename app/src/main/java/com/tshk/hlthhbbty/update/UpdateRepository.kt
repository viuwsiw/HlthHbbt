package com.tshk.hlthhbbty.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tshk.hlthhbbty.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int?,
    val apkUrl: String,
    val changelog: String,
    val sizeBytes: Long?,
    val beta: Boolean = false,
)

/**
 * Проверка обновлений в репозитории GitHub:
 * 1) последний GitHub Release с APK во вложениях;
 * 2) иначе — update.json в ветке по умолчанию.
 */
class UpdateRepository(private val context: Context) {

    private val owner = "viuwsiw"
    private val repo = "HlthHbbt"

    /**
     * Проверка обновлений по каналу. Релизный: GitHub Release + update.json.
     * Бета: дополнительно update-beta.json — ставится то, что новее (бета или релиз).
     */
    suspend fun check(beta: Boolean = false): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val candidates = listOfNotNull(
                runCatching { fromRelease() }.getOrNull(),
                runCatching { fromManifest("update.json", beta = false) }.getOrNull(),
                if (beta) runCatching { fromManifest("update-beta.json", beta = true) }.getOrNull() else null,
            )
            candidates.filter { isNewer(it) }.maxWithOrNull(
                compareBy<UpdateInfo> { it.versionCode ?: 0 }.thenComparator { a, b -> compareVersions(a.versionName, b.versionName) }
            )
        }
    }

    private fun fromRelease(): UpdateInfo? {
        val json = JSONObject(httpGet("https://api.github.com/repos/$owner/$repo/releases/latest", accept = "application/vnd.github+json") ?: return null)
        val assets = json.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
        return UpdateInfo(
            versionName = json.optString("tag_name").removePrefix("v").removePrefix("V"),
            versionCode = null,
            apkUrl = apk.getString("browser_download_url"),
            changelog = json.optString("body").trim(),
            sizeBytes = apk.optLong("size").takeIf { it > 0 },
        )
    }

    private fun fromManifest(file: String, beta: Boolean): UpdateInfo? {
        // API GitHub отдаёт свежий файл; raw-ссылка кэшируется CDN до 5 минут — она запасная.
        val text = runCatching {
            httpGet("https://api.github.com/repos/$owner/$repo/contents/$file", accept = "application/vnd.github.raw+json")
        }.getOrNull() ?: httpGet("https://raw.githubusercontent.com/$owner/$repo/HEAD/$file?t=${System.currentTimeMillis()}")
        val json = JSONObject(text ?: return null)
        return UpdateInfo(
            versionName = json.getString("versionName"),
            versionCode = json.optInt("versionCode").takeIf { it > 0 },
            apkUrl = json.getString("apkUrl"),
            changelog = json.optString("changelog").trim(),
            sizeBytes = json.optLong("size").takeIf { it > 0 },
            beta = beta,
        )
    }

    /** Опубликованные версии по каналам — для админ-панели. */
    suspend fun channels(): Map<String, UpdateInfo?> = withContext(Dispatchers.IO) {
        mapOf(
            "release" to runCatching { fromManifest("update.json", beta = false) }.getOrNull(),
            "beta" to runCatching { fromManifest("update-beta.json", beta = true) }.getOrNull(),
            "githubRelease" to runCatching { fromRelease() }.getOrNull(),
        )
    }

    private fun isNewer(info: UpdateInfo): Boolean =
        info.versionCode?.let { it > BuildConfig.VERSION_CODE }
            ?: (compareVersions(info.versionName, BuildConfig.VERSION_NAME) > 0)

    /**
     * Скачивает APK и проверяет, что внутри действительно новая версия.
     * Сначала через API GitHub (без кэша CDN), затем по прямой ссылке с защитой от кэша.
     */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "hlth-hbty-${info.versionName}.apk")
        val sources = listOfNotNull(apiUrlFor(info.apkUrl), cacheBusted(info.apkUrl))
        var lastError: Throwable? = null
        for (src in sources) {
            val ok = runCatching { fetch(src, file, info, onProgress) }.onFailure { lastError = it }.isSuccess
            if (ok && isNewerArchive(file)) return@withContext file
            if (ok) lastError = IllegalStateException("Скачалась старая версия (кэш GitHub). Попробуйте через пару минут.")
        }
        file.delete()
        throw lastError ?: IllegalStateException("Не удалось скачать обновление")
    }

    /** raw.githubusercontent.com/OWNER/REPO/REF/path → api.github.com/repos/OWNER/REPO/contents/path?ref=REF */
    private fun apiUrlFor(url: String): String? {
        val m = Regex("https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/([^?]+)").find(url) ?: return null
        val (o, r, ref, path) = m.destructured
        return "https://api.github.com/repos/$o/$r/contents/$path" + (if (ref != "HEAD") "?ref=$ref" else "")
    }

    private fun cacheBusted(url: String) = url + (if ('?' in url) "&" else "?") + "t=" + System.currentTimeMillis()

    /** Версия внутри скачанного APK новее установленной? */
    private fun isNewerArchive(file: File): Boolean {
        val pi = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return false
        if (pi.packageName != context.packageName) return false
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        return code > BuildConfig.VERSION_CODE
    }

    private fun fetch(src: String, file: File, info: UpdateInfo, onProgress: (Float) -> Unit) {
        var url = URL(src)
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", "HLTH-HBTy-Android")
                setRequestProperty("Cache-Control", "no-cache")
                if (url.host == "api.github.com") setRequestProperty("Accept", "application/vnd.github.raw")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects++ < 5) {
                url = URL(url, conn.getHeaderField("Location"))
                conn.disconnect()
                continue
            }
            if (code !in 200..299) error("Ошибка загрузки: HTTP $code")
            break
        }
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes ?: -1L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettings(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun httpGet(url: String, accept: String? = null): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "HLTH-HBTy-Android")
            if (accept != null) setRequestProperty("Accept", accept)
            setRequestProperty("Cache-Control", "no-cache")
            useCaches = false
        }
        return try {
            if (conn.responseCode == 404) null
            else if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            else conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Сравнение версий вида 2.10.1 по числовым компонентам. */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.', '-', ' ').mapNotNull { it.toIntOrNull() }
            val pb = b.split('.', '-', ' ').mapNotNull { it.toIntOrNull() }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }
    }
}
