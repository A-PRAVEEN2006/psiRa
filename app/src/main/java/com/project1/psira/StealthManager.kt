package com.project1.psira

import android.content.Context

data class StealthLogic(
    val id: String,
    val name: String,
    val description: String,
    val guide: String,
    val defaultPass: String,
    val defaultGateway: String,
    val defaultDecoy: String = "1234",
    val hint: String,
    val iconRes: Int = R.drawable.ic_cloak_clock
)

object StealthManager {
    val LOGICS = listOf(
        StealthLogic(
            "CLOCK",
            "World Clock FX",
            "A specialized world clock utility. Move the hands to a secret time and tap the center to unlock.",
            "1. Move the clock hands to your secret time.\n2. Tap the center button to unlock the app.\n3. Hold the center button to open settings.\n4. Set to decoy time to trigger Decoy Vault.",
            "02:35",
            "12:00",
            "06:30",
            "HH:MM",
            R.drawable.ic_cloak_clock
        ),
        StealthLogic(
            "CALCULATOR",
            "Scientific Calc FX",
            "An advanced scientific calculator. Justifies its presence alongside basic stock calculator.",
            "1. Type your secret equation on the calculator.\n2. Press [ = ] to open the app.\n3. Type your settings equation and press [ = ] to open settings.\n4. Enter decoy equation to open Decoy Vault.",
            "9999",
            "7777",
            "1234",
            "4-8 Digits",
            R.drawable.ic_cloak_calculator
        ),
        StealthLogic(
            "NOTEPAD",
            "Daily Journal Pro",
            "A personal daily journal. Distinct from basic system notes. Saving a secret word unlocks.",
            "1. Type your secret password into a diary entry (Title or Body).\n2. Press 'Save' to open the app.\n3. Typing your settings password and pressing Save opens settings.\n4. Type decoy word to open Decoy Vault.",
            "vault",
            "settings",
            "notes",
            "Any word",
            R.drawable.ic_cloak_notepad
        ),
        StealthLogic(
            "RECORDER",
            "Dictaphone HD",
            "A high-fidelity audio dictaphone tool with visualizer. Does not look like generic voice recorder.",
            "1. Long press the RED Record button a specific number of times.\n2. Example: 5 long presses for App, 10 for Settings, 3 for Decoy.",
            "5",
            "10",
            "3",
            "Long Press Count",
            R.drawable.ic_cloak_recorder
        ),
        StealthLogic(
            "CALENDAR",
            "Habit & Day Planner",
            "A specialized habit and day tracker. Distinct from standard Google/Samsung calendar.",
            "1. Select your secret date on the planner calendar.\n2. Use a different secret date to open Settings.\n3. Select decoy date for Decoy Vault.",
            "01-01",
            "12-25",
            "10-10",
            "DD-MM",
            R.drawable.ic_cloak_calendar
        ),
        StealthLogic(
            "WEATHER",
            "Forex & Currency Monitor",
            "A live foreign exchange rate tracker. Stock phones never include a standalone forex app.",
            "1. Tap the refresh icon a specific number of times.\n2. Sequence: 5 taps for App, 10 for Settings, 3 for Decoy.",
            "5",
            "10",
            "3",
            "Tap Count",
            R.drawable.ic_cloak_weather
        ),
        StealthLogic(
            "CONVERTER",
            "Unit & Metric Converter",
            "A metric and engineering conversion tool. Completely natural non-stock utility.",
            "1. Enter your secret number in the value box.\n2. Tap the convert button to unlock.\n3. Decoy number opens Decoy Vault.",
            "99.9",
            "00.0",
            "50.0",
            "Number",
            R.drawable.ic_cloak_converter
        ),
        StealthLogic(
            "GAME",
            "2048 Puzzle Classic",
            "Fully playable 2048 sliding puzzle game with score tracking. Complete stealth — casual games are never questioned.",
            "1. Play 2048 normally with swipe gestures.\n2. Tap the '2048' title 4 times (or long press it) to open Verification.\n3. Enter your secret passcode to unlock the app (decoy code opens Decoy Notes).",
            "9999",
            "7777",
            "1234",
            "4-8 Digits",
            R.drawable.ic_cloak_game
        ),
        StealthLogic(
            "GHOST",
            "Ghost Mode (Zero Icon)",
            "Completely hides the app icon from launcher & drawer. Launch via dialer (*#*#77472#*#*) or browser.",
            "1. App icon is removed from home screen & drawer.\n2. Open Phone dialer and dial: *#*#77472#*#*\n3. Or open browser and navigate to: psira://open\n4. Zero icon duplicates on phone.",
            "*#*#77472#*#*",
            "*#*#77472#*#*",
            "*#*#1234#*#*",
            "Dialer Code",
            R.drawable.ic_cloak_clock
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

    fun getDecoyPass(context: Context, id: String): String {
        val logic = getLogic(id) ?: return ""
        val prefs = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        return prefs.getString("DECOY_$id", logic.defaultDecoy) ?: logic.defaultDecoy
    }

    fun setDecoyPass(context: Context, id: String, pass: String) {
        context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
            .edit()
            .putString("DECOY_$id", pass)
            .apply()
    }
}
