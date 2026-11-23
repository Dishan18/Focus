package com.regain.app.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Lightweight overlays drawn by the accessibility service. Plain Views, not
 * Compose, so nothing heavy stays in memory while the user is in other apps.
 * TYPE_ACCESSIBILITY_OVERLAY needs no extra "draw over apps" permission.
 */
class Overlays(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private var dim: View? = null
    private var pill: View? = null
    private val dp = context.resources.displayMetrics.density

    // ------------------------------------------------------------ fade

    /** Darken the screen gradually as time runs out. [level] 0..1. */
    fun setDim(level: Float) {
        val alpha = (level.coerceIn(0f, 1f) * 0.5f)
        if (alpha <= 0.001f) { clearDim(); return }
        val view = dim ?: View(context).also { v ->
            v.setBackgroundColor(Color.BLACK)
            v.alpha = 0f
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )
            if (runCatching { wm.addView(v, lp) }.isFailure) return
            dim = v
        }
        view.animate().alpha(alpha).setDuration(1500).start()
    }

    fun clearDim() {
        dim?.let { v -> runCatching { wm.removeView(v) } }
        dim = null
    }

    // ------------------------------------------------------------ pill

    /** A small floating capsule at the top, like a Dynamic-Island notice. */
    fun showPill(text: String, accent: Int = 0xFF8B7CFF.toInt(), durationMs: Long = 2400) {
        removePill()
        val container = FrameLayout(context)
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding((18 * dp).toInt(), (11 * dp).toInt(), (18 * dp).toInt(), (11 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 100 * dp
                setColor(0xF2141416.toInt())
                setStroke((1 * dp).toInt(), (accent and 0x00FFFFFF) or 0x66000000)
            }
            elevation = 12 * dp
        }
        container.addView(tv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
        ))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (44 * dp).toInt()
        }
        if (runCatching { wm.addView(container, lp) }.isFailure) return
        pill = container

        tv.scaleX = 0.6f; tv.scaleY = 0.6f; tv.alpha = 0f; tv.translationY = -20 * dp
        tv.animate().scaleX(1f).scaleY(1f).alpha(1f).translationY(0f)
            .setInterpolator(OvershootInterpolator(1.6f)).setDuration(420).start()
        tv.postDelayed({
            tv.animate().scaleX(0.8f).scaleY(0.8f).alpha(0f).translationY(-12 * dp)
                .setInterpolator(DecelerateInterpolator()).setDuration(260)
                .withEndAction { if (pill === container) removePill() }.start()
        }, durationMs)
    }

    private fun removePill() {
        pill?.let { v -> runCatching { wm.removeView(v) } }
        pill = null
    }

    fun clearAll() {
        clearDim()
        removePill()
    }

    @Suppress("unused")
    private fun pulse(v: View) = ValueAnimator.ofFloat(1f, 1.06f, 1f).apply {
        duration = 500
        addUpdateListener { v.scaleX = it.animatedValue as Float; v.scaleY = v.scaleX }
        start()
    }
}
