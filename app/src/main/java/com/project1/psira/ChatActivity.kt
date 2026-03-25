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

class ChatActivity : BaseActivity() {
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

        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val secureStatusText = findViewById<TextView>(R.id.secureStatusText)
        val btnVoiceCall = findViewById<ImageButton>(R.id.btnVoiceCall)
        
        // Hide voice call from public nodes
        if (btnVoiceCall != null) {
            btnVoiceCall.visibility = android.view.View.GONE
        }

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
        messageAdapter.chatDbRef = db // Link for deletions

        val btnWipe = findViewById<ImageButton>(R.id.btnWipeChat)
        btnWipe?.setOnClickListener {
            PsiRaDialogs.showDeleteSheet(
                this,
                "SHRED ENCLAVE LOGS?",
                "Confirm total eradication of encrypted messages in this channel.",
                "ERADICATE"
            ) {
                db.removeValue().addOnSuccessListener {
                    Toast.makeText(this, "Vault Purged.", Toast.LENGTH_SHORT).show()
                }
            }
        }

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



        var isCipherMode = false
        val orbView = findViewById<android.widget.ImageView>(R.id.btnCipherOrb)
        orbView.setOnClickListener {
            isCipherMode = !isCipherMode
            if (isCipherMode) {
                orbView.setColorFilter(android.graphics.Color.parseColor("#FF3B30"))
                Toast.makeText(this, "⚠ PsiRa Mode: New words will be encoded", Toast.LENGTH_SHORT).show()
            } else {
                orbView.clearColorFilter()
                Toast.makeText(this, "✓ English Mode", Toast.LENGTH_SHORT).show()
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // --- 4.5. PRESENCE & TYPING INDICATORS ---
        val userAuth = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val presenceRef = FirebaseDatabase.getInstance().getReference("presence/$channelName")
        val myPresenceRef = presenceRef.child(userAuth?.uid ?: "anonymous")

        val isGhostMode = intent.getBooleanExtra("IS_GHOST_MODE", false)

        if (isGhostMode) {
            btnSend.isEnabled = false
            btnSend.text = "GHOST"
            editMessage.isEnabled = false
            editMessage.hint = "Wiretap Active. Read-Only Mode."
            Toast.makeText(this, "Ghost Wiretap engaged. You are invisible.", Toast.LENGTH_LONG).show()
        } else {
            myPresenceRef.child("online").setValue(true)
            myPresenceRef.child("online").onDisconnect().setValue(false)
            myPresenceRef.child("typing").setValue(false)
            myPresenceRef.child("typing").onDisconnect().setValue(false)

            editMessage.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    myPresenceRef.child("typing").setValue((s?.length ?: 0) > 0)
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        val tvTypingIndicator = findViewById<TextView>(R.id.tvTypingIndicator)
        presenceRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var someoneTyping = false
                for (child in snapshot.children) {
                    if (child.key != userAuth?.uid && child.child("typing").getValue(Boolean::class.java) == true) {
                        someoneTyping = true
                        break
                    }
                }
                tvTypingIndicator.visibility = if (someoneTyping) android.view.View.VISIBLE else android.view.View.GONE
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 5. Send Message Logic (Using Display Name)
        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            if (text.isNotEmpty()) {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val impersonatingName = sharedPref.getString("IMPERSONATING_NAME", null)
                val senderName = impersonatingName ?: user?.displayName ?: "Unknown Agent"
                
                // Read from our new toggle
                val isBurnable = findViewById<android.widget.ToggleButton>(R.id.toggleBurn).isChecked

                try {
                    val textToSend = if (isCipherMode) PsiRaConverter.encode(text) else text
                    val encrypted = AESEncryption.encrypt(textToSend)
                    // Push with the new isBurnable flag
                    db.push().setValue(Message(null, senderName, encrypted, isBurnable))
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
            PsiRaDialogs.showDeleteSheet(
                this,
                "EMERGENCY PURGE?",
                "Initiate immediate shredding of vault '${channelName}'?",
                "INITIATE PURGE"
            ) {
                db.removeValue().addOnSuccessListener {
                    Toast.makeText(this, "Vault Wiped Clean", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
    }
}