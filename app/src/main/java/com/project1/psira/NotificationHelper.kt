package com.project1.psira

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.google.firebase.database.FirebaseDatabase
import java.util.UUID

object NotificationHelper {

    const val CHANNEL_DIRECT_MESSAGES = "psira_direct_messages_v1"
    const val CHANNEL_GROUP_MESSAGES  = "psira_group_messages_v1"
    const val CHANNEL_STEALTH         = "psira_stealth_disguise_v1"
    const val CHANNEL_CALLS           = "psira_calls_v1"

    const val PREFS_NAME              = "PsiRaPrefs"
    const val KEY_NOTIF_CAMOUFLAGE    = "KEY_NOTIF_CAMOUFLAGE"
    const val KEY_IN_APP_BANNERS      = "KEY_IN_APP_BANNERS"

    private var activeChatUid: String? = null

    fun setActiveChat(targetUid: String?) {
        activeChatUid = targetUid
    }

    fun getActiveChat(): String? = activeChatUid

    fun isCamouflageEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIF_CAMOUFLAGE, false)
    }

    fun setCamouflageEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIF_CAMOUFLAGE, enabled).apply()
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
            .build()

        // 1. Direct Messages (High priority, Chime + Vibration)
        val directChannel = NotificationChannel(
            CHANNEL_DIRECT_MESSAGES,
            "Direct Signals",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Private 1-on-1 encrypted messages"
            enableLights(true)
            lightColor = Color.parseColor("#7B61FF")
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180, 80, 180)
            setSound(soundUri, audioAttributes)
        }

        // 2. Group Messages (Default priority)
        val groupChannel = NotificationChannel(
            CHANNEL_GROUP_MESSAGES,
            "Group Enclaves",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Group broadcasts and channel messages"
            enableLights(true)
            lightColor = Color.parseColor("#A78BFA")
            enableVibration(true)
        }

        // 3. Stealth Disguise (Low priority, Minimal / Routine look)
        val stealthChannel = NotificationChannel(
            CHANNEL_STEALTH,
            "System Updates",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Routine maintenance and sync notifications"
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        // 4. Calls
        val callChannel = NotificationChannel(
            CHANNEL_CALLS,
            "Secure Voice Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Encrypted voice call notifications"
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(directChannel, groupChannel, stealthChannel, callChannel))
    }

    /**
     * Shows a notification for an incoming direct message.
     */
    fun showDirectMessageNotification(
        context: Context,
        senderUid: String,
        senderName: String,
        messageText: String,
        channelName: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        // If the user currently has this exact conversation open, do not notify
        if (activeChatUid == senderUid) return

        createNotificationChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isStealth = isCamouflageEnabled(context)
        val notifId = senderUid.hashCode()

        if (isStealth) {
            // Disguised as a routine system/cloud sync notification
            val stealthIntent = Intent(context, DisguiseActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, notifId, stealthIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_STEALTH)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Cloud Sync Service")
                .setContentText("1 routine asset synchronized.")
                .setWhen(timestamp)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            manager.notify(notifId, builder.build())
        } else {
            // Standard notification with sender name, message preview, and Direct Reply
            val chatIntent = Intent(context, DirectChatActivity::class.java).apply {
                putExtra("TARGET_UID", senderUid)
                putExtra("TARGET_NAME", senderName)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val openPendingIntent = PendingIntent.getActivity(
                context, notifId, chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Direct Reply RemoteInput Action
            val remoteInput = RemoteInput.Builder(NotificationReplyReceiver.KEY_TEXT_REPLY)
                .setLabel("Reply securely…")
                .build()

            val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
                putExtra(NotificationReplyReceiver.EXTRA_TARGET_UID, senderUid)
                putExtra(NotificationReplyReceiver.EXTRA_TARGET_NAME, senderName)
                putExtra(NotificationReplyReceiver.EXTRA_CHANNEL_NAME, channelName)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context, notifId, replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Reply",
                replyPendingIntent
            ).addRemoteInput(remoteInput).build()

            val preview = when {
                messageText.startsWith("data:image") -> "📷 Photo"
                messageText.startsWith("data:audio") -> "🎤 Voice Note"
                messageText.startsWith("data:application") -> "📄 Document"
                else -> messageText
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_DIRECT_MESSAGES)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(senderName)
                .setContentText(preview)
                .setWhen(timestamp)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#7B61FF"))
                .setContentIntent(openPendingIntent)
                .addAction(replyAction)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)

            manager.notify(notifId, builder.build())
        }
    }

    /**
     * Clears notifications associated with a specific sender when entering their chat.
     */
    fun clearNotificationsForContact(context: Context, contactUid: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(contactUid.hashCode())
    }

    /**
     * Dispatches a realtime notification signal to the recipient's Firebase user_inbox node.
     */
    fun dispatchNotificationSignal(
        context: Context,
        targetUid: String,
        senderUid: String,
        senderName: String,
        previewText: String,
        channelName: String
    ) {
        try {
            val inboxRef = FirebaseDatabase.getInstance().getReference("user_inbox/$targetUid")
            val signal = mapOf(
                "id" to UUID.randomUUID().toString(),
                "senderUid" to senderUid,
                "senderName" to senderName,
                "preview" to previewText.take(120),
                "channelName" to channelName,
                "timestamp" to System.currentTimeMillis()
            )
            inboxRef.push().setValue(signal)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
