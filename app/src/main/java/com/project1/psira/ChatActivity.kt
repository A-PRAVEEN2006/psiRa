package com.project1.psira // Keep your exact package name!

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.yourname.psira.AESEncryption
import com.yourname.psira.Message
import com.yourname.psira.PsiRaConverter
import com.yourname.psira.PsiRaConverter.encode
import kotlin.collections.isNotEmpty

class ChatActivity : AppCompatActivity() {
    private lateinit var db: DatabaseReference
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: ArrayList<Message>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots in the chat room too!
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chat)

        // 1. Set up the RecyclerView (The List) and the Adapter (The Bridge)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        messageList = ArrayList()
        messageAdapter = MessageAdapter(messageList)
        recyclerView.adapter = messageAdapter

        // 2. Connect to the Firebase "messages" vault
        db = FirebaseDatabase.getInstance().getReference("messages")

        // 3. Listen for new messages from the internet
        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear() // Clear the old list to avoid duplicates

                for (postSnapshot in snapshot.children) {
                    val message = postSnapshot.getValue(Message::class.java)
                    if (message != null) {
                        messageList.add(message) // Add the encrypted message to the list
                    }
                }

                messageAdapter.notifyDataSetChanged() // Tell the bridge to update the screen

                // Automatically scroll to the bottom to see the newest message
                if (messageList.isNotEmpty()) {
                    recyclerView.scrollToPosition(messageList.size - 1)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // If something goes wrong with the database connection
            }
        })

        // 4. The Sending Logic (You already built this part!)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val editMessage = findViewById<EditText>(R.id.editMessage)

        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            if (text.isNotEmpty()) {
                // THE SECURITY CHAIN: Plaintext -> psiRa Symbols -> AES Encrypt
                val psiraText = encode(text)
                val encrypted = AESEncryption.encrypt(psiraText)

                // Send the completely unreadable text to the database
                db.push().setValue(Message("User", encrypted))

                // Clear the input box
                editMessage.setText("")
            }
        }
    }
}