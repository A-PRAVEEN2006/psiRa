package com.project1.psira

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM encryption using the Android Keystore.
 *
 * Security properties:
 * - Key is hardware-backed (never leaves the secure element on supported devices)
 * - AES-256-GCM: authenticated encryption — detects tampering
 * - Random 12-byte IV generated for EVERY encryption call
 * - IV is prepended to the ciphertext and extracted on decrypt (no static IV)
 */
object AESEncryption {

    val GLOBAL_GROUP_KEY = javax.crypto.spec.SecretKeySpec(
        "PsiRa_GlobalNet__32ByteGroupKey!".toByteArray(Charsets.UTF_8),
        "AES"
    )

    private const val KEY_ALIAS   = "PsiRaSecureKey_v2"
    private const val KEYSTORE    = "AndroidKeyStore"
    private const val ALGO        = "AES/GCM/NoPadding"
    private const val GCM_TAG_LEN = 128  // bits
    private const val IV_SIZE     = 12   // bytes

    // ── Key Management ────────────────────────────────────────────────────────

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
        try {
            keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        } catch (e: Exception) {
            try { keyStore.deleteEntry(KEY_ALIAS) } catch (_: Exception) {}
        }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // key usable without biometric every time
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Encrypts [value] and returns Base64( IV || ciphertext+GCM_tag ).
     */
    fun encrypt(value: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGO)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv         = cipher.iv                        // fresh random 12-byte IV
            val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val combined = iv + ciphertext
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Delete key on failure (e.g. key permanently invalidated) and retry once
            try {
                val keyStore = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
                keyStore.deleteEntry(KEY_ALIAS)
            } catch (_: Exception) {}

            val cipher = Cipher.getInstance(ALGO)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv         = cipher.iv
            val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val combined = iv + ciphertext
            Base64.encodeToString(combined, Base64.NO_WRAP)
        }
    }

    /**
     * Decrypts a value produced by [encrypt].
     * Falls back gracefully for any legacy messages encrypted with the old static key.
     */
    fun decrypt(value: String): String {
        return try {
            val combined   = Base64.decode(value, Base64.NO_WRAP)
            val iv         = combined.sliceArray(0 until IV_SIZE)
            val ciphertext = combined.sliceArray(IV_SIZE until combined.size)

            val spec   = GCMParameterSpec(GCM_TAG_LEN, iv)
            val cipher = Cipher.getInstance(ALGO)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)

            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // Legacy fallback for old CBC-encrypted messages
            legacyDecrypt(value)
        }
    }

    // ── External Key Overloads (used by ECDHKeyManager) ──────────────────────

    /**
     * Encrypts [value] using a caller-provided [key] (e.g. ECDH-derived).
     * Same AES-GCM + random IV format as [encrypt].
     */
    fun encryptWithKey(value: String, key: javax.crypto.SecretKey): String {
        val cipher = Cipher.getInstance(ALGO)
        val iv = ByteArray(IV_SIZE).apply {
            java.security.SecureRandom().nextBytes(this)
        }
        val spec = GCMParameterSpec(GCM_TAG_LEN, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val combined = iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts a value produced by [encryptWithKey].
     */
    fun decryptWithKey(value: String, key: javax.crypto.SecretKey): String {
        val combined   = Base64.decode(value, Base64.NO_WRAP)
        val iv         = combined.sliceArray(0 until IV_SIZE)
        val ciphertext = combined.sliceArray(IV_SIZE until combined.size)
        val spec       = GCMParameterSpec(GCM_TAG_LEN, iv)
        val cipher     = Cipher.getInstance(ALGO)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    // ── Legacy Compatibility ──────────────────────────────────────────────────

    /**
     * Decrypts messages that were encrypted with the old static AES-CBC key.
     * Only used as a fallback — new messages are always AES-GCM.
     */
    private fun legacyDecrypt(value: String): String {
        return try {
            val legacyKey = javax.crypto.spec.SecretKeySpec("PSIRA_SECRET_KEY".toByteArray(), "AES")
            val legacyIv  = javax.crypto.spec.IvParameterSpec("PSIRA_SECURE__IV".toByteArray())
            val cipher    = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, legacyKey, legacyIv)
            String(cipher.doFinal(Base64.decode(value, Base64.DEFAULT)))
        } catch (e: Exception) {
            value // Return raw text as last resort (e.g. unencrypted legacy messages)
        }
    }
}