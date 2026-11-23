package com.project1.psira

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DecoyNoteItem(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var body: String,
    var timestamp: Long = System.currentTimeMillis()
)

class DecoyActivity : BaseActivity() {

    private val notesList = mutableListOf<DecoyNoteItem>()
    private lateinit var adapter: DecoyNotesAdapter
    private val PREFS_DECOY = "DecoyNotesStore"
    private val KEY_NOTES_JSON = "DECOY_NOTES_JSON"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_decoy)

        val rv = findViewById<RecyclerView>(R.id.rvDecoyNotes)
        val fab = findViewById<FloatingActionButton>(R.id.fabAddDecoyNote)

        adapter = DecoyNotesAdapter(notesList, { note ->
            showNoteDialog(note)
        }, { note ->
            showDeleteConfirm(note)
        })

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        loadNotes()

        fab.setOnClickListener {
            showNoteDialog(null)
        }
    }

    private fun loadNotes() {
        val prefs = getSharedPreferences(PREFS_DECOY, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_NOTES_JSON, null)
        notesList.clear()

        if (jsonStr.isNullOrEmpty()) {
            // Seed initial realistic mundane notes
            notesList.add(
                DecoyNoteItem(
                    title = "Grocery List",
                    body = "- Almond milk & Greek yogurt\n- Whole grain sourdough bread\n- Eggs (1 dozen)\n- Fresh roast coffee beans\n- Extra virgin olive oil",
                    timestamp = System.currentTimeMillis() - 86400000L
                )
            )
            notesList.add(
                DecoyNoteItem(
                    title = "Work Standup Tasks",
                    body = "- Review sprint tickets & PR comments\n- Sync with design team on color accessibility\n- Send monthly expenditure report to accounting",
                    timestamp = System.currentTimeMillis() - 172800000L
                )
            )
            notesList.add(
                DecoyNoteItem(
                    title = "Weekly Workout Routine",
                    body = "Mon: Chest & Triceps\nWed: Back & Pullups\nFri: Squats & Mobility\nSun: 5km light recovery jog",
                    timestamp = System.currentTimeMillis() - 345600000L
                )
            )
            notesList.add(
                DecoyNoteItem(
                    title = "Home Maintenance",
                    body = "- Clean AC dust filters\n- Check balcony drain before monsoon\n- Water indoor monstera plants",
                    timestamp = System.currentTimeMillis() - 604800000L
                )
            )
            saveNotes()
        } else {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    notesList.add(
                        DecoyNoteItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.optString("title", ""),
                            body = obj.optString("body", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun saveNotes() {
        val prefs = getSharedPreferences(PREFS_DECOY, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (item in notesList) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("body", item.body)
            obj.put("timestamp", item.timestamp)
            array.put(obj)
        }
        prefs.edit().putString(KEY_NOTES_JSON, array.toString()).apply()
    }

    private fun showNoteDialog(existingNote: DecoyNoteItem?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_decoy_note, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etDecoyTitle)
        val etBody = dialogView.findViewById<EditText>(R.id.etDecoyBody)

        if (existingNote != null) {
            etTitle.setText(existingNote.title)
            etBody.setText(existingNote.body)
        }

        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val title = etTitle.text.toString().trim()
                val body = etBody.text.toString().trim()
                if (title.isNotEmpty() || body.isNotEmpty()) {
                    if (existingNote != null) {
                        existingNote.title = if (title.isEmpty()) "Untitled Note" else title
                        existingNote.body = body
                        existingNote.timestamp = System.currentTimeMillis()
                    } else {
                        notesList.add(
                            0,
                            DecoyNoteItem(
                                title = if (title.isEmpty()) "Untitled Note" else title,
                                body = body,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                    saveNotes()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirm(note: DecoyNoteItem) {
        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Delete note?")
            .setMessage("Are you sure you want to delete '${note.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                notesList.remove(note)
                saveNotes()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class DecoyNotesAdapter(
    private val items: List<DecoyNoteItem>,
    private val onClick: (DecoyNoteItem) -> Unit,
    private val onLongClick: (DecoyNoteItem) -> Unit
) : RecyclerView.Adapter<DecoyNotesAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvDecoyTitle)
        val body: TextView = view.findViewById(R.id.tvDecoyBody)
        val date: TextView = view.findViewById(R.id.tvDecoyDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_decoy_note, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.body.text = item.body
        holder.date.text = dateFormat.format(Date(item.timestamp))

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}
