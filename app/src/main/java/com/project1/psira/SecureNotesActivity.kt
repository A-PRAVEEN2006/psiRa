package com.project1.psira

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SecureNotesActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secure_notes)

        val rvNotes = findViewById<RecyclerView>(R.id.rvNotes)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAddNote)

        rvNotes.layoutManager = LinearLayoutManager(this)
        
        // Load notes from local vault
        loadNotes()

        fabAdd.setOnClickListener {
            createNewNote()
        }
    }

    private fun loadNotes() {
        // Placeholder for loading encrypted notes
    }

    private fun createNewNote() {
        // Placeholder for creating a new encrypted note
    }
}