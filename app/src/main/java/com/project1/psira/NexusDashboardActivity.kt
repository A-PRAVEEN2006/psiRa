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

class NexusDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_nexus_dashboard)
        
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseDatabase.getInstance().getReference("users").child(currentUser.uid).child("banned").get().addOnSuccessListener {
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
        val user = FirebaseAuth.getInstance().currentUser
        tvAgentName.text = "AGENT: ${user?.displayName ?: "Unknown"}"

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
        val tvLogout = findViewById<TextView>(R.id.tvLogout)
        tvLogout.setOnClickListener {
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
            input.hint = "7-Digit Frequency (e.g. 1234567)"
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

            AlertDialog.Builder(this)
                .setTitle("📻 Tune Frequency")
                .setMessage("Enter the 7-digit channel to enter a temporary, anonymous enclave.")
                .setView(input)
                .setPositiveButton("Connect") { _, _ ->
                    val freq = input.text.toString()
                    if (freq.length == 7) {
                        sharedPref.edit().putString("SECURE_CHANNEL", "freq_$freq").apply()
                        startActivity(Intent(this, ChatActivity::class.java))
                    } else {
                        Toast.makeText(this, "Must be 7 digits!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
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