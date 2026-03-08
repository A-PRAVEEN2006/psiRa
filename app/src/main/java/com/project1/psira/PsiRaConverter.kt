package com.project1.psira

object PsiRaConverter {
    private val map = mapOf(
        'a' to "ρ", 'b' to "Ψ", 'c' to "Ͼ", 'd' to "Δ", 'e' to "Σ",
        'f' to "Φ", 'g' to "Ω", 'h' to "λ", 'i' to "ι", 'j' to "ξ",
        'k' to "κ", 'l' to "ζ", 'm' to "η", 'n' to "ν", 'o' to "ʘ",
        'p' to "π", 'q' to "Θ", 'r' to "Г", 's' to "§", 't' to "τ",
        'u' to "μ", 'v' to "∇", 'w' to "ω", 'x' to "χ", 'y' to "γ", 'z' to "ϟ",
        '0' to "ᚫ", '1' to "ᛉ", '2' to "ᛊ", '3' to "ᚦ", '4' to "ᚱ",
        '5' to "ᚲ", '6' to "ᚷ", '7' to "ᚹ", '8' to "ᚺ", '9' to "ᛃ",
        ' ' to "  "
    )

    fun encode(text: String): String {
        val result = StringBuilder()
        for (char in text.lowercase()) {
            result.append(map[char] ?: char)
        }
        return result.toString()
    }

    // FIX: Added ": String" and "return"
    fun decode(text: String): String {
        var decoded = text
        map.forEach { (char, symbol) ->
            decoded = decoded.replace(symbol, char.toString())
        }
        return decoded // This sends the text back to the Adapter!
    }
}