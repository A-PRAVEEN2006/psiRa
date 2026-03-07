package com.project1.psira // Keeps your package name

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yourname.psira.AESEncryption
import com.yourname.psira.Message
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

        // THE REVERSE SECURITY CHAIN: AES Decrypt -> Reverse psiRa Symbols -> Plaintext
        try {
            // Unlock the AES encryption
            val decryptedPsiRa = AESEncryption.decrypt(message.content)
            // Translate the symbols back to normal text
            val plainText = PsiRaConverter.decode(decryptedPsiRa)

            // Put the translated text into the bubble
            holder.textMessage.text = plainText
        } catch (e: Exception) {
            // If something goes wrong (like an old test message), just show this:
            holder.textMessage.text = "Encrypted message..."
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }
}