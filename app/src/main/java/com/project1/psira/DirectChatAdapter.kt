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

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, DirectChatActivity::class.java)
            intent.putExtra("TARGET_UID", user.uid)
            intent.putExtra("TARGET_NAME", personalNickname ?: user.name)
            holder.itemView.context.startActivity(intent)
        }

        holder.itemView.setOnLongClickListener {
            val input = android.widget.EditText(context)
            input.setText(personalNickname ?: user.name ?: "")
            input.hint = "Enter Personal Nickname"

            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("🏷️ Set Personal Nickname")
                .setMessage("This specific label is only visible to YOU. Your contact won't know.")
                .setView(input)
                .setPositiveButton("SAVE") { _, _ ->
                    val newNick = input.text.toString().trim()
                    if (newNick.isNotEmpty()) {
                        sharedPrefNick.edit().putString(user.uid, newNick).apply()
                        notifyItemChanged(position)
                    }
                }
                .setNegativeButton("CANCEL", null)
                .show()
            true
        }
    }

    override fun getItemCount() = chatList.size
}
