package com.project1.psira

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class NoteAdapter(private val noteList: List<Note>) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvNoteTitle)
        val tvPreview: TextView = itemView.findViewById(R.id.tvNotePreview)
        val tvDate: TextView = itemView.findViewById(R.id.tvNoteDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = noteList[position]

        // Decrypt and auto-detect old or new PsiRa symbols
        try {
            val rawTitle = AESEncryption.decrypt(note.title ?: "")
            val rawContent = AESEncryption.decrypt(note.content ?: "")
            val title = PsiRaConverter.decodeAny(rawTitle)
            val content = PsiRaConverter.decodeAny(rawContent)
            holder.tvTitle.text = if (title.isEmpty()) "Untitled" else title
            holder.tvPreview.text = content
        } catch (e: Exception) {
            holder.tvTitle.text = "Locked Note"
            holder.tvPreview.text = "Error decoding vault data."
        }

        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(note.timestamp))

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, NoteEditorActivity::class.java)
            intent.putExtra("NOTE_ID", note.id)
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount() = noteList.size
}
