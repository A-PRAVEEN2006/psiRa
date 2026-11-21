package com.project1.psira

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PsiraMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("users/$uid/fcmToken").setValue(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data

        val senderUid   = data["senderUid"] ?: remoteMessage.notification?.title ?: "system"
        val senderName  = data["senderName"] ?: remoteMessage.notification?.title ?: "System Signal"
        val messageText = data["preview"] ?: remoteMessage.notification?.body ?: "New transmission received"
        val channelName = data["channelName"] ?: ""

        NotificationHelper.showDirectMessageNotification(
            context = applicationContext,
            senderUid = senderUid,
            senderName = senderName,
            messageText = messageText,
            channelName = channelName,
            timestamp = System.currentTimeMillis()
        )
    }
}
