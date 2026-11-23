package com.project1.psira

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val messageList: List<Message>,
    private val alreadyDecrypted: Boolean = false
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    var chatDbRef: DatabaseReference? = null
    var onQuoteClicked: ((String?) -> Unit)? = null

    var isMirrored: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val VIEW_TYPE_SENT_TEXT = 1
    private val VIEW_TYPE_RECEIVED_TEXT = 2
    private val VIEW_TYPE_SENT_VOICE = 3
    private val VIEW_TYPE_RECEIVED_VOICE = 4
    private val VIEW_TYPE_SENT_MEDIA = 5
    private val VIEW_TYPE_RECEIVED_MEDIA = 6

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDateHeader: TextView? = itemView.findViewById(R.id.tvDateHeader)
        val textMessage: TextView? = itemView.findViewById(R.id.textMessage)
        val tvMessageTime: TextView? = itemView.findViewById(R.id.tvMessageTime)
        val tvMessageStatus: TextView? = itemView.findViewById(R.id.tvMessageStatus)
        val tvSenderName: TextView? = itemView.findViewById(R.id.tvSenderName)
        val tvBurnIcon: TextView? = itemView.findViewById(R.id.tvBurnIcon)
        val btnPlayVoice: ImageButton? = itemView.findViewById(R.id.btnPlayVoice)
        val tvVoiceDuration: TextView? = itemView.findViewById(R.id.tvVoiceDuration)
        val ivMessageMedia: ImageView? = itemView.findViewById(R.id.ivMessageMedia)
        val tvFileName: TextView? = itemView.findViewById(R.id.tvFileName)
        val btnQuickSaveMedia: ImageButton? = itemView.findViewById(R.id.btnQuickSaveMedia)

        // Quoted reply fields
        val layoutQuotedReply: View? = itemView.findViewById(R.id.layoutQuotedReply)
        val tvQuoteSender: TextView? = itemView.findViewById(R.id.tvQuoteSender)
        val tvQuoteText: TextView? = itemView.findViewById(R.id.tvQuoteText)
    }

    override fun getItemViewType(position: Int): Int {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val message = messageList[position]
        val isMe = message.sender == currentUser?.displayName

        return when (message.type) {
            "voice" -> if (isMe) VIEW_TYPE_SENT_VOICE else VIEW_TYPE_RECEIVED_VOICE
            "image", "steg", "doc" -> if (isMe) VIEW_TYPE_SENT_MEDIA else VIEW_TYPE_RECEIVED_MEDIA
            else -> if (isMe) VIEW_TYPE_SENT_TEXT else VIEW_TYPE_RECEIVED_TEXT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = when (viewType) {
            VIEW_TYPE_SENT_TEXT -> R.layout.item_message_sent
            VIEW_TYPE_RECEIVED_TEXT -> R.layout.item_message_received
            VIEW_TYPE_SENT_VOICE -> R.layout.item_message_voice_sent
            VIEW_TYPE_RECEIVED_VOICE -> R.layout.item_message_voice_received
            VIEW_TYPE_SENT_MEDIA -> R.layout.item_message_media_sent
            VIEW_TYPE_RECEIVED_MEDIA -> R.layout.item_message_media_received
            else -> R.layout.item_message_sent
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val context = holder.itemView.context
        val message = messageList[position]

        val rawResolved = if (alreadyDecrypted) {
            message.content ?: ""
        } else {
            try { 
                AESEncryption.decryptWithKey(message.content ?: "", AESEncryption.GLOBAL_GROUP_KEY) 
            } catch (e: Exception) {
                try {
                    AESEncryption.decrypt(message.content ?: "")
                } catch (e2: Exception) {
                    message.content ?: ""
                }
            }
        }

        val resolvedContent = if (message.type == "text") {
            try { PsiRaConverter.decodeAny(rawResolved) } catch (_: Exception) { rawResolved }
        } else {
            rawResolved
        }

        // --- DATE HEADER LOGIC (WhatsApp-style group by day) ---
        val showDateHeader = if (position == 0) {
            true
        } else {
            val prevMsg = messageList[position - 1]
            !isSameDay(message.timestamp, prevMsg.timestamp)
        }

        if (showDateHeader && holder.tvDateHeader != null) {
            holder.tvDateHeader.visibility = View.VISIBLE
            holder.tvDateHeader.text = formatDateHeader(message.timestamp)
        } else {
            holder.tvDateHeader?.visibility = View.GONE
        }

        // --- TIMESTAMP & STATUS CHECKMARKS ---
        val timeStr = if (message.timestamp > 0) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.format(Date(message.timestamp))
        } else ""
        holder.tvMessageTime?.text = timeStr

        val currentUser = FirebaseAuth.getInstance().currentUser
        val isMe = message.sender == currentUser?.displayName

        if (isMe) {
            holder.tvMessageStatus?.let { tvStatus ->
                when (message.status) {
                    "read" -> {
                        tvStatus.text = "✓✓"
                        tvStatus.setTextColor(Color.parseColor("#34B7F1")) // WhatsApp blue tick
                    }
                    "delivered" -> {
                        tvStatus.text = "✓✓"
                        tvStatus.setTextColor(Color.parseColor("#B3FFFFFF"))
                    }
                    else -> {
                        tvStatus.text = "✓"
                        tvStatus.setTextColor(Color.parseColor("#B3FFFFFF"))
                    }
                }
            }
        } else {
            holder.tvSenderName?.let { tvName ->
                if (!message.sender.isNullOrEmpty()) {
                    tvName.text = message.sender
                    tvName.visibility = View.VISIBLE
                } else {
                    tvName.visibility = View.GONE
                }
            }
        }

        // --- QUOTED REPLY PREVIEW ---
        if (!message.replyToText.isNullOrEmpty()) {
            holder.layoutQuotedReply?.visibility = View.VISIBLE
            holder.tvQuoteSender?.text = message.replyToSender ?: "Agent"
            holder.tvQuoteText?.text = message.replyToText
            holder.layoutQuotedReply?.setOnClickListener {
                onQuoteClicked?.invoke(message.replyToId)
            }
        } else {
            holder.layoutQuotedReply?.visibility = View.GONE
        }

        // --- CONTENT RENDERING ---
        when (message.type) {
            "text" -> {
                holder.textMessage?.text = resolvedContent
            }
            "voice" -> {
                holder.tvVoiceDuration?.text = "Voice Note (Encrypted)"
                holder.btnPlayVoice?.setOnClickListener {
                    playOneAudio(context, resolvedContent)
                }
            }
            "image", "steg" -> {
                holder.ivMessageMedia?.visibility = View.VISIBLE
                holder.tvFileName?.visibility = View.GONE
                holder.ivMessageMedia?.let { imageView ->
                    Glide.with(context)
                        .load(resolvedContent)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(imageView)

                    imageView.setOnClickListener {
                        showFullScreenImage(context, resolvedContent)
                    }
                }

                // 1-Click Fast Save button on received image
                holder.btnQuickSaveMedia?.let { btnSave ->
                    btnSave.visibility = View.VISIBLE
                    btnSave.setOnClickListener {
                        val fileName = "photo_${System.currentTimeMillis()}.png"
                        saveFileToLocal(context, resolvedContent, fileName)
                    }
                }
            }
            "doc" -> {
                holder.ivMessageMedia?.visibility = View.GONE
                holder.tvFileName?.visibility = View.VISIBLE
                holder.tvFileName?.text = "Encrypted Document (${resolvedContent.length / 1024} KB)"
            }
        }

        // --- SELF-DESTRUCT LOGIC ---
        holder.tvBurnIcon?.visibility = if (message.isBurnable) View.VISIBLE else View.GONE
        if (message.isBurnable && message.sender != currentUser?.displayName) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val messageIdToDelete = message.id
                if (messageIdToDelete != null && chatDbRef != null) {
                    chatDbRef!!.child(messageIdToDelete).removeValue()
                }
            }, 10000)
        }

        // --- LONG PRESS OPTIONS ---
        holder.itemView.setOnLongClickListener {
            val messageId = message.id
            if (messageId != null) {
                val type = message.type
                val options = mutableListOf<String>()

                if (type in listOf("image", "steg")) {
                    options.add("View Full Screen")
                    options.add("Save to Private Vault 🔒")
                    options.add("Save to Gallery")
                    options.add("Extract Steganography 🕵️")
                } else if (type in listOf("doc", "voice")) {
                    options.add("Save to Device")
                    options.add("Save to Private Vault 🔒")
                }

                if (type == "text") {
                    options.add("Copy to Clipboard")
                }
                options.add("Delete for Everyone")

                PsiRaDialogs.showOptionsSheet(context, "MESSAGE OPTIONS", options) { which ->
                    when (options[which]) {
                        "View Full Screen" -> showFullScreenImage(context, resolvedContent)
                        "Save to Gallery", "Save to Device" -> {
                            val ext = when (type) {
                                "image", "steg" -> "png"
                                "doc" -> "pdf"
                                "voice" -> "m4a"
                                else -> "bin"
                            }
                            saveFileToLocal(context, resolvedContent, "${type}_${System.currentTimeMillis()}.$ext")
                        }
                        "Save to Private Vault 🔒" -> {
                            try {
                                val base64Data = resolvedContent.substringAfter("base64,")
                                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                val mime = if (type == "voice") "video/mp4" else "image/png"
                                saveToVault(context, bytes, mime, "${type}_${System.currentTimeMillis()}.${if (type == "voice") "mp4" else "png"}")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Vault save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Extract Steganography 🕵️" -> {
                            try {
                                val base64Data = resolvedContent.substringAfter("base64,")
                                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                val opts = BitmapFactory.Options().apply {
                                    inPreferredConfig = Bitmap.Config.ARGB_8888
                                    inPremultiplied = false
                                }
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                                if (bmp != null) {
                                    extractSteganographyFromImage(context, bmp)
                                } else {
                                    Toast.makeText(context, "Cannot decode image", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Copy to Clipboard" -> {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("PsiRa Message", resolvedContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
                        }
                        "Delete for Everyone" -> {
                            PsiRaDialogs.showDeleteSheet(
                                context,
                                "ERASE SIGNAL?",
                                "This message will be purged from the encrypted logs forever.",
                                "PURGE"
                            ) {
                                chatDbRef?.child(messageId)?.removeValue()?.addOnSuccessListener {
                                    Toast.makeText(context, "Message Erased", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            true
        }
    }

    private fun showFullScreenImage(context: Context, imageContent: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.activity_media_vault_viewer)

        val imgView = dialog.findViewById<ImageView>(R.id.imgVaultViewer)
        val btnBack = dialog.findViewById<ImageButton>(R.id.btnVaultBack)
        val tvName = dialog.findViewById<TextView>(R.id.tvViewerFileName)
        val btnExport = dialog.findViewById<ImageButton>(R.id.btnExportVaultMedia)
        val progress = dialog.findViewById<ProgressBar>(R.id.progressVaultViewer)

        tvName.text = "Encrypted Media"
        imgView.visibility = View.VISIBLE
        progress.visibility = View.GONE
        btnBack.setOnClickListener { dialog.dismiss() }

        var currentBitmap: Bitmap? = null
        if (imageContent.startsWith("data:")) {
            try {
                val base64Data = imageContent.substringAfter("base64,")
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPremultiplied = false
                    inDither = false
                    inScaled = false
                }
                currentBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                imgView.setImageBitmap(currentBitmap)
            } catch (_: Exception) {}
        } else {
            Glide.with(context).load(imageContent).into(imgView)
        }

        var scaleFactor = 1f
        val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 5f)
                imgView.scaleX = scaleFactor
                imgView.scaleY = scaleFactor
                return true
            }
        })
        imgView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            true
        }

        btnExport.setOnClickListener {
            val options = arrayOf("Save to Gallery", "Save to Private Vault 🔒", "Extract Steganography 🕵️")
            MaterialAlertDialogBuilder(context)
                .setTitle("Image Options")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> saveFileToLocal(context, imageContent, "chat_${System.currentTimeMillis()}.png")
                        1 -> {
                            val base64Data = imageContent.substringAfter("base64,")
                            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                            saveToVault(context, bytes, "image/png", "chat_${System.currentTimeMillis()}.png")
                        }
                        2 -> {
                            if (currentBitmap != null) {
                                extractSteganographyFromImage(context, currentBitmap)
                            } else {
                                Toast.makeText(context, "Image still loading...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .show()
        }

        dialog.show()
    }

    private fun extractSteganographyFromImage(context: Context, bitmap: Bitmap) {
        val extracted = StegoHelper.extract(bitmap)
        if (extracted == null) {
            Toast.makeText(context, "No hidden steganography detected in this image.", Toast.LENGTH_SHORT).show()
            return
        }

        val defaultDecrypted = try {
            val str = String(extracted, Charsets.UTF_8)
            AESEncryption.decryptWithKey(str, AESEncryption.GLOBAL_GROUP_KEY)
        } catch (_: Exception) { null }

        if (defaultDecrypted != null) {
            showStegoResultDialog(context, defaultDecrypted)
        } else {
            val input = EditText(context)
            input.hint = "Enter decryption password"
            input.setTextColor(Color.WHITE)
            input.setHintTextColor(Color.GRAY)

            MaterialAlertDialogBuilder(context)
                .setTitle("🕵️ Hidden Data Found!")
                .setMessage("Data is encrypted with a custom key. Enter password:")
                .setView(input)
                .setPositiveButton("Decrypt") { _, _ ->
                    val pass = input.text.toString().trim()
                    try {
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        val keyBytes = digest.digest(pass.toByteArray(Charsets.UTF_8))
                        val key = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
                        val str = String(extracted, Charsets.UTF_8)
                        val result = AESEncryption.decryptWithKey(str, key)
                        showStegoResultDialog(context, result)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Decryption failed. Incorrect password.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showStegoResultDialog(context: Context, text: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle("🕵️ Hidden Message Extracted")
            .setMessage(text)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PsiRa Steg", text))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun saveToVault(context: Context, decodedBytes: ByteArray, mimeType: String, originalName: String) {
        try {
            val tmpFile = File(context.cacheDir, "vault_import_${System.currentTimeMillis()}.${if (mimeType.startsWith("video/")) "mp4" else "png"}")
            tmpFile.writeBytes(decodedBytes)
            val uri = Uri.fromFile(tmpFile)
            MediaVaultManager.importMedia(context, uri, mimeType)
            tmpFile.delete()
            Toast.makeText(context, "🔒 Stashed securely in Private Vault!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Vault save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playOneAudio(context: Context, url: String) {
        AudioPlayer.play(context, url,
            onStart = {
                Toast.makeText(context, "Initializing secure audio stream...", Toast.LENGTH_SHORT).show()
            },
            onError = {
                Toast.makeText(context, "Audio playback failed.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun saveFileToLocal(context: Context, url: String, fileName: String) {
        if (url.startsWith("data:")) {
            try {
                val base64Data = url.substringAfter("base64,")
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                writeBytesToStorage(context, decodedBytes, fileName)
            } catch (e: Exception) {
                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else if (url.startsWith("http://") || url.startsWith("https://")) {
            Toast.makeText(context, "Downloading secure asset...", Toast.LENGTH_SHORT).show()
            Thread {
                try {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.requestMethod = "GET"
                    conn.doInput = true
                    conn.connect()

                    if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                        val input = conn.inputStream
                        val decodedBytes = input.readBytes()
                        input.close()

                        (context as? android.app.Activity)?.runOnUiThread {
                            writeBytesToStorage(context, decodedBytes, fileName)
                        }
                    } else {
                        (context as? android.app.Activity)?.runOnUiThread {
                            Toast.makeText(context, "Download failed: HTTP ${conn.responseCode}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    private fun writeBytesToStorage(context: Context, decodedBytes: ByteArray, fileName: String) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "PsiRa_$fileName")
                    val mimeType = when {
                        fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
                        fileName.endsWith(".png", true) -> "image/png"
                        fileName.endsWith(".m4a", true) -> "audio/mp4"
                        fileName.endsWith(".pdf", true) -> "application/pdf"
                        else -> "*/*"
                    }
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    val relativePath = when {
                        mimeType.startsWith("image/") -> android.os.Environment.DIRECTORY_PICTURES
                        mimeType.startsWith("audio/") -> android.os.Environment.DIRECTORY_MUSIC
                        else -> android.os.Environment.DIRECTORY_DOWNLOADS
                    }
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collectionUri = when {
                    fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) || fileName.endsWith(".png", true) -> {
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    fileName.endsWith(".m4a", true) -> {
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    else -> {
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    }
                }

                val uri = resolver.insert(collectionUri, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(decodedBytes) }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    Toast.makeText(context, "Saved to Pictures/Music/Downloads", Toast.LENGTH_LONG).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val relativePath = when {
                    fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) || fileName.endsWith(".png", true) -> android.os.Environment.DIRECTORY_PICTURES
                    fileName.endsWith(".m4a", true) -> android.os.Environment.DIRECTORY_MUSIC
                    else -> android.os.Environment.DIRECTORY_DOWNLOADS
                }
                @Suppress("DEPRECATION")
                val destDir = android.os.Environment.getExternalStoragePublicDirectory(relativePath)
                if (!destDir.exists()) destDir.mkdirs()
                val destFile = File(destDir, "PsiRa_$fileName")
                destFile.writeBytes(decodedBytes)
                Toast.makeText(context, "Saved to ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        if (t1 <= 0 || t2 <= 0) return true
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    private fun formatDateHeader(timestamp: Long): String {
        if (timestamp <= 0) return "TODAY"
        val calMsg = Calendar.getInstance().apply { timeInMillis = timestamp }
        val calNow = Calendar.getInstance()

        val isToday = calMsg.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                      calMsg.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
        if (isToday) return "TODAY"

        val calYesterday = (calNow.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = calMsg.get(Calendar.YEAR) == calYesterday.get(Calendar.YEAR) &&
                          calMsg.get(Calendar.DAY_OF_YEAR) == calYesterday.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) return "YESTERDAY"

        return if (calMsg.get(Calendar.YEAR) == calNow.get(Calendar.YEAR)) {
            SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(timestamp))
        } else {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    override fun getItemCount(): Int = messageList.size
}
