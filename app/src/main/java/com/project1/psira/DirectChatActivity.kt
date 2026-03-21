package com.project1.psira

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DirectChatActivity : AppCompatActivity() {
    private lateinit var db: DatabaseReference
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: ArrayList<Message>
    private lateinit var recyclerView: RecyclerView
    private var targetUid: String? = null
    private var targetName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chat) // Harness identical UI for speed/stealth

        targetUid = intent.getStringExtra("TARGET_UID")
        targetName = intent.getStringExtra("TARGET_NAME")

        // Load local nickname if exists
        val myUid = FirebaseAuth.getInstance().currentUser!!.uid
        val sharedPrefNick = getSharedPreferences("PsiRaNicknames", android.content.Context.MODE_PRIVATE)
        val personalNickname = sharedPrefNick.getString(targetUid, null)
        val displayName = personalNickname ?: targetName ?: "Unknown Agent"

        recyclerView = findViewById(R.id.recyclerView)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val editMessage = findViewById<EditText>(R.id.editMessage)
        val secureStatusText = findViewById<TextView>(R.id.secureStatusText)
        
        findViewById<android.view.View>(R.id.chatBg).setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
        
        findViewById<ImageButton>(R.id.btnLearningPage).visibility = android.view.View.GONE
        findViewById<ImageButton>(R.id.btnSettings).visibility = android.view.View.GONE

        secureStatusText.text = "🔒 DIRECT LINK: $displayName"
        secureStatusText.setTextColor(android.graphics.Color.parseColor("#7B61FF"))

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager

        messageList = ArrayList()
        messageAdapter = MessageAdapter(messageList)
        recyclerView.adapter = messageAdapter

        val channelName = if (myUid < targetUid!!) "${myUid}_$targetUid" else "${targetUid}_${myUid}"
        
        db = FirebaseDatabase.getInstance().getReference("direct_messages/$channelName")

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

        btnSend.setOnClickListener {
            val text = editMessage.text.toString()
            if (text.isNotEmpty()) {
                val user = FirebaseAuth.getInstance().currentUser
                val senderName = user?.displayName ?: "Unknown Agent"
                val isBurnable = findViewById<android.widget.ToggleButton>(R.id.toggleBurn).isChecked

                try {
                    val encrypted = AESEncryption.encrypt(text)
                    db.push().setValue(Message(null, senderName, encrypted, isBurnable))
                    editMessage.setText("")
                    
                    FirebaseDatabase.getInstance().getReference("user_direct_chats").child(myUid).child(targetUid!!).setValue(true)
                    FirebaseDatabase.getInstance().getReference("user_direct_chats").child(targetUid!!).child(myUid).setValue(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
