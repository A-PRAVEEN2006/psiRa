package com.project1.psira

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import com.bumptech.glide.Glide

class MessageAdapter(
    private val messageList: List<Message>,
    /**
     * Set to true when the calling activity has already decrypted the messages
     * before adding them to the list (e.g. DirectChatActivity with ECDH).
     * When false, the adapter decrypts itself using the global AES key (group chat).
     */
    private val alreadyDecrypted: Boolean = false
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    
    var chatDbRef: com.google.firebase.database.DatabaseReference? = null

    var isMirrored: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val VIEW_TYPE_SENT_TEXT = 1
    private val VIEW_TYPE_RECEIVED_TEXT = 2
    private val VIEW_TYPE_SENT_VOICE = 3
    private val VIEW_TYPE_RECEIVED_VOICE = 4
    private val VIEW_TYPE_SENT_MEDIA = 5
    private val VIEW_TYPE_RECEIVED_MEDIA = 6

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textMessage: TextView? = itemView.findViewById(R.id.textMessage)
        val tvBurnIcon: TextView? = itemView.findViewById(R.id.tvBurnIcon)
        val btnPlayVoice: android.widget.ImageButton? = itemView.findViewById(R.id.btnPlayVoice)
        val tvVoiceDuration: TextView? = itemView.findViewById(R.id.tvVoiceDuration)
        val ivMessageMedia: android.widget.ImageView? = itemView.findViewById(R.id.ivMessageMedia)
        val tvFileName: TextView? = itemView.findViewById(R.id.tvFileName)
    }

    override fun getItemViewType(position: Int): Int {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val message = messageList[position]
        val isMe = message.sender == currentUser?.displayName

        return when (message.type) {
            "voice" -> if (isMe) VIEW_TYPE_SENT_VOICE else VIEW_TYPE_RECEIVED_VOICE
            "image", "doc" -> if (isMe) VIEW_TYPE_SENT_MEDIA else VIEW_TYPE_RECEIVED_MEDIA
            else -> if (isMe) VIEW_TYPE_SENT_TEXT else VIEW_TYPE_RECEIVED_TEXT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = when (viewType) {
            VIEW_TYPE_SENT_TEXT -> R.layout.item_message_sent
            VIEW_TYPE_RECEIVED_TEXT -> R.layout.item_message_received
            VIEW_TYPE_SENT_VOICE -> R.layout.item_message_voice_sent
            VIEW_TYPE_RECEIVED_VOICE -> R.layout.item_message_voice_received
            VIEW_TYPE_SENT_MEDIA -> R.layout.item_message_media_sent
            VIEW_TYPE_RECEIVED_MEDIA -> R.layout.item_message_media_received
            else -> R.layout.item_message_sent
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val context = holder.itemView.context
        val message = messageList[position]

        // Use the Firebase key (id) for deletion
        val messageId = message.id

        when (message.type) {
            "text" -> {
                val displayText = if (alreadyDecrypted) {
                    // Already decrypted by the activity — show as-is
                    // (PsiRa cipher symbols are intentional — receiver sees the cipher)
                    message.content ?: ""
                } else {
                    // Group chat path — decrypt with global AES key
                    try { AESEncryption.decrypt(message.content!!) }
                    catch (e: Exception) { message.content ?: "" }
                }
                holder.textMessage?.text = displayText
            }
            "voice" -> {
                holder.tvVoiceDuration?.text = "Voice Note (Encrypted)"
                holder.btnPlayVoice?.setOnClickListener {
                    // Start simple playback (URL from message.content)
                    playOneAudio(context, message.content ?: "")
                }

            }
            "image" -> {
                holder.ivMessageMedia?.visibility = View.VISIBLE
                holder.tvFileName?.visibility = View.GONE
                holder.ivMessageMedia?.let { imageView ->
                    Glide.with(context)
                        .load(message.content)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(imageView)
                }
            }
            "doc" -> {
                holder.ivMessageMedia?.visibility = View.GONE
                holder.tvFileName?.visibility = View.VISIBLE
                holder.tvFileName?.text = "Encrypted Doc: ${message.content?.takeLast(10)}"
            }
        }

        // --- SELF-DESTRUCT LOGIC ---
        holder.tvBurnIcon?.visibility = if (message.isBurnable) View.VISIBLE else View.GONE
        
        // If this is a burnable message AND we are the receiver (not the sender)
        if (message.isBurnable && message.sender != com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val messageIdToDelete = message.id
                if (messageIdToDelete != null && chatDbRef != null) {
                    chatDbRef!!.child(messageIdToDelete).removeValue()
                }
            }, 10000) // 10 seconds
        }

        // --- LONG PRESS TO DELETE FEATURE ---
        // Inside onBindViewHolder in MessageAdapter.kt
        holder.itemView.setOnLongClickListener {
            val messageId = messageList[position].id
            if (messageId != null) {
                val type = messageList[position].type ?: "text"
                val options = mutableListOf("Delete", "Copy to Clipboard")
                if (type in listOf("image", "doc", "voice")) {
                    options.add(0, "Save to Device")
                }

                PsiRaDialogs.showOptionsSheet(context, "MESSAGE OPTIONS", options) { which ->
                    val selectedOption = options[which]
                    if (selectedOption == "Save to Device") {
                        val ext = when(type) {
                            "image" -> "jpg"
                            "doc" -> "pdf"
                            "voice" -> "m4a"
                            else -> "file"
                        }
                        saveFileToLocal(context, messageList[position].content ?: "", "${type}_${System.currentTimeMillis()}.$ext")
                    } else if (selectedOption == "Delete") {
                        PsiRaDialogs.showDeleteSheet(
                            context,
                            "ERASE SIGNAL?",
                            "This message will be purged from the encrypted logs forever.",
                            "PURGE"
                        ) {
                            chatDbRef?.child(messageId)?.removeValue()?.addOnSuccessListener {
                                Toast.makeText(context, "Message Erased", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else if (selectedOption == "Copy to Clipboard") {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val textToCopy = holder.textMessage?.text?.toString() ?: messageList[position].content ?: ""
                        val clip = android.content.ClipData.newPlainText("PsiRa Message", textToCopy)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Error: Message ID missing", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun playOneAudio(context: Context, url: String) {
        AudioPlayer.play(url, 
            onStart = {
                Toast.makeText(context, "Initializing secure audio stream...", Toast.LENGTH_SHORT).show()
            },
            onError = {
                Toast.makeText(context, "Audio playback failed.", Toast.LENGTH_SHORT).show()
            }
        )
    }


    private fun saveFileToLocal(context: Context, url: String, fileName: String) {
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle("PsiRa Secure Download")
                .setDescription("Downloading encrypted asset...")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "PsiRa_$fileName")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(context, "Encryption bypass successful. Saving to Downloads/PsiRa...", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Save failed. Check storage permissions.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }
}
