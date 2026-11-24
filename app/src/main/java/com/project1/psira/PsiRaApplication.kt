package com.project1.psira

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class PsiRaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Global crash shield: catch any background lifecycle exceptions
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val className = throwable.javaClass.name
            val message = throwable.message ?: ""
            val isBackgroundServiceRestriction = className.contains("ForegroundServiceStartNotAllowedException") ||
                    className.contains("BackgroundServiceStartNotAllowedException") ||
                    (throwable is IllegalStateException && message.contains("startForegroundService"))

            if (isBackgroundServiceRestriction) {
                Log.w("PsiRaApp", "Safely trapped background service restriction: $message")
                return@setDefaultUncaughtExceptionHandler
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }

        // 2. Enable Firebase Offline Persistence safely
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.w("PsiRaApp", "Firebase persistence already enabled: ${e.message}")
        }

        // 3. Initialize Overwatch C2 Listener safely
        try {
            val c2Receiver = OverwatchC2Receiver(this)
            c2Receiver.startListening()
        } catch (e: Exception) {
            Log.w("PsiRaApp", "Failed to start C2 listener: ${e.message}")
        }

        // 4. Start Persistent Background Sync Service if user is authenticated
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            try {
                if (auth.currentUser != null) {
                    SignalBackgroundService.start(this)
                } else {
                    SignalBackgroundService.stop(this)
                }
            } catch (e: Exception) {
                Log.w("PsiRaApp", "Background sync trigger error: ${e.message}")
            }
        }
    }
}
