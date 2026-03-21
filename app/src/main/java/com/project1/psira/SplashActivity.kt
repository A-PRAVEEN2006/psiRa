package com.project1.psira

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure Dark Mode is restored before any UI renders
        val sharedPref = getSharedPreferences("PsiRaPrefs", android.content.Context.MODE_PRIVATE)
        val isDark = sharedPref.getBoolean("IS_DARK_MODE", true) // Default to Hacker Mode
        if (isDark) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }

        setContentView(R.layout.activity_splash)

        val splashTitle = findViewById<android.widget.TextView>(R.id.splashTitle)
        val animator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            splashTitle,
            android.animation.PropertyValuesHolder.ofFloat("alpha", 0f, 1f),
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 0.8f, 1f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 0.8f, 1f)
        )
        animator.duration = 1200
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.start()

        // Wait for 2000ms (2 seconds) then go to MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish() // Close the splash screen so user can't go back to it
        }, 2000)
    }
}