package com.project1.psira

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Base Activity for all PsiRa activities to ensure 100% presence tracking.
 */
open class BaseActivity : AppCompatActivity() {

    companion object {
        var startedActivitiesCount = 0
    }

    private var initialThemeName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val themeName = sharedPref.getString("APP_THEME", "MIDNIGHT") ?: "MIDNIGHT"
        initialThemeName = themeName
        
        when (themeName) {
            "EMERALD" -> setTheme(R.style.Theme_PsiRa_Emerald)
            "RUBY" -> setTheme(R.style.Theme_PsiRa_Ruby)
            "COBALT" -> setTheme(R.style.Theme_PsiRa_Cobalt)
            "OBSIDIAN" -> setTheme(R.style.Theme_PsiRa_Obsidian)
            "DESERT" -> setTheme(R.style.Theme_PsiRa_Desert)
            "GHOST" -> setTheme(R.style.Theme_PsiRa_Ghost)
            "DEEPSEA" -> setTheme(R.style.Theme_PsiRa_DeepSea)
            "NOVA" -> setTheme(R.style.Theme_PsiRa_Nova)
            else -> setTheme(R.style.Theme_PsiRa_Midnight)
        }

        super.onCreate(savedInstanceState)
        
        // Apply saved Dark/Light preference
        val isDarkMode = sharedPref.getBoolean("IS_DARK_MODE", true)
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }

        // Ensure FLAG_SECURE is applied everywhere by default
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        startedActivitiesCount++
        updatePresence(true)
    }

    override fun onResume() {
        super.onResume()
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val currentThemeName = sharedPref.getString("APP_THEME", "MIDNIGHT") ?: "MIDNIGHT"
        if (currentThemeName != initialThemeName) {
            recreate()
        }
    }

    override fun onStop() {
        super.onStop()
        startedActivitiesCount--
        if (startedActivitiesCount <= 0) {
            updatePresence(false)
            startedActivitiesCount = 0
        }
    }


    fun getSmartDestination(context: Context): Class<*> {
        val auth = FirebaseAuth.getInstance()
        val sharedPref = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val autoCount = sharedPref.getInt("AUTO_LOGIN_COUNT", 0)

        return if (auth.currentUser != null && autoCount < 10) {
            NexusDashboardActivity::class.java
        } else {
            LoginActivity::class.java
        }
    }

    private fun updatePresence(isOnline: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        
        if (isOnline) {
            userRef.child("online").onDisconnect().setValue(false)
            userRef.child("online").setValue(true)
        } else {
            userRef.child("online").setValue(false)
        }
    }

    fun terminatePresence() {
        updatePresence(false)
    }

    protected fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val n = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(n) ?: return false
            return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && 
                   cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            return cm.activeNetworkInfo?.isConnected ?: false
        }
    }

    protected fun vibrate(duration: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

}
