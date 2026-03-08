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

class MessageAdapter(private val messageList: List<Message>) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textMessage: TextView = itemView.findViewById(R.id.textMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        // This inflates your item_message.xml bubble design
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val context = holder.itemView.context
        val message = messageList[position]

        // Use the Firebase key (id) for deletion
        val messageId = message.id

        try {
            // THE REVERSE SECURITY CHAIN:
            // 1. AES Decrypt the raw Firebase data
            val decryptedPsiRa = AESEncryption.decrypt(message.content!!)

            // 2. Decode the PsiRa Symbols back into English/Numbers
            val plainText = PsiRaConverter.decode(decryptedPsiRa)

            holder.textMessage.text = plainText
        } catch (e: Exception) {
            // If it fails (e.g. old messages using a different key), show the raw content
            holder.textMessage.text = message.content
        }

        // --- LONG PRESS TO DELETE FEATURE ---
        // Inside onBindViewHolder in MessageAdapter.kt
        holder.itemView.setOnLongClickListener {
            val messageId = messageList[position].id

            if (messageId != null) {
                val builder = AlertDialog.Builder(context)
                builder.setTitle("Delete Message")
                builder.setMessage("Permanently erase this data from the vault?")

                builder.setPositiveButton("Delete") { _, _ ->
                    // Use the shared channel name to find the right database folder
                    val sharedPref = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
                    val channelName = sharedPref.getString("SECURE_CHANNEL", "messages") ?: "messages"

                    val db = FirebaseDatabase.getInstance().getReference(channelName)
                    db.child(messageId).removeValue().addOnSuccessListener {
                        Toast.makeText(context, "Message Erased", Toast.LENGTH_SHORT).show()
                    }
                }

                builder.setNegativeButton("Cancel", null)
                builder.show()
            } else {
                Toast.makeText(context, "Error: Message ID missing", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }
}
