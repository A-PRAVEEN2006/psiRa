package com.project1.psira

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class VaultActivity : BaseActivity() {

    private lateinit var noteList: ArrayList<Note>
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var db: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_vault)

        val bottomNav: BottomNavigationView = findViewById(R.id.bottomNavigationView)
        bottomNav.selectedItemId = R.id.nav_death_note // Keep the existing ID for navigation mapping
        
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, NexusDashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_chats -> {
                    startActivity(Intent(this, ChatsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_groups -> {
                    startActivity(Intent(this, GroupsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_death_note -> true
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }

        val rvNotes: RecyclerView = findViewById(R.id.rvNotes)
        rvNotes.layoutManager = LinearLayoutManager(this)
        noteList = ArrayList()
        noteAdapter = NoteAdapter(noteList)
        rvNotes.adapter = noteAdapter

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            db = FirebaseDatabase.getInstance().getReference("user_vault").child(currentUser.uid).child("notes")
            
            db.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    noteList.clear()
                    for (child in snapshot.children) {
                        val note = child.getValue(Note::class.java)
                        if (note != null) {
                            note.id = child.key
                            noteList.add(note)
                        }
                    }
                    noteList.sortByDescending { it.timestamp }
                    noteAdapter.notifyDataSetChanged()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        val fabAddNote: FloatingActionButton = findViewById(R.id.fabAddNote)
        fabAddNote.setOnClickListener {
            startActivity(Intent(this, NoteEditorActivity::class.java))
        }
    }
}
