package com.project1.psira

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView

object InAppNotificationBanner {

    private const val BANNER_TAG = "psira_inapp_banner"
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null
    private var currentMessageSignature: String? = null
    private var lastShowTime: Long = 0L

    fun show(activity: Activity, title: String, message: String, onClick: () -> Unit) {
        val now = System.currentTimeMillis()
        val signature = "$title::$message"
        // Prevent duplicate banner for the exact same message if shown within last 4 seconds
        if (signature == currentMessageSignature && (now - lastShowTime) < 4000) {
            return
        }
        currentMessageSignature = signature
        lastShowTime = now

        val decorView = activity.window.decorView as? ViewGroup ?: return

        // Cancel previous pending dismissal
        dismissRunnable?.let { handler.removeCallbacks(it) }

        // Remove any existing banner
        val existing = decorView.findViewWithTag<View>(BANNER_TAG)
        if (existing != null) {
            decorView.removeView(existing)
        }

        val density = activity.resources.displayMetrics.density

        val card = CardView(activity).apply {
            tag = BANNER_TAG
            radius = 16f * density
            cardElevation = 12f * density
            setCardBackgroundColor(Color.parseColor("#EE1A1A32"))

            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                val padH = (16 * density).toInt()
                val padV = (12 * density).toInt()
                setPadding(padH, padV, padH, padV)

                val tvTitle = TextView(activity).apply {
                    text = "💬 $title"
                    setTextColor(Color.parseColor("#A78BFA"))
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                }

                val preview = when {
                    message.startsWith("data:image") -> "📷 Photo transmission"
                    message.startsWith("data:audio") -> "🎤 Encrypted voice note"
                    message.startsWith("data:application") -> "📄 Encrypted document"
                    else -> message
                }

                val tvBody = TextView(activity).apply {
                    text = preview
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, (2 * density).toInt(), 0, 0)
                }

                addView(tvTitle)
                addView(tvBody)
            }
            addView(container)

            setOnClickListener {
                decorView.removeView(this)
                onClick()
            }
        }

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            val marginH = (16 * density).toInt()
            val marginT = (32 * density).toInt()
            setMargins(marginH, marginT, marginH, 0)
        }

        card.translationY = -300f
        decorView.addView(card, lp)

        card.animate()
            .translationY(0f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val runnable = Runnable {
            if (card.parent != null) {
                card.animate()
                    .translationY(-300f)
                    .setDuration(220)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        decorView.removeView(card)
                        if (currentMessageSignature == signature) {
                            currentMessageSignature = null
                        }
                    }
                    .start()
            }
        }
        dismissRunnable = runnable
        handler.postDelayed(runnable, 3600)
    }
}
