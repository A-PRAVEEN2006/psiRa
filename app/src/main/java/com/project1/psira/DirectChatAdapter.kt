package com.project1.psira

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DirectChatAdapter(private val chatList: List<User>) : RecyclerView.Adapter<DirectChatAdapter.DirectChatViewHolder>() {

    class DirectChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvChatName)
        val tvId: TextView = itemView.findViewById(R.id.tvChatId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirectChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_direct_chat, parent, false)
        return DirectChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: DirectChatViewHolder, position: Int) {
        val context = holder.itemView.context
        val user = chatList[position]
        
        val sharedPrefNick = context.getSharedPreferences("PsiRaNicknames", android.content.Context.MODE_PRIVATE)
        val personalNickname = sharedPrefNick.getString(user.uid, null)

        holder.tvName.text = personalNickname ?: user.name ?: "Unknown Agent"
        holder.tvId.text = "ID: #${user.agentId}"
        
        val presenceDot = holder.itemView.findViewById<View>(R.id.presenceDot)
        if (user.online) {
            presenceDot.setBackgroundResource(R.drawable.presence_online)
        } else {
            presenceDot.setBackgroundResource(R.drawable.presence_offline)
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
                        input.setTextColor(android.graphics.Color.WHITE)
                        input.setHintTextColor(android.graphics.Color.GRAY)

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

    override fun getItemCount() = chatList.size
}
