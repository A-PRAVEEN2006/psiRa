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
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val secureStatusText = findViewById<TextView>(R.id.secureStatusText)

        // 1. Setup RecyclerView
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager

        messageList = ArrayList()
        messageAdapter = MessageAdapter(messageList)
        recyclerView.adapter = messageAdapter

        // 2. DATABASE CONNECTION (NOW WITH SECURE CHANNELS!)
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        // Grab the saved channel name (Defaults to "messages" if blank)
        val channelName = sharedPref.getString("SECURE_CHANNEL", "messages") ?: "messages"

        // Tune Firebase to that specific channel
        db = FirebaseDatabase.getInstance().getReference(channelName)

        // Listen for messages in this specific room
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

        // 3. Navigation Buttons
        btnLearning.setOnClickListener {
            val intent = Intent(this, LearningActivity::class.java)
            startActivity(intent)
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 4. Send Message Logic (Plain English + AES Encryption)
        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            if (text.isNotEmpty()) {
                // Grab the Display Name (Praveen) instead of the Email
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val senderName = user?.displayName ?: "Unknown Agent"

                try {
                    val encrypted = AESEncryption.encrypt(text)
                    // Send the message with the NAME
                    db.push().setValue(Message(null, senderName, encrypted))
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

        // 6. The Glowing Pulse Animation
        val pulseAnimation = android.view.animation.AlphaAnimation(0.2f, 1.0f)
        pulseAnimation.duration = 1000 // 1 second fade
        pulseAnimation.repeatMode = android.view.animation.Animation.REVERSE
        pulseAnimation.repeatCount = android.view.animation.Animation.INFINITE
        secureStatusText.startAnimation(pulseAnimation)

        // 7. THE STEALTH WIPE PROTOCOL (Wipes only the current channel)
        secureStatusText.setOnLongClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("⚠️ WIPE VAULT?")
            builder.setMessage("Initiate emergency wipe? This will permanently delete ALL messages in channel '$channelName'. This action cannot be undone.")

            builder.setPositiveButton("WIPE") { _, _ ->
                db.removeValue().addOnSuccessListener {
                    Toast.makeText(this, "Vault Wiped Clean", Toast.LENGTH_SHORT).show()
                }
            }

            builder.setNegativeButton("CANCEL", null)

            val dialog = builder.create()
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.RED)

            true
        }
    }
}