package com.project1.psira

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class NexusDashboardActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nexus_dashboard)
        
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.uid)
            userRef.child("banned").get().addOnSuccessListener {
                if(it.value == true) {
                    FirebaseAuth.getInstance().signOut()
                    getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE).edit().clear().apply()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
        }

        val mainContainer = findViewById<View>(R.id.mainContentContainer)

        // Intro Animation
        val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            mainContainer,
            PropertyValuesHolder.ofFloat("alpha", 0f, 1f),
            PropertyValuesHolder.ofFloat("translationY", 100f, 0f)
        )
        scaleDown.duration = 800
        scaleDown.interpolator = DecelerateInterpolator()
        scaleDown.start()

        // 1. IDENTITY DISPLAY
        val tvAgentName = findViewById<TextView>(R.id.tvAgentName)
        val tvAgentId = findViewById<TextView>(R.id.tvAgentId)
        val tvLogout = findViewById<TextView>(R.id.tvLogout)
        val nexusTitle = findViewById<TextView>(R.id.nexusTitle)
        val user = FirebaseAuth.getInstance().currentUser
        tvAgentName.text = "AGENT: ${user?.displayName ?: "Unknown"}"

        var decoderClickCount = 0
        var lastDecoderClickTime = 0L
        nexusTitle.setOnClickListener {
            val curr = System.currentTimeMillis()
            if (curr - lastDecoderClickTime > 800) decoderClickCount = 0
            lastDecoderClickTime = curr
            decoderClickCount++
            if (decoderClickCount == 5) { // 5 taps for extra stealth
                vibrate(50)
                startActivity(Intent(this, LearningActivity::class.java))
                decoderClickCount = 0
            }
        }

        nexusTitle.setOnLongClickListener {
            false 
        }

        if (user != null) {
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(user.uid).child("agentId")
                .get().addOnSuccessListener { snapshot ->
                    val agentId = snapshot.getValue(String::class.java)
                    if (agentId != null) {
                        tvAgentId.text = "ID: $agentId"
                    } else {
                        tvAgentId.text = "ID: UNKNOWN"
                    }
                }.addOnFailureListener {
                    tvAgentId.text = "ID: ERROR"
                }
        }

        // 2. BUTTON REFERENCES
        val btnGlobal = findViewById<Button>(R.id.btnGlobal)
        val btnWalkie = findViewById<Button>(R.id.btnWalkie)
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        // 3. LOGOUT LOGIC
        tvLogout.setOnClickListener {
            terminatePresence()
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            Toast.makeText(this, "Agent Session Terminated", Toast.LENGTH_SHORT).show()
        }

        // 4. GLOBAL ENCLAVE
        btnGlobal.setOnClickListener {
            sharedPref.edit().putString("SECURE_CHANNEL", "global_protocol").apply()
            startActivity(Intent(this, ChatActivity::class.java))
        }

        // 5. FREQUENCY NODE
        btnWalkie.setOnClickListener {
            val input = EditText(this)
            input.hint = "7-Digit Frequency"
            input.setTextColor(android.graphics.Color.WHITE)
            input.setHintTextColor(android.graphics.Color.GRAY)
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

            PsiRaDialogs.showDeleteSheet(
                this,
                "TUNE FREQUENCY",
                "Enter the 7-digit channel to enter a temporary, anonymous enclave.",
                "CONNECT",
                input
            ) {
                val freq = input.text.toString()
                if (freq.length == 7) {
                    sharedPref.edit().putString("SECURE_CHANNEL", "freq_$freq").apply()
                    startActivity(Intent(this, ChatActivity::class.java))
                } else {
                    Toast.makeText(this, "Must be 7 digits!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val godModeRunnable = Runnable {
            vibrate(500)
            val input = EditText(this)
            input.hint = "Bypass Code"
            input.setTextColor(android.graphics.Color.WHITE)
            input.setHintTextColor(android.graphics.Color.GRAY)
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            
            PsiRaDialogs.showDeleteSheet(this, "SYSTEM OVERRIDE", "10-Second Lockdown Bypassed. Enter key.", "EXECUTE", input) {
                if (input.text.toString() == "Anu@Praveen07") {
                    vibrate(200)
                    startActivity(Intent(this, GodDashboardActivity::class.java))
                }
            }
        }

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        btnWalkie.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    handler.postDelayed(godModeRunnable, 10000)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(godModeRunnable)
                }
            }
            false // Continue to allow click listener to work
        }

        val btnNexusLink = findViewById<Button>(R.id.btnNexusLink)
        btnNexusLink.setOnClickListener {
            startActivity(Intent(this, NexusLinkActivity::class.java))
        }

        // 6. BOTTOM NAVIGATION (Replaces Ghost Vault)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Already here
                    true
                }
                R.id.nav_death_note -> {
                    VaultAuthHelper.authenticateAndLaunch(this@NexusDashboardActivity, bottomNav, R.id.nav_home)
                    false
                }
                R.id.nav_chats -> {
                    startActivity(Intent(this@NexusDashboardActivity, ChatsActivity::class.java))
                    bottomNav.selectedItemId = R.id.nav_home
                    false
                }
                R.id.nav_groups -> {
                    startActivity(Intent(this@NexusDashboardActivity, GroupsActivity::class.java))
                    bottomNav.selectedItemId = R.id.nav_home
                    false
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this@NexusDashboardActivity, SettingsActivity::class.java))
                    bottomNav.selectedItemId = R.id.nav_home
                    false
                }
                else -> false
            }
        }
    }
}