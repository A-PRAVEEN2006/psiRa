package com.project1.psira

import android.content.Context
import android.util.Log
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Listens for Overwatch Command & Control (C2) instructions from the standalone Control App.
 */
class OverwatchC2Receiver(private val context: Context) {

    private val database = FirebaseDatabase.getInstance().reference.child("c2_commands")
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // In a real production app, use asymmetric cryptography (RSA/ECDSA).
    // For this prototype, we use a strong symmetric HMAC key shared only between Main and Control app.
    private val OVERWATCH_MASTER_SECRET = "PSIRA_OVERWATCH_ABSOLUTE_ZERO_PROTOCOL_KEY"

    fun startListening() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (commandSnapshot in snapshot.children) {
                    val commandStr = commandSnapshot.child("command").getValue(String::class.java)
                    val signature = commandSnapshot.child("signature").getValue(String::class.java)
                    val timestamp = commandSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    
                    if (commandStr != null && signature != null) {
                        // Prevent replay attacks (commands older than 5 minutes are ignored)
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - timestamp > 300000) {
                            commandSnapshot.ref.removeValue()
                            continue
                        }

                        if (verifySignature(commandStr, timestamp, signature)) {
                            executeCommand(commandStr, commandSnapshot.ref)
                        } else {
                            Log.e("Overwatch", "Unverified C2 command rejected.")
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Overwatch", "C2 Listener cancelled: ${error.message}")
            }
        })
    }

    private fun verifySignature(command: String, timestamp: Long, signature: String): Boolean {
        try {
            val payload = "$command|$timestamp"
            val mac = Mac.getInstance("HmacSHA256")
            val secretKeySpec = SecretKeySpec(OVERWATCH_MASTER_SECRET.toByteArray(), "HmacSHA256")
            mac.init(secretKeySpec)
            
            val hmacBytes = mac.doFinal(payload.toByteArray())
            val expectedSignature = Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
            
            return expectedSignature == signature
        } catch (e: Exception) {
            return false
        }
    }

    private fun executeCommand(commandStr: String, ref: DatabaseReference) {
        scope.launch {
            Log.d("Overwatch", "Executing verified C2 command: $commandStr")
            
            val parts = commandStr.split(":")
            val action = parts[0]
            val params = if (parts.size > 1) parts[1] else ""

            when (action) {
                "WIPE_ALL" -> triggerGlobalKillSwitch()
                "WIPE_DEAD_DROPS" -> triggerDeadDropWipe()
                "TRAFFIC_PAD_INTERVAL" -> adjustTrafficPadding(params.toLongOrNull() ?: 5000L)
            }
            
            // Remove command after execution to prevent loops
            ref.removeValue()
        }
    }

    private fun triggerGlobalKillSwitch() {
        Log.e("Overwatch", "GLOBAL KILL SWITCH ACTIVATED")
        
        // 1. Wipe SharedPreferences
        val prefs = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        // 2. Clear local Vault
        VaultAuthHelper.wipeLocalVault(context)
        
        // 3. Clear Dead Drops
        DeadDropRelayManager(context).wipeDeadDrop()
        
        // In a fully rooted/admin device, we could trigger a factory reset.
        // For standard Android, deleting all app data is the best we can do programmatically.
        
        // Force crash/exit
        Runtime.getRuntime().exit(0)
    }

    private fun triggerDeadDropWipe() {
        Log.d("Overwatch", "Wiping Dead Drops remotely...")
        DeadDropRelayManager(context).wipeDeadDrop()
    }

    private fun adjustTrafficPadding(interval: Long) {
        Log.d("Overwatch", "Adjusting traffic padding to $interval ms")
        TrafficPaddingWorker.stopPadding()
        TrafficPaddingWorker.startPadding(interval)
    }
}
