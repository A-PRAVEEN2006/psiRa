package com.project1.psira

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                SignalBackgroundService.start(context)
            }
        }
    }
}
