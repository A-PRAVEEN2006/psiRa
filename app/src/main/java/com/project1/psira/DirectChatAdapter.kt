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
        val user = chatList[position]
        holder.tvName.text = user.name ?: "Unknown Agent"
        holder.tvId.text = "ID: #${user.agentId}"

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, DirectChatActivity::class.java)
            intent.putExtra("TARGET_UID", user.uid)
            intent.putExtra("TARGET_NAME", user.name)
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount() = chatList.size
}
