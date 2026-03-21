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

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_settings)

        val tvSettingsTitle = findViewById<TextView>(R.id.tvSettingsTitle)
        val btnChangePasscode = findViewById<Button>(R.id.btnChangePasscode)
        val btnWipeLocal = findViewById<Button>(R.id.btnWipeLocal)
        val switchTheme = findViewById<Switch>(R.id.switchTheme)
        val btnAbout = findViewById<Button>(R.id.btnAbout)

        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        // --- 1. CHANGE PASSCODE ---
        btnChangePasscode.setOnClickListener {
            val input = EditText(this)
            input.hint = "Enter New Passcode"
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

            AlertDialog.Builder(this)
                .setTitle("Update Security Key")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newCode = input.text.toString()
                    if (newCode.isNotEmpty()) {
                        sharedPref.edit().putString("VAULT_PASS", newCode).apply()
                        Toast.makeText(this, "Passcode Updated!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // --- 1.5. SET PANIC PIN ---
        val btnSetPanicCode = findViewById<Button>(R.id.btnSetPanicCode)
        btnSetPanicCode.setOnClickListener {
            val input = EditText(this)
            input.hint = "Enter Emergency Panic PIN"
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

            AlertDialog.Builder(this)
                .setTitle("🚨 Set EMP Panic PIN")
                .setMessage("Entering this on the calculator will wipe all local data and log out your account immediately.")
                .setView(input)
                .setPositiveButton("Set Trigger") { _, _ ->
                    val newCode = input.text.toString()
                    if (newCode.isNotEmpty()) {
                        sharedPref.edit().putString("PANIC_PASSCODE", newCode).apply()
                        Toast.makeText(this, "Panic PIN Activated!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // --- 2. WIPE LOCAL CACHE ---
        btnWipeLocal.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Wipe Local Cache")
                .setMessage("This will clear your saved secure preferences on this device.")
                .setPositiveButton("Clear") { _, _ ->
                    sharedPref.edit().clear().apply()
                    Toast.makeText(this, "Local cache erased.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
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
                input.hint = "System Override Code"
                input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                
                AlertDialog.Builder(this)
                    .setTitle("TERMINAL ACCESS")
                    .setView(input)
                    .setPositiveButton("EXECUTE") { _, _ ->
                        if (input.text.toString() == "Anu@Praveen01") {
                            Toast.makeText(this, "GOD MODE ACTIVATED.", Toast.LENGTH_LONG).show()
                            startActivity(Intent(this, GodDashboardActivity::class.java))
                        } else {
                            Toast.makeText(this, "Access Denied.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("ABORT", null)
                    .show()
                titleClickCount = 0
            }
        }

        // --- 5. ABOUT PSIRA ---
        btnAbout.setOnClickListener {
            val aboutMessage = "PsiRa \nVersion: 1.0 (Stealth Build)\n\n" +
                "DECLASSFIED FEATURES:\n" +
                "• True Stealth Calculator Cloak\n" +
                "• EMP Panic Protocol Reset\n" +
                "• Biometric Vault Locking\n" +
                "• 5-Digit Agent Identity Nodes\n" +
                "• Multi-Agent Group Enclaves\n" +
                "• Self-Destructing Volatile Chat"

            AlertDialog.Builder(this)
                .setTitle("SYSTEM DOSSIER")
                .setMessage(aboutMessage)
                .setPositiveButton("ACKNOWLEDGE", null)
                .show()
        }
    }
}