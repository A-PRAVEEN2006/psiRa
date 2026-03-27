package com.project1.psira

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BluetoothMessage(val sender: String, val message: String, val isMe: Boolean)

class BluetoothChatAdapter(private val messageList: List<BluetoothMessage>) :
    RecyclerView.Adapter<BluetoothChatAdapter.BTViewHolder>() {

    class BTViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.textMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BTViewHolder {
        val layout = if (viewType == 1) R.layout.item_message_sent else R.layout.item_message_received
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return BTViewHolder(view)
    }

    override fun onBindViewHolder(holder: BTViewHolder, position: Int) {
        val msg = messageList[position]
        holder.tvMessage.text = msg.message
    }


    override fun getItemViewType(position: Int): Int {
        return if (messageList[position].isMe) 1 else 0
    }

    override fun getItemCount(): Int = messageList.size
}
