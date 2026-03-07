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
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chat)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        // Push messages from the bottom up
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager

        messageList = ArrayList()
        messageAdapter = MessageAdapter(messageList)
        recyclerView.adapter = messageAdapter

        db = FirebaseDatabase.getInstance().getReference("messages")

        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()
                for (postSnapshot in snapshot.children) {
                    val message = postSnapshot.getValue(Message::class.java)
                    if (message != null) {
                        messageList.add(message)
                    }
                }
                messageAdapter.notifyDataSetChanged()
                if (messageList.isNotEmpty()) {
                    recyclerView.scrollToPosition(messageList.size - 1)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // THE SPRING: Force list up when keyboard appears
        recyclerView.addOnLayoutChangeListener { _, _, _, bottom, _, _, _, _, oldBottom ->
            if (bottom < oldBottom && messageList.isNotEmpty()) {
                recyclerView.postDelayed({
                    recyclerView.smoothScrollToPosition(messageList.size - 1)
                }, 100)
            }
        }

        val btnSend = findViewById<Button>(R.id.btnSend)
        val editMessage = findViewById<EditText>(R.id.editMessage)

        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            if (text.isNotEmpty()) {
                val psiraText = PsiRaConverter.encode(text)
                val encrypted = AESEncryption.encrypt(psiraText)

                db.push().setValue(Message("User", encrypted))
                editMessage.setText("")
            }
        }
    }
}