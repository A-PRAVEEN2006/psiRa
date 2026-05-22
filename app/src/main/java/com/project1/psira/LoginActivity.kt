package com.project1.psira

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : BaseActivity() {
    private lateinit var auth: FirebaseAuth
    private var isLoginMode = true
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        val sharedPref = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)

        // ── View references ──────────────────────────────────────────────────
        val tabLogin             = findViewById<TextView>(R.id.tabLogin)
        val tabRegister          = findViewById<TextView>(R.id.tabRegister)
        val editEmail            = findViewById<EditText>(R.id.editEmail)
        val editPassword         = findViewById<EditText>(R.id.editPassword)
        val editConfirmPassword  = findViewById<EditText>(R.id.editConfirmPassword)
        val editAlias            = findViewById<EditText>(R.id.editAlias)
        val frameConfirm         = findViewById<FrameLayout>(R.id.frameConfirmPassword)
        val labelConfirm         = findViewById<TextView>(R.id.labelConfirmPassword)
        val frameAlias           = findViewById<FrameLayout>(R.id.frameAlias)
        val labelAlias           = findViewById<TextView>(R.id.labelAlias)
        val ivToggle             = findViewById<ImageView>(R.id.ivTogglePassword)
        val tvForgot             = findViewById<TextView>(R.id.tvForgotPassword)
        val btnAction            = findViewById<Button>(R.id.btnLogin)
        val tvError              = findViewById<TextView>(R.id.tvLoginError)

        // ── 1. Auto-login (session guard) ────────────────────────────────────
        if (auth.currentUser != null) {
            val autoCount = sharedPref.getInt("AUTO_LOGIN_COUNT", 0)
            if (autoCount < 10) {
                ensureAgentId(auth.currentUser!!.uid) {
                    sharedPref.edit().putInt("AUTO_LOGIN_COUNT", autoCount + 1).apply()
                    startActivity(Intent(this, NexusDashboardActivity::class.java))
                    finish()
                }
                return
            }
        }

        // ── 2. Tab switching ──────────────────────────────────────────────────
        fun switchToLogin() {
            isLoginMode = true
            tabLogin.setBackgroundResource(R.drawable.bg_rounded_primary)
            tabLogin.setTextColor(getColor(android.R.color.white))
            tabRegister.setBackgroundResource(0)
            tabRegister.setTextColor(getColorAttr(android.R.attr.textColorPrimary))
            frameConfirm.visibility = View.GONE
            labelConfirm.visibility = View.GONE
            frameAlias.visibility   = View.GONE
            labelAlias.visibility   = View.GONE
            tvForgot.visibility     = View.VISIBLE
            btnAction.text = "SIGN IN"
            tvError.visibility = View.GONE
        }

        fun switchToRegister() {
            isLoginMode = false
            tabRegister.setBackgroundResource(R.drawable.bg_rounded_primary)
            tabRegister.setTextColor(getColor(android.R.color.white))
            tabLogin.setBackgroundResource(0)
            tabLogin.setTextColor(getColorAttr(android.R.attr.textColorPrimary))
            frameConfirm.visibility = View.VISIBLE
            labelConfirm.visibility = View.VISIBLE
            frameAlias.visibility   = View.VISIBLE
            labelAlias.visibility   = View.VISIBLE
            tvForgot.visibility     = View.GONE
            btnAction.text = "CREATE ACCOUNT"
            tvError.visibility = View.GONE
        }

        tabLogin.setOnClickListener    { switchToLogin() }
        tabRegister.setOnClickListener { switchToRegister() }

        // ── 3. Show / Hide password ──────────────────────────────────────────
        ivToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            editPassword.transformationMethod = if (isPasswordVisible)
                HideReturnsTransformationMethod.getInstance()
            else
                PasswordTransformationMethod.getInstance()
            editPassword.setSelection(editPassword.text.length)
        }

        // ── 4. Forgot Password ────────────────────────────────────────────────
        tvForgot.setOnClickListener {
            val email = editEmail.text.toString().trim()
            if (email.isEmpty()) {
                showError(tvError, "Enter your email above first.")
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showSuccess(tvError, "✅ Reset link sent to $email")
                } else {
                    showError(tvError, task.exception?.message ?: "Failed to send reset email.")
                }
            }
        }

        // ── 5. Primary action button ──────────────────────────────────────────
        btnAction.setOnClickListener {
            val email = editEmail.text.toString().trim()
            val pass  = editPassword.text.toString()
            tvError.visibility = View.GONE

            // Shared validation
            if (email.isEmpty()) { showError(tvError, "Email is required."); return@setOnClickListener }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showError(tvError, "Enter a valid email address."); return@setOnClickListener
            }
            if (pass.isEmpty()) { showError(tvError, "Password is required."); return@setOnClickListener }
            if (pass.length < 6) { showError(tvError, "Password must be at least 6 characters."); return@setOnClickListener }

            btnAction.isEnabled = false
            btnAction.text = if (isLoginMode) "SIGNING IN…" else "CREATING…"

            if (isLoginMode) {
                // ── Login ──
                auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                    btnAction.isEnabled = true
                    btnAction.text = "SIGN IN"
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        sharedPref.edit().putInt("AUTO_LOGIN_COUNT", 0).apply()
                        if (user?.displayName.isNullOrEmpty()) {
                            showNamePopup()
                        } else {
                            ensureAgentId(user!!.uid) {
                                ECDHKeyManager.initializeKeys(this)
                                startDashboard()
                            }
                        }
                    } else {
                        val msg = when {
                            task.exception?.message?.contains("password") == true  -> "Incorrect password."
                            task.exception?.message?.contains("no user") == true   -> "No account found with this email."
                            task.exception?.message?.contains("blocked") == true   -> "Too many attempts. Try again later."
                            else -> task.exception?.message ?: "Sign in failed."
                        }
                        showError(tvError, msg)
                    }
                }
            } else {
                // ── Register ──
                val confirm = editConfirmPassword.text.toString()
                val alias   = editAlias.text.toString().trim()

                if (confirm != pass) {
                    btnAction.isEnabled = true
                    btnAction.text = "CREATE ACCOUNT"
                    showError(tvError, "Passwords do not match.")
                    return@setOnClickListener
                }
                if (alias.isEmpty()) {
                    btnAction.isEnabled = true
                    btnAction.text = "CREATE ACCOUNT"
                    showError(tvError, "Agent alias is required.")
                    return@setOnClickListener
                }

                auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                    btnAction.isEnabled = true
                    btnAction.text = "CREATE ACCOUNT"
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        sharedPref.edit().putInt("AUTO_LOGIN_COUNT", 0).apply()
                        val profileUpdates = userProfileChangeRequest { setDisplayName(alias) }
                        user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                            ensureAgentId(user.uid) {
                                FirebaseDatabase.getInstance()
                                    .getReference("users").child(user.uid).child("name")
                                    .setValue(alias)
                                ECDHKeyManager.initializeKeys(this)
                                startDashboard()
                            }
                        }
                    } else {
                        val msg = when {
                            task.exception?.message?.contains("already in use") == true -> "An account with this email already exists."
                            task.exception?.message?.contains("weak") == true           -> "Password is too weak. Use at least 6 characters."
                            else -> task.exception?.message ?: "Registration failed."
                        }
                        showError(tvError, msg)
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showError(tv: TextView, msg: String) {
        tv.text = msg
        tv.setTextColor(android.graphics.Color.parseColor("#FF6B6B"))
        tv.visibility = View.VISIBLE
    }

    private fun showSuccess(tv: TextView, msg: String) {
        tv.text = msg
        tv.setTextColor(android.graphics.Color.parseColor("#44FF88"))
        tv.visibility = View.VISIBLE
    }

    /** Resolve a theme colour attribute to a real colour int. */
    private fun getColorAttr(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, android.graphics.Color.WHITE)
        ta.recycle()
        return color
    }

    private fun startDashboard() {
        Toast.makeText(this, "Welcome back, ${auth.currentUser?.displayName}", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, NexusDashboardActivity::class.java))
        finish()
    }

    private fun showNamePopup() {
        val nameInput = EditText(this)
        nameInput.hint = "Agent Alias"
        nameInput.setTextColor(android.graphics.Color.WHITE)
        PsiRaDialogs.showDeleteSheet(this, "SET YOUR ALIAS", "Choose a display name for your profile.", "SAVE", nameInput, false) {
            val displayName = nameInput.text.toString().trim()
            if (displayName.isNotEmpty()) {
                val user = auth.currentUser
                val profileUpdates = userProfileChangeRequest { setDisplayName(displayName) }
                user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                    ensureAgentId(user.uid) {
                        FirebaseDatabase.getInstance()
                            .getReference("users").child(user.uid).child("name")
                            .setValue(displayName)
                        startDashboard()
                    }
                }
            }
        }
    }

    private fun ensureAgentId(uid: String, onComplete: () -> Unit) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        usersRef.child("agentId").get().addOnSuccessListener { snapshot ->
            val existing = snapshot.value?.toString()
            if (existing.isNullOrEmpty()) {
                val newId = (10000..99999).random().toString()
                usersRef.child("agentId").setValue(newId).addOnCompleteListener {
                    getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
                        .edit().putString("AGENT_ID_LOCAL", newId).apply()
                    onComplete()
                }
            } else {
                getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
                    .edit().putString("AGENT_ID_LOCAL", existing).apply()
                onComplete()
            }
        }.addOnFailureListener {
            onComplete() // Allow entry even if DB check fails (offline)
        }
    }
}