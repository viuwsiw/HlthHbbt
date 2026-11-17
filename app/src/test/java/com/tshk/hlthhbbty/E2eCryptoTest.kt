package com.tshk.hlthhbbty

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.hybrid.HybridConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Та же схема, что в E2eCrypto: HPKE X25519/AES-256-GCM, открытый ключ передаётся как сериализованный keyset. */
class E2eCryptoTest {
    init { HybridConfig.register() }

    private fun newKey() = KeysetHandle.generateNew(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"))
    private fun pub(h: KeysetHandle) = TinkProtoKeysetFormat.serializeKeysetWithoutSecret(h.publicKeysetHandle)

    @Suppress("DEPRECATION")
    private fun enc(pub: ByteArray, msg: String, ctx: String) =
        TinkProtoKeysetFormat.parseKeysetWithoutSecret(pub).getPrimitive(HybridEncrypt::class.java).encrypt(msg.toByteArray(), ctx.toByteArray())

    @Suppress("DEPRECATION")
    private fun dec(h: KeysetHandle, ct: ByteArray, ctx: String) =
        runCatching { String(h.getPrimitive(HybridDecrypt::class.java).decrypt(ct, ctx.toByteArray())) }.getOrNull()

    @Test
    fun roundTripAndIsolation() {
        val anton = newKey()
        val maria = newKey()
        val eve = newKey()
        val ctx = "hlth-e2e-v1:anton:maria"
        val ct = enc(pub(maria), "Привет, Мария! 💧", ctx)

        assertEquals("Привет, Мария! 💧", dec(maria, ct, ctx))       // получатель читает
        assertEquals(null, dec(anton, ct, ctx))                        // отправитель — только свою копию
        assertEquals(null, dec(eve, ct, ctx))                          // посторонний ключ — нет
        assertEquals(null, dec(maria, ct, "hlth-e2e-v1:eve:maria"))    // подмена отправителя ломает расшифровку

        val ct2 = enc(pub(maria), "Привет, Мария! 💧", ctx)
        assertNotEquals(ct.toList(), ct2.toList())                     // одинаковый текст — разный шифротекст
        assertTrue(pub(maria).size < 200)                              // открытый ключ компактный
    }
}
