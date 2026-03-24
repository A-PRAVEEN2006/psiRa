package com.project1.psira

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.database.FirebaseDatabase

class GodDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_god_dashboard)

        val tabLayout: TabLayout = findViewById(R.id.godTabLayout)
        val viewPager: ViewPager2 = findViewById(R.id.godViewPager)
        val btnBroadcast: Button = findViewById(R.id.btnGlobalBroadcast)
        val btnRestoreId: ImageButton = findViewById(R.id.btnRestoreIdentity)

        val adapter = GodPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "AGENTS"
                1 -> "ENCLAVES"
                2 -> "DIRECT LINKS"
                3 -> "VAULTS"
                else -> null
            }
        }.attach()

        btnBroadcast.setOnClickListener {
            showBroadcastDialog()
        }

        btnRestoreId.setOnClickListener {
            getSharedPreferences("PsiRaPrefs", android.content.Context.MODE_PRIVATE).edit()
                .remove("IMPERSONATING_NAME")
                .remove("IMPERSONATING_ID")
                .apply()
            Toast.makeText(this, "Creator status restored.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBroadcastDialog() {
        val input = EditText(this)
        input.hint = "System Override Message"
        input.setTextColor(android.graphics.Color.WHITE)
        input.setHintTextColor(android.graphics.Color.GRAY)

        PsiRaDialogs.showDeleteSheet(
            this,
            "VOICE OF GOD",
            "Inject a priority message into every single group enclave simultaneously.",
            "EXECUTE",
            input
        ) {
            val msg = input.text.toString().trim()
            if (msg.isNotEmpty()) injectBroadcast(msg)
        }
    }

    private fun injectBroadcast(text: String) {
        val db = FirebaseDatabase.getInstance()
        val godMsg = Message(id = "GOD_OVERRIDE", sender = "OVERSEER", content = "SYSTEM BROADCAST: $text")
        
        db.getReference("groups").get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                val groupId = child.key
                if (groupId != null) {
                    db.getReference("messages").child("group_$groupId").push().setValue(godMsg)
                }
            }
            // Also inject into Global
            db.getReference("messages").child("global_chat").push().setValue(godMsg)
            Toast.makeText(this, "Broadcast signal frequency synchronized.", Toast.LENGTH_SHORT).show()
        }
    }
}
