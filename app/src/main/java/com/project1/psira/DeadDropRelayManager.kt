package com.project1.psira

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manages the "Dead Drop" functionality for headless nodes (like an ESP32 or old Android).
 * It stores encrypted payloads securely until the intended recipient connects to the mesh.
 */
class DeadDropRelayManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "PsiRa_DeadDrop_Vault"
        private const val KEY_PAYLOADS = "stored_payloads"
        private const val MAX_PAYLOADS = 100 // Prevent storage bloat
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // In-memory cache of payloads
    private val storedPayloads = mutableListOf<String>()

    init {
        loadPayloads()
    }

    private fun loadPayloads() {
        val saved = prefs.getStringSet(KEY_PAYLOADS, emptySet()) ?: emptySet()
        storedPayloads.clear()
        storedPayloads.addAll(saved)
    }

    private fun savePayloads() {
        prefs.edit().putStringSet(KEY_PAYLOADS, storedPayloads.toSet()).apply()
    }

    /**
     * Stores a new payload in the dead drop if it hasn't been stored before.
     */
    fun storePayload(packet: String) {
        synchronized(storedPayloads) {
            if (!storedPayloads.contains(packet)) {
                if (storedPayloads.size >= MAX_PAYLOADS) {
                    storedPayloads.removeAt(0) // Remove oldest
                }
                storedPayloads.add(packet)
                savePayloads()
                Log.d("DeadDrop", "Stored new payload. Total: ${storedPayloads.size}")
            }
        }
    }

    /**
     * Retrieves all stored payloads to broadcast to newly connected nodes.
     */
    fun getAllStoredPayloads(): List<String> {
        return synchronized(storedPayloads) {
            storedPayloads.toList()
        }
    }

    /**
     * Optional: Clear payloads when a "WIPE" command is received or if memory is full.
     */
    fun wipeDeadDrop() {
        synchronized(storedPayloads) {
            storedPayloads.clear()
            savePayloads()
        }
        Log.d("DeadDrop", "Vault Wiped")
    }
}
