package com.project1.psira

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.yourname.psira.AESEncryption
import com.yourname.psira.PsiRaConverter

class ChatActivity : AppCompatActivity() {
    private lateinit var db: DatabaseReference
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: ArrayList<Message>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots for security
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chat)

        // Initialize the message list and the adapter bridge
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        messageList = ArrayList()
        messageAdapter = MessageAdapter(messageList)
        recyclerView.adapter = messageAdapter

        // Connect to your Firebase vault
        db = FirebaseDatabase.getInstance().getReference("messages")

        // Listen for new messages in real-time
        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()
                for (postSnapshot in snapshot.children) {
                    // Extract message data and include the unique ID for deleting
                    val content = postSnapshot.child("content").getValue(String::class.java)
                    val sender = postSnapshot.child("sender").getValue(String::class.java)
                    val id = postSnapshot.key

                    if (content != null && sender != null) {
                        messageList.add(Message(sender, content, id))
                    }
                }
                // Refresh the screen to show new messages
                messageAdapter.notifyDataSetChanged()

                if (messageList.isNotEmpty()) {
                    recyclerView.scrollToPosition(messageList.size - 1)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        val btnSend = findViewById<Button>(R.id.btnSend)
        val editMessage = findViewById<EditText>(R.id.editMessage)

        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            if (text.isNotEmpty()) {
                // THE SECURITY CHAIN: Plaintext -> psiRa Symbols -> AES Encrypt
                val psiraText = PsiRaConverter.encode(text)
                val encrypted = AESEncryption.encrypt(psiraText)

                // Send to Firebase (ID is null because Firebase creates it for us)
                db.push().setValue(Message("User", encrypted, null))

                editMessage.setText("")
            }
        }
    }
}