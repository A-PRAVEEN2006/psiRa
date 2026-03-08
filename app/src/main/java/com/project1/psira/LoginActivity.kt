package com.project1.psira

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val editEmail = findViewById<EditText>(R.id.editEmail)
        val editPassword = findViewById<EditText>(R.id.editPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

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
                            Toast.makeText(this, "Welcome back, ${user?.displayName}!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, NexusDashboardActivity::class.java))
                            finish()
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
                nameInput.hint = "Agent Name (e.g., Praveen)"

                AlertDialog.Builder(this)
                    .setTitle("New Agent Profile")
                    .setMessage("Enter your display name for the vault.")
                    .setView(nameInput)
                    .setPositiveButton("Register") { _, _ ->
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
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
    private fun showNamePopup() {
        val nameInput = EditText(this)
        nameInput.hint = "Agent Name (e.g., Praveen)"

        AlertDialog.Builder(this)
            .setTitle("Identity Required")
            .setMessage("Your Agent Profile is incomplete. Please enter your name.")
            .setView(nameInput)
            .setCancelable(false) // User CANNOT skip this
            .setPositiveButton("Save Profile") { _, _ ->
                val displayName = nameInput.text.toString().trim()
                if (displayName.isNotEmpty()) {
                    val user = auth.currentUser
                    val profileUpdates = userProfileChangeRequest {
                        setDisplayName(displayName)
                    }
                    user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                        Toast.makeText(this, "Profile Updated: $displayName", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, NexusDashboardActivity::class.java))
                        finish()
                    }
                } else {
                    Toast.makeText(this, "Name cannot be empty!", Toast.LENGTH_SHORT).show()
                    showNamePopup() // Ask again if they left it blank
                }
            }
            .show()
    }
}