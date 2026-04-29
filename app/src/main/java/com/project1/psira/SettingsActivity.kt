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

        // Theme selector replacing simple switch
        val btnThemeSelection = findViewById<Button>(R.id.btnThemeSelection)
        btnThemeSelection.setOnClickListener { showThemeSelector() }
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