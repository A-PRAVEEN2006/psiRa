package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var calcDisplay: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        calcDisplay = findViewById(R.id.calcDisplay)
        calcDisplay.showSoftInputOnFocus = false // Keep phone keyboard hidden

        setNumberListeners()
        setOperatorListeners()

        // --- THE ENGINE ---
        val btnEqual = findViewById<Button>(R.id.btnEqual)
        btnEqual.setOnClickListener {
            val currentText = calcDisplay.text.toString().trim()
            val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

            // Check if a password exists. If it's the first time, this is null.
            val savedPassword = sharedPref.getString("VAULT_PASS", null)

            if (savedPassword == null) {
                // 1. FIRST TIME SETUP: Whatever you type becomes the password
                if (currentText.isNotEmpty()) {
                    sharedPref.edit().putString("VAULT_PASS", currentText).apply()
                    Toast.makeText(this, "Secret Code Set!", Toast.LENGTH_SHORT).show()
                    enterVault()
                }
            } else {
                // 2. NORMAL MODE: Check if it matches the saved password
                if (currentText == savedPassword) {
                    enterVault()
                }
                // 3. DO MATH: If it's the wrong code, just act like a calculator
                else if (currentText.isNotEmpty()) {
                    val mathResult = evaluateMath(currentText)
                    calcDisplay.setText(mathResult)
                }
            }
        }

        // Clear (C)
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            calcDisplay.setText("")
        }

        // Delete (Del)
        findViewById<Button>(R.id.btnDel).setOnClickListener {
            val text = calcDisplay.text.toString()
            if (text.isNotEmpty()) {
                calcDisplay.setText(text.substring(0, text.length - 1))
            }
        }
    }

    // Logic to handle real math results
    private fun evaluateMath(expression: String): String {
        return try {
            val parts = expression.split(Regex("(?<=[-+*/])|(?=[-+*/])"))
            if (parts.size < 3) return expression

            val num1 = parts[0].trim().toDouble()
            val op = parts[1].trim()
            val num2 = parts[2].trim().toDouble()

            val res = when (op) {
                "+" -> num1 + num2
                "-" -> num1 - num2
                "*" -> num1 * num2
                "/" -> if (num2 != 0.0) num1 / num2 else 0.0
                else -> 0.0
            }
            // Drop the .0 if it's a clean number
            if (res % 1 == 0.0) res.toInt().toString() else res.toString()
        } catch (e: Exception) {
            "Error"
        }
    }

    private fun setNumberListeners() {
        val ids = arrayOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9)
        for (id in ids) {
            findViewById<Button>(id).setOnClickListener {
                calcDisplay.append((it as Button).text)
            }
        }
    }

    private fun setOperatorListeners() {
        val ids = arrayOf(R.id.btnAdd, R.id.btnSubtract, R.id.btnMultiply, R.id.btnDivide)
        for (id in ids) {
            findViewById<Button>(id).setOnClickListener {
                calcDisplay.append((it as Button).text)
            }
        }
    }

    private fun enterVault() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(150)
        }
        startActivity(Intent(this, LoginActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        calcDisplay.setText("")
    }
}