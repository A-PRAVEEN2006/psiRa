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

    private var mediaRecorder: MediaRecorder? = null
    private var voiceOutputFile: File? = null
    private var isRecording = false

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val type = if (contentResolver.getType(uri)?.startsWith("image/") == true) "image" else "doc"
            encodeAndSendBase64(uri, type)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chat) // Harness identical UI for speed/stealth

        targetUid = intent.getStringExtra("TARGET_UID")
        targetName = intent.getStringExtra("TARGET_NAME")

        if (targetUid == null) {
            Toast.makeText(this, "⚠ Secure link corrupted. Re-establish contact.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

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
        messageAdapter = MessageAdapter(messageList, alreadyDecrypted = true)
        recyclerView.adapter = messageAdapter

        loadLocalCache() // Instant history recovery

        // 🔐 Pre-derive the ECDH shared key for this contact in background
        secureStatusText.text = "🔒 ESTABLISHING E2EE..."
        ECDHKeyManager.deriveSharedKey(
            this, targetUid!!,
            onReady = { _ ->
                runOnUiThread { secureStatusText.text = "🔒 E2EE DIRECT LINK: $displayName" }
            },
            onError = { err ->
                runOnUiThread {
                    secureStatusText.text = "🔒 DIRECT LINK: $displayName"
                    android.widget.Toast.makeText(this, "E2EE notice: $err", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        )

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
                    val raw = postSnapshot.getValue(Message::class.java) ?: continue
                    val decryptedContent = ECDHKeyManager.decryptFromContact(
                        this@DirectChatActivity, targetUid!!, raw.content ?: ""
                    )
                    // Build a new Message with decrypted content
                    val msg = Message(postSnapshot.key, raw.sender, decryptedContent, raw.isBurnable, raw.type)
                    messageList.add(msg)
                }
                messageAdapter.notifyDataSetChanged()
                if (messageList.isNotEmpty()) {
                    recyclerView.scrollToPosition(messageList.size - 1)
                    saveLocalCache()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        btnSend.setOnClickListener {
            val text = editMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                val user = FirebaseAuth.getInstance().currentUser
                val senderName = user?.displayName ?: "Unknown Agent"
                val isBurnable = false
                sendMessage(text, "text", isBurnable, senderName, myUid, isCipherMode)
                editMessage.setText("")
            } else {
                Toast.makeText(this, "Empty signals are not broadcasted.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun sendMessage(content: String, type: String, isBurnable: Boolean, senderName: String, myUid: String, encodeCipher: Boolean = false) {
        val textToSend = if (type == "text" && encodeCipher) PsiRaConverter.encode(content) else content

        if (type == "text") {
            // 🔐 Encrypt with ECDH-derived key (falls back to shared static key transparently)
            ECDHKeyManager.encryptForContact(
                this, targetUid!!, textToSend,
                onResult = { encrypted ->
                    db.push().setValue(Message(null, senderName, encrypted, isBurnable, type))
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Signal sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    registerContactLink(myUid)
                },
                onError = { _ ->
                    // This path should not be reached — fallback always succeeds
                    // but just in case, send raw text
                    db.push().setValue(Message(null, senderName, textToSend, isBurnable, type))
                    registerContactLink(myUid)
                }
            )
        } else {
            // Media/voice — content is a URL, no text encryption
            db.push().setValue(Message(null, senderName, content, isBurnable, type))
            registerContactLink(myUid)
        }
    }

    private fun registerContactLink(myUid: String) {
        FirebaseDatabase.getInstance().getReference("user_direct_chats").child(myUid).child(targetUid!!).setValue(true)
        FirebaseDatabase.getInstance().getReference("user_direct_chats").child(targetUid!!).child(myUid).setValue(true)
    }

    private fun saveLocalCache() {
        val sharedPref = getSharedPreferences("PsiRaCache_$targetUid", android.content.Context.MODE_PRIVATE)
        val sb = StringBuilder()
        val limit = if (messageList.size > 50) messageList.size - 50 else 0
        for (i in limit until messageList.size) {
            val m = messageList[i]
            sb.append("${m.sender}|${m.content}|${m.isBurnable}|${m.type}")
            if (i < messageList.size - 1) sb.append("[MSG_SEP]")
        }
        sharedPref.edit().putString("history", sb.toString()).apply()
    }

    private fun loadLocalCache() {
        val sharedPref = getSharedPreferences("PsiRaCache_$targetUid", android.content.Context.MODE_PRIVATE)
        val raw = sharedPref.getString("history", "") ?: ""
        if (raw.isNotEmpty()) {
            val items = raw.split("[MSG_SEP]")
            for (item in items) {
                val f = item.split("|")
                if (f.size >= 4) {
                    messageList.add(Message(null, f[0], f[1], f[2] == "true", f[3]))
                }
            }
            messageAdapter.notifyDataSetChanged()
            recyclerView.scrollToPosition(messageList.size - 1)
        }
    }

    override fun onResume() {
        super.onResume()
        CallManager.listenForIncomingCalls(this)
        CallManager.updateContext(this)
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            stopRecording(false)
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
                    val myUid = FirebaseAuth.getInstance().currentUser!!.uid
                    val user = FirebaseAuth.getInstance().currentUser
                    val senderName = user?.displayName ?: "Unknown Agent"
                    sendMessage(base64String, type, false, senderName, myUid, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@DirectChatActivity, "Encryption failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
