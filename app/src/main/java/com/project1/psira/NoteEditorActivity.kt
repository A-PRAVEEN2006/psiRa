package com.project1.psira

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var editTitle: EditText
    private lateinit var editContent: EditText
    private lateinit var btnDelete: ImageButton
    private lateinit var db: DatabaseReference
    private var noteId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_note_editor)

        editTitle = findViewById(R.id.editNoteTitle)
        editContent = findViewById(R.id.editNoteContent)
        btnDelete = findViewById(R.id.btnDeleteNote)
        val btnSave = findViewById<Button>(R.id.btnSaveNote)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        noteId = intent.getStringExtra("NOTE_ID")
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            finish()
            return
        }

        db = FirebaseDatabase.getInstance().getReference("user_vault").child(currentUser.uid).child("notes")

        if (noteId != null) {
            btnDelete.visibility = View.VISIBLE
            loadNote(noteId!!)
        }

        btnBack.setOnClickListener { finish() }

        btnDelete.setOnClickListener {
            confirmDelete()
        }

        btnSave.setOnClickListener {
            saveNote()
        }
    }

    private fun loadNote(id: String) {
        db.child(id).get().addOnSuccessListener { snapshot ->
            val note = snapshot.getValue(Note::class.java)
            if (note != null) {
                try {
                    val decryptedTitle = PsiRaConverter.decode(AESEncryption.decrypt(note.title ?: ""))
                    val decryptedContent = PsiRaConverter.decode(AESEncryption.decrypt(note.content ?: ""))
                    editTitle.setText(decryptedTitle)
                    editContent.setText(decryptedContent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Decryption Error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveNote() {
        val title = editTitle.text.toString()
        val content = editContent.text.toString()

        if (title.isEmpty() && content.isEmpty()) {
            finish()
            return
        }

        try {
            // HIGH SECURITY: Double layer (PsiRa Encode + AES Encrypt)
            val encryptedTitle = AESEncryption.encrypt(PsiRaConverter.encode(title))
            val encryptedContent = AESEncryption.encrypt(PsiRaConverter.encode(content))

            val note = Note(noteId, encryptedTitle, encryptedContent, System.currentTimeMillis())
            
            if (noteId == null) {
                db.push().setValue(note).addOnSuccessListener {
                    Toast.makeText(this, "Note vaulted", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                db.child(noteId!!).setValue(note).addOnSuccessListener {
                    Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Encryption failed!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Permanently Delete?")
            .setMessage("This note will be erased from the encrypted matrix forever.")
            .setPositiveButton("DELETE") { _, _ ->
                if (noteId != null) {
                    db.child(noteId!!).removeValue().addOnSuccessListener {
                        Toast.makeText(this, "Note purged", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}
