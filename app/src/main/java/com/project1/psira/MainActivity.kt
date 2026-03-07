package com.project1.psira // Important: Keep your actual package name here!

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots and screen recording!
        // Blocks screenshots and screen recording!
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)
        val display = findViewById<EditText>(R.id.calcDisplay)
        val btnEqual = findViewById<Button>(R.id.btnEqual)

        // This is the hidden vault that remembers your passcode
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        btnEqual.setOnClickListener {
            val inputText = display.text.toString()

            // Check if a passcode is already saved
            val savedPasscode = sharedPref.getString("SECRET_PASSCODE", null)

            if (savedPasscode == null) {
                // FIRST TIME SETUP: No passcode exists yet. Save what they typed!
                if (inputText.isNotEmpty()) {
                    sharedPref.edit().putString("SECRET_PASSCODE", inputText).apply()

                    // Show a tiny pop-up message saying it was saved
                    Toast.makeText(this, "Passcode Set! Entering vault...", Toast.LENGTH_SHORT).show()

                    display.setText("") // Clear the screen
                    startActivity(Intent(this, ChatActivity::class.java))
                }
            } else {
                // NORMAL LOGIN: Compare what they typed to the saved passcode
                if (inputText == savedPasscode) {
                    display.setText("") // Clear the screen
                    startActivity(Intent(this, ChatActivity::class.java))
                } else {
                    // DECOY: If they type the wrong code, just clear the screen
                    // (Later, you could actually make it calculate the math here to be extra sneaky!)
                    display.setText("")
                }
            }
        }
    }
}