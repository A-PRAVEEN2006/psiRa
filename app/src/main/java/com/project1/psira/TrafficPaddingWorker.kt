package com.project1.psira

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import java.util.UUID

/**
 * Metadata Camouflage Worker
 * Periodically sends dummy packets to the Firebase server to prevent traffic analysis.
 * An observer intercepting the network will see constant streams of uniform data,
 * hiding when real messages are being sent.
 */
object TrafficPaddingWorker {
    
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = FirebaseDatabase.getInstance().reference

    fun startPadding(intervalMs: Long = 5000) {
        if (job?.isActive == true) return
        
        job = scope.launch {
            while (isActive) {
                sendDummyPacket()
                // Randomize interval slightly to make it even harder to fingerprint
                val jitter = (Math.random() * 2000).toLong() - 1000
                delay(intervalMs + jitter)
            }
        }
        Log.d("TrafficPadding", "Started metadata camouflage.")
    }

    fun stopPadding() {
        job?.cancel()
        Log.d("TrafficPadding", "Stopped metadata camouflage.")
    }

    private fun sendDummyPacket() {
        // Create a fake message
        val fakeBase64 = AESEncryption.encrypt(UUID.randomUUID().toString())
        var fakeAlien = PsiraCipher.toAlien(fakeBase64)
        
        // Pad it to uniform size
        fakeAlien = PsiraCipher.padPayload(fakeAlien, 512)

        val packetId = "DUMMY_" + UUID.randomUUID().toString()
        
        // Push to a designated padding or general node
        val node = database.child("global_mesh").child(packetId)
        
        // To complete the Zero-Knowledge & Self-Destruct
        node.setValue(fakeAlien).addOnCompleteListener {
            // Delete immediately after receipt to simulate ephemeral traffic
            node.removeValue()
        }
    }
}
