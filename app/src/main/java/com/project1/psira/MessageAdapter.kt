package com.project1.psira

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
        holder.itemView.setOnLongClickListener {
            val context = holder.itemView.context
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Erase Evidence?")
            builder.setMessage("This will permanently delete this message from the secure vault.")

            builder.setPositiveButton("Delete") { _, _ ->
                val db = FirebaseDatabase.getInstance().getReference("messages")

                if (messageId != null) {
                    db.child(messageId).removeValue()
                        .addOnSuccessListener {
                            Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(context, "Error: Message ID not found", Toast.LENGTH_SHORT).show()
                }
            }

            builder.setNegativeButton("Cancel", null)
            builder.show()
            true
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }
}