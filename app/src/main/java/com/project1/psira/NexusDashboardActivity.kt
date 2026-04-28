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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class NexusDashboardActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nexus_dashboard)
        
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            ensureAgentId(currentUser.uid) {}
            
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.uid)
            userRef.child("banned").get().addOnSuccessListener {
                if(it.value == true) {
                    FirebaseAuth.getInstance().signOut()
                    // Partial clear: Preserve identity
                    val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
                    val cachedId = sharedPref.getString("AGENT_ID_LOCAL", "")
                    sharedPref.edit().clear().apply()
                    sharedPref.edit().putString("AGENT_ID_LOCAL", cachedId).apply()
                    
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
        tvAgentName.text = "User: ${user?.displayName ?: "Unknown"}"

        var titleTapCount = 0
        var lastTitleTapTime = 0L
        nexusTitle.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTitleTapTime > 1500) {
                titleTapCount = 0
            }
            lastTitleTapTime = now
            titleTapCount++

            if (titleTapCount >= 3) { // Let's make it 3 for easier access
                titleTapCount = 0
                vibrate(100)
                startActivity(Intent(this, LearningActivity::class.java))
            } else {
               // Optional invisible toast if we want, but better keep secret
            }
        }

        // Immediate local ID display (Instant Identity)
        val initialId = sharedPref.getString("AGENT_ID_LOCAL", "Loading...")
        tvAgentId.text = "User ID: $initialId"

        if (user != null) {
            val agentRef = FirebaseDatabase.getInstance().getReference("users").child(user.uid).child("agentId")
            agentRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val agentId = snapshot.value?.toString()
                    if (agentId != null) {
                        tvAgentId.text = "User ID: $agentId"
                        sharedPref.edit().putString("AGENT_ID_LOCAL", agentId).apply()
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    // Fallback to local if Firebase fails
                    val cached = sharedPref.getString("AGENT_ID_LOCAL", "OFFLINE")
                    tvAgentId.text = "User ID: $cached"
                }
            })
        }

        // 2. BUTTON REFERENCES
        val btnGlobal = findViewById<Button>(R.id.btnGlobal)
        val btnWalkie = findViewById<Button>(R.id.btnWalkie)
        val btnNexusLink = findViewById<Button>(R.id.btnNexusLink)

        // 3. LOGOUT LOGIC
        tvLogout.setOnClickListener {
            terminatePresence()
            FirebaseAuth.getInstance().signOut()
            
            // Partial clear: Keep ID for offline recovery next time
            val cachedId = sharedPref.getString("AGENT_ID_LOCAL", "")
            sharedPref.edit().clear().apply()
            sharedPref.edit().putString("AGENT_ID_LOCAL", cachedId).apply()
            
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            Toast.makeText(this, "Session Terminated. Local ID Preserved.", Toast.LENGTH_SHORT).show()
        }

        // 4. GLOBAL CHAT
        btnGlobal.setOnClickListener {
            if (isNetworkAvailable()) {
                sharedPref.edit().putString("SECURE_CHANNEL", "global_protocol").apply()
                startActivity(Intent(this, ChatActivity::class.java))
            } else {
                Toast.makeText(this, "⚠ Global signal blocked by interference (Offline).", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. PRIVATE CHANNEL
        btnWalkie.setOnClickListener {
            val input = EditText(this)
            input.hint = "7-Digit Frequency"
            input.setTextColor(android.graphics.Color.WHITE)
            input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

            PsiRaDialogs.showDeleteSheet(this, "JOIN CHANNEL", "Enter 7-digit number for private mesh.", "JOIN", input) {
                val freq = input.text.toString()
                if (freq.length == 7) {
                    sharedPref.edit().putString("SECURE_CHANNEL", "freq_$freq").apply()
                    startActivity(Intent(this, ChatActivity::class.java))
                } else {
                    Toast.makeText(this, "Must be 7 digits!", Toast.LENGTH_SHORT).show()
                }
            }
        }



        val btnBluetooth = findViewById<Button>(R.id.btnBluetooth)
        val btnWifiMesh = findViewById<Button>(R.id.btnWifiMesh)

        btnBluetooth.setOnClickListener {
            startActivity(Intent(this, BluetoothChatActivity::class.java))
        }

        btnWifiMesh.setOnClickListener {
            startActivity(Intent(this, WifiDirectChatActivity::class.java))
        }

        btnNexusLink.setOnClickListener {
            startActivity(Intent(this, NexusLinkActivity::class.java))
        }

        // 6. BOTTOM NAVIGATION
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_death_note -> {
                    VaultAuthHelper.authenticateAndLaunch(this, bottomNav, R.id.nav_home)
                    false
                }
                R.id.nav_chats -> {
                    startActivity(Intent(this, ChatsActivity::class.java))
                    false
                }
                R.id.nav_groups -> {
                    startActivity(Intent(this, GroupsActivity::class.java))
                    false
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val tvStatus = findViewById<TextView>(R.id.tvStatusIndicator)
        val btnGlobal = findViewById<Button>(R.id.btnGlobal)
        val btnWalkie = findViewById<Button>(R.id.btnWalkie)
        val btnBluetooth = findViewById<Button>(R.id.btnBluetooth)
        val btnWifiMesh = findViewById<Button>(R.id.btnWifiMesh)
        val nexusTitle = findViewById<TextView>(R.id.nexusTitle)
        
        if (!isNetworkAvailable()) {
            tvStatus.text = "⚠ SIGNAL JAMMED | OFFLINE MODE"
            tvStatus.setTextColor(android.graphics.Color.YELLOW)
            nexusTitle.setTextColor(android.graphics.Color.RED)
            
            btnGlobal.text = "📡 CLOUD ENCLAVE (SIGNAL LOST)"
            btnGlobal.alpha = 0.3f
            
            btnWalkie.text = "📻 ACTIVE MESH: LOCAL ENCLAVE"
            btnWalkie.setBackgroundColor(android.graphics.Color.parseColor("#B71C1C"))
            btnWalkie.setTextColor(android.graphics.Color.WHITE)

            btnBluetooth.setBackgroundColor(android.graphics.Color.parseColor("#1976D2")) // Active Blue
            btnWifiMesh.setBackgroundColor(android.graphics.Color.parseColor("#0097A7")) // Active Cyan
        } else {
            tvStatus.text = "STATUS: ONLINE"
            tvStatus.setTextColor(android.graphics.Color.GREEN)
            nexusTitle.setTextColor(android.graphics.Color.parseColor("#BC13FE"))
            
            btnGlobal.text = "📡 Global Enclave"
            btnGlobal.alpha = 1.0f
            
            btnWalkie.text = "📻 Private Channel"
            btnWalkie.setBackgroundColor(android.graphics.Color.parseColor("#16213E"))
            btnWalkie.setTextColor(android.graphics.Color.parseColor("#7B61FF"))

            btnBluetooth.setBackgroundColor(android.graphics.Color.parseColor("#0D47A1"))
            btnWifiMesh.setBackgroundColor(android.graphics.Color.parseColor("#006064"))
        }
    }

    private fun ensureAgentId(uid: String, onComplete: () -> Unit) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        usersRef.child("agentId").get().addOnSuccessListener { snapshot ->
            val value = snapshot.value?.toString()
            if (value == null || value.isEmpty()) {
                val newId = (10000..99999).random().toString()
                usersRef.child("agentId").setValue(newId).addOnCompleteListener { onComplete() }
            } else {
                onComplete()
            }
        }.addOnFailureListener { onComplete() }
    }
}