package com.project1.psira

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.content.Context

/**
 * Base Activity for all PsiRa activities to ensure 100% presence tracking.
 */
open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure FLAG_SECURE is applied everywhere by default
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        updatePresence(true)
    }

    private fun updatePresence(isOnline: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        
        if (isOnline) {
            // Set up onDisconnect for server-side cleanup
            userRef.child("online").onDisconnect().setValue(false)
            // Set local status
            userRef.child("online").setValue(true)
        } else {
            userRef.child("online").setValue(false)
        }
    }

    /**
     * Call this explicitly during logout or session termination.
     */
    fun terminatePresence() {
        updatePresence(false)
    }

    protected fun vibrate(duration: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}
