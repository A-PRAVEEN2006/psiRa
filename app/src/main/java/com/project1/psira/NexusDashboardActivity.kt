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

        // 2. LOGOUT LOGIC
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        // 3. LOGOUT LOGIC
        val tvLogout = findViewById<TextView>(R.id.tvLogout)
        tvLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            Toast.makeText(this, "Agent Session Terminated", Toast.LENGTH_SHORT).show()
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
                    // Biometric Authentication for Private Vault
                    val executor = ContextCompat.getMainExecutor(this)
                    val biometricPrompt = BiometricPrompt(this, executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                super.onAuthenticationError(errorCode, errString)
                                Toast.makeText(applicationContext, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                                bottomNav.selectedItemId = R.id.nav_home
                            }

                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                super.onAuthenticationSucceeded(result)
                                val userId = user?.uid ?: "anonymous"
                                sharedPref.edit().putString("SECURE_CHANNEL", "private_$userId").apply()
                                startActivity(Intent(this@NexusDashboardActivity, ChatActivity::class.java))
                                bottomNav.selectedItemId = R.id.nav_home
                            }

                            override fun onAuthenticationFailed() {
                                super.onAuthenticationFailed()
                                Toast.makeText(applicationContext, "Auth Failed", Toast.LENGTH_SHORT).show()
                                bottomNav.selectedItemId = R.id.nav_home
                            }
                        })

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Vault Biometric Lock")
                        .setSubtitle("Authenticate to access the Secure Vault")
                        .setNegativeButtonText("Cancel")
                        .build()

                    biometricPrompt.authenticate(promptInfo)
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