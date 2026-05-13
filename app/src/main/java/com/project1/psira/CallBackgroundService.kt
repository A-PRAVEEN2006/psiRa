package com.project1.psira

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class CallBackgroundService : Service() {

    private val CHANNEL_ID = "PsiRaCallService"
    private var callListener: ValueEventListener? = null
    private var lastCallId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(2001, buildServiceNotification())
        listenForIncomingCalls()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun listenForIncomingCalls() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val ref = FirebaseDatabase.getInstance().getReference("calls").child(user.uid)

        callListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    lastCallId = null
                    return
                }

                val status = snapshot.child("status").getValue(String::class.java)
                val callerUid = snapshot.child("callerUid").getValue(String::class.java)
                val callerName = snapshot.child("callerName").getValue(String::class.java)

                if (status == "calling" && callerUid != null && callerName != null && callerUid != lastCallId) {
                    lastCallId = callerUid
                    showIncomingCallNotification(callerName, callerUid)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(callListener!!)
    }

    private fun showIncomingCallNotification(name: String, uid: String) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("TARGET_NAME", name)
            putExtra("CALLER_UID", uid)
            putExtra("CALL_MODE", "INCOMING")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Secure Call")
            .setContentText("Agent $name is initiating a link...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setColor(Color.parseColor("#7B61FF"))
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(2002, notification)
    }

    private fun buildServiceNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PsiRa Link Active")
            .setContentText("Listening for secure signals...")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PsiRa Secure Communications",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Critical alerts for incoming voice links"
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && callListener != null) {
            FirebaseDatabase.getInstance().getReference("calls").child(user.uid).removeEventListener(callListener!!)
        }
        super.onDestroy()
    }
}
