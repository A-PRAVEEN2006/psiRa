package com.project1.psira

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class ChatActivity : AppCompatActivity() {
    private lateinit var db: DatabaseReference
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: ArrayList<Message>
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Stealth: No screenshots allowed
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chat)

        recyclerView = findViewById(R.id.recyclerView)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val editMessage = findViewById<EditText>(R.id.editMessage)
        val btnLearning = findViewById<ImageButton>(R.id.btnLearningPage)

        // 1. Setup RecyclerView
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager

        messageList = ArrayList()
        messageAdapter = MessageAdapter(messageList)
        recyclerView.adapter = messageAdapter

        // 2. Database Connection
        db = FirebaseDatabase.getInstance().getReference("messages")

        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()
                for (postSnapshot in snapshot.children) {
                    val message = postSnapshot.getValue(Message::class.java)
                    if (message != null) {
                        // FIX: Captures the ID so the Delete button in Adapter works!
                        message.id = postSnapshot.key
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

        // 3. Learning Page Button Logic
        btnLearning.setOnClickListener {
            val intent = Intent(this, LearningActivity::class.java)
            startActivity(intent)
        }
        // The Glowing Pulse Animation
        val secureStatusText = findViewById<android.widget.TextView>(R.id.secureStatusText)
        val pulseAnimation = android.view.animation.AlphaAnimation(0.2f, 1.0f)
        pulseAnimation.duration = 1000 // 1 second fade
        pulseAnimation.repeatMode = android.view.animation.Animation.REVERSE
        pulseAnimation.repeatCount = android.view.animation.Animation.INFINITE
        secureStatusText.startAnimation(pulseAnimation)
        // THE STEALTH WIPE PROTOCOL: Long-press the pulsing text to wipe the vault
        secureStatusText.setOnLongClickListener {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("⚠️ WIPE VAULT?")
            builder.setMessage("Initiate emergency wipe? This will permanently delete ALL messages in the database. This action cannot be undone.")

            builder.setPositiveButton("WIPE") { _, _ ->
                // This line nukes the entire "messages" branch in Firebase
                db.removeValue().addOnSuccessListener {
                    android.widget.Toast.makeText(this, "Vault Wiped Clean", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            builder.setNegativeButton("CANCEL", null)

            // Make the pop-up look dangerous (optional)
            val dialog = builder.create()
            dialog.show()
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.RED)

            true // Tells Android the long-press was handled
        }
        // 4. Send Message Logic (Plain English + AES Encryption)
        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            if (text.isNotEmpty()) {
                try {
                    val encrypted = AESEncryption.encrypt(text)
                    // We push the encrypted string. The ID is null here because Firebase generates it.
                    db.push().setValue(Message(null, "User", encrypted))
                    editMessage.setText("")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 5. Auto-scroll when keyboard opens
        recyclerView.addOnLayoutChangeListener { _, _, _, bottom, _, _, _, _, oldBottom ->
            if (bottom < oldBottom && messageList.isNotEmpty()) {
                recyclerView.postDelayed({
                    recyclerView.smoothScrollToPosition(messageList.size - 1)
                }, 100)
            }
        }
    }
}