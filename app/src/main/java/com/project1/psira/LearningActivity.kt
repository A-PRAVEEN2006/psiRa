package com.project1.psira

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LearningActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_learning)

        // Back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Final Tifinagh Geometric Alphabet
        val entries = listOf(
            "ⴰ" to "A",  "ⴱ" to "B",  "ⵛ" to "C",  "ⴷ" to "D",  "ⴻ" to "E",
            "ⴼ" to "F",  "ⴳ" to "G",  "ⵀ" to "H",  "ⵉ" to "I",  "ⵊ" to "J",
            "ⴽ" to "K",  "ⵍ" to "L",  "ⵎ" to "M",  "ⵏ" to "N",  "ⵄ" to "O",
            "ⵒ" to "P",  "ⵗ" to "Q",  "ⵔ" to "R",  "ⵙ" to "S",  "ⵜ" to "T",
            "ⵓ" to "U",  "ⵖ" to "V",  "ⵡ" to "W",  "ⵅ" to "X",  "ⵢ" to "Y",  "ⵣ" to "Z",
            "⦲" to "0",  "⦳" to "1",  "⦴" to "2",  "⦵" to "3",  "⦶" to "4",
            "⦷" to "5",  "⦸" to "6",  "⦹" to "7",  "⦺" to "8",  "⦻" to "9"
        )

        // Setup RecyclerView Grid (4 columns)
        val rv = findViewById<RecyclerView>(R.id.rvAlphabet)
        rv.layoutManager = GridLayoutManager(this, 4)
        rv.adapter = AlphabetAdapter(entries)

        // Live Translator
        val etInput = findViewById<EditText>(R.id.etTranslatorInput)
        val tvOutput = findViewById<TextView>(R.id.tvTranslatorOutput)

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString() ?: ""
                if (input.isEmpty()) {
                    tvOutput.text = "⍙⍢⍣⍤⍥⍦⍧⍨⍩..."
                } else {
                    tvOutput.text = PsiRaConverter.encode(input)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}