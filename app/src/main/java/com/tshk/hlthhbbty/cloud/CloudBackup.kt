package com.tshk.hlthhbbty.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.tshk.hlthhbbty.data.AppSettings
import com.tshk.hlthhbbty.data.SettingsStore
import com.tshk.hlthhbbty.data.ThemeMode
import com.tshk.hlthhbbty.data.db.AppDatabase
import com.tshk.hlthhbbty.data.db.Gender
import com.tshk.hlthhbbty.data.db.UserEntity
import com.tshk.hlthhbbty.data.db.WalkEntity
import com.tshk.hlthhbbty.data.db.WalkPointEntity
import com.tshk.hlthhbbty.data.repo.BackupRepository
import com.tshk.hlthhbbty.messenger.MessengerRepository
import com.tshk.hlthhbbty.messenger.Privacy
import com.tshk.hlthhbbty.messenger.Visibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class BackupState { OFF, NEEDS_PASSWORD, IDLE, SYNCING, ERROR }

/**
 * Облачная копия всех данных пользователя со сквозным шифрованием.
 * Ключ копии выводится из пароля (PBKDF2) — сервер хранит только шифротекст.
 * Ключ на устройстве хранится зашифрованным ключом Android Keystore.
 * В копию входят: оформление, профиль и фото, «о себе», конфиденциальность, привычки и отметки,
 * вода, заметки, прогулки и закрытый ключ переписки (чтобы старые сообщения читались после переустановки).
 */
