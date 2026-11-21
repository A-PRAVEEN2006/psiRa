package com.project1.psira

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream
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

    private var presenceRef: DatabaseReference? = null
    private var targetPresenceListener: ValueEventListener? = null

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val type = if (contentResolver.getType(uri)?.startsWith("image/") == true) "image" else "doc"
            encodeAndSendBase64(uri, type)
        }
    }

    private val pickStegoImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            promptAndSendStegoImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chat)

        targetUid = intent.getStringExtra("TARGET_UID")
        targetName = intent.getStringExtra("TARGET_NAME")

        if (targetUid == null) {
            Toast.makeText(this, "⚠ Secure link corrupted. Re-establish contact.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val myUid = FirebaseAuth.getInstance().currentUser!!.uid
        val sharedPrefNick = getSharedPreferences("PsiRaNicknames", android.content.Context.MODE_PRIVATE)
        val personalNickname = sharedPrefNick.getString(targetUid, null)
        val displayName = personalNickname ?: targetName ?: "Unknown Agent"

        recyclerView = findViewById(R.id.recyclerView)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val editMessage = findViewById<EditText>(R.id.editMessage)
        val secureStatusText = findViewById<TextView>(R.id.secureStatusText)
        val tvTypingIndicator = findViewById<TextView>(R.id.tvTypingIndicator)

        findViewById<View>(R.id.chatBg).setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
        findViewById<ImageButton>(R.id.btnSettings).visibility = View.GONE

        val btnAttachMedia = findViewById<ImageButton>(R.id.btnAttachMedia)
        val btnRecordVoice = findViewById<ImageButton>(R.id.btnRecordVoice)

        btnAttachMedia.setOnClickListener {
            PsiRaDialogs.showOptionsSheet(
                this,
                "ATTACH FILE",
                listOf("📸 Image", "🕵️ Hide Message in Image (Stego)", "📄 Document")
            ) { index ->
                when (index) {
                    0 -> pickMediaLauncher.launch("image/*")
                    1 -> pickStegoImageLauncher.launch("image/*")
                    2 -> pickMediaLauncher.launch("*/*")
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
        val orbView = findViewById<ImageView>(R.id.btnCipherOrb)
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

        loadLocalCache()

        // 🔐 Pre-derive ECDH shared key
        secureStatusText.text = "🔒 ESTABLISHING E2EE..."
        ECDHKeyManager.deriveSharedKey(
            this, targetUid!!,
            onReady = { _ ->
                runOnUiThread {
                    secureStatusText.text = "🔒 E2EE DIRECT LINK: $displayName"
                    reDecryptMessages()
                }
            },
            onError = { err ->
                runOnUiThread {
                    secureStatusText.text = "🔒 DIRECT LINK: $displayName"
                    Toast.makeText(this, "E2EE notice: $err", Toast.LENGTH_LONG).show()
                }
            }
        )

        val btnVoiceCall = findViewById<ImageButton>(R.id.btnVoiceCall)
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
        db.keepSynced(true)
        messageAdapter.chatDbRef = db

        // --- PRESENCE & REALTIME TYPING INDICATORS ---
        presenceRef = FirebaseDatabase.getInstance().getReference("presence/$channelName")
        val myPresence = presenceRef!!.child(myUid)
        myPresence.child("online").setValue(true)
        myPresence.child("online").onDisconnect().setValue(false)
        myPresence.child("typing").setValue(false)
        myPresence.child("typing").onDisconnect().setValue(false)

        targetPresenceListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isTyping = snapshot.child("typing").getValue(Boolean::class.java) ?: false
                val isOnline = snapshot.child("online").getValue(Boolean::class.java) ?: false

                if (isTyping) {
                    tvTypingIndicator.visibility = View.VISIBLE
                    tvTypingIndicator.text = "$displayName is typing..."
                    secureStatusText.text = "✏️ typing..."
                } else {
                    tvTypingIndicator.visibility = View.GONE
                    val statusPrefix = if (isOnline) "🟢 ONLINE" else "⚪ OFFLINE"
                    secureStatusText.text = "$statusPrefix • $displayName"
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        presenceRef!!.child(targetUid!!).addValueEventListener(targetPresenceListener!!)

        val typingHandler = Handler(Looper.getMainLooper())
        val stopTypingRunnable = Runnable {
            myPresence.child("typing").setValue(false)
        }

        editMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = (s?.length ?: 0) > 0
                myPresence.child("typing").setValue(hasText)
                typingHandler.removeCallbacks(stopTypingRunnable)
                if (hasText) {
                    typingHandler.postDelayed(stopTypingRunnable, 2500)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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

        val decryptedCache = HashMap<String, String>()
        val myDisplayName = FirebaseAuth.getInstance().currentUser?.displayName

        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messageList.clear()
                for (postSnapshot in snapshot.children) {
                    val raw = postSnapshot.getValue(Message::class.java) ?: continue
                    val msgKey = postSnapshot.key ?: ""

                    // Mark as read if sent by other party
                    if (raw.sender != myDisplayName && raw.status != "read") {
                        db.child(msgKey).child("status").setValue("read")
                    }

                    val rawContent = raw.content ?: ""
                    val decryptedContent = decryptedCache.getOrPut(msgKey) {
                        ECDHKeyManager.decryptFromContact(
                            this@DirectChatActivity, targetUid!!, rawContent
                        )
                    }
                    val msg = Message(
                        id = msgKey,
                        sender = raw.sender,
                        content = decryptedContent,
                        isBurnable = raw.isBurnable,
                        type = raw.type,
                        timestamp = if (raw.timestamp > 0) raw.timestamp else System.currentTimeMillis(),
                        status = raw.status
                    )
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

        var isBurnMode = false
        btnSend.setOnLongClickListener {
            isBurnMode = !isBurnMode
            vibrate(80)
            if (isBurnMode) {
                btnSend.setTextColor(android.graphics.Color.parseColor("#FF3B30"))
                btnSend.text = "🔥"
                Toast.makeText(this, "🔥 Burn-on-Read Active: Messages burn 10s after viewing", Toast.LENGTH_SHORT).show()
            } else {
                btnSend.setTextColor(android.graphics.Color.WHITE)
                btnSend.text = "➤"
                Toast.makeText(this, "Standard Signal Mode", Toast.LENGTH_SHORT).show()
            }
            true
        }

        btnSend.setOnClickListener {
            val text = editMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                val user = FirebaseAuth.getInstance().currentUser
                val senderName = user?.displayName ?: "Unknown Agent"
                sendMessage(text, "text", isBurnMode, senderName, myUid, isCipherMode)
                editMessage.setText("")
                myPresence.child("typing").setValue(false)
            } else {
                Toast.makeText(this, "Empty signals are not broadcasted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun promptAndSendStegoImage(uri: Uri) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }
        val etSecret = EditText(this).apply {
            hint = "Secret message to conceal"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }
        val etKey = EditText(this).apply {
            hint = "Optional encryption password"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }
        layout.addView(etSecret)
        layout.addView(etKey)

        MaterialAlertDialogBuilder(this)
            .setTitle("🕵️ Conceal Secret in Image")
            .setMessage("Select text to embed into this carrier image:")
            .setView(layout)
            .setPositiveButton("Conceal & Send") { _, _ ->
                val secret = etSecret.text.toString().trim()
                if (secret.isEmpty()) {
                    Toast.makeText(this, "Secret message cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val password = etKey.text.toString().trim()
                encodeAndSendStegoImage(uri, secret, password)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun encodeAndSendStegoImage(uri: Uri, secret: String, password: String) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Steganography Encoding")
            .setMessage("Embedding secret message into lossless carrier...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPremultiplied = false
                    inDither = false
                    inScaled = false
                }
                val bitmap = BitmapFactory.decodeStream(inputStream, null, opts)
                inputStream?.close()

                if (bitmap == null) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this, "Failed to load image carrier", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val encryptedData: ByteArray = if (password.isNotEmpty()) {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val keyBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
                    val key = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
                    AESEncryption.encryptWithKey(secret, key).toByteArray(Charsets.UTF_8)
                } else {
                    AESEncryption.encryptWithKey(secret, AESEncryption.GLOBAL_GROUP_KEY).toByteArray(Charsets.UTF_8)
                }

                val stegoBitmap = StegoHelper.conceal(bitmap, encryptedData)
                if (stegoBitmap == null) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this, "Image too small to hold this message!", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }

                val baos = ByteArrayOutputStream()
                stegoBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                val pngBytes = baos.toByteArray()
                val base64String = "data:image/png;base64," + android.util.Base64.encodeToString(pngBytes, android.util.Base64.DEFAULT)

                runOnUiThread {
                    progressDialog.dismiss()
                    val myUid = FirebaseAuth.getInstance().currentUser!!.uid
                    val senderName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown Agent"
                    sendMessage(base64String, "steg", false, senderName, myUid, false)
                    Toast.makeText(this@DirectChatActivity, "🕵️ Stego carrier transmitted!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Steganography failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun sendMessage(content: String, type: String, isBurnable: Boolean, senderName: String, myUid: String, encodeCipher: Boolean = false) {
        val payloadToSend = if (type == "text" && encodeCipher) PsiRaConverter.encode(content) else content

        ECDHKeyManager.encryptForContact(
            this, targetUid!!, payloadToSend,
            onResult = { encrypted ->
                val newMsg = Message(
                    id = null,
                    sender = senderName,
                    content = encrypted,
                    isBurnable = isBurnable,
                    type = type,
                    timestamp = System.currentTimeMillis(),
                    status = "sent"
                )
                db.push().setValue(newMsg)
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Signal sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                registerContactLink(myUid)
            },
            onError = { _ ->
                val newMsg = Message(
                    id = null,
                    sender = senderName,
                    content = payloadToSend,
                    isBurnable = isBurnable,
                    type = type,
                    timestamp = System.currentTimeMillis(),
                    status = "sent"
                )
                db.push().setValue(newMsg)
                registerContactLink(myUid)
            }
        )
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
        targetUid?.let {
            val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@let
            val channelName = if (myUid < it) "${myUid}_$it" else "${it}_${myUid}"
            FirebaseDatabase.getInstance().getReference("presence/$channelName/$myUid/online").setValue(true)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            stopRecording(false)
        }
        targetUid?.let {
            val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@let
            val channelName = if (myUid < it) "${myUid}_$it" else "${it}_${myUid}"
            FirebaseDatabase.getInstance().getReference("presence/$channelName/$myUid/typing").setValue(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        targetUid?.let {
            val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@let
            val channelName = if (myUid < it) "${myUid}_$it" else "${it}_${myUid}"
            val pRef = FirebaseDatabase.getInstance().getReference("presence/$channelName")
            pRef.child(myUid).child("online").setValue(false)
            pRef.child(myUid).child("typing").setValue(false)
            targetPresenceListener?.let { l -> pRef.child(it).removeEventListener(l) }
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
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            findViewById<ImageButton>(R.id.btnRecordVoice).setColorFilter(android.graphics.Color.parseColor("#FF3B30"))
            Toast.makeText(this, "Recording audio...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording(send: Boolean) {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            isRecording = false
            findViewById<ImageButton>(R.id.btnRecordVoice).clearColorFilter()
        }

        if (send && voiceOutputFile != null && voiceOutputFile!!.exists()) {
            val uri = Uri.fromFile(voiceOutputFile)
            encodeAndSendBase64(uri, "voice")
        } else {
            voiceOutputFile?.delete()
        }
    }

    private fun encodeAndSendBase64(uri: Uri, type: String) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Uploading...")
            .setMessage("Encrypting asset...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            try {
                var base64String = ""
                if (type == "image") {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        val maxDim = 1200
                        val width = bitmap.width
                        val height = bitmap.height
                        val scaledBitmap = if (width > maxDim || height > maxDim) {
                            val ratio = width.toFloat() / height.toFloat()
                            val newWidth = if (ratio > 1) maxDim else (maxDim * ratio).toInt()
                            val newHeight = if (ratio > 1) (maxDim / ratio).toInt() else maxDim
                            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                        } else bitmap

                        val baos = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                        val b = baos.toByteArray()
                        base64String = "data:image/jpeg;base64," + android.util.Base64.encodeToString(b, android.util.Base64.DEFAULT)
                    }
                } else if (type == "voice") {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: ByteArray(0)
                    inputStream?.close()
                    base64String = "data:audio/mp4;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                } else {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: ByteArray(0)
                    inputStream?.close()
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

    private fun reDecryptMessages() {
        val target = targetUid ?: return
        var updated = false
        for (i in 0 until messageList.size) {
            val msg = messageList[i]
            if (msg.type == "text") {
                val decrypted = ECDHKeyManager.decryptFromContact(this, target, msg.content ?: "")
                if (decrypted != msg.content) {
                    messageList[i] = msg.copy(content = decrypted)
                    updated = true
                }
            }
        }
        if (updated) {
            messageAdapter.notifyDataSetChanged()
        }
    }
}
