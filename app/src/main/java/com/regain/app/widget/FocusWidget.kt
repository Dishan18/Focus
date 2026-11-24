package com.regain.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.widget.RemoteViews
import com.regain.app.R
import com.regain.app.RegainApp
import com.regain.app.core.TimeUtil
import com.regain.app.data.Store
import com.regain.app.ui.MainActivity
import com.regain.app.ui.screens.streak
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Home-screen widget: today's Bloom, time used vs goal, and the limit
 * closest to running out. Refreshed every 30 min by the system, when the app
 * is used, and (throttled) on unlock. Rendering happens off the main thread.
 */
class FocusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // A chance to restart the engine if Android stopped it.
        com.regain.app.service.GuardService.start(context)
        val pending = goAsync()
        io.execute {
            try { render(context.applicationContext, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        private val io = Executors.newSingleThreadExecutor()
        @Volatile private var lastUpdate = 0L

        /** Ask all widgets to refresh. With [throttle], at most every 10 minutes. */
        fun requestUpdate(context: Context, throttle: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (throttle && now - lastUpdate < 10 * TimeUtil.MINUTE) return
            lastUpdate = now
            val app = context.applicationContext
            io.execute {
                runCatching {
                    val manager = AppWidgetManager.getInstance(app)
                    val ids = manager.getAppWidgetIds(ComponentName(app, FocusWidget::class.java))
                    if (ids.isNotEmpty()) render(app, manager, ids)
                }
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context as? RegainApp ?: RegainApp.instance
            val today = runCatching { app.usage.today() }.getOrNull()
            val total = today?.totalMs ?: 0
            val goal = Store.dailyGoalMin * TimeUtil.MINUTE
            val over = total > goal
            val health = (1f - total.toFloat() / goal).coerceIn(0f, 1f)
            val petals = runCatching { streak(total) }.getOrDefault(0) + 3

            // The limit with the least time left.
            // The app or group with the least time left.
            val keys = Store.limits().keys + Store.groups().keys.map { "g:$it" }
            val limitLine = keys.mapNotNull { key ->
                val b = Store.budget(key) ?: return@mapNotNull null
                val allowed = (Store.allowedMinutes(key) ?: return@mapNotNull null) * TimeUtil.MINUTE
                val left = allowed - b.pkgs.sumOf { today?.timeOf(it) ?: 0 }
                Triple(b.label ?: app.catalog.label(key), left, key)
            }.minByOrNull { it.second }?.let { (label, left, _) ->
                if (left <= 0) "$label · done" else "$label · ${TimeUtil.formatDuration(left)} left"
            }

            val bloom = bloomBitmap(220, health, petals, over)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_focus).apply {
                setImageViewBitmap(R.id.widget_bloom, bloom)
                setTextViewText(R.id.widget_time, TimeUtil.formatDuration(total))
                setTextViewText(R.id.widget_goal, if (over) "over your ${TimeUtil.formatDuration(goal)} goal" else "of ${TimeUtil.formatDuration(goal)} goal")
                setTextViewText(R.id.widget_limit, limitLine ?: "")
                setTextColor(R.id.widget_limit, if (limitLine?.endsWith("done") == true) 0xFFFF6961.toInt() else 0xFF8B7CFF.toInt())
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }

        /** A still frame of the Bloom, drawn with plain Android graphics. */
        fun bloomBitmap(size: Int, health: Float, petals: Int, wilted: Boolean): Bitmap {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val cx = size / 2f
            val cy = size / 2f
            val maxR = size / 2f
            val warm = if (wilted) 0xFF9C8A6E.toInt() else lerp(0xFFFFB340.toInt(), 0xFF63E6BE.toInt(), health)
            val cool = if (wilted) 0xFF6B6470.toInt() else lerp(0xFFFF6961.toInt(), 0xFF8B7CFF.toInt(), health)
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, maxR, (warm and 0x00FFFFFF) or 0x55000000, 0, Shader.TileMode.CLAMP)
            }
            c.drawCircle(cx, cy, maxR, glow)
            val base = maxR * (if (wilted) 0.42f else 0.48f + 0.26f * health)
            val amp = if (wilted) 0.04f else 0.05f + 0.16f * health
            val n = petals.coerceIn(3, 12)
            val droop = if (wilted) base * 0.12f else 0f
            for (layer in 0..2) {
                val scale = 1f - layer * 0.22f
                val path = Path()
                for (i in 0..120) {
                    val th = i / 120f * 2f * PI.toFloat()
                    val rr = base * scale * (1f + amp * sin(n * th + layer * 0.9f))
                    val x = cx + rr * cos(th)
                    // Wilted petals sag toward the bottom.
                    val y = cy + rr * sin(th) + droop * ((sin(th) + 1f) / 2f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                val a = (0.55f - layer * 0.12f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        cx - base, cy - base, cx + base, cy + base,
                        withAlpha(warm, a), withAlpha(cool, a + 0.05f), Shader.TileMode.CLAMP,
                    )
                }
                c.drawPath(path, paint)
            }
            c.drawCircle(cx, cy + droop / 2, min(size, size) * 0.025f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() })
            return bmp
        }

        private fun withAlpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

        private fun lerp(a: Int, b: Int, f: Float): Int {
            fun ch(x: Int, s: Int) = (x shr s) and 0xFF
            fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * f).toInt() and 0xFF
            return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }
}
