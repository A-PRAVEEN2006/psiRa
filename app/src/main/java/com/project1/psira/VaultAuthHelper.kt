package com.project1.psira

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

object VaultAuthHelper {
    fun authenticateAndLaunch(activity: AppCompatActivity, bottomNav: BottomNavigationView, currentNavId: Int) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(activity, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                    bottomNav.selectedItemId = currentNavId
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    activity.startActivity(Intent(activity, VaultActivity::class.java))
                    if (activity !is NexusDashboardActivity) {
                        activity.finish()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(activity, "Auth Failed", Toast.LENGTH_SHORT).show()
                    bottomNav.selectedItemId = currentNavId
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Vault Biometric Lock")
            .setSubtitle("Authenticate to access the Secure Vault")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
