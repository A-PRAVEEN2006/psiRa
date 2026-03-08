package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView // Import for the Name Label
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class NexusDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nexus_dashboard)

        // 1. IDENTITY DISPLAY: Shows "AGENT: Praveen" at the top
        val tvAgentName = findViewById<TextView>(R.id.tvAgentName)
        val user = FirebaseAuth.getInstance().currentUser
        tvAgentName.text = "AGENT: ${user?.displayName ?: "Unknown"}"

        // 2. BUTTON REFERENCES
        val btnGlobal = findViewById<Button>(R.id.btnGlobal)
        val btnWalkie = findViewById<Button>(R.id.btnWalkie)
        val btnPrivate = findViewById<Button>(R.id.btnPrivate)
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        // --- ADD THE LOGOUT LOGIC HERE ---
        val tvLogout = findViewById<TextView>(R.id.tvLogout)
        tvLogout.setOnClickListener {
            // 1. Sign out from Firebase
            FirebaseAuth.getInstance().signOut()

            // 2. Head back to the Login screen
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)

            // 3. Destroy this dashboard so they can't click 'back' to get in
            finish()

            Toast.makeText(this, "Agent Session Terminated", Toast.LENGTH_SHORT).show()
        }
        // ---------------------------------

        // ... (Rest of your 🌍 GLOBAL, 📻 WALKIE-TALKIE, and 🔒 PRIVATE listeners)

        // 🌍 GLOBAL MODE: Public channel for everyone
        btnGlobal.setOnClickListener {
            sharedPref.edit().putString("SECURE_CHANNEL", "global_protocol").apply()
            startActivity(Intent(this, ChatActivity::class.java))
        }

        // 📻 WALKIE-TALKIE MODE: 7-digit frequency match
        btnWalkie.setOnClickListener {
            val input = EditText(this)
            input.hint = "7-Digit Frequency (e.g. 1234567)"
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

            AlertDialog.Builder(this)
                .setTitle("📻 Tune Frequency")
                .setMessage("Enter the 7-digit code to connect with your partner.")
                .setView(input)
                .setPositiveButton("Connect") { _, _ ->
                    val freq = input.text.toString()
                    if (freq.length == 7) {
                        sharedPref.edit().putString("SECURE_CHANNEL", "freq_$freq").apply()
                        startActivity(Intent(this, ChatActivity::class.java))
                    } else {
                        Toast.makeText(this, "Must be 7 digits!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 🔒 PRIVATE MODE: Your own UID-locked vault
        btnPrivate.setOnClickListener {
            val userId = user?.uid ?: "anonymous"
            sharedPref.edit().putString("SECURE_CHANNEL", "private_$userId").apply()
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }
}