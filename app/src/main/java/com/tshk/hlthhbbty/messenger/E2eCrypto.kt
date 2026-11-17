package com.tshk.hlthhbbty.messenger

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.MessageDigest

/**
 * Сквозное шифрование сообщений (HPKE: X25519 + HKDF-SHA256 + AES-256-GCM, библиотека Google Tink).
 * Закрытый ключ создаётся на устройстве и хранится зашифрованным мастер-ключом Android Keystore —
 * на сервер уходит только открытый ключ. Каждое сообщение шифруется дважды: для получателя и для себя.
 */
class E2eCrypto(private val context: Context) {
    init {
        HybridConfig.register()
    }

    private val handles = HashMap<String, KeysetHandle>()

    private fun privateHandle(username: String): KeysetHandle = handles.getOrPut(username) {
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "e2e_keyset_$username", "hlth_e2e")
            .withKeyTemplate(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"))
            .withMasterKeyUri("android-keystore://hlth_e2e_master")
            .build()
            .keysetHandle
    }

    /** Открытый ключ пользователя (создаётся при первом вызове). */
    fun publicKey(username: String): String {
        val pub = privateHandle(username).publicKeysetHandle
        return Base64.encodeToString(TinkProtoKeysetFormat.serializeKeysetWithoutSecret(pub), Base64.NO_WRAP)
    }

    private fun context(from: String, to: String) = "hlth-e2e-v1:$from:$to".toByteArray()

    fun encrypt(recipientPublicKey: String, plaintext: String, from: String, to: String): String {
        val handle = TinkProtoKeysetFormat.parseKeysetWithoutSecret(Base64.decode(recipientPublicKey, Base64.NO_WRAP))
        @Suppress("DEPRECATION")
        val enc = handle.getPrimitive(HybridEncrypt::class.java)
        return Base64.encodeToString(enc.encrypt(plaintext.toByteArray(), context(from, to)), Base64.NO_WRAP)
    }

    fun decrypt(me: String, ciphertext: String, from: String, to: String): String? = runCatching {
        @Suppress("DEPRECATION")
        val dec = privateHandle(me).getPrimitive(HybridDecrypt::class.java)
        String(dec.decrypt(Base64.decode(ciphertext, Base64.NO_WRAP), context(from, to)))
    }.getOrNull()

    /** Код безопасности чата: одинаков у обоих собеседников, если ключи не подменены. */
    fun safetyCode(keyA: String, keyB: String): String {
        val (x, y) = listOf(keyA, keyB).sorted()
        val digest = MessageDigest.getInstance("SHA-256").digest((x + "|" + y).toByteArray())
        return digest.take(12).joinToString("") { "%02d".format((it.toInt() and 0xFF) % 100) }.chunked(4).joinToString(" ")
    }
}
