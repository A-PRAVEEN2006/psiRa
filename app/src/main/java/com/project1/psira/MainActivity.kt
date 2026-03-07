package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Stealth Mode: Block screenshots
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        val display = findViewById<EditText>(R.id.calcDisplay)
        val btnEqual = findViewById<Button>(R.id.btnEqual)

        // This finds the "grid" that holds all your buttons
        val gridLayout = findViewById<GridLayout>(R.id.mainGrid)
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        // 1. This is the logic for ALL number and operator buttons
        val listener = View.OnClickListener { v ->
            val b = v as Button
            val text = b.text.toString()

            when (text) {
                "C" -> display.setText("")
                "Del" -> {
                    val currentText = display.text.toString()
                    if (currentText.isNotEmpty()) {
                        display.setText(currentText.substring(0, currentText.length - 1))
                    }
                }
                "=" -> {
                    // We handle the Equals button separately below
                }
                else -> {
                    // For numbers 0-9 and +, -, *, /
                    display.append(text)
                }
            }
        }

        // This loop automatically attaches that logic to every button in the grid
        for (i in 0 until gridLayout.childCount) {
            val child = gridLayout.getChildAt(i)
            if (child is Button) {
                child.setOnClickListener(listener)
            }
        }

        // 2. The Secret Vault Logic (The Equal Button)
        btnEqual.setOnClickListener {
            val inputText = display.text.toString()
            val savedPasscode = sharedPref.getString("SECRET_PASSCODE", null)

            if (savedPasscode == null) {
                // First Time Setup: Save the code they just typed
                if (inputText.isNotEmpty()) {
                    sharedPref.edit().putString("SECRET_PASSCODE", inputText).apply()
                    Toast.makeText(this, "Passcode Set!", Toast.LENGTH_SHORT).show()
                    enterVault()
                }
            } else {
                // Unlock Check: Does it match the secret code?
                if (inputText == savedPasscode) {
                    enterVault()
                } else {
                    display.setText("") // Clear the screen if wrong (Decoy mode)
                }
            }
        }
    }

    private fun enterVault() {
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
        // We don't use finish() so the user can go back to the calculator later
    }
}