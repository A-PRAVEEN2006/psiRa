package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val editEmail = findViewById<EditText>(R.id.editEmail)
        val editPassword = findViewById<EditText>(R.id.editPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvLoginTitle = findViewById<android.widget.TextView>(R.id.tvLoginTitle)

        tvLoginTitle.setOnLongClickListener {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            Toast.makeText(this, "INITIALIZING SPECTRE NODE...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, NexusLinkActivity::class.java))
            true
        }

        // --- 1. INITIALIZE CONNECTION (LOGIN) ---
        // --- 1. INITIALIZE CONNECTION (LOGIN) ---
        btnLogin.setOnClickListener {
            val email = editEmail.text.toString().trim()
            val pass = editPassword.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser

                        // Check if the user ALREADY has a name
                        if (user?.displayName.isNullOrEmpty()) {
                            // If NO NAME exists, show the popup now!
                            showNamePopup()
                        } else {
                            // If name exists, proceed to Dashboard
                            ensureAgentId(user!!.uid) {
                                Toast.makeText(this, "Welcome back, ${user.displayName}!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, NexusDashboardActivity::class.java))
                                finish()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // --- 2. REGISTER NEW AGENT (WITH NAME POPUP) ---
        btnRegister.setOnClickListener {
            val email = editEmail.text.toString().trim()
            val pass = editPassword.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                val nameInput = EditText(this)
                nameInput.hint = "Agent Alias"
                nameInput.setTextColor(android.graphics.Color.WHITE)
                nameInput.setHintTextColor(android.graphics.Color.GRAY)

                PsiRaDialogs.showDeleteSheet(
                    this,
                    "NEW AGENT PROFILE",
                    "Enter your public identifier for the encrypted network.",
                    "INITIALIZE",
                    nameInput
                ) {
                    val displayName = nameInput.text.toString().trim()
                    if (displayName.isNotEmpty()) {
                        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                val profileUpdates = userProfileChangeRequest {
                                    setDisplayName(displayName)
                                }
                                user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                    Toast.makeText(this, "Agent $displayName Registered!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this, "Reg Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }
    private fun showNamePopup() {
        val nameInput = EditText(this)
        nameInput.hint = "Agent Alias"
        nameInput.setTextColor(android.graphics.Color.WHITE)
        nameInput.setHintTextColor(android.graphics.Color.GRAY)

        PsiRaDialogs.showDeleteSheet(
            this,
            "IDENTITY REQUIRED",
            "Your Agent Profile is incomplete. Enter your Alias to proceed.",
            "SYNCHRONIZE",
            nameInput,
            false // Not cancelable
        ) {
            val displayName = nameInput.text.toString().trim()
            if (displayName.isNotEmpty()) {
                val user = auth.currentUser
                val profileUpdates = userProfileChangeRequest {
                    setDisplayName(displayName)
                }
                user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                    ensureAgentId(user!!.uid) {
                        FirebaseDatabase.getInstance().getReference("users").child(user.uid).child("name").setValue(displayName)
                        Toast.makeText(this, "Profile Updated: $displayName", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, NexusDashboardActivity::class.java))
                        finish()
                    }
                }
            } else {
                Toast.makeText(this, "Name cannot be empty!", Toast.LENGTH_SHORT).show()
                showNamePopup()
            }
        }
    }

    private fun ensureAgentId(uid: String, onComplete: () -> Unit) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        usersRef.child("agentId").get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists() || snapshot.value == null) {
                // Generate 5 digit ID
                val newId = (10000..99999).random().toString()
                usersRef.child("agentId").setValue(newId).addOnCompleteListener {
                    onComplete()
                }
            } else {
                onComplete()
            }
        }.addOnFailureListener {
            onComplete()
        }
    }
}