package com.project1.psira

object PsiRaConverter {

    // Phase 14: Tifinagh (Berber) Geometric Alphabet — stealthy, single-width, no similarity to English
    // Numbers use "Circle Cross" geometric symbols for added complexity
    private val map = mapOf(
        'a' to "ⴰ", 'b' to "ⴱ", 'c' to "ⵛ", 'd' to "ⴷ", 'e' to "ⴻ",
        'f' to "ⴼ", 'g' to "ⴳ", 'h' to "ⵀ", 'i' to "ⵉ", 'j' to "ⵊ",
        'k' to "ⴽ", 'l' to "ⵍ", 'm' to "ⵎ", 'n' to "ⵏ", 'o' to "ⵄ",
        'p' to "ⵒ", 'q' to "ⵗ", 'r' to "ⵔ", 's' to "ⵙ", 't' to "ⵜ",
        'u' to "ⵓ", 'v' to "ⵖ", 'w' to "ⵡ", 'x' to "ⵅ", 'y' to "ⵢ", 'z' to "ⵣ",
        '0' to "⦲", '1' to "⦳", '2' to "⦴", '3' to "⦵", '4' to "⦶",
        '5' to "⦷", '6' to "⦸", '7' to "⦹", '8' to "⦺", '9' to "⦻",
        ' ' to " "
    )

    // Legacy map 5 — Circled numbers era (⓪①②③④⑤⑥⑦⑧⑨) + Greek
    private val legacyMapGreek = mapOf(
        'a' to "ρ", 'b' to "Ψ", 'c' to "Ͼ", 'd' to "Δ", 'e' to "Σ",
        'f' to "Φ", 'g' to "Ω", 'h' to "λ", 'i' to "ι", 'j' to "ξ",
        'k' to "κ", 'l' to "ζ", 'm' to "η", 'n' to "ν", 'o' to "ʘ",
        'p' to "π", 'q' to "Θ", 'r' to "Г", 's' to "§", 't' to "τ",
        'u' to "μ", 'v' to "∇", 'w' to "ω", 'x' to "χ", 'y' to "γ", 'z' to "ϟ",
        '0' to "⓪", '1' to "①", '2' to "②", '3' to "③", '4' to "④",
        '5' to "⑤", '6' to "⑥", '7' to "⑦", '8' to "⑧", '9' to "⑨"
    )

    // Legacy map 4 — Ethiopic era
    private val legacyMapEthiopic = mapOf(
        'a' to "ሀ", 'b' to "ሁ", 'c' to "ሂ", 'd' to "ሃ", 'e' to "ሄ",
        'f' to "ህ", 'g' to "ሆ", 'h' to "ለ", 'i' to "ሉ", 'j' to "ሊ",
        'k' to "ላ", 'l' to "ሌ", 'm' to "ል", 'n' to "ሎ", 'o' to "ሐ",
        'p' to "ሑ", 'q' to "ሒ", 'r' to "ሓ", 's' to "ሔ", 't' to "ሕ",
        'u' to "ሖ", 'v' to "ሗ", 'w' to "መ", 'x' to "ሙ", 'y' to "ሚ", 'z' to "ማ",
        '0' to "ሜ", '1' to "ም", '2' to "ሞ", '3' to "ሠ", '4' to "ሡ",
        '5' to "ሢ", '6' to "ሣ", '7' to "ሤ", '8' to "ሥ", '9' to "ሦ"
    )

    // Legacy map 3 — Runic era
    private val legacyMapRunic = mapOf(
        'a' to "ᚠ", 'b' to "ᚢ", 'c' to "ᚦ", 'd' to "ᚩ", 'e' to "ᚱ",
        'f' to "ᚳ", 'g' to "ᚷ", 'h' to "ᚹ", 'i' to "ᚻ", 'j' to "ᚾ",
        'k' to "ᛁ", 'l' to "ᛄ", 'm' to "ᛇ", 'n' to "ᛈ", 'o' to "ᛉ",
        'p' to "ᛋ", 'q' to "ᛏ", 'r' to "ᛒ", 's' to "ᛖ", 't' to "ᛗ",
        'u' to "ᛚ", 'v' to "ᛜ", 'w' to "ᛞ", 'x' to "ᛟ", 'y' to "ᚫ", 'z' to "ᛃ",
        '0' to "₀", '1' to "₁", '2' to "₂", '3' to "₃", '4' to "₄",
        '5' to "₅", '6' to "₆", '7' to "₇", '8' to "₈", '9' to "₉"
    )

    // Legacy map 2 — APL/Geometric era
    private val legacyMapAPL = mapOf(
        'a' to "⍙", 'b' to "⍢", 'c' to "⍣", 'd' to "⍤", 'e' to "⍥",
        'f' to "⍦", 'g' to "⍧", 'h' to "⍨", 'i' to "⍩", 'j' to "⍪",
        'k' to "⍫", 'l' to "⍬", 'm' to "⍭", 'n' to "⍮", 'o' to "⍯",
        'p' to "⍰", 'q' to "⍱", 'r' to "⍲", 's' to "⍳", 't' to "⍴",
        'u' to "⍵", 'v' to "⍶", 'w' to "⍷", 'x' to "⍸", 'y' to "⍹", 'z' to "⍺",
        '0' to "▖", '1' to "▗", '2' to "▘", '3' to "▙", '4' to "▚",
        '5' to "▛", '6' to "▜", '7' to "▝", '8' to "▞", '9' to "▟"
    )

    // Mega reverse map — decodes ALLera in one pass
    private val reverseCombined: Map<String, Char> =
        legacyMapAPL.entries.associate { (k, v) -> v to k } +
        legacyMapRunic.entries.associate { (k, v) -> v to k } +
        legacyMapEthiopic.entries.associate { (k, v) -> v to k } +
        legacyMapGreek.entries.associate { (k, v) -> v to k } +
        map.entries.associate { (k, v) -> v to k }

    fun encode(text: String): String {
        val result = StringBuilder()
        for (char in text.lowercase()) {
            result.append(map[char] ?: char)
        }
        return result.toString()
    }

    fun decode(text: String): String {
        var decoded = text
        map.entries.associate { (k, v) -> v to k }
            .forEach { (symbol, char) -> decoded = decoded.replace(symbol, char.toString()) }
        return decoded
    }

    /** Auto-decodes ANY era stored message */
    fun decodeAny(text: String): String {
        var decoded = text
        reverseCombined.forEach { (symbol, char) ->
            decoded = decoded.replace(symbol, char.toString())
        }
        return decoded
    }
}