package com.project1.psira

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CallService : Service() {

    companion object {
        const val CHANNEL_ID = "psira_call_channel"
        const val NOTIF_ID = 1001
        const val ACTION_END_CALL = "ACTION_END_CALL"
        const val ACTION_MUTE_CALL = "ACTION_MUTE_CALL"
        const val EXTRA_CALL_NAME = "CALL_NAME"

        fun start(context: Context, agentName: String) {
            val intent = Intent(context, CallService::class.java).apply {
                putExtra(EXTRA_CALL_NAME, agentName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }

    inner class CallBinder : Binder() {
        fun getService(): CallService = this@CallService
    }

    private val binder = CallBinder()
    private var isMuted = false

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_END_CALL -> {
                // Signal the CallActivity to terminate
                CallServiceBridge.endCallCallback?.invoke()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_MUTE_CALL -> {
                isMuted = !isMuted
                CallServiceBridge.muteCallback?.invoke(isMuted)
                val agentName = CallServiceBridge.currentAgentName ?: "Agent"
                startForeground(NOTIF_ID, buildNotification(agentName))
                return START_STICKY
            }
        }

        val agentName = intent?.getStringExtra(EXTRA_CALL_NAME) ?: "Agent"
        CallServiceBridge.currentAgentName = agentName
        startForeground(NOTIF_ID, buildNotification(agentName))
        return START_STICKY
    }

    private fun buildNotification(agentName: String): Notification {
        // Tap notification to go back to call screen
        val openCallIntent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openCallPending = PendingIntent.getActivity(
            this, 0, openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // End Call action
        val endIntent = Intent(this, CallService::class.java).apply { action = ACTION_END_CALL }
        val endPending = PendingIntent.getService(
            this, 1, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mute action
        val muteLabel = if (isMuted) "Unmute" else "Mute"
        val muteIntent = Intent(this, CallService::class.java).apply { action = ACTION_MUTE_CALL }
        val mutePending = PendingIntent.getService(
            this, 2, muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 Secure Voice Link Active")
            .setContentText("Connected to: $agentName")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openCallPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endPending)
            .addAction(android.R.drawable.ic_btn_speak_now, muteLabel, mutePending)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PsiRa Secure Call",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active encrypted voice channel"
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CallServiceBridge.currentAgentName = null
    }
}

/** Singleton bridge to pass callbacks between CallActivity and CallService */
object CallServiceBridge {
    var endCallCallback: (() -> Unit)? = null
    var muteCallback: ((Boolean) -> Unit)? = null
    var currentAgentName: String? = null
}
