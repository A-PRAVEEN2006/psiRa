package com.project1.psira

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*

class MediaVaultActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: MediaVaultAdapter
    private var items = mutableListOf<VaultMediaItem>()
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Original file(s) removed from gallery", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Original file(s) kept on device", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@registerForActivityResult
        importUris(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_media_vault)

        recyclerView = findViewById(R.id.rvVaultMedia)
        emptyState   = findViewById(R.id.tvVaultEmpty)
        fab          = findViewById(R.id.fabAddMedia)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarMediaVault).setNavigationOnClickListener {
            finish()
        }

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = MediaVaultAdapter(
            context    = this,
            items      = items,
            onClick    = { openViewer(it) },
            onLongClick= { showItemOptions(it) }
        )
        recyclerView.adapter = adapter

        fab.setOnClickListener {
            pickMedia.launch("*/*")
        }

        loadVault()
    }

    private fun loadVault() {
        uiScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                MediaVaultManager.getAll(this@MediaVaultActivity)
            }
            items.clear()
            items.addAll(loaded)
            adapter.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    private fun importUris(uris: List<Uri>) {
        Toast.makeText(this, "Encrypting ${uris.size} file(s)…", Toast.LENGTH_SHORT).show()
        uiScope.launch {
            var successCount = 0
            var failCount = 0
            val successfulUris = mutableListOf<Uri>()

            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    try {
                        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                        if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) {
                            failCount++
                            continue
                        }
                        MediaVaultManager.importMedia(this@MediaVaultActivity, uri, mimeType)
                        successfulUris.add(uri)
                        successCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                        failCount++
                    }
                }
            }

            val msg = when {
                failCount == 0 -> "✓ $successCount file(s) added to vault"
                successCount == 0 -> "Failed to import files"
                else -> "✓ $successCount added, $failCount failed"
            }
            Toast.makeText(this@MediaVaultActivity, msg, Toast.LENGTH_SHORT).show()
            loadVault()

            if (successfulUris.isNotEmpty()) {
                promptDeleteOriginals(successfulUris)
            }
        }
    }

    private fun promptDeleteOriginals(uris: List<Uri>) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Original Files?")
            .setMessage(
                "The selected file(s) are now securely encrypted in your private vault.\n\n" +
                "Would you like to delete the unencrypted original(s) from your device gallery to protect your privacy?"
            )
            .setPositiveButton("Delete Originals") { _, _ ->
                performDeleteOriginals(uris)
            }
            .setNegativeButton("Keep Originals", null)
            .show()
    }

    private fun performDeleteOriginals(uris: List<Uri>) {
        val mediaStoreUris = mutableListOf<Uri>()
        val documentUris = mutableListOf<Uri>()

        for (uri in uris) {
            val resolved = resolveToMediaStoreUri(uri)
            if (resolved != null) {
                mediaStoreUris.add(resolved)
            } else {
                documentUris.add(uri)
            }
        }

        var directDeletedCount = 0
        for (docUri in documentUris) {
            try {
                if (DocumentsContract.isDocumentUri(this, docUri)) {
                    if (DocumentsContract.deleteDocument(contentResolver, docUri)) {
                        directDeletedCount++
                    }
                } else {
                    if (contentResolver.delete(docUri, null, null) > 0) {
                        directDeletedCount++
                    }
                }
            } catch (e: Exception) {
                // Ignore failure on SAF document provider
            }
        }

        if (mediaStoreUris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(contentResolver, mediaStoreUris)
                    deleteRequestLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } catch (e: Exception) {
                    for (mUri in mediaStoreUris) {
                        try { contentResolver.delete(mUri, null, null) } catch (_: Exception) {}
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    for (mUri in mediaStoreUris) {
                        contentResolver.delete(mUri, null, null)
                    }
                    Toast.makeText(this, "Original files deleted from gallery", Toast.LENGTH_SHORT).show()
                } catch (sec: RecoverableSecurityException) {
                    deleteRequestLauncher.launch(
                        IntentSenderRequest.Builder(sec.userAction.actionIntent.intentSender).build()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                var count = 0
                for (mUri in mediaStoreUris) {
                    try {
                        if (contentResolver.delete(mUri, null, null) > 0) count++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (count > 0 || directDeletedCount > 0) {
                    Toast.makeText(this, "Original files deleted from gallery", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (directDeletedCount > 0) {
            Toast.makeText(this, "Original files deleted from gallery", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveToMediaStoreUri(uri: Uri): Uri? {
        if (uri.authority == "media" || uri.toString().startsWith("content://media/")) {
            return uri
        }
        if (DocumentsContract.isDocumentUri(this, uri)) {
            val authority = uri.authority
            if (authority == "com.android.providers.media.documents") {
                val docId = DocumentsContract.getDocumentId(uri)
                val parts = docId.split(":")
                if (parts.size >= 2) {
                    val type = parts[0]
                    val id = parts[1].toLongOrNull() ?: return null
                    return when (type.lowercase()) {
                        "image" -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        "video" -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        else -> null
                    }
                }
            }
        }
        return null
    }

    private fun openViewer(item: VaultMediaItem) {
        val intent = Intent(this, MediaVaultViewerActivity::class.java)
        intent.putExtra(MediaVaultViewerActivity.EXTRA_ITEM_ID, item.id)
        intent.putExtra(MediaVaultViewerActivity.EXTRA_MIME, item.mimeType)
        intent.putExtra(MediaVaultViewerActivity.EXTRA_ENC_FILENAME, item.encFileName)
        intent.putExtra(MediaVaultViewerActivity.EXTRA_ORIGINAL_NAME, item.originalName)
        startActivity(intent)
    }

    private fun showItemOptions(item: VaultMediaItem) {
        val options = arrayOf("Export to Gallery", "Delete from Vault")
        MaterialAlertDialogBuilder(this)
            .setTitle(item.originalName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportItem(item)
                    1 -> confirmDelete(item)
                }
            }
            .show()
    }

    private fun exportItem(item: VaultMediaItem) {
        uiScope.launch {
            val success = withContext(Dispatchers.IO) {
                MediaVaultManager.exportToGallery(this@MediaVaultActivity, item)
            }
            if (success) {
                Toast.makeText(this@MediaVaultActivity, "Exported to Pictures/Movies", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MediaVaultActivity, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(item: VaultMediaItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete from Vault?")
            .setMessage("\"${item.originalName}\" will be permanently destroyed.")
            .setPositiveButton("Delete") { _, _ ->
                uiScope.launch {
                    withContext(Dispatchers.IO) {
                        MediaVaultManager.delete(this@MediaVaultActivity, item)
                    }
                    Toast.makeText(this@MediaVaultActivity, "Deleted", Toast.LENGTH_SHORT).show()
                    loadVault()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEmptyState() {
        if (items.isEmpty()) {
            emptyState.visibility   = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility   = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.destroy()
        uiScope.cancel()
        MediaVaultManager.clearTempFiles(this)
    }
}
