package com.project1.psira

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit

class StealthDetailActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stealth_detail)

        val id = intent.getStringExtra("LOGIC_ID") ?: "CLOCK"
        val logic = StealthManager.getLogic(id) ?: return

        findViewById<TextView>(R.id.tvMaskTitle).text = "DISGUISE: ${logic.name.uppercase()}"
        findViewById<TextView>(R.id.tvDescription).text = logic.description
        findViewById<TextView>(R.id.tvGuide).text = logic.guide

        val btnSetPass = findViewById<Button>(R.id.btnSetPasscode)
        val btnSetGate = findViewById<Button>(R.id.btnSetGateway)
        val btnBack = findViewById<Button>(R.id.btnBack)

        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        btnSetPass.setOnClickListener {
            val input = EditText(this)
            input.hint = "Example: ${logic.hint}"
            input.setText(StealthManager.getPass(this, id))
            input.setTextColor(android.graphics.Color.WHITE)

            PsiRaDialogs.showDeleteSheet(this, "SET APP CODE", "Enter the secret code to open the app using the ${logic.name}.", "SAVE", input) {
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    sharedPref.edit { putString("PASS_$id", value) }
                    Toast.makeText(this, "Unlock code saved.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnSetGate.setOnClickListener {
            val input = EditText(this)
            input.hint = "Example: ${logic.hint}"
            input.setText(StealthManager.getGateway(this, id))
            input.setTextColor(android.graphics.Color.WHITE)

            PsiRaDialogs.showDeleteSheet(this, "SET SETTINGS CODE", "Enter the secret code to open settings using the ${logic.name}.", "SAVE", input) {
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    sharedPref.edit { putString("GATE_$id", value) }
                    Toast.makeText(this, "Settings code saved.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnBack.setOnClickListener { finish() }
    }
}
