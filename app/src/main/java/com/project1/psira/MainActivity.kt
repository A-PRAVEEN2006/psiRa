package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.os.Build
import androidx.core.content.edit

class MainActivity : BaseActivity() {
    private lateinit var calcDisplay: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
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

            // Check Panic Code First
            val panicCode = sharedPref.getString("PANIC_PASSCODE", null)
            if (panicCode != null && currentText == panicCode) {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                sharedPref.edit { clear() }
                calcDisplay.setText("")
                Toast.makeText(this, "System Error 0x000F", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if a password exists. If it's the first time, this is null.
            val savedPassword = sharedPref.getString("VAULT_PASS", null)

            if (savedPassword == null) {
                // 1. FIRST TIME SETUP: Whatever you type becomes the password
                if (currentText.isNotEmpty()) {
                    sharedPref.edit { putString("VAULT_PASS", currentText) }
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
                calcDisplay.setText(text.lowercase().dropLast(1))
            }
        }
    }

    // Logic to handle real math results
    private fun evaluateMath(expression: String): String {
        return try {
            val tokens = expression.split(Regex("(?<=[-+*/])|(?=[-+*/])")).map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return "0"
            
            var result = tokens[0].toDouble()
            var i = 1
            while (i < tokens.size) {
                val op = tokens[i]
                val nextVal = tokens.getOrNull(i + 1)?.toDouble() ?: 0.0
                result = when (op) {
                    "+" -> result + nextVal
                    "-" -> result - nextVal
                    "*" -> result * nextVal
                    "/" -> if (nextVal != 0.0) result / nextVal else 0.0
                    else -> result
                }
                i += 2
            }
            
            if (result % 1 == 0.0) result.toInt().toString() else "%.2f".format(result)
        } catch (_: Exception) {
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

    @Suppress("DEPRECATION")
    private fun enterVault() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }

        val destination = getSmartDestination(this)
        startActivity(Intent(this, destination))
        finish()

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        calcDisplay.setText("")
    }
}