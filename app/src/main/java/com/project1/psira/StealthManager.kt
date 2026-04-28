package com.project1.psira

import android.content.Context

data class StealthLogic(
    val id: String,
    val name: String,
    val description: String,
    val guide: String,
    val defaultPass: String,
    val defaultGateway: String,
    val hint: String
)

object StealthManager {
    val LOGICS = listOf(
        StealthLogic(
            "CLOCK",
            "Clock",
            "A simple analog clock. You can unlock the app by moving the hands to a secret time.",
            "1. Move the clock hands to your secret time.\n2. Tap the center button to unlock the app.\n3. Hold the center button to open settings.",
            "02:35",
            "12:00",
            "HH:MM"
        ),
        StealthLogic(
            "CALCULATOR",
            "Calculator",
            "A working calculator. It has a hidden code feature to open your secret vault.",
            "1. Type your secret code on the calculator.\n2. Press [ = ] to open the app.\n3. Type your settings code and press [ = ] to open settings.",
            "9999",
            "7777",
            "4-8 Digits"
        ),
        StealthLogic(
            "NOTEPAD",
            "Notes",
            "A simple notepad. Saving a specific secret word will unlock the app.",
            "1. Type your secret password into the note.\n2. Press 'SAVE' to open the app.\n3. Typing your settings password and saving opens settings.",
            "vault",
            "settings",
            "Any word"
        ),
        StealthLogic(
            "RECORDER",
            "Voice Recorder",
            "A voice recording app. Tapping the record button in a pattern unlocks the vault.",
            "1. Tap the RED Record button a specific number of times.\n2. Example: 5 taps for App, 10 taps for Settings.",
            "5",
            "10",
            "Tap Count"
        ),
        StealthLogic(
            "COMPASS",
            "Compass",
            "A direction tool. Tapping directions in a specific order unlocks the hidden area.",
            "1. Tap the direction buttons in your secret order.\n2. Example: North -> South -> East -> West to open the app.\n3. Use the reverse order for Settings.",
            "NSEW",
            "WSEN",
            "e.g. NSEW"
        ),
        StealthLogic(
            "CALENDAR",
            "Calendar",
            "A normal calendar. Picking a secret date acts as a key to open the app.",
            "1. Select your secret date on the calendar.\n2. Use a different secret date to open Settings.",
            "01-01",
            "12-25",
            "DD-MM"
        ),
        StealthLogic(
            "WEATHER",
            "Weather",
            "A weather app. Tapping the refresh icon multiple times unlocks the secret vault.",
            "1. Tap the refresh icon at the top multiple times.\n2. Sequence: 5 taps for App, 10 for Settings.",
            "5",
            "10",
            "Tap Count"
        ),
        StealthLogic(
            "CONVERTER",
            "Unit Converter",
            "A unit conversion tool. Entering a secret 'magic' number unlocks the app.",
            "1. Enter your secret number in the box.\n2. Tap the 'Convert' icon to unlock.",
            "99.9",
            "00.0",
            "Number"
        ),
        StealthLogic(
            "FLASHLIGHT",
            "Flashlight",
            "A simple flashlight app. Turning the switch on and off a few times unlocks the app.",
            "1. Toggle the flashlight switch multiple times.\n2. 7 times for App, 14 times for Settings.",
            "7",
            "14",
            "Click Count"
        ),
        StealthLogic(
            "RADIO",
            "Radio",
            "An FM radio tuner. Moving the frequency bar to a secret station opens the vault.",
            "1. Slide the frequency bar to your secret station.\n2. Example: 108.0 for App, 88.0 for Settings.",
            "1080",
            "880",
            "Station Num"
        )
    )

    fun getLogic(id: String): StealthLogic? = LOGICS.find { it.id == id }

    fun getPass(context: Context, id: String): String {
        val logic = getLogic(id) ?: return ""
        val prefs = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        if (id == "CALCULATOR") {
            return prefs.getString("VAULT_PASS", logic.defaultPass) ?: logic.defaultPass
        }
        return prefs.getString("PASS_$id", logic.defaultPass) ?: logic.defaultPass
    }

    fun getGateway(context: Context, id: String): String {
        val logic = getLogic(id) ?: return ""
        return context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
            .getString("GATE_$id", logic.defaultGateway) ?: logic.defaultGateway
    }
}
