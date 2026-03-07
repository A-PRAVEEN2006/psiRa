package com.yourname.psira // Again, keep your package name at the top

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AESEncryption {
    private const val ALGO = "AES/CBC/PKCS5Padding"
    // The key and IV must be exactly 16 characters long!
    private val key = SecretKeySpec("PSIRA_SECRET_KEY".toByteArray(), "AES")
    private val iv = IvParameterSpec("PSIRA_SECURE__IV".toByteArray())

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encrypted = cipher.doFinal(value.toByteArray())
        return Base64.encodeToString(encrypted, Base64.DEFAULT)
    }

    fun decrypt(value: String): String {
        val cipher = Cipher.getInstance(ALGO)
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        val decoded = Base64.decode(value, Base64.DEFAULT)
        return String(cipher.doFinal(decoded))
    }
}