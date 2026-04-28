package com.project1.psira

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AliasManager {

    private val ALIASES = listOf(
        "LauncherClock",
        "LauncherCalculator",
        "LauncherNotepad",
        "LauncherRecorder",
        "LauncherCompass",
        "LauncherCalendar",
        "LauncherWeather",
        "LauncherConverter",
        "LauncherFlashlight",
        "LauncherRadio"
    )

    fun applyCloak(context: Context, cloakType: String) {
        val pm = context.packageManager
        val packageName = context.packageName

        // Map cloakType to Alias name
        val targetAlias = when (cloakType) {
            "CLOCK" -> "LauncherClock"
            "CALCULATOR" -> "LauncherCalculator"
            "NOTEPAD" -> "LauncherNotepad"
            "RECORDER" -> "LauncherRecorder"
            "COMPASS" -> "LauncherCompass"
            "CALENDAR" -> "LauncherCalendar"
            "WEATHER" -> "LauncherWeather"
            "CONVERTER" -> "LauncherConverter"
            "FLASHLIGHT" -> "LauncherFlashlight"
            "RADIO" -> "LauncherRadio"
            else -> "LauncherClock"
        }

        // Disable all and enable target
        ALIASES.forEach { alias ->
            val state = if (alias == targetAlias) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(packageName, "$packageName.$alias"),
                state,
                PackageManager.DONT_KILL_APP
            )
        }

        // Save selection
        val sharedPref = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().putString("CLOAK_TYPE", cloakType).apply()
    }
}
