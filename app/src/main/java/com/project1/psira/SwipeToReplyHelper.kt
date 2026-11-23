package com.project1.psira

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class SwipeToReplyHelper(
    private val context: Context,
    private val getMessage: (Int) -> Message?,
    private val onReply: (Message) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val replyIcon: Drawable? = try {
        AppCompatResources.getDrawable(context, R.drawable.ic_reply_arrow)
    } catch (_: Exception) {
        null
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
    }

    private var swipeThresholdMet = false
    private var hasVibrated = false
    private var swipedPosition = -1
    private val triggerThreshold = dpToPx(65f)
    private val maxSwipeDistance = dpToPx(95f)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No-op: messages are never dismissed by swipe
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 5.0f

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = Float.MAX_VALUE

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val absDx = abs(dX)
        val sign = if (dX >= 0) 1f else -1f

        // Clamp translation so bubble stays nicely bounded
        val clampedAbsDx = if (absDx > maxSwipeDistance) {
            maxSwipeDistance + (absDx - maxSwipeDistance) * 0.2f
        } else {
            absDx
        }
        val clampedDx = clampedAbsDx * sign

        // Check if swipe distance reached reply threshold
        if (clampedAbsDx >= triggerThreshold) {
            if (!hasVibrated) {
                itemView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                hasVibrated = true
            }
            swipeThresholdMet = true
            swipedPosition = viewHolder.bindingAdapterPosition
        } else if (isCurrentlyActive) {
            hasVibrated = false
            swipeThresholdMet = false
        }

        // Draw side-pull reply indicator icon behind message
        if (clampedAbsDx > 4f) {
            val progress = (clampedAbsDx / triggerThreshold).coerceIn(0f, 1f)
            val centerY = itemView.top + itemView.height / 2f
            val radius = dpToPx(18f) * progress

            val centerX = if (dX > 0) {
                itemView.left + clampedAbsDx / 2f
            } else {
                itemView.right - clampedAbsDx / 2f
            }

            if (radius > 2f) {
                // Circle styling
                if (swipeThresholdMet) {
                    bgPaint.color = Color.parseColor("#00E5FF")
                    borderPaint.color = Color.WHITE
                } else {
                    bgPaint.color = Color.parseColor("#1E293B")
                    borderPaint.color = Color.parseColor("#00E5FF")
                }
                c.drawCircle(centerX, centerY, radius, bgPaint)
                c.drawCircle(centerX, centerY, radius, borderPaint)

                // Reply arrow icon
                replyIcon?.let { icon ->
                    val iconSize = (dpToPx(18f) * progress).toInt()
                    val iconColor = if (swipeThresholdMet) Color.BLACK else Color.parseColor("#00E5FF")
                    icon.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
                    val left = (centerX - iconSize / 2).toInt()
                    val top = (centerY - iconSize / 2).toInt()
                    icon.setBounds(left, top, left + iconSize, top + iconSize)
                    icon.draw(c)
                }
            }
        }

        // Detect user releasing finger after meeting threshold
        if (!isCurrentlyActive && swipeThresholdMet) {
            val pos = swipedPosition
            swipeThresholdMet = false
            hasVibrated = false
            swipedPosition = -1
            if (pos != RecyclerView.NO_POSITION) {
                getMessage(pos)?.let { msg ->
                    itemView.post { onReply(msg) }
                }
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, clampedDx, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.translationX = 0f
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
