package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/**
 * Listens in the background for real-time signals and direct chat messages,
 * popping up high-priority notifications (sound, vibration, heads-up) ONLY when a message is received,
 * exactly like WhatsApp and Telegram.
 */
class SignalNotificationManager(private val context: Context) {

    private var inboxListener: ChildEventListener? = null
    private var inboxRef: DatabaseReference? = null

    private var directChatsListener: ValueEventListener? = null
    private var directChatsRef: DatabaseReference? = null
    private val channelListeners = mutableMapOf<String, Pair<Query, ChildEventListener>>()

    private val notifiedMessageIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private var lastNotifiedKey: String? = null
    private var lastNotifiedTime: Long = 0L

    private fun isDuplicate(id: String?): Boolean {
        if (!id.isNullOrEmpty() && notifiedMessageIds.contains(id)) {
            return true
        }
        val now = System.currentTimeMillis()
        if (!id.isNullOrEmpty() && id == lastNotifiedKey && (now - lastNotifiedTime) < 3000) {
            return true
        }
        return false
    }

    private fun markNotified(id: String?) {
        if (!id.isNullOrEmpty()) {
            if (notifiedMessageIds.size > 300) {
                val it = notifiedMessageIds.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
            notifiedMessageIds.add(id)
        }
        lastNotifiedKey = id
        lastNotifiedTime = System.currentTimeMillis()
    }

    fun startListening() {
        NotificationHelper.createNotificationChannels(context)

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                attachInboxListener(user.uid)
                attachDirectChatsListener(user.uid)
            } else {
                detachAllListeners()
            }
        }
    }

    private fun attachInboxListener(uid: String) {
        detachInboxListener()

        val ref = FirebaseDatabase.getInstance().getReference("user_inbox/$uid")
        ref.keepSynced(true)
        inboxRef = ref

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val signalId    = snapshot.key ?: snapshot.child("id").getValue(String::class.java) ?: ""
                    val senderUid   = snapshot.child("senderUid").getValue(String::class.java) ?: return
                    val senderName  = snapshot.child("senderName").getValue(String::class.java) ?: "Agent"
                    val preview     = snapshot.child("preview").getValue(String::class.java) ?: "New encrypted transmission"
                    val channelName = snapshot.child("channelName").getValue(String::class.java) ?: ""
                    val timestamp   = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                    // Always delete processed signal immediately from inbox
                    snapshot.ref.removeValue()

                    if (signalId.isNotEmpty() && isDuplicate(signalId)) {
                        return
                    }
                    markNotified(signalId)

                    notifyMessageReceived(signalId, senderUid, senderName, preview, channelName, timestamp)
                } catch (e: Exception) {
                    Log.e("SignalNotif", "Error in inbox signal: ${e.message}")
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
        inboxListener = listener
    }

    private fun attachDirectChatsListener(myUid: String) {
        detachDirectChatsListener()

        val ref = FirebaseDatabase.getInstance().getReference("user_direct_chats/$myUid")
        ref.keepSynced(true)
        directChatsRef = ref

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentContactUids = snapshot.children.mapNotNull { it.key }.toSet()

                // Remove channels no longer in direct chats
                val toRemove = channelListeners.keys.filter { it !in currentContactUids }
                for (contactUid in toRemove) {
                    channelListeners.remove(contactUid)?.let { (query, l) ->
                        query.removeEventListener(l)
                    }
                }

                // Attach listener for each contact channel
                for (contactUid in currentContactUids) {
                    if (!channelListeners.containsKey(contactUid)) {
                        attachChannelMessageListener(myUid, contactUid)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("SignalNotif", "Direct chats listener cancelled: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        directChatsListener = listener
    }

    private fun attachChannelMessageListener(myUid: String, contactUid: String) {
        val channelName = if (myUid < contactUid) "${myUid}_$contactUid" else "${contactUid}_${myUid}"
        val channelQuery = FirebaseDatabase.getInstance()
            .getReference("direct_messages/$channelName")
            .limitToLast(1)

        val attachTimestamp = System.currentTimeMillis()

        val childListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val msgKey = snapshot.key ?: ""
                    val rawMsg = snapshot.getValue(Message::class.java) ?: return
                    val myDisplayName = FirebaseAuth.getInstance().currentUser?.displayName

                    // Only notify if message is from the other party and is unread
                    if (rawMsg.sender != myDisplayName && rawMsg.status != "read") {
                        // Mark as delivered on server so sender gets double-tick (✓✓)
                        if (rawMsg.status == "sent") {
                            snapshot.ref.child("status").setValue("delivered")
                        }
                        // Only trigger for fresh messages received AFTER listener attached or within 15 seconds
                        if (rawMsg.timestamp >= attachTimestamp - 15_000) {
                            if (msgKey.isNotEmpty() && isDuplicate(msgKey)) {
                                return
                            }
                            markNotified(msgKey)

                            var preview = when (rawMsg.type) {
                                "image" -> "📷 Photo"
                                "voice" -> "🎤 Voice Note"
                                "steg"  -> "🕵️ Hidden Image"
                                "doc"   -> "📄 Document"
                                else    -> {
                                    val decrypted = try {
                                        ECDHKeyManager.decryptFromContact(context, contactUid, rawMsg.content ?: "")
                                    } catch (e: Exception) {
                                        rawMsg.content ?: ""
                                    }
                                    try { PsiRaConverter.decodeAny(decrypted) } catch (e: Exception) { decrypted }
                                }
                            }

                            notifyMessageReceived(
                                messageId = msgKey,
                                senderUid = contactUid,
                                senderName = rawMsg.sender ?: "Agent",
                                preview = preview,
                                channelName = channelName,
                                timestamp = rawMsg.timestamp
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SignalNotif", "Error in channel listener: ${e.message}")
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        channelQuery.addChildEventListener(childListener)
        channelListeners[contactUid] = Pair(channelQuery, childListener)
    }

    private fun notifyMessageReceived(
        messageId: String,
        senderUid: String,
        senderName: String,
        preview: String,
        channelName: String,
        timestamp: Long
    ) {
        // If user is actively inside this chat, don't show notifications
        if (NotificationHelper.getActiveChat() == senderUid) {
            return
        }

        // Show in-app banner if active in foreground
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

        // Pop up system notification (sound + vibration + heads-up banner)
        NotificationHelper.showDirectMessageNotification(
            context = context,
            senderUid = senderUid,
            senderName = senderName,
            messageText = preview,
            channelName = channelName,
            timestamp = timestamp
        )
    }

    private fun detachInboxListener() {
        if (inboxListener != null && inboxRef != null) {
            inboxRef?.removeEventListener(inboxListener!!)
            inboxListener = null
            inboxRef = null
        }
    }

    private fun detachDirectChatsListener() {
        if (directChatsListener != null && directChatsRef != null) {
            directChatsRef?.removeEventListener(directChatsListener!!)
            directChatsListener = null
            directChatsRef = null
        }
        for ((_, pair) in channelListeners) {
            pair.first.removeEventListener(pair.second)
        }
        channelListeners.clear()
    }

    private fun detachAllListeners() {
        detachInboxListener()
        detachDirectChatsListener()
    }
}
