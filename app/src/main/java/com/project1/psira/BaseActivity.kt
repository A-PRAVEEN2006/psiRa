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
        var currentActivity: AppCompatActivity? = null

        // Auto-lock: lock app after 3 minutes in background
        private const val AUTO_LOCK_DELAY_MS = 3 * 60 * 1000L
        private var isAppLocked = false
        private val lockHandler = Handler(Looper.getMainLooper())
        private val lockRunnable = Runnable { isAppLocked = true }
        val systemDefaultProxySelector: ProxySelector? = ProxySelector.getDefault()
    }

    private var initialThemeName: String = ""
    private var initialUiStyle: String = ""
    private var sensorManager: android.hardware.SensorManager? = null
    private var accelerometer: android.hardware.Sensor? = null

    private var securityListener: com.google.firebase.database.ValueEventListener? = null
    private var securityUserRef: com.google.firebase.database.DatabaseReference? = null
    private var securityBannedNodeListener: com.google.firebase.database.ValueEventListener? = null
    private var securityBannedNodeRef: com.google.firebase.database.DatabaseReference? = null
    private var securityLockdownListener: com.google.firebase.database.ValueEventListener? = null
    private var securityLockdownRef: com.google.firebase.database.DatabaseReference? = null

    private val flipListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
            if (event == null || event.sensor.type != android.hardware.Sensor.TYPE_ACCELEROMETER) return
            if (this@BaseActivity is DisguiseActivity || this@BaseActivity is LoginActivity) return

            // z < -8.5 m/s^2 indicates phone is placed flat face-down
            val z = event.values[2]
            if (z < -8.5f) {
                triggerInstantStealthLock()
            }
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    private fun triggerInstantStealthLock() {
        val intent = Intent(this, DisguiseActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)

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

        // If the app was locked while in background, force re-authentication or disguise
        if (isAppLocked && this !is LoginActivity && this !is DisguiseActivity) {
            isAppLocked = false
            val isCamouflage = NotificationHelper.isCamouflageEnabled(this)
            val destination = if (isCamouflage) DisguiseActivity::class.java else LoginActivity::class.java
            startActivity(Intent(this, destination).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        // Ensure E2EE keys exist for this device (asynchronously offloaded to prevent ANR)
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
            Thread {
                try {
                    ECDHKeyManager.initializeKeys(this)
                } catch (e: Exception) {
                    android.util.Log.w("BaseActivity", "Key initialization background error: ${e.message}")
                }
            }.start()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        updatePresence(true)
        setupRealtimeSecurity()
    }

    override fun onResume() {
        super.onResume()
        currentActivity = this
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val currentThemeName = sharedPref.getString("APP_THEME", "MIDNIGHT") ?: "MIDNIGHT"
        val currentUiStyle = sharedPref.getString("UI_STYLE", "LIQUID_GLASS") ?: "LIQUID_GLASS"
        if (currentThemeName != initialThemeName || currentUiStyle != initialUiStyle) {
            recreate()
        }

        accelerometer?.let {
            sensorManager?.registerListener(flipListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        if (currentActivity == this) {
            currentActivity = null
        }
        sensorManager?.unregisterListener(flipListener)
    }

    override fun onStop() {
        super.onStop()
        detachRealtimeSecurity()
        startedActivitiesCount--
        if (startedActivitiesCount <= 0) {
            updatePresence(false)
            startedActivitiesCount = 0
            // Clear and start the auto-lock countdown when app goes to background
            lockHandler.removeCallbacks(lockRunnable)
            lockHandler.postDelayed(lockRunnable, AUTO_LOCK_DELAY_MS)
        }
    }

    // ── Security & Realtime Enforcement ───────────────────────────────────────

    private fun setupRealtimeSecurity() {
        if (this is LoginActivity || this is DisguiseActivity) return
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid

        securityUserRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(uid)
        securityListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (isFinishing || isDestroyed) return

                // 0. Check if account was deleted by Commander
                val isDeleted = snapshot.child("account_deleted").getValue(Boolean::class.java) == true
                if (isDeleted) {
                    executeAccountDeletion("Your account was deleted by Network Command.")
                    return
                }

                // 1. Check if account is banned
                val isBanned = snapshot.child("banned").getValue(Boolean::class.java) == true
                if (isBanned) {
                    val reason = snapshot.child("ban_reason").getValue(String::class.java) ?: "PROTOCOL SECURITY TERMINATION"
                    triggerAccountBan(reason)
                    return
                }

                // 2. Check if remote vault wipe was triggered by Commander
                val purgeVault = snapshot.child("purge_vault").getValue(Boolean::class.java) == true
                if (purgeVault) {
                    securityUserRef?.child("purge_vault")?.removeValue()
                    VaultAuthHelper.wipeLocalVault(this@BaseActivity)
                    Toast.makeText(this@BaseActivity, "🚨 REMOTE PROTOCOL: Secure Vault Purged by Command", Toast.LENGTH_LONG).show()
                }

                // 3. Check for Commander direct advisory / alert
                val alert = snapshot.child("commander_alert").getValue(String::class.java)
                if (!alert.isNullOrBlank()) {
                    securityUserRef?.child("commander_alert")?.removeValue()
                    androidx.appcompat.app.AlertDialog.Builder(this@BaseActivity)
                        .setTitle("⚠️ NETWORK COMMAND DISPATCH")
                        .setMessage(alert)
                        .setCancelable(false)
                        .setPositiveButton("ACKNOWLEDGE", null)
                        .show()
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        securityUserRef?.addValueEventListener(securityListener!!)

        // Redundant check against global banned_nodes registry
        securityBannedNodeRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("banned_nodes").child(uid)
        securityBannedNodeListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (isFinishing || isDestroyed) return
                if (snapshot.exists()) {
                    val isDeleted = snapshot.child("deleted").getValue(Boolean::class.java) == true
                    if (isDeleted) {
                        executeAccountDeletion("Your account was deleted by Network Command.")
                        return
                    }
                    val isBanned = snapshot.child("banned").getValue(Boolean::class.java) ?: true
                    if (isBanned) {
                        val reason = snapshot.child("reason").getValue(String::class.java) ?: "NODE ACCESS REVOKED BY COMMAND"
                        triggerAccountBan(reason)
                    }
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        securityBannedNodeRef?.addValueEventListener(securityBannedNodeListener!!)

        // Check emergency network lockdown
        securityLockdownRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("network_status").child("lockdown")
        securityLockdownListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (isFinishing || isDestroyed) return
                val isLockdown = snapshot.getValue(Boolean::class.java) == true
                val isCommander = user.email?.equals("anbuselvan1613@gmail.com", ignoreCase = true) == true
                if (isLockdown && !isCommander) {
                    androidx.appcompat.app.AlertDialog.Builder(this@BaseActivity)
                        .setTitle("🚨 ENCLAVE UNDER LOCKDOWN")
                        .setMessage("High Command has initiated emergency network maintenance. Tactical communications are temporarily frozen. Standby for broadcast.")
                        .setCancelable(false)
                        .setPositiveButton("STANDBY") { _, _ -> finishAffinity() }
                        .show()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        securityLockdownRef?.addValueEventListener(securityLockdownListener!!)
    }

    private fun detachRealtimeSecurity() {
        securityListener?.let { listener ->
            securityUserRef?.removeEventListener(listener)
        }
        securityListener = null
        securityUserRef = null

        securityBannedNodeListener?.let { listener ->
            securityBannedNodeRef?.removeEventListener(listener)
        }
        securityBannedNodeListener = null
        securityBannedNodeRef = null

        securityLockdownListener?.let { listener ->
            securityLockdownRef?.removeEventListener(listener)
        }
        securityLockdownListener = null
        securityLockdownRef = null
    }

    private fun triggerAccountBan(reason: String) {
        val intent = Intent(this, AccountBannedActivity::class.java).apply {
            putExtra("BAN_REASON", reason)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finishAffinity()
    }

    private fun executeAccountDeletion(reason: String) {
        try {
            VaultAuthHelper.wipeLocalVault(this)
            getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("PsiRa_Presence", Context.MODE_PRIVATE).edit().clear().apply()
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            user?.delete()?.addOnCompleteListener {
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                } catch (_: Exception) {}
            } ?: run {
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        val intent = Intent(this, LoginActivity::class.java).apply {
            putExtra("FORCE_LOGOUT_REASON", reason)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finishAffinity()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun applyTheme() {
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val uiStyle = sharedPref.getString("UI_STYLE", "LIQUID_GLASS") ?: "LIQUID_GLASS"
        val themeName = sharedPref.getString("APP_THEME", "MIDNIGHT") ?: "MIDNIGHT"
        initialThemeName = themeName
        initialUiStyle = uiStyle

        when (uiStyle) {
            "LIQUID_GLASS", "GLASS" -> setTheme(R.style.Theme_PsiRa_LiquidGlass)
            "CYBER", "CYBER_TACTICAL" -> setTheme(R.style.Theme_PsiRa_CyberTactical)
            "OLED", "OLED_STEALTH" -> setTheme(R.style.Theme_PsiRa_OledStealth)
            "MERCURY", "MERCURY_CHROME" -> setTheme(R.style.Theme_PsiRa_MercuryChrome)
            "CRIMSON", "NEON_CRIMSON" -> setTheme(R.style.Theme_PsiRa_CrimsonVoid)
            "COSMOS", "DEEP_COSMOS" -> setTheme(R.style.Theme_PsiRa_DeepCosmos)
            else -> {
                when (themeName) {
                    "EMERALD"     -> setTheme(R.style.Theme_PsiRa_Emerald)
                    "RUBY"        -> setTheme(R.style.Theme_PsiRa_Ruby)
                    "COBALT"      -> setTheme(R.style.Theme_PsiRa_Cobalt)
                    "OBSIDIAN"    -> setTheme(R.style.Theme_PsiRa_Obsidian)
                    "DESERT"      -> setTheme(R.style.Theme_PsiRa_Desert)
                    "GHOST"       -> setTheme(R.style.Theme_PsiRa_Ghost)
                    "DEEPSEA"     -> setTheme(R.style.Theme_PsiRa_DeepSea)
                    "NOVA"        -> setTheme(R.style.Theme_PsiRa_Nova)
                    "COSMOS", "DEEP_COSMOS" -> setTheme(R.style.Theme_PsiRa_DeepCosmos)
                    "CUSTOM"      -> setTheme(R.style.Theme_PsiRa_Custom)
                    else          -> setTheme(R.style.Theme_PsiRa_Midnight)
                }
            }
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
     * Restores system proxy configuration cleanly when disabled.
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
                        systemDefaultProxySelector?.select(uri) ?: Collections.singletonList(Proxy.NO_PROXY)
                    } else {
                        Collections.singletonList(torProxy)
                    }
                }
                override fun connectFailed(uri: URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {
                    systemDefaultProxySelector?.connectFailed(uri, sa, ioe)
                }
            })
        } else {
            // Restore actual system default selector (preserving VPN/corporate configs)
            ProxySelector.setDefault(systemDefaultProxySelector)
        }
    }
}
