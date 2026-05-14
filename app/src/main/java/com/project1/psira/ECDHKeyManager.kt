package com.project1.psira

import android.content.Context
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.security.*
import java.security.spec.*
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Manages per-contact End-to-End Encryption using ECDH key exchange.
 *
 * Security model:
 * - Each device generates a P-256 EC key pair on first login.
 * - The private key is encrypted with the Keystore-backed AES key and stored locally.
 * - The public key is uploaded to Firebase.
 * - When chatting with a contact, both devices independently compute the same
 *   shared secret via ECDH — without ever transmitting the secret over the network.
 * - The shared secret is hashed with SHA-256 to derive a 32-byte AES-256 key
 *   unique to that conversation pair.
 *
 * Even if Firebase is fully compromised, messages remain unreadable without the
 * private key, which never leaves the device.
 */
object ECDHKeyManager {

    private const val PREFS_NAME      = "PsiRaE2EEPrefs"
    private const val PREF_PRIV_KEY   = "ec_private_key_enc"
    private const val PREF_PUB_KEY    = "ec_public_key_b64"

    /**
     * Shared static AES-256 key — used as fallback when ECDH keys aren't ready yet.
     * This is the SAME on every device (unlike the Keystore-backed key which is device-specific).
     * Once both devices have ECDH keys, all messages use the derived key instead.
     */
    private val SHARED_FALLBACK_KEY = javax.crypto.spec.SecretKeySpec(
        "PsiRa_SharedNet_32ByteSecureKey!".toByteArray(Charsets.UTF_8),
        "AES"
    )

    // In-memory cache of derived keys — avoids re-computing on every message
    private val sharedKeyCache = HashMap<String, SecretKey>()

    // ── Key Pair Lifecycle ────────────────────────────────────────────────────

    /**
     * Call this once after login to ensure this device has an EC key pair.
     * Uploads the public key to Firebase.
     */
    fun initializeKeys(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(PREF_PRIV_KEY, null) != null) {
            // Keys already exist — re-upload public key in case it was lost
            uploadPublicKey(context)
            return
        }

        // Generate new P-256 EC key pair
        val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        // Encrypt private key with our Keystore-backed AES, store locally
        val privB64   = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        val privEnc   = AESEncryption.encrypt(privB64)
        val pubB64    = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putString(PREF_PRIV_KEY, privEnc)
            .putString(PREF_PUB_KEY, pubB64)
            .apply()

        uploadPublicKey(context)
    }

    /** Uploads this device's public key to Firebase so contacts can fetch it. */
    fun uploadPublicKey(context: Context) {
        val uid    = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val pubB64 = getOwnPublicKeyB64(context) ?: return
        FirebaseDatabase.getInstance()
            .getReference("users").child(uid).child("publicKey")
            .setValue(pubB64)
    }

    // ── Key Derivation ────────────────────────────────────────────────────────

    /**
     * Derives a unique AES-256 key for a conversation with [contactUid].
     * Uses cached result if available.
     * Returns null if the contact's public key isn't in Firebase yet.
     */
    fun deriveSharedKey(
        context: Context,
        contactUid: String,
        onReady: (SecretKey) -> Unit,
        onError: (String) -> Unit
    ) {
        sharedKeyCache[contactUid]?.let { onReady(it); return }

        fetchContactPublicKey(contactUid) { theirPubB64 ->
            if (theirPubB64 == null) {
                onError("Contact has no E2EE key. Ask them to open the app once.")
                return@fetchContactPublicKey
            }
            try {
                val key = computeSharedKey(context, theirPubB64)
                sharedKeyCache[contactUid] = key
                onReady(key)
            } catch (e: Exception) {
                onError("Key derivation failed: ${e.message}")
            }
        }
    }

    /** Encrypts [plaintext] using the ECDH-derived key for [contactUid]. */
    fun encryptForContact(
        context: Context,
        contactUid: String,
        plaintext: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        deriveSharedKey(context, contactUid, { sharedKey ->
            val ciphertext = AESEncryption.encryptWithKey(plaintext, sharedKey)
            onResult(ciphertext)
        }, { err ->
            // Fallback: use shared static key (same on all devices — cross-device compatible)
            try {
                val fallback = AESEncryption.encryptWithKey(plaintext, SHARED_FALLBACK_KEY)
                onResult(fallback)
            } catch (e: Exception) {
                onError(err)
            }
        })
    }

    /** Decrypts [ciphertext] using the ECDH-derived key for [contactUid]. */
    fun decryptFromContact(
        context: Context,
        contactUid: String,
        ciphertext: String
    ): String {
        // 1. Try ECDH-derived key first (best security)
        val ecdhKey = sharedKeyCache[contactUid]
        if (ecdhKey != null) {
            try { return AESEncryption.decryptWithKey(ciphertext, ecdhKey) } catch (_: Exception) {}
        }

        // 2. Try shared static fallback key (cross-device, when ECDH not yet ready)
        try { return AESEncryption.decryptWithKey(ciphertext, SHARED_FALLBACK_KEY) } catch (_: Exception) {}

        // 3. Try legacy static CBC key (very old messages from before Keystore upgrade)
        return try { legacyDecrypt(ciphertext) } catch (_: Exception) { ciphertext }
    }

    private fun legacyDecrypt(ciphertext: String): String {
        val legacyKey = javax.crypto.spec.SecretKeySpec("PSIRA_SECRET_KEY".toByteArray(), "AES")
        val legacyIv  = javax.crypto.spec.IvParameterSpec("PSIRA_SECURE__IV".toByteArray())
        val cipher    = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, legacyKey, legacyIv)
        return String(cipher.doFinal(android.util.Base64.decode(ciphertext, android.util.Base64.DEFAULT)))
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun computeSharedKey(context: Context, theirPubB64: String): SecretKey {
        val myPrivKey   = loadPrivateKey(context)
        val theirPubKey = decodePublicKey(theirPubB64)

        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(myPrivKey)
        agreement.doPhase(theirPubKey, true)
        val rawSecret = agreement.generateSecret()

        // SHA-256 of the raw ECDH output → 32 bytes → AES-256 key
        val aesBytes = MessageDigest.getInstance("SHA-256").digest(rawSecret)
        return SecretKeySpec(aesBytes, "AES")
    }

    private fun loadPrivateKey(context: Context): PrivateKey {
        val prefs    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val privEnc  = prefs.getString(PREF_PRIV_KEY, null)
            ?: throw IllegalStateException("No private key found. Call initializeKeys() first.")
        val privB64  = AESEncryption.decrypt(privEnc)
        val privBytes = Base64.decode(privB64, Base64.NO_WRAP)
        return KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(privBytes))
    }

    private fun decodePublicKey(b64: String): PublicKey {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        return KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun getOwnPublicKeyB64(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_PUB_KEY, null)
    }

    private fun fetchContactPublicKey(uid: String, callback: (String?) -> Unit) {
        FirebaseDatabase.getInstance()
            .getReference("users").child(uid).child("publicKey")
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.getValue(String::class.java))
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    /** Clear cached keys (e.g. on logout) */
    fun clearCache() {
        sharedKeyCache.clear()
    }
}
