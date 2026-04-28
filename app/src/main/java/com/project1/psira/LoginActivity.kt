package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : BaseActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // super.onCreate handles FLAG_SECURE and Theme
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        val editEmail = findViewById<EditText>(R.id.editEmail)
        val editPassword = findViewById<EditText>(R.id.editPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // 1. Persistent Login Check (Auto-Bypass with 10-Session Security Guard)
        if (auth.currentUser != null) {
            val autoCount = sharedPref.getInt("AUTO_LOGIN_COUNT", 0)
            if (autoCount < 10) {
                val user = auth.currentUser
                ensureAgentId(user!!.uid) {
                    sharedPref.edit().putInt("AUTO_LOGIN_COUNT", autoCount + 1).apply()
                    startActivity(Intent(this, NexusDashboardActivity::class.java))
                    finish()
                }
                return
            }
        }

        // 2. LOGIN LOGIC
        btnLogin.setOnClickListener {
            val email = editEmail.text.toString().trim()
            val pass = editPassword.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        sharedPref.edit().putInt("AUTO_LOGIN_COUNT", 0).apply() // Reset on successful login
                        
                        if (user?.displayName.isNullOrEmpty()) {
                            showNamePopup()
                        } else {
                            ensureAgentId(user!!.uid) {
                                Toast.makeText(this, "Agent Verified: ${user.displayName}", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, NexusDashboardActivity::class.java))
                                finish()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Access Denied: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // 3. REGISTER LOGIC
        btnRegister.setOnClickListener {
            val email = editEmail.text.toString().trim()
            val pass = editPassword.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                val nameInput = EditText(this)
                nameInput.hint = "Agent Alias"
                nameInput.setTextColor(android.graphics.Color.WHITE)
                
                PsiRaDialogs.showDeleteSheet(this, "NEW PROFILE", "Set your identifier.", "INITIALIZE", nameInput) {
                    val displayName = nameInput.text.toString().trim()
                    if (displayName.isNotEmpty()) {
                        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                sharedPref.edit().putInt("AUTO_LOGIN_COUNT", 0).apply()
                                val profileUpdates = userProfileChangeRequest { setDisplayName(displayName) }
                                user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                    Toast.makeText(this, "Agent $displayName Registered!", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, NexusDashboardActivity::class.java))
                                    finish()
                                }
                            } else {
                                Toast.makeText(this, "Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
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

        PsiRaDialogs.showDeleteSheet(this, "IDENTITY RECOVERY", "Enter Alias to synchronize profile.", "SAVE", nameInput, false) {
            val displayName = nameInput.text.toString().trim()
            if (displayName.isNotEmpty()) {
                val user = auth.currentUser
                val profileUpdates = userProfileChangeRequest { setDisplayName(displayName) }
                user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                    ensureAgentId(user!!.uid) {
                        FirebaseDatabase.getInstance().getReference("users").child(user.uid).child("name").setValue(displayName)
                        startActivity(Intent(this, NexusDashboardActivity::class.java))
                        finish()
                    }
                }
            }
        }
    }

    private fun ensureAgentId(uid: String, onComplete: () -> Unit) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        usersRef.child("agentId").get().addOnSuccessListener { snapshot ->
            val existing = snapshot.value?.toString()
            if (existing == null || existing.isEmpty()) {
                val newId = (10000..99999).random().toString()
                usersRef.child("agentId").setValue(newId).addOnCompleteListener {
                    getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE).edit().putString("AGENT_ID_LOCAL", newId).apply()
                    onComplete()
                }
            } else {
                getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE).edit().putString("AGENT_ID_LOCAL", existing).apply()
                onComplete()
            }
        }.addOnFailureListener {
            onComplete() // Allow entry even if DB check fails (e.g., offline)
        }
    }
}