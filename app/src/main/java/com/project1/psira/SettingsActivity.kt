package com.project1.psira

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_settings)

        val btnChangePasscode = findViewById<Button>(R.id.btnChangePasscode)
        val btnSetChannel = findViewById<Button>(R.id.btnSetChannel) // NEW: The Channel Button
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

        // --- 2. SECURE CHANNEL PROTOCOL (NEW) ---
        btnSetChannel.setOnClickListener {
            val input = EditText(this)
            input.hint = "Enter Channel Name (e.g. Alpha)"
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT

            AlertDialog.Builder(this)
                .setTitle("Tune Secure Channel")
                .setMessage("You will only see messages from users in this exact channel.")
                .setView(input)
                .setPositiveButton("Connect") { _, _ ->
                    val newChannel = input.text.toString().trim()
                    if (newChannel.isNotEmpty()) {
                        sharedPref.edit().putString("SECURE_CHANNEL", newChannel).apply()
                        Toast.makeText(this, "Tuned to Channel: $newChannel", Toast.LENGTH_SHORT).show()
                    }
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

        // --- 4. ABOUT PSIRA ---
        btnAbout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("About PsiRa")
                .setMessage("Version 1.0\nSecure AES Encrypted Vault.\nBuilt for maximum stealth.")
                .setPositiveButton("Close", null)
                .show()
        }
    }
}