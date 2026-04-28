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
        val appSecret = StealthManager.getPass(this, id)
        
        // Handle Panic Wipe
        if (panicCode.isNotEmpty() && input == panicCode) {
            vibrate(500)
            sharedPref.edit().clear().apply()
            Toast.makeText(this, "SYSTEM ERASE EXECUTED", Toast.LENGTH_LONG).show()
            finishAffinity()
            return
        }

        // Standard Secret Unlock
        if (input == appSecret) {
            unlock(false)
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
        var currentInput = ""

        val listener = View.OnClickListener { v ->
            val b = v as Button
            val text = b.text.toString()
            when (text) {
                "C" -> { currentInput = ""; display.text = "0" }
                "=" -> {
                    handleInput(currentInput, "CALCULATOR")
                    try {
                        val tokens = currentInput.replace("×", "*").replace("÷", "/").split(Regex("(?<=[-+*/])|(?=[-+*/])"))
                        if (tokens.size >= 1) {
                            var result = tokens[0].trim().toDouble()
                            var i = 1
                            while (i < tokens.size) {
                                val op = tokens[i].trim()
                                val nextVal = if (i + 1 < tokens.size) tokens[i+1].trim().toDouble() else 0.0
                                result = when (op) {
                                    "+" -> result + nextVal
                                    "-" -> result - nextVal
                                    "*" -> result * nextVal
                                    "/" -> if(nextVal != 0.0) result / nextVal else result
                                    else -> result
                                }
                                i += 2
                            }
                            val resStr = if (result == result.toLong().toDouble()) result.toLong().toString() else "%.2f".format(result)
                            display.text = resStr
                            currentInput = resStr
                        }
                    } catch (e: Exception) {
                        display.text = "0"
                        currentInput = ""
                    }
                }
                else -> { currentInput += text; display.text = currentInput }
            }
        }

        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,
            R.id.btnEqual, R.id.btnC, R.id.btnPlus, R.id.btnMinus,
            R.id.btnMultiply, R.id.btnDivide, R.id.btnDot
        )
        buttonIds.forEach { id -> findViewById<Button>(id)?.setOnClickListener(listener) }
    }

    private fun setupNotepad() {
        setContentView(R.layout.activity_cloak_notepad)
        val etContent = findViewById<EditText>(R.id.etNoteContent)
        val tvLabel = findViewById<TextView>(R.id.tvNotepadLabel)
        
        findViewById<Button>(R.id.btnSaveNote).setOnClickListener {
            Toast.makeText(this, "Note Saved Externally", Toast.LENGTH_SHORT).show()
        }

        var lastTapToken = 0L
        tvLabel?.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapToken < 500) { vibrate(50); unlock(false) }
            lastTapToken = now
        }
    }

    private fun setupRecorder() {
        setContentView(R.layout.activity_cloak_recorder)
        val btn = findViewById<ImageButton>(R.id.btnRecord)
        val tvTimer = findViewById<TextView>(R.id.tvTimer)
        var isRecording = false
        var startTimeToken = 0L
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val timerRunnable = object : Runnable {
            override fun run() {
                val sec = (System.currentTimeMillis() - startTimeToken) / 1000
                tvTimer.text = String.format("%02d:%02d:%02d", sec/3600, (sec%3600)/60, sec%60)
                handler.postDelayed(this, 1000)
            }
        }
        btn?.setOnClickListener {
            isRecording = !isRecording
            if (isRecording) { startTimeToken = System.currentTimeMillis(); handler.post(timerRunnable); btn.animate().scaleX(1.1f).scaleY(1.1f).start() } 
            else { handler.removeCallbacks(timerRunnable); btn.animate().scaleX(1.0f).scaleY(1.0f).start() }
        }
        btn?.setOnLongClickListener { vibrate(50); unlock(false); true }
    }

    private fun setupCompass() {
        setContentView(R.layout.activity_cloak_compass)
        val compassImg = findViewById<ImageView>(R.id.ivCompass)
        val sequenceListener = View.OnClickListener { v ->
            val dir = (v as Button).text.toString()
            compassImg?.animate()?.rotation(when(dir){"N"->0f;"E"->90f;"S"->180f;"W"->270f;else->0f})?.setDuration(500)?.start()
        }
        findViewById<Button>(R.id.btnNorth)?.setOnClickListener(sequenceListener)
        findViewById<Button>(R.id.btnSouth)?.setOnClickListener(sequenceListener)
        findViewById<Button>(R.id.btnEast)?.setOnClickListener(sequenceListener)
        findViewById<Button>(R.id.btnWest)?.setOnClickListener(sequenceListener)
        compassImg?.setOnLongClickListener { vibrate(50); unlock(false); true }
    }

    private fun setupCalendar() {
        setContentView(R.layout.activity_cloak_calendar)
        val calendar = findViewById<CalendarView>(R.id.calendarView)
        calendar?.setOnLongClickListener { vibrate(50); unlock(false); true }
    }

    private fun setupWeather() {
        setContentView(R.layout.activity_cloak_weather)
        val tvCity = findViewById<TextView>(R.id.tvCity)
        var lastWeatherTap = 0L
        tvCity?.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastWeatherTap < 500) { vibrate(50); unlock(false) }
            lastWeatherTap = now
        }
    }

    private fun setupConverter() {
        setContentView(R.layout.activity_cloak_converter)
        val ivConvert = findViewById<ImageView>(R.id.ivConvert)
        ivConvert?.setOnLongClickListener { vibrate(50); unlock(false); true }
    }

    private fun setupFlashlight() {
        setContentView(R.layout.activity_cloak_flashlight)
        val switch = findViewById<ImageView>(R.id.ivFlashlightSwitch)
        val beam = findViewById<View>(R.id.vFlashlightBeam)
        var isOn = false
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val cameraId = try { cameraManager.cameraIdList[0] } catch (e: Exception) { null }

        switch?.setOnClickListener {
            isOn = !isOn
            switch.rotation = if (isOn) 180f else 0f
            switch.setColorFilter(if (isOn) android.graphics.Color.YELLOW else android.graphics.Color.GRAY)
            beam?.visibility = if (isOn) View.VISIBLE else View.GONE
            if (cameraId != null) try { cameraManager.setTorchMode(cameraId, isOn) } catch (e: Exception) {}
        }
        switch?.setOnLongClickListener { 
            if (cameraId != null && isOn) try { cameraManager.setTorchMode(cameraId, false) } catch (e: Exception) {}
            vibrate(50); unlock(false); true 
        }
    }

    private fun setupRadio() {
        setContentView(R.layout.activity_cloak_radio)
        val tvFreq = findViewById<TextView>(R.id.tvFrequency)
        findViewById<SeekBar>(R.id.seekBarFrequency)?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, b: Boolean) {
                tvFreq?.text = String.format("%.1f", p/10.0)
                if (p == 1077) { vibrate(50); unlock(false) }
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
