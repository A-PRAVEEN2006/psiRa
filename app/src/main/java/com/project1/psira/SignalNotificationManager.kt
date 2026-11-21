package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SignalNotificationManager(private val context: Context) {

    private var currentListener: ChildEventListener? = null
    private var currentInboxRef: DatabaseReference? = null

    fun startListening() {
        NotificationHelper.createNotificationChannels(context)

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                attachInboxListener(user.uid)
            } else {
                detachInboxListener()
            }
        }
    }

    private fun attachInboxListener(uid: String) {
        detachInboxListener()

        val ref = FirebaseDatabase.getInstance().getReference("user_inbox/$uid")
        ref.keepSynced(true)
        currentInboxRef = ref

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val senderUid   = snapshot.child("senderUid").getValue(String::class.java) ?: return
                    val senderName  = snapshot.child("senderName").getValue(String::class.java) ?: "Agent"
                    val preview     = snapshot.child("preview").getValue(String::class.java) ?: "New encrypted transmission"
                    val channelName = snapshot.child("channelName").getValue(String::class.java) ?: ""
                    val timestamp   = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                    // Remove processed signal immediately
                    snapshot.ref.removeValue()

                    // If user is actively inside this chat, don't show notifications
                    if (NotificationHelper.getActiveChat() == senderUid) {
                        return
                    }

                    // Show in-app banner if an activity is in the foreground
                    val foregroundActivity = BaseActivity.currentActivity
                    if (foregroundActivity != null && !foregroundActivity.isFinishing) {
                        Handler(Looper.getMainLooper()).post {
                            InAppNotificationBanner.show(foregroundActivity, senderName, preview) {
                                val intent = Intent(foregroundActivity, DirectChatActivity::class.java).apply {
                                    putExtra("TARGET_UID", senderUid)
                                    putExtra("TARGET_NAME", senderName)
                                }
                                foregroundActivity.startActivity(intent)
                            }
                        }
                    }

                    // Show system notification
                    NotificationHelper.showDirectMessageNotification(
                        context = context,
                        senderUid = senderUid,
                        senderName = senderName,
                        messageText = preview,
                        channelName = channelName,
                        timestamp = timestamp
                    )
                } catch (e: Exception) {
                    Log.e("SignalNotif", "Error handling notification signal: ${e.message}")
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w("SignalNotif", "Inbox listener cancelled: ${error.message}")
            }
        }

        ref.addChildEventListener(listener)
        currentListener = listener
    }

    private fun detachInboxListener() {
        if (currentListener != null && currentInboxRef != null) {
            currentInboxRef?.removeEventListener(currentListener!!)
            currentListener = null
            currentInboxRef = null
        }
    }
}
