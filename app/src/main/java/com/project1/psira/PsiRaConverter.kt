package com.yourname.psira // Make sure this matches your actual package name at the very top!

object PsiRaConverter {
    private val map = mapOf(
        'A' to '☉', 'B' to '☾', 'C' to '⊗', 'D' to 'Ψ', 'E' to 'λ',
        'F' to '∆', 'G' to 'Ω', 'H' to '⚡', 'I' to '∞', 'J' to 'ℵ'
    )
    private val reverseMap = map.entries.associate { (k, v) -> v to k }

    fun encode(text: String): String = text.uppercase().map { map[it] ?: it }.joinToString("")
    fun decode(psira: String): String = psira.map { reverseMap[it] ?: it }.joinToString("")
}