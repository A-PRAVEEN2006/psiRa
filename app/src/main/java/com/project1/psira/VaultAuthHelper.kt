package com.project1.psira

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

object VaultAuthHelper {

    /**
     * Original overload — used by bottom-nav tabs to open VaultActivity.
     */
    fun authenticateAndLaunch(activity: AppCompatActivity, bottomNav: BottomNavigationView, currentNavId: Int) {
        authenticateAndLaunch(
            activity    = activity,
            targetClass = VaultActivity::class.java,
            title       = "Vault Biometric Lock",
            subtitle    = "Authenticate to access the Secure Vault",
            onSuccess   = {
                activity.startActivity(Intent(activity, VaultActivity::class.java))
                if (activity !is NexusDashboardActivity) activity.finish()
            },
            onCancel    = { bottomNav.selectedItemId = currentNavId }
        )
    }

    /**
     * Generic overload — used by Settings to open any vault screen.
     */
    fun authenticateAndLaunch(
        activity: AppCompatActivity,
        targetClass: Class<*>,
        title: String = "Biometric Lock",
        subtitle: String = "Authenticate to continue",
        onSuccess: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(activity, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                    onCancel?.invoke()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (onSuccess != null) {
                        onSuccess()
                    } else {
                        activity.startActivity(Intent(activity, targetClass))
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(activity, "Auth Failed", Toast.LENGTH_SHORT).show()
                    onCancel?.invoke()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun wipeLocalVault(context: android.content.Context) {
        try {
            // 1. Wipe Encrypted Media Vault (files + thumbnails + metadata)
            MediaVaultManager.wipeAll(context)

            // 2. Wipe Dead Drop payloads
            DeadDropRelayManager(context).wipeDeadDrop()

            // 3. Clear cloud notes if user is signed in
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user != null) {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("user_vault").child(user.uid).removeValue()
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            }

            // 4. Wipe all sensitive SharedPreferences
            listOf(
                "VaultPrefs",
                "PsiRaPrefs",
                "PsiRaE2EEPrefs",
                "PsiRaUpdatePrefs",
                "PsiRa_DeadDrop_Vault",
                "VaultMediaPrefs"
            ).forEach { prefName ->
                context.getSharedPreferences(prefName, android.content.Context.MODE_PRIVATE)
                    .edit().clear().apply()
            }

            // 5. Clean internal cache directory
            context.cacheDir.deleteRecursively()

            android.util.Log.e("Vault", "All vault and local sensitive data wiped thoroughly.")
        } catch (e: Exception) {
            android.util.Log.e("Vault", "Error during vault wipe: ${e.message}")
        }
    }
}
