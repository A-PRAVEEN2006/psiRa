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
        setContentView(R.layout.activity_settings)

        val btnChangePasscode = findViewById<Button>(R.id.btnChangePasscode)
        val switchTheme = findViewById<Switch>(R.id.switchTheme)
        val btnAbout = findViewById<Button>(R.id.btnAbout)

        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        // --- 1. CHANGE PASSCODE ---
        btnChangePasscode.setOnClickListener {
            val input = EditText(this)
            input.hint = "Enter New Passcode"
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

            AlertDialog.Builder(this)
                .setTitle("Update Security Key")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newCode = input.text.toString()
                    if (newCode.isNotEmpty()) {
                        sharedPref.edit().putString("SECRET_PASSCODE", newCode).apply()
                        Toast.makeText(this, "Passcode Updated!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // --- 2. THEME TOGGLE ---
        // Check current theme state
        switchTheme.isChecked = AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO

        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        // --- 3. ABOUT PSIRA ---
        btnAbout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("About PsiRa")
                .setMessage("Version 1.0\nSecure AES Encrypted Vault.\nBuilt for maximum stealth.")
                .setPositiveButton("Close", null)
                .show()
        }
    }
}