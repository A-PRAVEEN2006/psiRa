package com.project1.psira

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
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
            onLongClick= { confirmDelete(it) }
        )
        recyclerView.adapter = adapter

        fab.setOnClickListener {
            // Open system media picker for images + videos
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
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    try {
                        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                        // Only allow images and videos
                        if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) {
                            failCount++
                            continue
                        }
                        MediaVaultManager.importMedia(this@MediaVaultActivity, uri, mimeType)
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
        }
    }

    private fun openViewer(item: VaultMediaItem) {
        val intent = Intent(this, MediaVaultViewerActivity::class.java)
        intent.putExtra(MediaVaultViewerActivity.EXTRA_ITEM_ID, item.id)
        intent.putExtra(MediaVaultViewerActivity.EXTRA_MIME, item.mimeType)
        intent.putExtra(MediaVaultViewerActivity.EXTRA_ENC_FILENAME, item.encFileName)
        intent.putExtra(MediaVaultViewerActivity.EXTRA_ORIGINAL_NAME, item.originalName)
        startActivity(intent)
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
            emptyState.visibility  = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility  = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.destroy()
        uiScope.cancel()
        // Clean up any temp files left over
        MediaVaultManager.clearTempFiles(this)
    }
}
