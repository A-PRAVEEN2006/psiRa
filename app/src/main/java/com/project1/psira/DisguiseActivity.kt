package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/**
 * High-Realism Tactical Disguise Engine.
 * 10 functional decoy modules masking the Spectre Enclave.
 */
open class DisguiseActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!isTaskRoot) {
            finish()
            return
        }
        
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)

        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val isDark = sharedPref.getBoolean("IS_DARK_MODE", true)
        AppCompatDelegate.setDefaultNightMode(if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)

        val cloakType = sharedPref.getString("CLOAK_TYPE", "CLOCK") ?: "CLOCK"
        
        when (cloakType) {
            "CALCULATOR" -> setupCalculator()
            "NOTEPAD" -> setupNotepad()
            "RECORDER" -> setupRecorder()
            "COMPASS" -> setupCompass()
            "CALENDAR" -> setupCalendar()
            "WEATHER" -> setupWeather()
            "CONVERTER" -> setupConverter()
            "FLASHLIGHT" -> setupFlashlight()
            "RADIO" -> setupRadio()
            else -> setupClock()
        }
    }

    private fun handleInput(input: String, id: String) {
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val panicCode = sharedPref.getString("PANIC_PASSCODE", "") ?: ""
        
        // Normalize input for comparison (especially for calculator)
        val normalizedInput = input.replace("×", "*").replace("÷", "/")
        
        val appSecret = StealthManager.getPass(this, id).replace("×", "*").replace("÷", "/")
        val gateSecret = StealthManager.getGateway(this, id).replace("×", "*").replace("÷", "/")
        
        // Handle Panic Wipe
        if (panicCode.isNotEmpty() && normalizedInput == panicCode) {
            vibrate(500)
            sharedPref.edit().clear().apply()
            Toast.makeText(this, "SYSTEM ERASE EXECUTED", Toast.LENGTH_LONG).show()
            finishAffinity()
            return
        }

        // Standard Secret Unlock
        if (normalizedInput == appSecret) {
            unlock(false)
        } else if (normalizedInput == gateSecret) {
            unlock(true)
        }
    }

    private fun setupClock() {
        setContentView(R.layout.activity_cloak_clock)
        val clockView = findViewById<AnalogClockView>(R.id.analogClock)
        clockView.onClockUnlockListener = { inputTime ->
            handleInput(inputTime, "CLOCK")
            clockView.resetToRealTime()
        }
    }

    private fun setupCalculator() {
        setContentView(R.layout.activity_cloak_calculator)
        val display = findViewById<TextView>(R.id.tvCalcDisplay)
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        
        // Check if calculator password has ever been set manually
        var isPassSet = sharedPref.getBoolean("CALC_PASS_SET", false)
        
        var currentInput = ""
        var lastResult: String? = null

        if (!isPassSet) {
            display.text = "SET PASS"
            display.textSize = 48f
        }

        val listener = View.OnClickListener { v ->
            val b = v as Button
            val text = b.text.toString()
            
            when (text) {
                "AC", "C" -> {
                    currentInput = ""
                    display.text = if (!isPassSet) "SET PASS" else "0"
                    lastResult = null
                }
                "=" -> {
                    if (currentInput.isEmpty()) return@OnClickListener
                    
                    if (!isPassSet) {
                        // First equation becomes the password
                        sharedPref.edit {
                            putString("VAULT_PASS", currentInput)
                            putString("PASS_CALCULATOR", currentInput) // For consistency
                            putBoolean("CALC_PASS_SET", true)
                            apply()
                        }
                        isPassSet = true
                        vibrate(200)
                        Toast.makeText(this, "Secret Equation Locked", Toast.LENGTH_LONG).show()
                        display.text = "0"
                        display.textSize = 80f // Reset size
                        currentInput = ""
                        return@OnClickListener
                    }

                    handleInput(currentInput, "CALCULATOR")
                    try {
                        val expr = currentInput.replace("×", "*").replace("÷", "/")
                        val result = evaluateExpression(expr)
                        val resStr = if (result == result.toLong().toDouble()) result.toLong().toString() else "%.2f".format(result)
                        display.text = resStr
                        currentInput = resStr
                        lastResult = resStr
                    } catch (e: Exception) {
                        display.text = "Error"
                        currentInput = ""
                        lastResult = null
                    }
                }
                "+", "-", "×", "÷" -> {
                    if (currentInput.isEmpty()) {
                        if (text == "-") {
                            currentInput = "-"
                            display.text = currentInput
                        }
                    } else {
                        val lastChar = currentInput.last()
                        if (lastChar in "+-×÷") {
                            // Replace last operator
                            currentInput = currentInput.dropLast(1) + text
                        } else {
                            currentInput += text
                        }
                        display.text = currentInput
                    }
                    lastResult = null
                }
                "." -> {
                    val lastNumber = currentInput.split(Regex("[+÷×-]")).lastOrNull() ?: ""
                    if (!lastNumber.contains(".")) {
                        currentInput += if (currentInput.isEmpty() || currentInput.last() in "+-×÷") "0." else "."
                        display.text = currentInput
                    }
                    lastResult = null
                }
                "+/-" -> {
                    if (currentInput.isNotEmpty()) {
                        val tokens = currentInput.split(Regex("(?<=[-+÷×])|(?=[-+÷×])")).toMutableList()
                        val lastToken = tokens.last()
                        if (lastToken.isNotEmpty() && lastToken.first().isDigit() || lastToken.contains(".")) {
                            val negated = if (lastToken.startsWith("-")) lastToken.substring(1) else "-$lastToken"
                            tokens[tokens.size - 1] = negated
                            currentInput = tokens.joinToString("")
                        } else if (lastToken == "-") {
                            tokens.removeAt(tokens.size - 1)
                            currentInput = tokens.joinToString("")
                        } else if (lastToken in "+÷×") {
                            currentInput += "-"
                        }
                        display.text = currentInput
                    } else {
                        currentInput = "-"
                        display.text = currentInput
                    }
                    lastResult = null
                }
                "%" -> {
                    if (currentInput.isNotEmpty()) {
                        val tokens = currentInput.split(Regex("(?<=[-+÷×])|(?=[-+÷×])")).toMutableList()
                        val lastToken = tokens.last()
                        try {
                            val value = lastToken.toDouble() / 100.0
                            tokens[tokens.size - 1] = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
                            currentInput = tokens.joinToString("")
                            display.text = currentInput
                        } catch (e: Exception) {}
                    }
                    lastResult = null
                }
                else -> {
                    // Numeric input
                    if (lastResult != null) {
                        currentInput = text
                        lastResult = null
                    } else {
                        currentInput += text
                    }
                    display.text = currentInput
                }
            }
        }

        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,
            R.id.btnEqual, R.id.btnC, R.id.btnPlus, R.id.btnMinus,
            R.id.btnMultiply, R.id.btnDivide, R.id.btnDot, R.id.btnPlusMinus, R.id.btnPercent
        )
        buttonIds.forEach { id -> findViewById<Button>(id)?.setOnClickListener(listener) }
    }

    private fun evaluateExpression(expr: String): Double {
        val tokens = expr.split(Regex("(?<=[-+*/])|(?=[-+*/])")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 0.0
        
        val simplifiedTokens = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token == "-" && (i == 0 || tokens[i-1] in "+-*/")) {
                // Unary minus
                if (i + 1 < tokens.size && (tokens[i+1].first().isDigit() || tokens[i+1].contains("."))) {
                    simplifiedTokens.add("-" + tokens[i+1])
                    i += 2
                    continue
                }
            }
            simplifiedTokens.add(token)
            i++
        }
        
        if (simplifiedTokens.isEmpty()) return 0.0
        
        var result = 0.0
        var currentOp = "+"
        
        for (token in simplifiedTokens) {
            if (token in "+-*/") {
                currentOp = token
            } else {
                val value = token.toDoubleOrNull() ?: 0.0
                result = when (currentOp) {
                    "+" -> result + value
                    "-" -> result - value
                    "*" -> result * value
                    "/" -> if (value != 0.0) result / value else result
                    else -> result
                }
            }
        }
        return result
    }

    private fun setupNotepad() {
        setContentView(R.layout.activity_cloak_notepad)
        val etContent = findViewById<EditText>(R.id.etNoteContent)
        
        findViewById<Button>(R.id.btnSaveNote).setOnClickListener {
            val content = etContent.text.toString().trim()
            handleInput(content, "NOTEPAD")
            Toast.makeText(this, "Note Saved Externally", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecorder() {
        setContentView(R.layout.activity_cloak_recorder)
        val btn = findViewById<ImageButton>(R.id.btnRecord)
        val tvTimer = findViewById<TextView>(R.id.tvTimer)
        var isRecording = false
        var startTimeToken = 0L
        var tapCount = 0
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        val timerRunnable = object : Runnable {
            override fun run() {
                val sec = (System.currentTimeMillis() - startTimeToken) / 1000
                tvTimer.text = String.format("%02d:%02d:%02d", sec/3600, (sec%3600)/60, sec%60)
                handler.postDelayed(this, 1000)
            }
        }
        
        btn?.setOnClickListener {
            tapCount++
            handleInput(tapCount.toString(), "RECORDER")
            
            isRecording = !isRecording
            if (isRecording) { 
                startTimeToken = System.currentTimeMillis()
                handler.post(timerRunnable)
                btn.animate().scaleX(1.1f).scaleY(1.1f).start() 
            } else { 
                handler.removeCallbacks(timerRunnable)
                btn.animate().scaleX(1.0f).scaleY(1.0f).start() 
            }
        }
    }

    private fun setupCompass() {
        setContentView(R.layout.activity_cloak_compass)
        val compassImg = findViewById<ImageView>(R.id.ivCompass)
        var sequence = ""
        
        val sequenceListener = View.OnClickListener { v ->
            val dir = (v as Button).text.toString()
            sequence += dir
            handleInput(sequence, "COMPASS")
            compassImg?.animate()?.rotation(when(dir){"N"->0f;"E"->90f;"S"->180f;"W"->270f;else->0f})?.setDuration(500)?.start()
        }
        findViewById<Button>(R.id.btnNorth)?.setOnClickListener(sequenceListener)
        findViewById<Button>(R.id.btnSouth)?.setOnClickListener(sequenceListener)
        findViewById<Button>(R.id.btnEast)?.setOnClickListener(sequenceListener)
        findViewById<Button>(R.id.btnWest)?.setOnClickListener(sequenceListener)
    }

    private fun setupCalendar() {
        setContentView(R.layout.activity_cloak_calendar)
        val calendar = findViewById<CalendarView>(R.id.calendarView)
        calendar?.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val dateStr = String.format("%02d-%02d", dayOfMonth, month + 1)
            handleInput(dateStr, "CALENDAR")
        }
    }

    private fun setupWeather() {
        setContentView(R.layout.activity_cloak_weather)
        val ivRefresh = findViewById<ImageView>(R.id.ivRefresh)
        var tapCount = 0
        ivRefresh?.setOnClickListener {
            tapCount++
            handleInput(tapCount.toString(), "WEATHER")
            vibrate(20)
            it.animate().rotationBy(360f).setDuration(500).start()
        }
    }

    private fun setupConverter() {
        setContentView(R.layout.activity_cloak_converter)
        val etValue = findViewById<EditText>(R.id.etConvertValue)
        val ivConvert = findViewById<ImageView>(R.id.ivConvert)
        
        ivConvert?.setOnClickListener {
            val input = etValue?.text?.toString() ?: ""
            handleInput(input, "CONVERTER")
            Toast.makeText(this, "Units Converted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFlashlight() {
        setContentView(R.layout.activity_cloak_flashlight)
        val switch = findViewById<ImageView>(R.id.ivFlashlightSwitch)
        val beam = findViewById<View>(R.id.vFlashlightBeam)
        var isOn = false
        var toggleCount = 0
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val cameraId = try { cameraManager.cameraIdList[0] } catch (e: Exception) { null }
 
        switch?.setOnClickListener {
            isOn = !isOn
            toggleCount++
            handleInput(toggleCount.toString(), "FLASHLIGHT")
            
            switch.rotation = if (isOn) 180f else 0f
            switch.setColorFilter(if (isOn) android.graphics.Color.YELLOW else android.graphics.Color.GRAY)
            beam?.visibility = if (isOn) View.VISIBLE else View.GONE
            if (cameraId != null) try { cameraManager.setTorchMode(cameraId, isOn) } catch (e: Exception) {}
        }
    }

    private fun setupRadio() {
        setContentView(R.layout.activity_cloak_radio)
        val tvFreq = findViewById<TextView>(R.id.tvFrequency)
        findViewById<SeekBar>(R.id.seekBarFrequency)?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, b: Boolean) {
                tvFreq?.text = String.format("%.1f", p/10.0)
                handleInput(p.toString(), "RADIO")
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun unlock(goToSettings: Boolean = false) {
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        vibrate(150)
        val autoCount = sharedPref.getInt("AUTO_LOGIN_COUNT", 0)
        
        if (autoCount >= 10 && !goToSettings) {
            Toast.makeText(this, "⚠ LOGIC LOCK: Manual Verification Required", Toast.LENGTH_LONG).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else {
            val destination = if (goToSettings) SettingsActivity::class.java else getSmartDestination(this)
            val intent = Intent(this, destination)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        finish()
    }
}
