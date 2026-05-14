package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.google.firebase.database.FirebaseDatabase

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_settings)

        val btnConfigureCloak = findViewById<Button>(R.id.btnConfigureCloak)
        val btnSecurityFamily = findViewById<Button>(R.id.btnSecurityFamily)
        val btnChangeName = findViewById<Button>(R.id.btnChangeName)
        val btnSystemFamily = findViewById<Button>(R.id.btnSystemFamily)

        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        // 1. STEALTH MASK - SEPARATE
        btnConfigureCloak.setOnClickListener { showMaskSelector() }

        // 2. SECURITY SETTINGS
        btnSecurityFamily.setOnClickListener {
            val options = listOf(
                "Change Main App Password",
                "Configure Disguise Codes",
                "Set Self-Destruct Code",
                "Enable Double Protection"
            )
            PsiRaDialogs.showOptionsSheet(this, "SECURITY SETTINGS", options) { index ->
                when (index) {
                    0 -> showStealthPasscodeEditor()
                    1 -> showLogicConfigSelector()
                    2 -> showPanicCodeEditor()
                    3 -> toggleDoubleLayer()
                }
            }
        }

        // 3. PROFILE SETTINGS
        btnChangeName.setOnClickListener {
            val input = EditText(this)
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            input.setText(user?.displayName ?: "")
            input.hint = "Your Name"
            input.setTextColor(android.graphics.Color.WHITE)
            PsiRaDialogs.showDeleteSheet(this, "PROFILE SETTINGS", "Change your public name in the chat.", "SAVE", input) {
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val profileUpdates = com.google.firebase.auth.userProfileChangeRequest { displayName = newName }
                    user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                        FirebaseDatabase.getInstance().getReference("users").child(user!!.uid).child("name").setValue(newName)
                        Toast.makeText(this, "Name Updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 4. GENERAL SETTINGS
        btnSystemFamily.setOnClickListener {
            val options = listOf(
                "Clear Local Data",
                "About This App",
                "How to use Disguises",
                "Report an Agent"
            )
            PsiRaDialogs.showOptionsSheet(this, "SYSTEM SETTINGS", options) { index ->
                when (index) {
                    0 -> wipeCache()
                    1 -> showAbout()
                    2 -> showDecoyBriefing()
                    3 -> showReportAgentDialog()
                }
            }
        }

        val btnThemeSelection = findViewById<Button>(R.id.btnThemeSelection)
        btnThemeSelection.setOnClickListener { showThemeSelector() }

        // ── TOR Toggle ────────────────────────────────────────────────────────
        val switchTor   = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchTorMode)
        val tvTorStatus = findViewById<android.widget.TextView>(R.id.tvTorStatus)
        val layoutOff   = findViewById<android.view.View>(R.id.layoutTorOff)
        val layoutOn    = findViewById<android.view.View>(R.id.layoutTorOn)

        val isTorOn = sharedPref.getBoolean("TOR_MODE", false)
        switchTor.isChecked = isTorOn
        updateTorUi(tvTorStatus, layoutOff, layoutOn, isTorOn)

        switchTor.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("TOR_MODE", isChecked).apply()
            updateTorUi(tvTorStatus, layoutOff, layoutOn, isChecked)
            applyTorProxy(isChecked)

            val msg = if (isChecked)
                "🧅 TOR Mode ON — Your IP is now hidden. Speed reduced ~3×."
            else
                "⚡ Direct Mode ON — Full speed. E2EE still active."
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        // Verify TOR button
        val btnVerify = findViewById<Button>(R.id.btnVerifyTor)
        btnVerify.setOnClickListener {
            btnVerify.isEnabled = false
            btnVerify.text = "⏳ CHECKING..."
            verifyTorConnection { result ->
                runOnUiThread {
                    btnVerify.isEnabled = true
                    btnVerify.text = "🔍 VERIFY TOR CONNECTION"
                    showTorVerifyResult(result)
                }
            }
        }
    }

    private fun updateTorUi(
        tvStatus: android.widget.TextView,
        layoutOff: android.view.View,
        layoutOn: android.view.View,
        torEnabled: Boolean
    ) {
        if (torEnabled) {
            tvStatus.text  = "🧅 ON — Routing through TOR (E2EE + Anonymous)"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#CE93D8"))
            layoutOn.alpha  = 1.0f
            layoutOff.alpha = 0.4f
        } else {
            tvStatus.text  = "● OFF — Direct connection (E2EE active)"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#88AA99"))
            layoutOn.alpha  = 0.4f
            layoutOff.alpha = 1.0f
        }
    }

    /**
     * Checks if a SOCKS5 proxy is reachable at 127.0.0.1:9050, then connects through
     * it to check.torproject.org using an EXPLICIT proxy (Android ignores system properties).
     */
    private fun verifyTorConnection(callback: (TorVerifyResult) -> Unit) {
        val isTorModeOn = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
            .getBoolean("TOR_MODE", false)

        Thread {
            // Step 1: Check if a SOCKS5 server is actually running at 127.0.0.1:9050
            val isDaemonRunning = try {
                val sock = java.net.Socket()
                sock.connect(java.net.InetSocketAddress("127.0.0.1", 9050), 2_000)
                sock.close()
                true
            } catch (_: Exception) { false }

            if (isTorModeOn && !isDaemonRunning) {
                // TOR mode is ON but no daemon is running
                callback(TorVerifyResult(
                    isTor    = false,
                    ip       = "N/A",
                    country  = "??",
                    error    = "NO_DAEMON"
                ))
                return@Thread
            }

            // Step 2: Make the request — through explicit SOCKS5 proxy if TOR is ON
            try {
                val torProxy = java.net.Proxy(
                    java.net.Proxy.Type.SOCKS,
                    java.net.InetSocketAddress("127.0.0.1", 9050)
                )
                val url  = java.net.URL("https://check.torproject.org/api/ip")
                // IMPORTANT: openConnection(proxy) — Android ignores System.setProperty for proxy
                val conn = (if (isTorModeOn && isDaemonRunning)
                    url.openConnection(torProxy)
                else
                    url.openConnection()) as java.net.HttpURLConnection

                conn.connectTimeout = 20_000
                conn.readTimeout    = 20_000
                conn.requestMethod  = "GET"

                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                // Response: {"IsTor":true,"IP":"185.220.101.x"}
                val isTor = response.contains("\"IsTor\":true")
                val ip    = Regex("\"IP\":\"([^\"]+)\"").find(response)
                    ?.groupValues?.get(1) ?: "Unknown"

                // Step 3: Get country for the exit node IP
                val countryUrl  = java.net.URL("https://ipinfo.io/$ip/country")
                val countryConn = (if (isTorModeOn && isDaemonRunning)
                    countryUrl.openConnection(torProxy)
                else
                    countryUrl.openConnection()) as java.net.HttpURLConnection
                countryConn.connectTimeout = 5_000
                countryConn.readTimeout    = 5_000
                val country = try {
                    countryConn.inputStream.bufferedReader().readText().trim()
                } catch (_: Exception) { "??" }
                countryConn.disconnect()

                callback(TorVerifyResult(isTor = isTor, ip = ip, country = country, error = null))
            } catch (e: Exception) {
                callback(TorVerifyResult(isTor = false, ip = "N/A", country = "??", error = e.message))
            }
        }.start()
    }

    data class TorVerifyResult(
        val isTor: Boolean,
        val ip: String,
        val country: String,
        val error: String?
    )

    private fun showTorVerifyResult(result: TorVerifyResult) {
        val (title, message, color) = when {
            result.error == "NO_DAEMON" -> Triple(
                "🧅 TOR MODE ON — DAEMON MISSING",
                "TOR mode is enabled but no TOR service is running on this device.\n\n" +
                "To activate TOR anonymization:\n" +
                "1. Install Orbot (from GitHub or F-Droid)\n" +
                "2. Open Orbot and tap START\n" +
                "3. Return here and tap VERIFY again\n\n" +
                "✅ Your E2EE encryption is still fully active.\n" +
                "❌ Your IP address is currently visible.",
                "#FF9800"
            )
            result.error != null -> Triple(
                "⚠ CHECK FAILED",
                "Could not reach check.torproject.org.\n\nError: ${result.error}\n\n" +
                "Check your internet connection and try again.",
                "#FF9800"
            )
            result.isTor -> Triple(
                "✅ TOR CONFIRMED — YOU ARE ANONYMOUS",
                "🧅 Your traffic is routed through TOR.\n\n" +
                "Exit Node IP :  ${result.ip}\n" +
                "Exit Country :  ${result.country}\n\n" +
                "This IP is NOT your real address.\n" +
                "Network observers cannot trace you back.\n" +
                "Messages are protected by E2EE on top of TOR.",
                "#44FF88"
            )
            else -> Triple(
                "⚡ DIRECT CONNECTION",
                "Your traffic is going directly to the internet.\n\n" +
                "Current IP :  ${result.ip}\n" +
                "Country    :  ${result.country}\n\n" +
                "Enable TOR mode in the toggle above to hide your IP.\n" +
                "Your messages are still protected by E2EE.",
                "#CE93D8"
            )
        }

        val tv = android.widget.TextView(this).apply {
            text     = message
            setPadding(48, 32, 48, 16)
            setTextColor(android.graphics.Color.parseColor(color))
            textSize = 13f
            setLineSpacing(4f, 1f)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(tv)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showMaskSelector() {
        val options = StealthManager.LOGICS.map { it.name }
        PsiRaDialogs.showOptionsSheet(this, "SELECT DISGUISE", options) { index ->
            val logic = StealthManager.LOGICS[index]
            AliasManager.applyCloak(this, logic.id)
            Toast.makeText(this, "App Disguise set to: ${logic.name}. Icon will update on home screen.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showLogicConfigSelector() {
        val options = StealthManager.LOGICS.map { "Config: ${it.name}" }
        PsiRaDialogs.showOptionsSheet(this, "CONFIGURE LOGIC", options) { index ->
            val intent = Intent(this, StealthDetailActivity::class.java)
            intent.putExtra("LOGIC_ID", StealthManager.LOGICS[index].id)
            startActivity(intent)
        }
    }

    private fun showStealthPasscodeEditor() {
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val input = EditText(this)
        input.hint = "New Password"
        input.setText(sharedPref.getString("VAULT_PASS", "unlockpsira"))
        input.setTextColor(android.graphics.Color.WHITE)
        PsiRaDialogs.showDeleteSheet(this, "APP PASSWORD", "Set the code to unlock the app from your disguise.", "SAVE", input) {
            val v = input.text.toString().trim()
            if (v.isNotEmpty()) {
                sharedPref.edit {
                    putString("VAULT_PASS", v)
                    putString("PASS_CALCULATOR", v)
                    putBoolean("CALC_PASS_SET", true)
                }
                Toast.makeText(this, "Password saved.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPanicCodeEditor() {
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val input = EditText(this)
        input.hint = "4-Digit PIN"
        input.setText(sharedPref.getString("PANIC_PASSCODE", ""))
        input.setTextColor(android.graphics.Color.WHITE)
        PsiRaDialogs.showDeleteSheet(this, "SELF-DESTRUCT CODE", "If you enter this code into any disguise, all your local data will be deleted.", "SAVE", input) {
            val v = input.text.toString().trim()
            if (v.isNotEmpty()) { sharedPref.edit { putString("PANIC_PASSCODE", v) }; Toast.makeText(this, "Self-Destruct PIN set!", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun toggleDoubleLayer() {
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val current = sharedPref.getBoolean("DOUBLE_LAYER", false)
        sharedPref.edit { putBoolean("DOUBLE_LAYER", !current) }
        Toast.makeText(this, "Double Layer Logic: ${if (!current) "Armed" else "Standby"}", Toast.LENGTH_SHORT).show()
    }

    private fun wipeCache() {
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        PsiRaDialogs.showDeleteSheet(this, "DELETE DATA?", "This will delete all your local secrets. Your chat history in the cloud will be safe.", "DELETE") {
            sharedPref.edit { clear() }
            Toast.makeText(this, "Data Deleted.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showReportAgentDialog() {
        val input = EditText(this)
        input.hint = "Agent ID (e.g. 12345)"
        input.setTextColor(android.graphics.Color.WHITE)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        PsiRaDialogs.showDeleteSheet(this, "REPORT AGENT", "Enter the Agent ID of the user breaking rules.", "NEXT", input) {
            val agentId = input.text.toString().trim()
            if (agentId.isNotEmpty()) {
                val reasonInput = EditText(this)
                reasonInput.hint = "Reason"
                reasonInput.setTextColor(android.graphics.Color.WHITE)
                PsiRaDialogs.showDeleteSheet(this, "REPORT REASON", "Why are you reporting User $agentId?", "SUBMIT", reasonInput) {
                    val reason = reasonInput.text.toString().trim()
                    
                    // Fetch last 5 messages for context
                    FirebaseDatabase.getInstance().getReference("messages").child("global_chat")
                        .limitToLast(5).get().addOnSuccessListener { snapshot ->
                            val messages = snapshot.children.mapNotNull { 
                                it.child("sender").getValue(String::class.java) + ": " + it.child("content").getValue(String::class.java)
                            }
                            
                            val repMap = mapOf(
                                "reportedUserId" to agentId, 
                                "reason" to reason, 
                                "timestamp" to System.currentTimeMillis(),
                                "lastMessages" to messages
                            )
                            
                            FirebaseDatabase.getInstance().getReference("reports").push().setValue(repMap).addOnSuccessListener {
                                Toast.makeText(this, "Report sent to the Admin Panel.", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }
        }
    }

    private fun showStealthGuide() {
        val intent = Intent(this, LearningActivity::class.java)
        startActivity(intent)
    }

    private fun showAbout() {
        val about = """
            PsiRa | Overseer Build v1.5
            ----------------------------
            THE SPECTRE ENCLAVE:
            A premium, high-fidelity secure communication network.
            
            10-MODULE DECOY SYSTEM:
            Active Camouflage Disguises including functional Clock, Calculator, FM Radio, and Weather modules. Each decoy operates as a legitimate utility while masking the entrance to the Spectre Enclave.
            
            SECURITY PROTOCOLS:
            • 10-Session Logic Guard
            • AES-256 Vector Encryption
            • Self-Destruct 'EMP' Trigger
            • Biometric Vault Isolation
            • Zero-Footprint Registry
            
            PsiRa is built for those who operate in the shadows of the digital matrix.
        """.trimIndent()
        PsiRaDialogs.showDeleteSheet(this, "ABOUT THIS APP", about, "CLOSE") {}
    }

    private fun showDecoyBriefing() {
        val guide = """
            DECOY TRIGGER LOGICS:
            
            1. CLOCK: Set the hands exactly to 10:10.
            2. CALCULATOR: Long-Press the '=' button.
            3. NOTEPAD: Double-Tap the 'NOTES' title.
            4. RECORDER: Long-Press the 'Red' button.
            5. COMPASS: Tap any direction for vectors.
            6. CALENDAR: Long-Press the calendar area.
            7. WEATHER: Double-Tap the City Name.
            8. CONVERTER: Long-Press the 'Convert' icon.
            9. FLASHLIGHT: Long-Press the physical switch.
            10. FM RADIO: Tune exactly to 107.7 FM.
            
            BYPASS LIMIT: After 10 auto-entries, the disguise will force a manual password verification.
        """.trimIndent()
        PsiRaDialogs.showDeleteSheet(this, "DECOY BRIEFING", guide, "UNDERSTOOD") {}
    }

    private fun showThemeSelector() {
        val options = listOf(
            "Midnight & Gold", 
            "Emerald Shadow", 
            "Ruby Protocol", 
            "Cobalt Sky", 
            "Obsidian Pulse", 
            "Desert Storm", 
            "Ghost Protocol", 
            "Deep Sea (Aqua)", 
            "Nova Flare", 
            "Classic Light"
        )
        PsiRaDialogs.showOptionsSheet(this, "COLOR THEMES", options) { index ->
            val themeName = when (index) {
                0 -> "MIDNIGHT"
                1 -> "EMERALD"
                2 -> "RUBY"
                3 -> "COBALT"
                4 -> "OBSIDIAN"
                5 -> "DESERT"
                6 -> "GHOST"
                7 -> "DEEPSEA"
                8 -> "NOVA"
                9 -> "LIGHT"
                else -> "MIDNIGHT"
            }
            getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE).edit {
                putString("APP_THEME", themeName)
                putBoolean("IS_DARK_MODE", themeName != "LIGHT")
            }
            Toast.makeText(this, "Theme set to: $themeName", Toast.LENGTH_SHORT).show()
            recreate()
        }
    }
}