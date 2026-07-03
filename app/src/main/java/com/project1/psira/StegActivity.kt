package com.project1.psira

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.io.InputStream

class StegActivity : BaseActivity() {

    private enum class TabMode { CONCEAL, EXTRACT }
    private var currentTab = TabMode.CONCEAL

    // UI elements
    private lateinit var tabConceal: TextView
    private lateinit var tabExtract: TextView
    private lateinit var layoutConceal: LinearLayout
    private lateinit var layoutExtract: LinearLayout

    // Conceal tab views
    private lateinit var ivConcealPreview: ImageView
    private lateinit var layoutConcealPlaceholder: View
    private lateinit var etConcealMessage: EditText
    private lateinit var etConcealKey: EditText
    private lateinit var btnConcealAction: Button
    private var concealBitmap: Bitmap? = null

    // Extract tab views
    private lateinit var ivExtractPreview: ImageView
    private lateinit var layoutExtractPlaceholder: View
    private lateinit var etExtractKey: EditText
    private lateinit var btnExtractAction: Button
    private lateinit var layoutResult: View
    private lateinit var tvExtractedMessage: TextView
    private lateinit var btnCopyText: ImageButton
    private var extractBitmap: Bitmap? = null

    // Pickers using modern Activity Result API
    private val pickConcealImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = getBitmapFromUri(it)
            if (bitmap != null) {
                // Ensure bitmap config is ARGB_8888
                concealBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                ivConcealPreview.setImageBitmap(concealBitmap)
                ivConcealPreview.visibility = View.VISIBLE
                layoutConcealPlaceholder.visibility = View.GONE
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickExtractImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = getBitmapFromUri(it)
            if (bitmap != null) {
                extractBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                ivExtractPreview.setImageBitmap(extractBitmap)
                ivExtractPreview.visibility = View.VISIBLE
                layoutExtractPlaceholder.visibility = View.GONE
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_steg)

        initUI()
        setupListeners()
    }

    private fun initUI() {
        tabConceal = findViewById(R.id.tabConceal)
        tabExtract = findViewById(R.id.tabExtract)
        layoutConceal = findViewById(R.id.layoutConceal)
        layoutExtract = findViewById(R.id.layoutExtract)

        // Conceal tab binds
        ivConcealPreview = findViewById(R.id.ivConcealPreview)
        layoutConcealPlaceholder = findViewById(R.id.layoutConcealPlaceholder)
        etConcealMessage = findViewById(R.id.etConcealMessage)
        etConcealKey = findViewById(R.id.etConcealKey)
        btnConcealAction = findViewById(R.id.btnConcealAction)

        // Extract tab binds
        ivExtractPreview = findViewById(R.id.ivExtractPreview)
        layoutExtractPlaceholder = findViewById(R.id.layoutExtractPlaceholder)
        etExtractKey = findViewById(R.id.etExtractKey)
        btnExtractAction = findViewById(R.id.btnExtractAction)
        layoutResult = findViewById(R.id.layoutResult)
        tvExtractedMessage = findViewById(R.id.tvExtractedMessage)
        btnCopyText = findViewById(R.id.btnCopyText)
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        tabConceal.setOnClickListener { switchTab(TabMode.CONCEAL) }
        tabExtract.setOnClickListener { switchTab(TabMode.EXTRACT) }

        // Pick Image clicks
        findViewById<View>(R.id.cardSelectConcealImage).setOnClickListener {
            pickConcealImage.launch("image/*")
        }
        findViewById<View>(R.id.cardSelectExtractImage).setOnClickListener {
            pickExtractImage.launch("image/*")
        }

        // Action Buttons
        btnConcealAction.setOnClickListener { handleConceal() }
        btnExtractAction.setOnClickListener { handleExtract() }

        // Clipboard Copy
        btnCopyText.setOnClickListener {
            val text = tvExtractedMessage.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("PsiRa Steg", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                vibrate(50)
            }
        }
    }

