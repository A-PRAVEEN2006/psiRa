package com.project1.psira

import android.content.Context

data class StealthLogic(
    val id: String,
    val name: String,
    val description: String,
    val guide: String,
    val defaultPass: String,
    val defaultGateway: String,
    val hint: String,
    val iconRes: Int = R.drawable.ic_cloak_clock
)

object StealthManager {
    val LOGICS = listOf(
        StealthLogic(
            "CLOCK",
            "Analog Clock",
            "A realistic clock. Move the hands to a secret time and tap the center to unlock.",
            "1. Move the clock hands to your secret time.\n2. Tap the center button to unlock the app.\n3. Hold the center button to open settings.",
            "02:35",
            "12:00",
            "HH:MM",
            R.drawable.ic_cloak_clock
        ),
        StealthLogic(
            "CALCULATOR",
            "Calculator",
            "A fully working scientific calculator. A hidden equation acts as your secret key.",
            "1. Type your secret equation on the calculator.\n2. Press [ = ] to open the app.\n3. Type your settings equation and press [ = ] to open settings.",
            "9999",
            "7777",
            "4-8 Digits",
            R.drawable.ic_cloak_calculator
        ),
        StealthLogic(
            "NOTEPAD",
            "Diary & Notes",
            "A personal diary app. Saving a specific secret word in an entry will unlock the app.",
            "1. Type your secret password into a diary entry (Title or Body).\n2. Press 'Save' to open the app.\n3. Typing your settings password and pressing Save opens settings.",
            "vault",
            "settings",
            "Any word",
            R.drawable.ic_cloak_notepad
        ),
        StealthLogic(
            "RECORDER",
            "Voice Memos",
            "A voice memo recorder. Long pressing the record button a specific number of times unlocks the vault.",
            "1. Long press the RED Record button a specific number of times.\n2. Example: 5 long presses for App, 10 long presses for Settings.",
            "5",
            "10",
            "Long Press Count",
            R.drawable.ic_cloak_recorder
        ),
        StealthLogic(
            "CALENDAR",
            "Day Planner",
            "A daily planner. Picking a secret date acts as a key to open the app.",
            "1. Select your secret date on the planner calendar.\n2. Use a different secret date to open Settings.",
            "01-01",
            "12-25",
            "DD-MM",
            R.drawable.ic_cloak_calendar
        ),
        StealthLogic(
            "WEATHER",
            "Currency",
            "A live currency exchange rate app. Tapping the refresh icon multiple times unlocks the vault.",
            "1. Tap the refresh icon a specific number of times.\n2. Sequence: 5 taps for App, 10 for Settings.",
            "5",
            "10",
            "Tap Count",
            R.drawable.ic_cloak_weather
        ),
        StealthLogic(
            "CONVERTER",
            "Unit Converter",
            "A unit conversion tool. Entering a secret 'magic' number unlocks the app.",
            "1. Enter your secret number in the value box.\n2. Tap the convert button to unlock.",
            "99.9",
            "00.0",
            "Number",
            R.drawable.ic_cloak_converter
        ),
        StealthLogic(
            "GAME",
            "Retro Arcade",
            "Playable games (2048, Retro Snake, Space Defense). Tap the top title 4 times to unlock.",
            "1. Play 2048, Retro Snake, or Space Defense.\n2. Tap the top arcade title 4 times to reveal the unlock prompt.",
            "2048",
            "7777",
            "4 Taps / PIN",
            R.drawable.ic_cloak_game
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
