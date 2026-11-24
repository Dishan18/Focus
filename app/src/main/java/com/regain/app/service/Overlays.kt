package com.regain.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.regain.app.core.Haptics
import com.regain.app.core.Permissions

/**
 * Lightweight overlays drawn over other apps ("Display over other apps").
 * Plain Views, not Compose, so nothing heavy stays in memory while the user
 * is in other apps. Main thread only.
 *
 * Android 12+ blocks touches that pass through another app's overlay unless
 * the overlay window is at most 80% opaque, so the see-through overlays (dim,
 * pill) set their window alpha accordingly.
 */
class Overlays(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private var dim: View? = null
    private var pill: View? = null
    private var cover: FrameLayout? = null
    private val dp = context.resources.displayMetrics.density

    private fun params(width: Int, height: Int, flags: Int, alpha: Float = 1f) =
        WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.alpha = alpha
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private fun add(view: View, lp: WindowManager.LayoutParams): Boolean {
        if (!Permissions.canDrawOverlays(context)) return false
        return runCatching { wm.addView(view, lp) }.isSuccess
    }

    private fun remove(view: View?) {
        view ?: return
        runCatching { wm.removeView(view) }
    }

    // ------------------------------------------------------------ fade

    /** Darken the screen gradually as time runs out. [level] 0..1. */
    fun setDim(level: Float) {
        val l = level.coerceIn(0f, 1f)
        if (l <= 0.002f) { clearDim(); return }
        val view = dim ?: View(context).also { v ->
            v.setBackgroundColor(Color.BLACK)
            v.alpha = 0f
            // Window alpha 0.5 is the darkest the fade gets, and keeps touches working.
            val lp = params(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                alpha = 0.5f,
            )
            if (!add(v, lp)) return
            dim = v
        }
        view.animate().alpha(l).setDuration(1500).start()
    }

    fun clearDim() {
        remove(dim)
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
                setColor(0xFF141416.toInt())
                setStroke((1 * dp).toInt(), (accent and 0x00FFFFFF) or 0x88000000.toInt())
            }
        }
        container.addView(tv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
        ))
        val lp = params(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            alpha = 0.8f,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (44 * dp).toInt()
        }
        if (!add(container, lp)) return
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
        remove(pill)
        pill = null
    }

    // ------------------------------------------------------------ cover

    /**
     * A full black screen over the app while its gate opens. It hides the
     * app at once and blocks touches; the system bars stay usable, so Home
     * and Back always work.
     */
    fun showCover() {
        if (cover != null) return
        val v = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            isClickable = true
        }
        val lp = params(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        if (!add(v, lp)) return
        cover = v
        v.animate().alpha(1f).setDuration(120).start()
    }

    /** The gate didn't open on its own: ask for one tap, which always works. */
    fun showCoverButton(title: String, onContinue: () -> Unit) {
        showCover()
        val c = cover ?: return
        if (c.childCount > 0) return
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((32 * dp).toInt(), 0, (32 * dp).toInt(), 0)
        }
        col.addView(TextView(context).apply {
            text = title
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        col.addView(TextView(context).apply {
            text = "Tap continue to see your options."
            gravity = Gravity.CENTER
            setTextColor(0xFF8E8E93.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, (8 * dp).toInt(), 0, (28 * dp).toInt())
        })
        col.addView(TextView(context).apply {
            text = "Continue"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor(0xFF8B7CFF.toInt())
            }
            setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
            setOnClickListener {
                Haptics.play(Haptics.Kind.Tick)
                onContinue()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(TextView(context).apply {
            text = "Go home"
            gravity = Gravity.CENTER
            setTextColor(0xFF8B7CFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(0, (18 * dp).toInt(), 0, (18 * dp).toInt())
            setOnClickListener {
                Haptics.play(Haptics.Kind.Tick)
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(home) }
                removeCover()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        c.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
        ))
    }

    fun removeCover() {
        val v = cover ?: return
        cover = null
        v.isClickable = false
        v.animate().alpha(0f).setDuration(180).withEndAction { remove(v) }.start()
    }

    fun clearAll() {
        clearDim()
        removePill()
        cover?.let { remove(it) }
        cover = null
    }
}
