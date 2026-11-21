package com.project1.psira

import android.content.Context
import android.os.SystemClock

/**
 * Global brute-force rate limiter for all disguise unlock attempts.
 * Uses SystemClock.elapsedRealtime() to prevent clock tampering bypasses.
 */
object RateLimiter {

    /** Maximum consecutive failed attempts before a cooldown is triggered. */
    const val MAX_ATTEMPTS = 5

    /** Base lockout duration in ms (30 seconds). Doubles per escalation tier. */
    private const val BASE_LOCKOUT_MS = 30_000L

    /** Maximum backoff tier — gives: 30s → 60s → 120s → 240s (4 minutes cap). */
    private const val MAX_TIER = 3

    private const val PREFS          = "PsiRaPrefs"
    private const val KEY_FAIL_COUNT = "RATE_FAIL_COUNT"
    private const val KEY_LAST_FAIL  = "RATE_LAST_FAIL_ELAPSED"
    private const val KEY_TIER       = "RATE_LOCKOUT_TIER"

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns true if the user is currently locked out.
     * Automatically clears the lockout (but not the tier) once the window expires.
     */
    fun isLockedOut(context: Context): Boolean {
        val prefs     = prefs(context)
        val failCount = prefs.getInt(KEY_FAIL_COUNT, 0)
        if (failCount < MAX_ATTEMPTS) return false

        val lastFail    = prefs.getLong(KEY_LAST_FAIL, 0L)
        val tier        = prefs.getInt(KEY_TIER, 0)
        val lockoutMs   = lockoutDurationMs(tier)
        val now         = SystemClock.elapsedRealtime()
        val elapsed     = now - lastFail

        return if (elapsed in 0 until lockoutMs) {
            true
        } else {
            // Window expired or device rebooted past window — reset fail counter but keep tier
            prefs.edit().putInt(KEY_FAIL_COUNT, 0).apply()
            false
        }
    }

    /**
     * Remaining seconds in the current lockout window (0 if not locked out).
     */
    fun getRemainingLockoutSeconds(context: Context): Long {
        val prefs   = prefs(context)
        val last    = prefs.getLong(KEY_LAST_FAIL, 0L)
        val tier    = prefs.getInt(KEY_TIER, 0)
        val now     = SystemClock.elapsedRealtime()
        val elapsed = now - last
        val duration = lockoutDurationMs(tier)
        return if (elapsed in 0 until duration) {
            maxOf(0L, (duration - elapsed) / 1000L)
        } else {
            0L
        }
    }

    /**
     * Records one failed unlock attempt.
     * @return true if this attempt just triggered a lockout.
     */
    fun recordFailedAttempt(context: Context): Boolean {
        val prefs   = prefs(context)
        val newCount = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
        val tier    = prefs.getInt(KEY_TIER, 0)

        prefs.edit()
            .putInt(KEY_FAIL_COUNT, newCount)
            .putLong(KEY_LAST_FAIL, SystemClock.elapsedRealtime())
            .apply()

        return if (newCount >= MAX_ATTEMPTS) {
            // Escalate tier for the NEXT lockout round
            prefs.edit()
                .putInt(KEY_TIER, minOf(tier + 1, MAX_TIER))
                .apply()
            true
        } else {
            false
        }
    }

    /**
     * Number of attempts still allowed before lockout kicks in (0 when already locked).
     */
    fun remainingAttempts(context: Context): Int {
        val count = prefs(context).getInt(KEY_FAIL_COUNT, 0)
        return maxOf(0, MAX_ATTEMPTS - count)
    }

    /**
     * Resets counter AND backoff tier on a successful unlock.
     * Call this immediately before navigating to the real app.
     */
    fun resetAttempts(context: Context) {
        prefs(context).edit()
            .putInt(KEY_FAIL_COUNT, 0)
            .putInt(KEY_TIER, 0)
            .putLong(KEY_LAST_FAIL, 0L)
            .apply()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Lockout duration for the given escalation tier (doubles each tier). */
    private fun lockoutDurationMs(tier: Int): Long =
        BASE_LOCKOUT_MS * (1L shl tier.coerceIn(0, MAX_TIER))
}
