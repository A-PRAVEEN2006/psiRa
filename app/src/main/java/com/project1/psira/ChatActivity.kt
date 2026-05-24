package com.project1.psira

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.UUID

class ChatActivity : BaseActivity() {
    private lateinit var db: DatabaseReference
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: ArrayList<Message>
    private lateinit var recyclerView: RecyclerView

    private var messageListener: ValueEventListener? = null
    private var presenceListener: ValueEventListener? = null
    private lateinit var presenceRef: DatabaseReference

    private var mediaRecorder: MediaRecorder? = null
    private var voiceOutputFile: File? = null
    private var isRecording = false

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val type = if (contentResolver.getType(uri)?.startsWith("image/") == true) "image" else "doc"
            encodeAndSendBase64(uri, type)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messageListener?.let { db.removeEventListener(it) }
        presenceListener?.let { presenceRef.removeEventListener(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chat)

        recyclerView = findViewById(R.id.recyclerView)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val editMessage = findViewById<EditText>(R.id.editMessage)

        val btnAttachMedia = findViewById<ImageButton>(R.id.btnAttachMedia)
        val btnRecordVoice = findViewById<ImageButton>(R.id.btnRecordVoice)

        btnAttachMedia.setOnClickListener {
            PsiRaDialogs.showOptionsSheet(this, "ATTACH FILE", listOf("Image", "Document")) { index ->
                if (index == 0) {
                    pickMediaLauncher.launch("image/*")
                } else {
                    pickMediaLauncher.launch("*/*")
                }
            }
        }

        btnRecordVoice.setOnClickListener {
            if (isRecording) {
                stopRecording(true)
            } else {
                startRecording()
            }
        }

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
        db.keepSynced(true) // Ensure offline message consistency
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
        messageListener = object : ValueEventListener {
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
        }
        db.addValueEventListener(messageListener!!)



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
        presenceRef = FirebaseDatabase.getInstance().getReference("presence/$channelName")
        val myPresenceRef = presenceRef.child(userAuth?.uid ?: "anonymous")

        val isGhostMode = intent.getBooleanExtra("IS_GHOST_MODE", false)

        if (isGhostMode) {
            btnSend.isEnabled = false
            btnSend.text = "GHOST"
            editMessage.isEnabled = false
            editMessage.hint = "Wiretap Active. Read-Only Mode."
            btnAttachMedia.isEnabled = false
            btnRecordVoice.isEnabled = false
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
        presenceListener = object : ValueEventListener {
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
        }
        presenceRef.addValueEventListener(presenceListener!!)


        // 5. Send Message Logic (Using Display Name)
        btnSend.setOnClickListener {
            val text = editMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val impersonatingName = sharedPref.getString("IMPERSONATING_NAME", null)
                val senderName = impersonatingName ?: user?.displayName ?: "Unknown Agent"
                
                val isBurnable = false
                sendMessage(text, "text", isBurnable, senderName, isCipherMode)
                editMessage.setText("")
            } else {
                Toast.makeText(this, "Empty signals are rejected.", Toast.LENGTH_SHORT).show()
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

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            stopRecording(false)
        }
    }

    private fun sendMessage(content: String, type: String, isBurnable: Boolean, senderName: String, encodeCipher: Boolean = false) {
        if (type == "text") {
            try {
                val textToSend = if (encodeCipher) PsiRaConverter.encode(content) else content
                val encrypted = AESEncryption.encryptWithKey(textToSend, AESEncryption.GLOBAL_GROUP_KEY)
                db.push().setValue(Message(null, senderName, encrypted, isBurnable, type)).addOnFailureListener { e ->
                     Toast.makeText(this, "Transmission failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Encryption failure: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            db.push().setValue(Message(null, senderName, content, isBurnable, type)).addOnFailureListener { e ->
                 Toast.makeText(this, "Transmission failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkRecordAudioPermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
            return false
        }
        return true
    }

    private fun startRecording() {
        if (!checkRecordAudioPermission()) return
        
        val recDir = getExternalFilesDir(null) ?: filesDir
        val file = File(recDir, "voice_msg_${System.currentTimeMillis()}.m4a")
        voiceOutputFile = file
        
        try {
            @Suppress("DEPRECATION")
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= 31)
                MediaRecorder(this)
            else MediaRecorder()
            
            mediaRecorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            val btnRecordVoice = findViewById<ImageButton>(R.id.btnRecordVoice)
            btnRecordVoice.setColorFilter(android.graphics.Color.parseColor("#FF3B30"))
            val editMessage = findViewById<EditText>(R.id.editMessage)
            editMessage.isEnabled = false
            editMessage.hint = "🔴 Recording... Tap Mic again to Send"
            Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun stopRecording(shouldSend: Boolean) {
        if (!isRecording) return
        
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
        }
        
        val btnRecordVoice = findViewById<ImageButton>(R.id.btnRecordVoice)
        btnRecordVoice.clearColorFilter()
        val editMessage = findViewById<EditText>(R.id.editMessage)
        editMessage.isEnabled = true
        editMessage.hint = "Type a secure message..."
        
        val file = voiceOutputFile
        if (shouldSend && file != null && file.exists()) {
            encodeAndSendBase64(Uri.fromFile(file), "voice")
        }
        voiceOutputFile = null
    }

    private fun encodeAndSendBase64(uri: Uri, type: String) {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Encrypting asset...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        Thread {
            try {
                var base64String = ""
                if (type == "image") {
                    @Suppress("DEPRECATION")
                    val bitmap = android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    
                    val maxDim = 800
                    val width = bitmap.width
                    val height = bitmap.height
                    val scaledBitmap = if (width > maxDim || height > maxDim) {
                        val ratio = width.toFloat() / height.toFloat()
                        val newWidth = if (ratio > 1) maxDim else (maxDim * ratio).toInt()
                        val newHeight = if (ratio > 1) (maxDim / ratio).toInt() else maxDim
                        android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                    } else bitmap

                    val baos = java.io.ByteArrayOutputStream()
                    scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos)
                    val b = baos.toByteArray()
                    base64String = "data:image/jpeg;base64," + android.util.Base64.encodeToString(b, android.util.Base64.DEFAULT)
                } else if (type == "voice") {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: ByteArray(0)
                    base64String = "data:audio/mp4;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                } else {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: ByteArray(0)
                    base64String = "data:application/pdf;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                }

                runOnUiThread {
                    progressDialog.dismiss()
                    val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
                    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    val impersonatingName = sharedPref.getString("IMPERSONATING_NAME", null)
                    val senderName = impersonatingName ?: user?.displayName ?: "Unknown Agent"
                    sendMessage(base64String, type, false, senderName, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@ChatActivity, "Encryption failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}