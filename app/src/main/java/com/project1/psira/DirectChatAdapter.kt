package com.project1.psira

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class ChatPreviewInfo(
    val lastMessage: String = "",
    val lastMessageType: String = "text",
    val timestamp: Long = 0L,
    val unreadCount: Int = 0,
    val totalReceived: Int = 0,
    val isLastFromMe: Boolean = false,
    val lastStatus: String = "sent"
)

class DirectChatAdapter(private val chatList: MutableList<User>) : RecyclerView.Adapter<DirectChatAdapter.DirectChatViewHolder>() {

    private val previewMap = HashMap<String, ChatPreviewInfo>()

    class DirectChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvChatName)
        val tvId: TextView = itemView.findViewById(R.id.tvChatId)
        val tvTime: TextView = itemView.findViewById(R.id.tvChatTime)
        val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        val tvUnreadBadge: TextView = itemView.findViewById(R.id.tvUnreadBadge)
    }

    fun updatePreview(targetUid: String, info: ChatPreviewInfo) {
        previewMap[targetUid] = info
        val idx = chatList.indexOfFirst { it.uid == targetUid }
        if (idx != -1) {
            notifyItemChanged(idx)
        }
    }

    fun getTimestamp(targetUid: String?): Long {
        return previewMap[targetUid]?.timestamp ?: 0L
    }

    fun getPreview(targetUid: String?): ChatPreviewInfo? {
        return previewMap[targetUid]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirectChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_direct_chat, parent, false)
        return DirectChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: DirectChatViewHolder, position: Int) {
        val context = holder.itemView.context
        val user = chatList[position]
        
        val sharedPrefNick = context.getSharedPreferences("PsiRaNicknames", Context.MODE_PRIVATE)
        val personalNickname = sharedPrefNick.getString(user.uid, null)

        holder.tvName.text = personalNickname ?: user.name ?: "Unknown Agent"
        
        val presenceDot = holder.itemView.findViewById<View>(R.id.presenceDot)
        if (user.online) {
            presenceDot.setBackgroundResource(R.drawable.presence_online)
        } else {
            presenceDot.setBackgroundResource(R.drawable.presence_offline)
        }

        // WhatsApp-style Chat Preview & Received Count Badge
        val preview = previewMap[user.uid]
        if (preview != null && preview.timestamp > 0) {
            holder.tvTime.visibility = View.VISIBLE
            holder.tvTime.text = formatPreviewTime(preview.timestamp)

            val snippet = when (preview.lastMessageType) {
                "image" -> "📷 Photo"
                "voice" -> "🎤 Voice Note"
                "steg" -> "🕵️ Hidden Image"
                "doc" -> "📁 Document"
                else -> {
                    if (preview.lastMessage.isNotEmpty()) preview.lastMessage else "Encrypted Signal"
                }
            }

            if (preview.isLastFromMe) {
                val tick = if (preview.lastStatus == "read") "✓✓ " else "✓ "
                holder.tvLastMessage.text = "$tick$snippet"
                holder.tvLastMessage.setTextColor(Color.parseColor("#9E9E9E"))
            } else {
                holder.tvLastMessage.text = snippet
                if (preview.unreadCount > 0) {
                    holder.tvLastMessage.setTextColor(Color.parseColor("#FFFFFF"))
                } else {
                    holder.tvLastMessage.setTextColor(Color.parseColor("#9E9E9E"))
                }
            }

            // Unread message count badge
            if (preview.unreadCount > 0) {
                holder.tvUnreadBadge.visibility = View.VISIBLE
                holder.tvUnreadBadge.text = if (preview.unreadCount > 99) "99+" else preview.unreadCount.toString()
                holder.tvTime.setTextColor(Color.parseColor("#00C853"))
            } else {
                holder.tvUnreadBadge.visibility = View.GONE
                holder.tvTime.setTextColor(Color.parseColor("#888888"))
            }

            // Agent ID & Received Messages summary
            if (preview.totalReceived > 0) {
                holder.tvId.text = "ID: #${user.agentId} • ${preview.totalReceived} received"
            } else {
                holder.tvId.text = "ID: #${user.agentId}"
            }
        } else {
            holder.tvTime.visibility = View.GONE
            holder.tvLastMessage.text = "No messages yet • Tap to chat"
            holder.tvLastMessage.setTextColor(Color.parseColor("#666666"))
            holder.tvUnreadBadge.visibility = View.GONE
            holder.tvId.text = "ID: #${user.agentId}"
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, DirectChatActivity::class.java)
            intent.putExtra("TARGET_UID", user.uid)
            intent.putExtra("TARGET_NAME", personalNickname ?: user.name)
            holder.itemView.context.startActivity(intent)
        }

        holder.itemView.setOnLongClickListener {
            val options = listOf("Set Nickname", "Delete Secure Link")
            PsiRaDialogs.showOptionsSheet(context, "LINK OPTIONS", options) { which ->
                when (which) {
                    0 -> {
                        val input = android.widget.EditText(context)
                        input.setText(personalNickname ?: user.name ?: "")
                        input.hint = "Agent Alias"
                        input.setTextColor(Color.WHITE)
                        input.setHintTextColor(Color.GRAY)

                        PsiRaDialogs.showDeleteSheet(
                            context,
                            "OVERRIDE ALIAS",
                            "Set a local designation for this agent. This only affects your view.",
                            "SET NICKNAME",
                            input
                        ) {
                            val newNick = input.text.toString().trim()
                            if (newNick.isNotEmpty()) {
                                sharedPrefNick.edit().putString(user.uid, newNick).apply()
                                notifyItemChanged(position)
                            }
                        }
                    }
                    1 -> {
                        PsiRaDialogs.showDeleteSheet(
                            context,
                            "SEVER CONNECTION?",
                            "This will permanently wipe your secure link with ${personalNickname ?: user.name}.",
                            "WIPE LINK"
                        ) {
                            val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                            if (myUid != null && user.uid != null) {
                                com.google.firebase.database.FirebaseDatabase.getInstance()
                                    .getReference("user_direct_chats")
                                    .child(myUid).child(user.uid).removeValue()
                            }
                        }
                    }
                }
            }
            true
        }
    }

    private fun formatPreviewTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val now = Calendar.getInstance()
        val msg = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isToday = now.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
                      now.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)
        if (isToday) {
            return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
        }

        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterday.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
                          yesterday.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) {
            return "Yesterday"
        }

        val diffDays = (now.timeInMillis - timestamp) / (1000 * 60 * 60 * 24)
        if (diffDays < 7) {
            return SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        }

        return SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
    }

    override fun getItemCount() = chatList.size
}