    private fun switchTab(mode: TabMode) {
        if (currentTab == mode) return
        currentTab = mode

        if (mode == TabMode.CONCEAL) {
            tabConceal.setBackgroundResource(R.drawable.bg_rounded_input)
            tabConceal.setTextColor(Color.WHITE)
            tabExtract.background = null
            tabExtract.setTextColor(Color.GRAY)

            layoutConceal.visibility = View.VISIBLE
            layoutExtract.visibility = View.GONE
        } else {
            tabExtract.setBackgroundResource(R.drawable.bg_rounded_input)
            tabExtract.setTextColor(Color.WHITE)
            tabConceal.background = null
            tabConceal.setTextColor(Color.GRAY)

            layoutExtract.visibility = View.VISIBLE
            layoutConceal.visibility = View.GONE
        }
        vibrate(30)
    }

    private fun handleConceal() {
        val bitmap = concealBitmap
        if (bitmap == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            return
        }

        val message = etConcealMessage.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter a message to hide", Toast.LENGTH_SHORT).show()
            return
        }

        val password = etConcealKey.text.toString()

        // Encrypt the message
        val encryptedData = try {
            encryptMessage(message, password)
        } catch (e: Exception) {
            Toast.makeText(this, "Encryption failed!", Toast.LENGTH_SHORT).show()
            return
        }

        // Conceal the bytes
        Toast.makeText(this, "Embedding data into image...", Toast.LENGTH_SHORT).show()
        val stegoBitmap = StegoHelper.conceal(bitmap, encryptedData)

        if (stegoBitmap == null) {
            Toast.makeText(this, "Image too small to hold this message!", Toast.LENGTH_LONG).show()
            return
        }

        // Save lossless PNG to public pictures directory
        val savedUri = saveImageToGallery(stegoBitmap)
        if (savedUri != null) {
            vibrate(150)
            Toast.makeText(this, "Success! Saved carrier to pictures.", Toast.LENGTH_LONG).show()
            etConcealMessage.setText("")
        } else {
            Toast.makeText(this, "Failed to save stego image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleExtract() {
        val bitmap = extractBitmap
        if (bitmap == null) {
            Toast.makeText(this, "Please select carrier image", Toast.LENGTH_SHORT).show()
            return
        }

        val password = etExtractKey.text.toString()

        // Extract hidden bytes
        val extractedBytes = StegoHelper.extract(bitmap)
        if (extractedBytes == null) {
            layoutResult.visibility = View.GONE
            Toast.makeText(this, "No valid hidden data detected in this image", Toast.LENGTH_LONG).show()
            return
        }

        // Decrypt message
        try {
            val decryptedMessage = decryptMessage(extractedBytes, password)
            tvExtractedMessage.text = decryptedMessage
            layoutResult.visibility = View.VISIBLE
            vibrate(100)
            Toast.makeText(this, "Extraction successful!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            layoutResult.visibility = View.GONE
            Toast.makeText(this, "Decryption failed. Wrong password or corrupted data.", Toast.LENGTH_LONG).show()
        }
    }

    // ── Cryptography Helpers ──────────────────────────────────────────────────

    private fun getSecretKeyFromPassword(password: String): javax.crypto.SecretKey {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
    }

    private fun encryptMessage(message: String, password: String): ByteArray {
        val encryptedStr = if (password.isNotEmpty()) {
            val key = getSecretKeyFromPassword(password)
            AESEncryption.encryptWithKey(message, key)
        } else {
            AESEncryption.encryptWithKey(message, AESEncryption.GLOBAL_GROUP_KEY)
        }
        return encryptedStr.toByteArray(Charsets.UTF_8)
    }

    private fun decryptMessage(encryptedBytes: ByteArray, password: String): String {
        val encryptedStr = String(encryptedBytes, Charsets.UTF_8)
        return if (password.isNotEmpty()) {
            val key = getSecretKeyFromPassword(password)
            AESEncryption.decryptWithKey(encryptedStr, key)
        } else {
            AESEncryption.decryptWithKey(encryptedStr, AESEncryption.GLOBAL_GROUP_KEY)
        }
    }

    // ── Bitmap Loader ────────────────────────────────────────────────────────

    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        } finally {
            inputStream?.close()
        }
    }

    // ── Lossless Image Gallery Storage ───────────────────────────────────────

    private fun saveImageToGallery(bitmap: Bitmap): Uri? {
        val filename = "PsiRaSteg_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PsiRa")
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                val outputStream = contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                    outputStream.close()
                    return uri
                }
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
            }
        }
        return null
    }
}
