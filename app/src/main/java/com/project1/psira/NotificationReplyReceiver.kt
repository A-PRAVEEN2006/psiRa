package com.project1.psira

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class NotificationReplyReceiver : BroadcastReceiver() {

    companion object {
        const val KEY_TEXT_REPLY     = "key_text_reply"
        const val EXTRA_TARGET_UID   = "extra_target_uid"
        const val EXTRA_TARGET_NAME  = "extra_target_name"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim() ?: return
        if (replyText.isEmpty()) return

        val targetUid   = intent.getStringExtra(EXTRA_TARGET_UID) ?: return
        val targetName  = intent.getStringExtra(EXTRA_TARGET_NAME) ?: "Agent"
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: return
        val myUid       = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val senderName  = FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown Agent"

        ECDHKeyManager.encryptForContact(
            context, targetUid, replyText,
            onResult = { encrypted ->
                val newMsg = Message(
                    id = null,
                    sender = senderName,
                    content = encrypted,
                    isBurnable = false,
                    type = "text",
                    timestamp = System.currentTimeMillis(),
                    status = "sent"
                )
                val db = FirebaseDatabase.getInstance().getReference("direct_messages/$channelName")
                db.push().setValue(newMsg)

                NotificationHelper.dispatchNotificationSignal(
                    context, targetUid, myUid, senderName, replyText, channelName
                )

                NotificationHelper.showDirectMessageNotification(
                    context, targetUid, targetName, "✓ You: $replyText", channelName, System.currentTimeMillis()
                )
            },
            onError = { _ ->
                val newMsg = Message(
                    id = null,
                    sender = senderName,
                    content = replyText,
                    isBurnable = false,
                    type = "text",
                    timestamp = System.currentTimeMillis(),
                    status = "sent"
                )
                FirebaseDatabase.getInstance().getReference("direct_messages/$channelName").push().setValue(newMsg)
            }
        )
    }
}
