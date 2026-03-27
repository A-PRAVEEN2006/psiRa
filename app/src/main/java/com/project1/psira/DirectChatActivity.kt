package com.project1.psira

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class DirectChatActivity : BaseActivity() {
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
        

        findViewById<ImageButton>(R.id.btnSettings).visibility = android.view.View.GONE

        secureStatusText.text = "🔒 DIRECT LINK: $displayName"
        secureStatusText.setTextColor(android.graphics.Color.parseColor("#7B61FF"))

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

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager

        messageList = ArrayList()
        messageAdapter = MessageAdapter(messageList)
        recyclerView.adapter = messageAdapter

        val btnVoiceCall = findViewById<ImageButton>(R.id.btnVoiceCall)

        // 3. Call Buttons Logic
        btnVoiceCall.setOnClickListener {
            val callRef = FirebaseDatabase.getInstance().getReference("calls").child(targetUid!!)
            val callData = mapOf(
                "callerUid" to myUid,
                "callerName" to (FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown Agent"),
                "status" to "calling"
            )
            callRef.setValue(callData)
            val intent = Intent(this, CallActivity::class.java)
            intent.putExtra("TARGET_NAME", displayName)
            intent.putExtra("TARGET_UID", targetUid)
            intent.putExtra("CALL_MODE", "OUTGOING")
            startActivity(intent)
        }

        val channelName = if (myUid < targetUid!!) "${myUid}_$targetUid" else "${targetUid}_${myUid}"
        
        db = FirebaseDatabase.getInstance().getReference("direct_messages/$channelName")
        db.keepSynced(true) // Ensure private link offline consistency
        messageAdapter.chatDbRef = db // Corrected: Link for deletions after DB init

        val btnWipe = findViewById<ImageButton>(R.id.btnWipeChat)
        btnWipe?.setOnClickListener {
            PsiRaDialogs.showDeleteSheet(
                this,
                "SHRED CHANNEL?",
                "This will permanently obliterate all messages in this link. The signal cannot be recovered.",
                "SHRED"
            ) {
                db.removeValue().addOnSuccessListener {
                    Toast.makeText(this, "Channel Eradicated.", Toast.LENGTH_SHORT).show()
                }
            }
        }

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
                sendMessage(text, "text", isBurnable, senderName, myUid, isCipherMode)
                editMessage.setText("")
            }
        }
    }


    private fun sendMessage(content: String, type: String, isBurnable: Boolean, senderName: String, myUid: String, encodeCipher: Boolean = false) {
        try {
            val textToSend = if (type == "text" && encodeCipher) PsiRaConverter.encode(content) else content
            val encrypted = if (type == "text") AESEncryption.encrypt(textToSend) else content
            db.push().setValue(Message(null, senderName, encrypted, isBurnable, type)).addOnFailureListener {
                Toast.makeText(this, "Signal sync failed.", Toast.LENGTH_SHORT).show()
            }
            FirebaseDatabase.getInstance().getReference("user_direct_chats").child(myUid).child(targetUid!!).setValue(true)
            FirebaseDatabase.getInstance().getReference("user_direct_chats").child(targetUid!!).child(myUid).setValue(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        CallManager.listenForIncomingCalls(this)
        CallManager.updateContext(this)
    }

    override fun onStop() {
        super.onStop()
    }
}
