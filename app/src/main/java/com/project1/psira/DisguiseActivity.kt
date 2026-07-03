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
            "NOTEPAD"    -> setupNotepad()
            "RECORDER"   -> setupRecorder()
            "CALENDAR"   -> setupCalendar()
            "WEATHER"    -> setupCurrency()
            "CONVERTER"  -> setupConverter()
            else         -> setupClock()
        }
    }

    private fun handleInput(input: String, id: String) {
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val panicCode = sharedPref.getString("PANIC_PASSCODE", "") ?: ""

        // Normalize input for comparison (especially for calculator)
        val normalizedInput = input.replace("×", "*").replace("÷", "/")

        // ── 1. Panic code — bypasses rate limiter entirely ────────────────────
        if (panicCode.isNotEmpty() && normalizedInput == panicCode) {
            vibrate(500)
            sharedPref.edit().clear().apply()
            Toast.makeText(this, "SYSTEM ERASE EXECUTED", Toast.LENGTH_LONG).show()
            finishAffinity()
            return
        }

        // ── 2. Rate-limit gate — block if currently locked out ────────────────
        if (RateLimiter.isLockedOut(this)) {
            val remaining = RateLimiter.getRemainingLockoutSeconds(this)
            vibrate(80)
            // Minimal toast — avoids breaking the disguise UI while still signalling
            Toast.makeText(this, "⏳ ${remaining}s", Toast.LENGTH_SHORT).show()
            return
        }

        val appSecret  = StealthManager.getPass(this, id).replace("×", "*").replace("÷", "/")
        val gateSecret = StealthManager.getGateway(this, id).replace("×", "*").replace("÷", "/")

        // ── 3. Secret match ───────────────────────────────────────────────────
        when {
            normalizedInput == appSecret -> {
                RateLimiter.resetAttempts(this)
                unlock(false)
            }
            normalizedInput == gateSecret -> {
                RateLimiter.resetAttempts(this)
                unlock(true)
            }
            else -> {
                // Only count as a failed unlock attempt if the input is intentionally short.
                // This prevents normal Notepad diary entries (long body text) from
                // incrementing the counter and eventually locking out the real owner.
                val isLikelyUnlockAttempt = input.length <= 30
                if (isLikelyUnlockAttempt && input.isNotEmpty()) {
                    val justLocked = RateLimiter.recordFailedAttempt(this)
                    if (justLocked) {
                        val remaining = RateLimiter.getRemainingLockoutSeconds(this)
                        vibrate(120)
                        Toast.makeText(this, "⏳ ${remaining}s", Toast.LENGTH_SHORT).show()
                    }
                    // else: silent fail — preserves the disguise's credibility
                }
            }
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
                        sharedPref.edit()
                            .putString("VAULT_PASS", currentInput)
                            .putString("PASS_CALCULATOR", currentInput) // For consistency
                            .putBoolean("CALC_PASS_SET", true)
                            .apply()
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

    // ─── DIARY ────────────────────────────────────────────────────────────────
    private fun setupNotepad() {
        setContentView(R.layout.activity_cloak_notepad)

        val prefs        = getSharedPreferences("DiaryPrefs", android.content.Context.MODE_PRIVATE)
        val listView     = findViewById<android.widget.ListView>(R.id.listDiaryEntries)
        val btnNew       = findViewById<android.widget.ImageView>(R.id.btnNewEntry)
        val etSearch     = findViewById<EditText>(R.id.etSearch)
        val emptyLayout  = findViewById<android.view.View>(R.id.layoutEmptyDiary)

        // Entry = "MOOD|TITLE|BODY|TIMESTAMP" stored as JSON array in SharedPrefs
        data class DiaryEntry(val mood: String, val title: String, val body: String, val timestamp: Long)

        fun loadEntries(): MutableList<DiaryEntry> {
            val json = prefs.getString("entries", "[]") ?: "[]"
            val list = mutableListOf<DiaryEntry>()
            try {
                val arr = org.json.JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(DiaryEntry(o.getString("mood"), o.getString("title"),
                        o.getString("body"), o.getLong("ts")))
                }
            } catch (_: Exception) {}
            return list
        }

        fun saveEntries(list: List<DiaryEntry>) {
            val arr = org.json.JSONArray()
            list.forEach { e ->
                val o = org.json.JSONObject()
                o.put("mood", e.mood); o.put("title", e.title)
                o.put("body", e.body); o.put("ts", e.timestamp)
                arr.put(o)
            }
            prefs.edit().putString("entries", arr.toString()).apply()
        }

        fun formatDate(ts: Long): String {
            val sdf = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(ts))
        }

        var allEntries = loadEntries()
        var filteredEntries = allEntries.toMutableList()
        var selectedMood = "📔"

        // ── Adapter ──
        fun buildAdapter(entries: List<DiaryEntry>): android.widget.BaseAdapter {
            return object : android.widget.BaseAdapter() {
                override fun getCount() = entries.size
                override fun getItem(pos: Int) = entries[pos]
                override fun getItemId(pos: Int) = pos.toLong()
                override fun getView(pos: Int, convert: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
                    val v = convert ?: android.view.LayoutInflater.from(this@DisguiseActivity)
                        .inflate(R.layout.item_diary_entry, parent, false)
                    val e = entries[pos]
                    v.findViewById<TextView>(R.id.tvEntryMood).text    = e.mood
                    v.findViewById<TextView>(R.id.tvEntryTitle).text   = e.title.ifEmpty { "Untitled" }
                    v.findViewById<TextView>(R.id.tvEntryPreview).text = e.body.take(120)
                    v.findViewById<TextView>(R.id.tvEntryDate).text    = formatDate(e.timestamp)
                    return v
                }
            }
        }

        fun refreshList(query: String = "") {
            filteredEntries = if (query.isBlank()) allEntries.toMutableList()
            else allEntries.filter {
                it.title.contains(query, true) || it.body.contains(query, true)
            }.toMutableList()
            listView.adapter = buildAdapter(filteredEntries)
            emptyLayout.visibility = if (filteredEntries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            listView.visibility    = if (filteredEntries.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }

        refreshList()

        // ── Open editor ──
        fun openEditor(existing: DiaryEntry? = null) {
            val editorView = android.view.LayoutInflater.from(this)
                .inflate(R.layout.layout_diary_editor, null)

            val etTitle    = editorView.findViewById<EditText>(R.id.etEntryTitle)
            val etBody     = editorView.findViewById<EditText>(R.id.etEntryBody)
            val tvDate     = editorView.findViewById<TextView>(R.id.tvEditorDate)
            val tvWordCnt  = editorView.findViewById<TextView>(R.id.tvWordCount)
            val tvMoodDisp = editorView.findViewById<TextView>(R.id.tvSelectedMood)
            val btnBack    = editorView.findViewById<android.widget.ImageView>(R.id.btnBack)
            val btnSave    = editorView.findViewById<TextView>(R.id.btnSaveEntry)

            val moodIds = listOf(R.id.moodHappy, R.id.moodNeutral, R.id.moodSad, R.id.moodAngry, R.id.moodExcited)
            val moods   = listOf("😊", "😐", "😔", "😠", "🤩")

            selectedMood = existing?.mood ?: "📔"
            tvDate.text  = if (existing != null) formatDate(existing.timestamp)
                           else java.text.SimpleDateFormat("EEEE, d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            etTitle.setText(existing?.title ?: "")
            etBody.setText(existing?.body   ?: "")
            tvMoodDisp.text = selectedMood

            moodIds.forEachIndexed { i, id ->
                editorView.findViewById<TextView>(id).setOnClickListener {
                    selectedMood = moods[i]
                    tvMoodDisp.text = selectedMood
                }
            }

            etBody.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    val words = s?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.size ?: 0
                    tvWordCnt.text = "$words word${if (words == 1) "" else "s"}"
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            // Replace main content view
            setContentView(editorView)

            btnBack.setOnClickListener {
                setContentView(R.layout.activity_cloak_notepad)
                // re-bind after re-inflate
                val lv2    = findViewById<android.widget.ListView>(R.id.listDiaryEntries)
                val new2   = findViewById<android.widget.ImageView>(R.id.btnNewEntry)
                val es2    = findViewById<EditText>(R.id.etSearch)
                val emp2   = findViewById<android.view.View>(R.id.layoutEmptyDiary)
                allEntries = loadEntries()
                fun refreshList2(q: String = "") {
                    val fil = if (q.isBlank()) allEntries.toMutableList()
                    else allEntries.filter { it.title.contains(q, true) || it.body.contains(q, true) }.toMutableList()
                    lv2.adapter = buildAdapter(fil)
                    emp2.visibility = if (fil.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                    lv2.visibility  = if (fil.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                }
                refreshList2()
                new2.setOnClickListener { openEditor() }
                lv2.setOnItemClickListener  { _, _, pos, _ -> openEditor(allEntries[pos]) }
                lv2.setOnItemLongClickListener { _, _, pos, _ ->
                    val entry = allEntries[pos]
                    // check unlock
                    handleInput(entry.body.trim(), "NOTEPAD")
                    // delete option
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Delete entry?")
                        .setMessage("\"${entry.title.ifEmpty { "Untitled" }}\" will be deleted.")
                        .setPositiveButton("Delete") { _, _ ->
                            allEntries.removeAt(pos)
                            saveEntries(allEntries)
                            refreshList2()
                        }
                        .setNegativeButton("Cancel", null).show()
                    true
                }
                es2.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { refreshList2(s.toString()) }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
            }

            btnSave.setOnClickListener {
                val title = etTitle.text.toString().trim()
                val body  = etBody.text.toString().trim()
                if (body.isEmpty() && title.isEmpty()) {
                    Toast.makeText(this, "Nothing to save.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Check unlock secret BEFORE saving
                handleInput(body, "NOTEPAD")

                val ts    = existing?.timestamp ?: System.currentTimeMillis()
                val entry = DiaryEntry(selectedMood, title, body, ts)
                if (existing != null) {
                    val idx = allEntries.indexOfFirst { it.timestamp == existing.timestamp }
                    if (idx >= 0) allEntries[idx] = entry else allEntries.add(0, entry)
                } else {
                    allEntries.add(0, entry)
                }
                saveEntries(allEntries)
                Toast.makeText(this, "Entry saved.", Toast.LENGTH_SHORT).show()
                btnBack.performClick()
            }
        }

        // ── Main list interactions ──
        listView.setOnItemClickListener  { _, _, pos, _ -> openEditor(filteredEntries[pos]) }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            val entry = filteredEntries[pos]
            handleInput(entry.body.trim(), "NOTEPAD")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete entry?")
                .setMessage("\"${entry.title.ifEmpty { "Untitled" }}\" will be deleted.")
                .setPositiveButton("Delete") { _, _ ->
                    allEntries.remove(entry)
                    saveEntries(allEntries)
                    refreshList(etSearch.text.toString())
                }
                .setNegativeButton("Cancel", null).show()
            true
        }
        btnNew.setOnClickListener { openEditor() }
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { refreshList(s.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    // ─── VOICE MEMOS ─────────────────────────────────────────────────────────
    private fun setupRecorder() {
        setContentView(R.layout.activity_cloak_recorder)

        val tvTimer      = findViewById<TextView>(R.id.tvTimer)
        val tvStatus     = findViewById<TextView>(R.id.tvRecordStatus)
        val tvCount      = findViewById<TextView>(R.id.tvRecordingCount)
        val btnRecord    = findViewById<android.widget.ImageButton>(R.id.btnRecord)
        val listView     = findViewById<android.widget.ListView>(R.id.listRecordings)
        val emptyLayout  = findViewById<android.view.View>(R.id.layoutEmptyRecorder)

        var mediaRecorder: android.media.MediaRecorder? = null
        var mediaPlayer:   android.media.MediaPlayer?   = null
        var isRecording = false
        var playingFile: String? = null
        var recordStart = 0L
        var longPressCount = 0
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        // Recordings stored in app's files dir
        val recDir = getExternalFilesDir(null) ?: filesDir

        data class Recording(val file: java.io.File, val name: String, val durationSec: Int, val date: Long)

        fun loadRecordings(): MutableList<Recording> {
            return recDir.listFiles { f -> f.extension == "m4a" }
                ?.sortedByDescending { it.lastModified() }
                ?.map { f ->
                    // Duration stored in filename: "VoiceMemo_<ts>_<dur>.m4a"
                    val parts = f.nameWithoutExtension.split("_")
                    val dur = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    Recording(f, f.nameWithoutExtension.replace("_", " "), dur, f.lastModified())
                }?.toMutableList() ?: mutableListOf()
        }

        fun formatDur(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
        fun formatDate(ts: Long): String {
            val today = java.util.Calendar.getInstance()
            val d = java.util.Calendar.getInstance().also { it.timeInMillis = ts }
            return if (today.get(java.util.Calendar.DATE) == d.get(java.util.Calendar.DATE)) "Today"
            else java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(ts))
        }

        var allRecs = loadRecordings()

        fun stopPlayer() {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            playingFile = null
        }

        fun refreshList() {
            allRecs = loadRecordings()
            val count = allRecs.size
            tvCount.text = "$count memo${if (count == 1) "" else "s"}"
            emptyLayout.visibility = if (count == 0) android.view.View.VISIBLE else android.view.View.GONE
            listView.visibility    = if (count == 0) android.view.View.GONE else android.view.View.VISIBLE

            listView.adapter = object : android.widget.BaseAdapter() {
                override fun getCount() = allRecs.size
                override fun getItem(p: Int) = allRecs[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(pos: Int, convert: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
                    val v = convert ?: android.view.LayoutInflater.from(this@DisguiseActivity)
                        .inflate(R.layout.item_recording, parent, false)
                    val rec = allRecs[pos]
                    v.findViewById<TextView>(R.id.tvRecordingName).text = "Voice Memo ${pos + 1}"
                    v.findViewById<TextView>(R.id.tvRecordingInfo).text =
                        "${formatDur(rec.durationSec)}  •  ${formatDate(rec.date)}"

                    val ivPlay = v.findViewById<android.widget.ImageView>(R.id.ivPlayPause)
                    val isPlaying = playingFile == rec.file.absolutePath
                    ivPlay.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause
                                            else android.R.drawable.ic_media_play)

                    ivPlay.setOnClickListener {
                        if (isRecording) return@setOnClickListener
                        if (playingFile == rec.file.absolutePath) {
                            stopPlayer()
                        } else {
                            stopPlayer()
                            mediaPlayer = android.media.MediaPlayer().apply {
                                setDataSource(rec.file.absolutePath)
                                prepare()
                                start()
                                setOnCompletionListener { stopPlayer(); refreshList() }
                            }
                            playingFile = rec.file.absolutePath
                        }
                        refreshList()
                    }

                    v.findViewById<android.widget.ImageView>(R.id.ivDeleteRecording).setOnClickListener {
                        stopPlayer()
                        rec.file.delete()
                        refreshList()
                    }
                    return v
                }
            }
        }

        refreshList()

        // ── Timer runnable ──
        val timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = ((System.currentTimeMillis() - recordStart) / 1000).toInt()
                tvTimer.text = "%02d:%02d".format(elapsed / 60, elapsed % 60)
                handler.postDelayed(this, 500)
            }
        }

        // ── Record button ──
        btnRecord.setOnClickListener {
            if (isRecording) {
                // Stop recording
                val elapsed = ((System.currentTimeMillis() - recordStart) / 1000).toInt()
                try { mediaRecorder?.stop() } catch (_: Exception) {}
                mediaRecorder?.release()
                mediaRecorder = null
                handler.removeCallbacks(timerRunnable)
                isRecording = false
                tvTimer.text = "00:00"
                tvStatus.text = "Tap to record"
                btnRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#FF3B30")))
                refreshList()
            } else {
                // Start recording
                stopPlayer()
                longPressCount = 0  // reset on normal tap
                val ts   = System.currentTimeMillis()
                val file = java.io.File(recDir, "VoiceMemo_${ts}_0.m4a")
                try {
                    @Suppress("DEPRECATION")
                    mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= 31)
                        android.media.MediaRecorder(this)
                    else android.media.MediaRecorder()
                    mediaRecorder!!.apply {
                        setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                        setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(44100)
                        setAudioEncodingBitRate(128000)
                        setOutputFile(file.absolutePath)
                        prepare()
                        start()
                    }
                    recordStart = System.currentTimeMillis()
                    isRecording = true
                    tvStatus.text = "● Recording…"
                    btnRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#636366")))
                    handler.post(timerRunnable)
                } catch (e: Exception) {
                    Toast.makeText(this, "Microphone unavailable.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ── Long-press record = unlock check ──
        btnRecord.setOnLongClickListener {
            longPressCount++
            vibrate(40)
            handleInput(longPressCount.toString(), "RECORDER")
            true
        }
    }


    private fun setupCalendar() {
        setContentView(R.layout.activity_cloak_calendar)
        val calendar = findViewById<CalendarView>(R.id.calendarView)
        calendar?.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val dateStr = String.format("%02d-%02d", dayOfMonth, month + 1)
            handleInput(dateStr, "CALENDAR")
        }
    }

    private fun setupCurrency() {
        setContentView(R.layout.activity_cloak_weather)
        val tvCity    = findViewById<TextView>(R.id.tvCity)
        val tvTemp    = findViewById<TextView>(R.id.tvTemp)
        val tvDesc    = findViewById<android.widget.TextView>(R.id.tvCurrencyDesc)
        val ivRefresh = findViewById<ImageView>(R.id.ivWeatherRefresh)
        var tapCount  = 0

        tvCity.text = "💱  Exchange Rates"
        tvTemp.text = "--"
        tvDesc?.text = "Tap ↻ to refresh"

        fun fetchRates() {
            tvTemp.text = "…"
            Thread {
                try {
                    val url  = java.net.URL("https://open.er-api.com/v6/latest/USD")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8_000
                    conn.readTimeout    = 8_000
                    val json = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()

                    // Parse a few key pairs from the JSON manually (no extra lib needed)
                    fun parseRate(key: String): String {
                        val pattern = Regex("\"$key\"\\s*:\\s*([0-9.]+)")
                        return pattern.find(json)?.groupValues?.get(1)
                            ?.toDoubleOrNull()?.let { "%.4f".format(it) } ?: "N/A"
                    }

                    val eur = parseRate("EUR")
                    val gbp = parseRate("GBP")
                    val inr = parseRate("INR")
                    val jpy = parseRate("JPY")
                    val aed = parseRate("AED")

                    runOnUiThread {
                        tvTemp.text = "1 USD"
                        tvDesc?.text =
                            "EUR  $eur\n" +
                            "GBP  $gbp\n" +
                            "INR  $inr\n" +
                            "JPY  $jpy\n" +
                            "AED  $aed"
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvTemp.text  = "--"
                        tvDesc?.text = "No connection"
                    }
                }
            }.start()
        }

        fetchRates()

        ivRefresh?.setOnClickListener {
            tapCount++
            handleInput(tapCount.toString(), "WEATHER")
            vibrate(20)
            it.animate().rotationBy(360f).setDuration(500).start()
            fetchRates()
        }
    }

    private fun setupConverter() {
        setContentView(R.layout.activity_cloak_converter)
        val etValue = findViewById<EditText>(R.id.etInput)
        val ivConvert = findViewById<ImageView>(R.id.ivConvert)
        
        ivConvert?.setOnClickListener {
            val input = etValue?.text?.toString() ?: ""
            handleInput(input, "CONVERTER")
            Toast.makeText(this, "Units Converted", Toast.LENGTH_SHORT).show()
        }
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
