package com.project1.psira

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Secret Code Receiver for Ghost Mode.
 * Triggered by dialing *#*#77472#*#* (*#*#PSIRA#*#*) in the phone's native dialer.
 * Allows the app to be launched when all launcher icons are completely hidden.
 */
class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        if (action == "android.provider.Telephony.SECRET_CODE") {
            val launchIntent = Intent(context, DisguiseActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(launchIntent)
        }
    }
}
