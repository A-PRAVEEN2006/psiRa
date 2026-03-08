package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class NexusDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nexus_dashboard)

        val btnGlobal = findViewById<Button>(R.id.btnGlobal)
        val btnWalkie = findViewById<Button>(R.id.btnWalkie)
        val btnPrivate = findViewById<Button>(R.id.btnPrivate)
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        // 🌍 1. GLOBAL MODE: Sets channel to "global" for everyone
        btnGlobal.setOnClickListener {
            sharedPref.edit().putString("SECURE_CHANNEL", "global_protocol").apply()
            startActivity(Intent(this, ChatActivity::class.java))
        }

        // 📻 2. WALKIE-TALKIE MODE: Asks for 7-digit code
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
                        android.widget.Toast.makeText(this, "Must be 7 digits!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 🔒 3. PRIVATE MODE: Personal vault using your unique User ID
        btnPrivate.setOnClickListener {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
            sharedPref.edit().putString("SECURE_CHANNEL", "private_$userId").apply()
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }
}