package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging // NEW IMPORT

class ChatActivity : AppCompatActivity() {
    private lateinit var db: DatabaseReference
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: ArrayList<Message>
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chat)

        recyclerView = findViewById(R.id.recyclerView)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val editMessage = findViewById<EditText>(R.id.editMessage)
        val btnLearning = findViewById<ImageButton>(R.id.btnLearningPage)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val secureStatusText = findViewById<TextView>(R.id.secureStatusText)

        // 1. Setup RecyclerView
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager

        messageList = ArrayList()
        messageAdapter = MessageAdapter(messageList)
        recyclerView.adapter = messageAdapter

        // 2. DATABASE CONNECTION & NOTIFICATION TUNING
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val channelName = sharedPref.getString("SECURE_CHANNEL", "messages") ?: "messages"

        db = FirebaseDatabase.getInstance().getReference(channelName)

        // --- NEW: NOTIFICATION TOPIC SUBSCRIPTION ---
        if (channelName == "global_protocol") {
            FirebaseMessaging.getInstance().subscribeToTopic("global_messages")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Successfully tuned to the Global Alert frequency
                    }
                }
        }

        // 3. Listen for messages
        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()
                for (postSnapshot in snapshot.children) {
                    val message = postSnapshot.getValue(Message::class.java)
                    if (message != null) {
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

        // 4. Navigation Buttons
        btnLearning.setOnClickListener {
            startActivity(Intent(this, LearningActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 5. Send Message Logic (Using Display Name)
        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            if (text.isNotEmpty()) {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val senderName = user?.displayName ?: "Unknown Agent"

                try {
                    val encrypted = AESEncryption.encrypt(text)
                    db.push().setValue(Message(null, senderName, encrypted))
                    editMessage.setText("")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 6. Glowing Pulse Animation
        val pulseAnimation = android.view.animation.AlphaAnimation(0.2f, 1.0f)
        pulseAnimation.duration = 1000
        pulseAnimation.repeatMode = android.view.animation.Animation.REVERSE
        pulseAnimation.repeatCount = android.view.animation.Animation.INFINITE
        secureStatusText.startAnimation(pulseAnimation)

        // 7. WIPE VAULT logic
        secureStatusText.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("⚠️ WIPE VAULT?")
                .setMessage("Initiate emergency wipe for channel '$channelName'?")
                .setPositiveButton("WIPE") { _, _ ->
                    db.removeValue().addOnSuccessListener {
                        Toast.makeText(this, "Vault Wiped Clean", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("CANCEL", null)
                .show()
            true
        }
    }
}