class CloudBackup(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val backupRepo: BackupRepository,
    private val messenger: () -> MessengerRepository,
) {
    private val prefs = context.getSharedPreferences("cloud_backup", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _state = MutableStateFlow(BackupState.OFF)
    val state: StateFlow<BackupState> = _state.asStateFlow()
    private val _lastAt = MutableStateFlow(prefs.getLong("last_at", 0))
    val lastAt: StateFlow<Long> = _lastAt.asStateFlow()

    private val wrap: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "backup_wrap_keyset", "hlth_backup_wrap")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://hlth_backup_master")
            .build().keysetHandle
            .let { @Suppress("DEPRECATION") it.getPrimitive(Aead::class.java) }
    }

    // ---------- ключ ----------

    private fun deriveKey(username: String, password: String): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), "hlth-backup-v1:$username".toByteArray(), 150_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    /** Пароль известен (вход/смена пароля) — сохраняем ключ копии на устройстве. */
    fun rememberPassword(username: String, password: String) {
        val key = deriveKey(username, password)
        prefs.edit().putString("key_$username", Base64.encodeToString(wrap.encrypt(key, username.toByteArray()), Base64.NO_WRAP)).apply()
        refreshState(username)
    }

    private fun key(username: String): ByteArray? = prefs.getString("key_$username", null)?.let {
        runCatching { wrap.decrypt(Base64.decode(it, Base64.NO_WRAP), username.toByteArray()) }.getOrNull()
    }

    fun refreshState(username: String?) {
        _state.value = when {
            username == null || messenger().store.account.value == null -> BackupState.OFF
            key(username) == null -> BackupState.NEEDS_PASSWORD
            else -> BackupState.IDLE
        }
    }

    private fun encrypt(key: ByteArray, plain: ByteArray, username: String): ByteArray {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(username.toByteArray())
        return "HBK1".toByteArray() + nonce + c.doFinal(plain)
    }

    private fun decrypt(key: ByteArray, blob: ByteArray, username: String): ByteArray {
        require(blob.size > 16 && String(blob, 0, 4) == "HBK1") { "Неизвестный формат копии" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob.copyOfRange(4, 16)))
        c.updateAAD(username.toByteArray())
        return c.doFinal(blob.copyOfRange(16, blob.size))
    }

    // ---------- HTTP ----------

    private suspend fun upload(token: String, blob: ByteArray) = withContext(Dispatchers.IO) {
        val m = messenger()
        val req = Request.Builder().url(m.store.serverUrl.value.trimEnd('/') + "/v1/me/backup")
            .header("Authorization", "Bearer $token")
            .put(blob.toRequestBody("application/octet-stream".toMediaType())).build()
        m.api.http.newCall(req).execute().use { if (!it.isSuccessful) error("Сервер не принял копию (${it.code})") }
    }

    private suspend fun download(token: String): ByteArray? = withContext(Dispatchers.IO) {
        val m = messenger()
        val req = Request.Builder().url(m.store.serverUrl.value.trimEnd('/') + "/v1/me/backup")
            .header("Authorization", "Bearer $token").get().build()
        m.api.http.newCall(req).execute().use { r ->
            when {
                r.code == 404 -> null
                !r.isSuccessful -> error("Не удалось получить копию (${r.code})")
                else -> r.body!!.bytes()
            }
        }
    }

    // ---------- сохранение ----------

    /** Собрать и отправить копию. Тихо ничего не делает, если нет ключа или аккаунта. */
    suspend fun backupNow(userId: Long): Boolean = lock.withLock {
        val user = db.userDao().findById(userId) ?: return false
        val acc = messenger().store.account.value?.takeIf { it.username == user.username } ?: return false
        val key = key(user.username) ?: run { _state.value = BackupState.NEEDS_PASSWORD; return false }
        _state.value = BackupState.SYNCING
        return runCatching {
            val json = build(user)
            upload(acc.token, encrypt(key, json.toString().toByteArray(), user.username))
            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_at", now).apply()
            _lastAt.value = now
            _state.value = BackupState.IDLE
            true
        }.getOrElse { _state.value = BackupState.ERROR; false }
    }

    private suspend fun build(user: UserEntity): JSONObject = withContext(Dispatchers.IO) {
        val m = messenger()
        val s = settings.current()
        val p = m.store.privacy.value
        val walks = db.walkDao().allForUser(user.id)
        JSONObject()
            .put("format", "hlth-cloud").put("v", 1).put("createdAt", System.currentTimeMillis())
            .put("settings", JSONObject()
                .put("themeMode", s.themeMode.name).put("accentIndex", s.accentIndex)
                .put("waterReminders", s.waterReminders).put("waterIntervalHours", s.waterIntervalHours)
                .put("waterFromHour", s.waterFromHour).put("waterToHour", s.waterToHour)
                .put("animatedBackground", s.animatedBackground).put("bugButton", s.bugButton)
                .put("betaChannel", s.betaChannel).put("autoUpdate", s.autoUpdate))
            .put("profile", JSONObject()
                .put("displayName", user.displayName).put("age", user.age ?: JSONObject.NULL)
                .put("gender", user.gender?.name ?: JSONObject.NULL).put("waterGoalMl", user.waterGoalMl)
                .put("profileCompleted", user.profileCompleted)
                .put("avatar", when {
                    user.avatar?.startsWith("preset:") == true -> user.avatar
                    user.avatar?.startsWith("file:") == true -> avatarJpeg(user.avatar.removePrefix("file:"))?.let { "jpeg:$it" } ?: JSONObject.NULL
                    else -> JSONObject.NULL
                }))
            .put("privacy", JSONObject()
                .put("discoverable", p.discoverable).put("bio", p.bio).put("streak", p.streak.name)
                .put("achievements", p.achievements.name).put("habits", p.habits.name).put("water", p.water.name)
                .put("nowPlaying", p.nowPlaying.name))
            .put("myStatus", m.store.myStatus.value)
            .put("pins", JSONArray(m.store.pins.value.toList()))
            .put("data", backupRepo.exportJson(user.id))
            .put("walks", JSONArray(walks.map { w ->
                JSONObject().put("startedAt", w.startedAt).put("durationMs", w.durationMs).put("distanceM", w.distanceM).put("steps", w.steps)
                    .put("points", JSONArray(db.walkDao().points(w.id).map { JSONArray().put(it.lat).put(it.lon).put(it.time) }))
            }))
            .put("e2eKey", Base64.encodeToString(m.crypto.exportPrivateKey(user.username), Base64.NO_WRAP))
    }

    private fun avatarJpeg(path: String): String? = runCatching {
        val src = BitmapFactory.decodeFile(path) ?: return null
        val scale = 512f / maxOf(src.width, src.height)
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    // ---------- восстановление ----------

    /**
     * После входа на устройстве (пароль известен): скачать и применить копию.
     * Возвращает true, если копия была и применена.
     */
    suspend fun restore(userId: Long, username: String, token: String, password: String): Boolean = lock.withLock {
        val key = deriveKey(username, password)
        val blob = runCatching { download(token) }.getOrNull() ?: return false
        val json = runCatching { JSONObject(String(decrypt(key, blob, username))) }.getOrElse { return false }
        apply(userId, username, json)
        true
    }

    private suspend fun apply(userId: Long, username: String, j: JSONObject) = withContext(Dispatchers.IO) {
        val m = messenger()
        j.optJSONObject("settings")?.let { st ->
            settings.update {
                AppSettings(
                    themeMode = runCatching { ThemeMode.valueOf(st.optString("themeMode")) }.getOrDefault(it.themeMode),
                    accentIndex = st.optInt("accentIndex", it.accentIndex),
                    waterReminders = st.optBoolean("waterReminders", it.waterReminders),
                    waterIntervalHours = st.optInt("waterIntervalHours", it.waterIntervalHours),
                    waterFromHour = st.optInt("waterFromHour", it.waterFromHour),
                    waterToHour = st.optInt("waterToHour", it.waterToHour),
                    healthConnect = it.healthConnect,
                    autoUpdate = st.optBoolean("autoUpdate", it.autoUpdate),
                    bugButton = st.optBoolean("bugButton", it.bugButton),
                    animatedBackground = st.optBoolean("animatedBackground", it.animatedBackground),
                    betaChannel = st.optBoolean("betaChannel", it.betaChannel),
                )
            }
        }
        j.optJSONObject("profile")?.let { pr ->
            val user = db.userDao().findById(userId) ?: return@let
            val avatarRaw = pr.optString("avatar")
            val avatar = when {
                avatarRaw.startsWith("preset:") -> avatarRaw
                avatarRaw.startsWith("jpeg:") -> runCatching {
                    val dir = File(context.filesDir, "avatars").apply { mkdirs() }
                    val f = File(dir, "user_${userId}_restored.jpg")
                    f.writeBytes(Base64.decode(avatarRaw.removePrefix("jpeg:"), Base64.NO_WRAP))
                    "file:${f.absolutePath}"
                }.getOrNull()
                else -> user.avatar
            }
            db.userDao().update(user.copy(
                displayName = pr.optString("displayName", user.displayName),
                age = if (pr.isNull("age")) null else pr.optInt("age"),
                gender = pr.optString("gender").takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { Gender.valueOf(it) }.getOrNull() },
                waterGoalMl = pr.optInt("waterGoalMl", user.waterGoalMl),
                profileCompleted = pr.optBoolean("profileCompleted", true),
                avatar = avatar,
            ))
        }
        j.optJSONObject("privacy")?.let { pv ->
            fun vis(k: String, d: Visibility) = runCatching { Visibility.valueOf(pv.optString(k)) }.getOrDefault(d)
            m.store.savePrivacy(username, Privacy(
                discoverable = pv.optBoolean("discoverable", true), bio = pv.optString("bio"),
                streak = vis("streak", Visibility.ALL), achievements = vis("achievements", Visibility.ALL),
                habits = vis("habits", Visibility.CONTACTS), water = vis("water", Visibility.NOBODY),
                nowPlaying = vis("nowPlaying", Visibility.ALL),
            ))
        }
        j.optString("myStatus").takeIf { it == "ONLINE" || it == "DND" }?.let { m.store.setMyStatus(it) }
        j.optJSONArray("pins")?.let { arr -> m.store.setPins((0 until arr.length()).map { arr.getString(it) }.toSet()) }
        j.optJSONObject("data")?.let { backupRepo.importJson(userId, it) }
        j.optJSONArray("walks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val w = arr.getJSONObject(i)
                val id = db.walkDao().insert(WalkEntity(userId = userId, habitId = null, startedAt = w.optLong("startedAt"),
                    durationMs = w.optLong("durationMs"), distanceM = w.optDouble("distanceM"), steps = w.optInt("steps")))
                val pts = w.optJSONArray("points") ?: JSONArray()
                db.walkDao().insertPoints((0 until pts.length()).map { k ->
                    val pt = pts.getJSONArray(k)
                    WalkPointEntity(id, k, pt.getDouble(0), pt.getDouble(1), pt.getLong(2))
                })
            }
        }
        j.optString("e2eKey").takeIf { it.isNotBlank() }?.let {
            runCatching { m.crypto.importPrivateKey(username, Base64.decode(it, Base64.NO_WRAP)) }
        }
    }

    fun forget(username: String) = prefs.edit().remove("key_$username").apply()
}
