package com.project1.psira

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yourname.psira.AESEncryption
import com.yourname.psira.PsiRaConverter

class MessageAdapter(private val messageList: List<Message>) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textMessage: TextView = itemView.findViewById(R.id.textMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        // This grabs your item_message.xml bubble design!
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messageList[position]
        val messageId = message.id // Used for deleting

        try {
            // THE REVERSE SECURITY CHAIN: AES Decrypt -> Reverse psiRa Symbols
            // We call the classes directly so there are no import errors
            val decryptedPsiRa = AESEncryption.decrypt(message.content!!)
            val plainText = PsiRaConverter.decode(decryptedPsiRa)
            holder.textMessage.text = plainText
        } catch (e: Exception) {
            holder.textMessage.text = "Encrypted message..."
        }

        // LONG PRESS TO DELETE
        holder.itemView.setOnLongClickListener {
            val context = holder.itemView.context
            val builder = android.app.AlertDialog.Builder(context)
            builder.setTitle("Delete Message")
            builder.setMessage("Are you sure you want to delete this secret?")

            builder.setPositiveButton("Delete") { _, _ ->
                val db = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("messages")
                messageId?.let {
                    db.child(it).removeValue()
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