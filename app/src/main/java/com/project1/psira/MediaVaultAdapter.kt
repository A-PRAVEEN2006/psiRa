package com.project1.psira

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class MediaVaultAdapter(
    private val context: Context,
    private val items: List<VaultMediaItem>,
    private val onClick: (VaultMediaItem) -> Unit,
    private val onLongClick: (VaultMediaItem) -> Unit
) : RecyclerView.Adapter<MediaVaultAdapter.VaultVH>() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    inner class VaultVH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView   = view.findViewById(R.id.imgVaultThumb)
        val badge: TextView    = view.findViewById(R.id.tvVaultBadge)
        val name: TextView     = view.findViewById(R.id.tvVaultName)
        val overlay: View      = view.findViewById(R.id.viewVideoOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_vault_media, parent, false)
        return VaultVH(v)
    }

    override fun onBindViewHolder(holder: VaultVH, position: Int) {
        val item = items[position]
        holder.name.text = item.originalName
        holder.badge.text = if (item.isVideo()) "▶ VIDEO" else "🖼 PHOTO"
        holder.overlay.visibility = if (item.isVideo()) View.VISIBLE else View.GONE
        holder.thumb.setImageResource(R.drawable.ic_vault_placeholder)

        // Load thumbnail asynchronously
        scope.launch {
            val tmpFile = MediaVaultManager.decryptThumbToTemp(context, item)
            if (tmpFile != null) {
                val bmp = BitmapFactory.decodeFile(tmpFile.absolutePath)
                tmpFile.delete()
                withContext(Dispatchers.Main) {
                    if (holder.adapterPosition == position) {
                        holder.thumb.setImageBitmap(bmp)
                    }
                }
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    override fun getItemCount() = items.size

    fun destroy() { scope.cancel() }
}
