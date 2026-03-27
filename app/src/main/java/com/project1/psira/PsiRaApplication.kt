package com.project1.psira

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class PsiRaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable Firebase Offline Persistence
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
