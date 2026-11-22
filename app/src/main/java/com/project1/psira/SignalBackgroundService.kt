package com.project1.psira

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Persistent background sync service ensuring real-time messages and notifications
 * are reliably delivered even when the app is minimized, closed, or device is sleeping.
 */
class SignalBackgroundService : Service() {

    companion object {
        const val CHANNEL_SYNC_ID = "psira_sync_channel"
        const val NOTIF_SYNC_ID = 2001
        const val ACTION_START_SYNC = "com.project1.psira.START_SYNC"
        const val ACTION_STOP_SYNC = "com.project1.psira.STOP_SYNC"

        fun start(context: Context) {
            val intent = Intent(context, SignalBackgroundService::class.java).apply {
                action = ACTION_START_SYNC
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("SignalSync", "Failed to start background sync service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SignalBackgroundService::class.java).apply {
                action = ACTION_STOP_SYNC
            }
            context.stopService(intent)
        }
    }

    private var signalManager: SignalNotificationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createSyncNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SYNC) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        // Start foreground with silent, discreet notification
        val notification = buildSyncNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_SYNC_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_SYNC_ID, notification)
        }

        // Initialize and start persistent real-time listener
        if (signalManager == null) {
            signalManager = SignalNotificationManager(applicationContext)
            signalManager?.startListening()
        }

        // Ensure FCM token is kept up to date for hybrid cloud push
        syncFcmToken()

        return START_STICKY
    }

    private fun syncFcmToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null && !token.isNullOrEmpty()) {
                        FirebaseDatabase.getInstance().getReference("users/$uid/fcmToken").setValue(token)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SignalSync", "Could not query FCM token: ${e.message}")
        }
    }

    private fun createSyncNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_SYNC_ID,
                "PsiRa Background Node",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps secure encrypted transmissions connected in the background"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildSyncNotification(): Notification {
        val isCamouflage = NotificationHelper.isCamouflageEnabled(this)

        val targetActivity = if (isCamouflage) DisguiseActivity::class.java else NexusDashboardActivity::class.java
        val openIntent = Intent(this, targetActivity).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isCamouflage) "Cloud Sync Service" else "PsiRa Secure Node"
        val text = if (isCamouflage) "Background asset synchronization active" else "Encrypted real-time channel connected"
        val icon = if (isCamouflage) android.R.drawable.stat_notify_sync else R.drawable.ic_chat

        return NotificationCompat.Builder(this, CHANNEL_SYNC_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // If swiped away from Recents, schedule instant resurrection
        if (FirebaseAuth.getInstance().currentUser != null) {
            val restartIntent = Intent(applicationContext, SignalBackgroundService::class.java).apply {
                action = ACTION_START_SYNC
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext, 101, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartPendingIntent
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // If system kills service while user is logged in, attempt restart
        if (FirebaseAuth.getInstance().currentUser != null) {
            start(applicationContext)
        }
    }
}
