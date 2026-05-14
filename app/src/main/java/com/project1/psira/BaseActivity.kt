package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.os.Vibrator
import android.os.VibrationEffect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.net.Proxy
import java.net.ProxySelector
import java.net.InetSocketAddress
import java.net.URI
import java.util.Collections

/**
 * Base Activity for all PsiRa activities.
 * Enforces: FLAG_SECURE, auto-lock, root detection, presence tracking.
 */
open class BaseActivity : AppCompatActivity() {

    companion object {
        var startedActivitiesCount = 0

        // Auto-lock: lock app after 3 minutes in background
        private const val AUTO_LOCK_DELAY_MS = 3 * 60 * 1000L
        private var isAppLocked = false
        private val lockHandler = Handler(Looper.getMainLooper())
        private val lockRunnable = Runnable { isAppLocked = true }
    }

    private var initialThemeName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)

        // Apply dark/light preference
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("IS_DARK_MODE", true)
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            )
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // Prevent screenshots and screen recording on all screens
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // Root detection — warn if device is rooted
        if (isDeviceRooted()) {
            Toast.makeText(
                this,
                "⚠ Security Warning: Rooted device detected. Comms may be compromised.",
                Toast.LENGTH_LONG
            ).show()
        }

        // Apply global TOR proxy settings based on preference
        val isTorOn = sharedPref.getBoolean("TOR_MODE", false)
        applyTorProxy(isTorOn)
    }

    override fun onStart() {
        super.onStart()
        startedActivitiesCount++

        // Cancel any pending lock when coming back to foreground
        lockHandler.removeCallbacks(lockRunnable)

        // If the app was locked while in background, force re-authentication
        if (isAppLocked && this !is LoginActivity && this !is DisguiseActivity) {
            isAppLocked = false
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            return
        }

        // Ensure E2EE keys exist for this device (idempotent — safe to call every time)
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
            ECDHKeyManager.initializeKeys(this)
        }

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
            // Start the auto-lock countdown when app goes to background
            lockHandler.postDelayed(lockRunnable, AUTO_LOCK_DELAY_MS)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun applyTheme() {
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val themeName = sharedPref.getString("APP_THEME", "MIDNIGHT") ?: "MIDNIGHT"
        initialThemeName = themeName
        when (themeName) {
            "EMERALD"  -> setTheme(R.style.Theme_PsiRa_Emerald)
            "RUBY"     -> setTheme(R.style.Theme_PsiRa_Ruby)
            "COBALT"   -> setTheme(R.style.Theme_PsiRa_Cobalt)
            "OBSIDIAN" -> setTheme(R.style.Theme_PsiRa_Obsidian)
            "DESERT"   -> setTheme(R.style.Theme_PsiRa_Desert)
            "GHOST"    -> setTheme(R.style.Theme_PsiRa_Ghost)
            "DEEPSEA"  -> setTheme(R.style.Theme_PsiRa_DeepSea)
            "NOVA"     -> setTheme(R.style.Theme_PsiRa_Nova)
            else       -> setTheme(R.style.Theme_PsiRa_Midnight)
        }
    }

    /**
     * Checks common signs of a rooted device.
     * Not foolproof but catches most cases.
     */
    private fun isDeviceRooted(): Boolean {
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su", "/su/bin/su"
        )
        if (rootPaths.any { File(it).exists() }) return true

        // Try running su (will throw on non-rooted)
        return try {
            Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            true
        } catch (e: Exception) {
            false
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val n = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(n) ?: return false
            cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected ?: false
        }
    }

    protected fun vibrate(duration: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
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

    /**
     * Registers a global ProxySelector that routes all JVM TCP through TOR's SOCKS5 proxy.
     * This affects all network calls in the app (Firebase, URLConnection, etc).
     */
    protected fun applyTorProxy(enable: Boolean) {
        if (enable) {
            val torAddr = InetSocketAddress("127.0.0.1", 9050)
            val torProxy = Proxy(Proxy.Type.SOCKS, torAddr)
            ProxySelector.setDefault(object : ProxySelector() {
                override fun select(uri: URI?): MutableList<Proxy> {
                    // Route everything except localhost through TOR
                    val host = uri?.host ?: ""
                    return if (host.contains("127.0.0.1") || host.contains("localhost")) {
                        Collections.singletonList(Proxy.NO_PROXY)
                    } else {
                        Collections.singletonList(torProxy)
                    }
                }
                override fun connectFailed(uri: URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {}
            })
        } else {
            // Restore default direct-connection selector
            ProxySelector.setDefault(object : ProxySelector() {
                override fun select(uri: URI?) = Collections.singletonList(Proxy.NO_PROXY)
                override fun connectFailed(uri: URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {}
            })
        }
    }
}
