package com.project1.psira

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object MediaVaultCipher {

    private const val KEY_ALIAS   = "PsiRaMediaVault_v1"
    private const val KEYSTORE    = "AndroidKeyStore"
    private const val ALGO        = "AES/GCM/NoPadding"
    private const val GCM_TAG_LEN = 128  // bits
    private const val IV_SIZE     = 12   // bytes

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
            .setUserAuthenticationRequired(false)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    /** Encrypts raw bytes → IV(12) + ciphertext+GCM_tag */
    fun encryptBytes(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return iv + ciphertext
    }

    /** Decrypts bytes produced by encryptBytes */
    fun decryptBytes(enc: ByteArray): ByteArray {
        val iv = enc.sliceArray(0 until IV_SIZE)
        val ciphertext = enc.sliceArray(IV_SIZE until enc.size)
        val spec = GCMParameterSpec(GCM_TAG_LEN, iv)
        val cipher = Cipher.getInstance(ALGO)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return cipher.doFinal(ciphertext)
    }
}
