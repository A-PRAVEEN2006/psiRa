package com.project1.psira

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class AccountBannedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenSecurityHelper.apply(this)
        setContentView(R.layout.activity_banned)

        val reason = intent.getStringExtra("BAN_REASON") ?: "PROTOCOL VIOLATION"
        findViewById<TextView>(R.id.tvBanReason).text = "REASON: ${reason.uppercase()}"

        // Ensure user is signed out and credentials wiped
        try {
            FirebaseAuth.getInstance().signOut()
            getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}

        findViewById<Button>(R.id.btnExitBanned).setOnClickListener {
            finishAffinity()
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
            }
        })
    }
}
