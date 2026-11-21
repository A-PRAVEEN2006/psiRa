package com.project1.psira

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MediaVaultManager {

    private const val VAULT_DIR = "media_vault"
    private const val TMP_DIR   = "vault_tmp"
    private const val PREFS     = "VaultMediaPrefs"
    private const val KEY_ITEMS = "vault_items_v1"

    // ── Directory helpers ────────────────────────────────────────────────

    private fun vaultDir(ctx: Context): File =
        File(ctx.filesDir, VAULT_DIR).also { it.mkdirs() }

    private fun tmpDir(ctx: Context): File =
        File(ctx.cacheDir, TMP_DIR).also { it.mkdirs() }

    // ── Metadata (stored as JSON in SharedPreferences) ───────────────────

    private fun loadItems(ctx: Context): MutableList<VaultMediaItem> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json  = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val arr   = JSONArray(json)
        val list  = mutableListOf<VaultMediaItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(VaultMediaItem(
                id           = o.getString("id"),
                originalName = o.getString("originalName"),
                mimeType     = o.getString("mimeType"),
                sizeBytes    = o.getLong("sizeBytes"),
                importedAt   = o.getLong("importedAt"),
                encFileName  = o.getString("encFileName"),
                thumbFileName= o.getString("thumbFileName")
            ))
        }
        return list
    }

    private fun saveItems(ctx: Context, items: List<VaultMediaItem>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(JSONObject().apply {
                put("id",           item.id)
                put("originalName", item.originalName)
                put("mimeType",     item.mimeType)
                put("sizeBytes",    item.sizeBytes)
                put("importedAt",   item.importedAt)
                put("encFileName",  item.encFileName)
                put("thumbFileName",item.thumbFileName)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    // ── Public API ────────────────────────────────────────────────────────

    fun getAll(ctx: Context): List<VaultMediaItem> =
        loadItems(ctx).sortedByDescending { it.importedAt }

    /**
     * Imports a media file from the given URI into the vault.
     * Encrypts both the full file and a thumbnail.
     * Runs on the calling thread — call from a background thread or coroutine.
     */
    fun importMedia(ctx: Context, uri: Uri, mimeType: String): VaultMediaItem {
        val id           = UUID.randomUUID().toString()
        val encFileName  = "$id.enc"
        val thumbFileName= "$id.thumb.enc"

        // 1 ─ Read source bytes
        val sourceBytes = ctx.contentResolver.openInputStream(uri)?.readBytes()
            ?: throw IllegalArgumentException("Cannot open input stream for URI")

        // 2 ─ Encrypt and write main file
        val encBytes = MediaVaultCipher.encryptBytes(sourceBytes)
        File(vaultDir(ctx), encFileName).writeBytes(encBytes)

        // 3 ─ Generate thumbnail
        val thumbBitmap: Bitmap? = when {
            mimeType.startsWith("image/") -> {
                val bmp = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
                scaleBitmap(bmp, 256)
            }
            mimeType.startsWith("video/") -> {
                // Write to temp file briefly to extract frame
                val tmpFile = File(tmpDir(ctx), "$id.tmp")
                tmpFile.writeBytes(sourceBytes)
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(tmpFile.absolutePath)
                    val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    frame?.let { scaleBitmap(it, 256) }
                } finally {
                    retriever.release()
                    tmpFile.delete()
                }
            }
            else -> null
        }

        // 4 ─ Encrypt and write thumbnail
        val thumbBytes = thumbBitmap?.let {
            val bos = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            MediaVaultCipher.encryptBytes(bos.toByteArray())
        } ?: MediaVaultCipher.encryptBytes(ByteArray(0))

        File(vaultDir(ctx), thumbFileName).writeBytes(thumbBytes)

        // 5 ─ Resolve original filename from URI
        val originalName = resolveFileName(ctx, uri, mimeType)

        // 6 ─ Save metadata
        val item = VaultMediaItem(
            id            = id,
            originalName  = originalName,
            mimeType      = mimeType,
            sizeBytes     = sourceBytes.size.toLong(),
            importedAt    = System.currentTimeMillis(),
            encFileName   = encFileName,
            thumbFileName = thumbFileName
        )
        val items = loadItems(ctx)
        items.add(item)
        saveItems(ctx, items)

        return item
    }

    /**
     * Decrypts item to a temp file in cacheDir/vault_tmp/.
     * Caller MUST delete the returned File when done viewing.
     */
    fun decryptToTemp(ctx: Context, item: VaultMediaItem): File {
        val encFile = File(vaultDir(ctx), item.encFileName)
        val encBytes = encFile.readBytes()
        val plainBytes = MediaVaultCipher.decryptBytes(encBytes)

        val ext = extensionForMime(item.mimeType)
        val tmpFile = File(tmpDir(ctx), "${item.id}_view.$ext")
        tmpFile.writeBytes(plainBytes)
        return tmpFile
    }

    /**
     * Decrypts the thumbnail to a temp file.
     * Returns null if the thumbnail is empty.
     */
    fun decryptThumbToTemp(ctx: Context, item: VaultMediaItem): File? {
        return try {
            val encFile = File(vaultDir(ctx), item.thumbFileName)
            if (!encFile.exists()) return null
            val encBytes = encFile.readBytes()
            val plainBytes = MediaVaultCipher.decryptBytes(encBytes)
            if (plainBytes.isEmpty()) return null
            val tmpFile = File(tmpDir(ctx), "${item.id}_thumb.jpg")
            tmpFile.writeBytes(plainBytes)
            tmpFile
        } catch (e: Exception) { null }
    }

    /** Deletes item from vault (encrypted files + metadata) */
    fun delete(ctx: Context, item: VaultMediaItem) {
        File(vaultDir(ctx), item.encFileName).delete()
        File(vaultDir(ctx), item.thumbFileName).delete()
        val items = loadItems(ctx)
        items.removeAll { it.id == item.id }
        saveItems(ctx, items)
    }

    /** Wipes the entire vault — called on panic/self-destruct */
    fun wipeAll(ctx: Context) {
        vaultDir(ctx).listFiles()?.forEach { it.delete() }
        tmpDir(ctx).listFiles()?.forEach { it.delete() }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Clears all temp view/thumb files from cacheDir/vault_tmp/ */
    fun clearTempFiles(ctx: Context) {
        tmpDir(ctx).listFiles()?.forEach { it.delete() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun scaleBitmap(src: Bitmap, maxSide: Int): Bitmap {
        val ratio = maxSide.toFloat() / maxOf(src.width, src.height)
        if (ratio >= 1f) return src
        val w = (src.width * ratio).toInt()
        val h = (src.height * ratio).toInt()
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun extensionForMime(mime: String): String = when {
        mime == "image/jpeg"  -> "jpg"
        mime == "image/png"   -> "png"
        mime == "image/gif"   -> "gif"
        mime == "image/webp"  -> "webp"
        mime.startsWith("video/") -> "mp4"
        else -> "bin"
    }

    private fun resolveFileName(ctx: Context, uri: Uri, mimeType: String): String {
        return try {
            val cursor = ctx.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (it.moveToFirst() && idx >= 0) it.getString(idx) else null
            } ?: "media_${System.currentTimeMillis()}.${extensionForMime(mimeType)}"
        } catch (e: Exception) {
            "media_${System.currentTimeMillis()}.${extensionForMime(mimeType)}"
        }
    }
}
