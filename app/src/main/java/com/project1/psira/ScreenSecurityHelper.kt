package com.project1.psira

import android.app.Activity
import android.content.Context
import android.view.WindowManager

object ScreenSecurityHelper {
    const val PREF_KEY_ALLOW_SCREENSHOTS = "ALLOW_SCREENSHOTS"

    fun isScreenshotsAllowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_KEY_ALLOW_SCREENSHOTS, false)
    }

    fun setScreenshotsAllowed(context: Context, allowed: Boolean) {
        val prefs = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_KEY_ALLOW_SCREENSHOTS, allowed).apply()
    }

    fun apply(activity: Activity) {
        if (isScreenshotsAllowed(activity)) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }
}
