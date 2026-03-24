package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.database.FirebaseDatabase

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_settings)

        val tvSettingsTitle = findViewById<TextView>(R.id.tvSettingsTitle)
        val btnChangeName = findViewById<Button>(R.id.btnChangeName)
        val btnChangePasscode = findViewById<Button>(R.id.btnChangePasscode)
        val btnWipeLocal = findViewById<Button>(R.id.btnWipeLocal)
        val switchTheme = findViewById<Switch>(R.id.switchTheme)
        val btnAbout = findViewById<Button>(R.id.btnAbout)

        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        // --- 0. CHANGE NAME ---
        btnChangeName.setOnClickListener {
            val input = EditText(this)
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            input.setText(user?.displayName ?: "")
            input.hint = "Agent Alias"
            input.setTextColor(android.graphics.Color.WHITE)
            input.setHintTextColor(android.graphics.Color.GRAY)

            PsiRaDialogs.showDeleteSheet(
                this,
                "UPDATE ALIAS",
                "This name will be broadcast to all contacts in the encrypted network.",
                "SYNCHRONIZE",
                input
            ) {
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                        displayName = newName
                    }
                    user?.updateProfile(profileUpdates)?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            FirebaseDatabase.getInstance().getReference("users")
                                .child(user.uid).child("name").setValue(newName)
                            Toast.makeText(this, "Signal Synchronized", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        val btnChangeClockPasscode = findViewById<Button>(R.id.btnChangeClockPasscode)
        btnChangeClockPasscode.setOnClickListener {
            val input = EditText(this)
            input.hint = "HH:MM (e.g. 02:35)"
            input.setTextColor(android.graphics.Color.WHITE)
            input.setHintTextColor(android.graphics.Color.GRAY)
            input.inputType = android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME

            PsiRaDialogs.showDeleteSheet(
                this,
                "CALIBRATE CLOCK BYPASS",
                "Set the exact time trigger for the stealth entry point.",
                "CALIBRATE",
                input
            ) {
                val newTime = input.text.toString().trim()
                if (newTime.matches(Regex("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]\$"))) {
                    sharedPref.edit().putString("CLOCK_SECRET", newTime).apply()
                    Toast.makeText(this, "Clock Bypass Set to $newTime", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid Format! Must be HH:MM.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- 1. CHANGE PASSCODE ---
        btnChangePasscode.setOnClickListener {
            val input = EditText(this)
            input.hint = "New PIN"
            input.setTextColor(android.graphics.Color.WHITE)
            input.setHintTextColor(android.graphics.Color.GRAY)
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

            PsiRaDialogs.showDeleteSheet(
                this,
                "UPDATE VAULT KEY",
                "Modify the primary access code for your encrypted archives.",
                "ROTATE KEY",
                input
            ) {
                val newCode = input.text.toString()
                if (newCode.isNotEmpty()) {
                    sharedPref.edit().putString("VAULT_PASS", newCode).apply()
                    Toast.makeText(this, "Key Rotated", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnSetPanicCode = findViewById<Button>(R.id.btnSetPanicCode)
        btnSetPanicCode.setOnClickListener {
            val input = EditText(this)
            input.hint = "Enter 4-Digit PIN"
            input.setTextColor(android.graphics.Color.WHITE)
            input.setHintTextColor(android.graphics.Color.GRAY)
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

            PsiRaDialogs.showDeleteSheet(
                this,
                "SET PANIC TRIGGER?",
                "Entering this PIN on the calculator will immediately shred all local vault data.",
                "ARM TRIGGER",
                input
            ) {
                val newCode = input.text.toString()
                if (newCode.isNotEmpty()) {
                    sharedPref.edit().putString("PANIC_PASSCODE", newCode).apply()
                    Toast.makeText(this, "Panic PIN Armed!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- 2. WIPE LOCAL CACHE ---
        btnWipeLocal.setOnClickListener {
            PsiRaDialogs.showDeleteSheet(
                this,
                "WIPE LOCAL CACHE?",
                "This will clear your saved secure preferences on this device. You will NOT be logged out of the matrix.",
                "ERASE CACHE"
            ) {
                sharedPref.edit().clear().apply()
                Toast.makeText(this, "Local cache erased.", Toast.LENGTH_SHORT).show()
            }
        }

        // --- 3. THEME TOGGLE --
        // Check current theme state
        val isNight = sharedPref.getBoolean("IS_DARK_MODE", true)
        switchTheme.isChecked = isNight

        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("IS_DARK_MODE", isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        // --- 4. THE GOD MODE TERMINAL (HIDDEN TITLE TRIGGER) ---
        var titleClickCount = 0
        var lastTitleClickTime = 0L

        tvSettingsTitle.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTitleClickTime > 800) {
                titleClickCount = 0 // Reset if paused
            }
            lastTitleClickTime = currentTime
            titleClickCount++

            if (titleClickCount == 7) {
                val input = EditText(this)
                input.hint = "Access Token"
                input.setTextColor(android.graphics.Color.WHITE)
                input.setHintTextColor(android.graphics.Color.GRAY)
                input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                
                PsiRaDialogs.showDeleteSheet(
                    this,
                    "TERMINAL ACCESS",
                    "Unauthorized access will trigger a counter-strike. Enter override code.",
                    "EXECUTE",
                    input
                ) {
                    if (input.text.toString() == "Anu@Praveen01") {
                        Toast.makeText(this, "GOD MODE ACTIVATED.", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this, GodDashboardActivity::class.java))
                    } else {
                        Toast.makeText(this, "Access Denied.", Toast.LENGTH_SHORT).show()
                    }
                }
                titleClickCount = 0
            }
        }

        btnAbout.setOnClickListener {
            val aboutMessage = "PsiRa \nVersion: 1.0 (Stealth Build)\n\n" +
                "DECLASSFIED FEATURES:\n" +
                "• True Stealth Calculator Cloak\n" +
                "• EMP Panic Protocol Reset\n" +
                "• Biometric Vault Locking\n" +
                "• 5-Digit Agent Identity Nodes\n" +
                "• Multi-Agent Group Enclaves\n" +
                "• Self-Destructing Volatile Chat"

            PsiRaDialogs.showDeleteSheet(
                this,
                "SYSTEM DOSSIER",
                aboutMessage,
                "ACKNOWLEDGE"
            ) {
                // Done
            }
        }
    }
}