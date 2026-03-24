package com.project1.psira

object PsiraCipher {
    // English Base64 to Alien Geometric/APL mapping
    private val alienMap = mapOf(
        'A' to '⍙', 'B' to '⍢', 'C' to '⍣', 'D' to '⍤', 'E' to '⍥', 'F' to '⍦', 
        'G' to '⍧', 'H' to '⍨', 'I' to '⍩', 'J' to '⍪', 'K' to '⍫', 'L' to '⍬', 
        'M' to '⍭', 'N' to '⍮', 'O' to '⍯', 'P' to '⍰', 'Q' to '⍱', 'R' to '⍲', 
        'S' to '⍳', 'T' to '⍴', 'U' to '⍵', 'V' to '⍶', 'W' to '⍷', 'X' to '⍸', 
        'Y' to '⍹', 'Z' to '⍺',
        'a' to '⎈', 'b' to '⎉', 'c' to '⎊', 'd' to '⎋', 'e' to '⎌', 'f' to '⎍', 
        'g' to '⎎', 'h' to '⎏', 'i' to '⎐', 'j' to '⎑', 'k' to '⎒', 'l' to '⎓', 
        'm' to '⎔', 'n' to '⎕', 'o' to '⎖', 'p' to '⎗', 'q' to '⎘', 'r' to '⎙', 
        's' to '⎚', 't' to '⎛', 'u' to '⎜', 'v' to '⎝', 'w' to '⎞', 'x' to '⎟', 
        'y' to '⎠', 'z' to '⎡',
        '0' to '▖', '1' to '▗', '2' to '▘', '3' to '▙', '4' to '▚', '5' to '▛', 
        '6' to '▜', '7' to '▝', '8' to '▞', '9' to '▟',
        '+' to '✦', '/' to '✧', '=' to '⬡'
    )
    
    private val reverseMap = alienMap.entries.associate { (k, v) -> v to k }

    fun toAlien(base64: String): String {
        return base64.map { c -> alienMap[c] ?: c }.joinToString("")
    }

    fun toBase64(alien: String): String {
        return alien.map { c -> reverseMap[c] ?: c }.joinToString("")
    }
}
