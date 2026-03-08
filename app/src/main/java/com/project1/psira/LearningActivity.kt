package com.project1.psira

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LearningActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning)

        val alphabetList = findViewById<TextView>(R.id.alphabetList)

        // Your custom PsiRa Language Dictionary
        val dictionary = """
            PSI-RA ALPHABET:
            A ➔ ρ      N ➔ ν 
            B ➔ Ψ      O ➔ ʘ 
            C ➔ Ͼ      P ➔ π 
            D ➔ Δ      Q ➔ Θ 
            E ➔ Σ      R ➔ Г 
            F ➔ Φ      S ➔ § 
            G ➔ Ω      T ➔ τ 
            H ➔ λ      U ➔ μ 
            I ➔ ι      V ➔ ∇ 
            J ➔ ξ      W ➔ ω 
            K ➔ κ      X ➔ χ 
            L ➔ ζ      Y ➔ γ 
            M ➔ η      Z ➔ ϟ
            
            SECRET NUMBERS:
            0 ➔ ᚫ      5 ➔ ᚲ
            1 ➔ ᛉ      6 ➔ ᚷ
            2 ➔ ᛊ      7 ➔ ᚹ
            3 ➔ ᚦ      8 ➔ ᚺ
            4 ➔ ᚱ      9 ➔ ᛃ
        """.trimIndent()

        alphabetList.text = dictionary
    }
}