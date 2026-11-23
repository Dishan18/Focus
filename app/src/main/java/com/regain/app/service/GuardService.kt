package com.regain.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.util.Log
import com.regain.app.BuildConfig
import com.regain.app.RegainApp
import com.regain.app.core.Haptics
import com.regain.app.core.TimeUtil
import com.regain.app.data.Store
import com.regain.app.ui.shield.ShieldActivity
import com.regain.app.ui.shield.ShieldMode

/**
 * The engine. Wakes only when the app on screen changes (event driven, no
 * polling), decides whether to let the app through, and sets ONE timer for
 * when its time runs out. Everything stops while the screen is off.
 *
 * Heavy work (reading the usage log) runs on a small worker thread; views and
 * activity launches go back to the main thread.
 */
class GuardService : AccessibilityService() {

    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    private val main = Handler(Looper.getMainLooper())
    private lateinit var overlays: Overlays

    // Worker-thread state.
    private var current: String? = null
    private var pass: String? = null
    private var inOwnUi = false
    private var screenOn = true
    private var presentHandled = false
    private var launchers: Set<String> = emptySet()
    private var ignored: Set<String> = emptySet()

    // Doomscroll detection.
    @Volatile private var scrollEnabled = false
    @Volatile private var lastScrollPosted = 0L
    private var scrollStreakStart = 0L
    private var lastScroll = 0L
    private var lastNudge = 0L

    private val timerToken = Any()

    private val app get() = application as RegainApp

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        workerThread = HandlerThread("regain-guard").also { it.start() }
        worker = Handler(workerThread.looper)
        overlays = Overlays(this)
        worker.post { refreshSystemPackages() }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, pkgFilter)
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        main.post { overlays.clearAll() }
        if (::workerThread.isInitialized) workerThread.quitSafely()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> worker.post { onWindow(pkg) }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!scrollEnabled) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastScrollPosted < 700) return // throttle before hopping threads
                lastScrollPosted = now
                worker.post { onScroll(pkg) }
            }
        }
    }

    // ------------------------------------------------------------ decisions

    private fun refreshSystemPackages() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        launchers = packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }.toSet()
        val imes = runCatching {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        }.getOrDefault(emptyList())
        ignored = imes.toSet() + setOf("com.android.systemui", "android")
        app.catalog.invalidate()
    }

    private fun log(msg: String) { if (BuildConfig.DEBUG) Log.d("RegainGuard", msg) }

    private fun onWindow(pkg: String) {
        log("window $pkg current=$current inOwnUi=$inOwnUi pass=$pass")
        // Our own windows are tracked through activity lifecycle instead
        // (see ownUiResumed): their accessibility events can arrive late,
        // e.g. while a gate is animating out, and would cancel live timers.
        if (pkg == packageName) return
        if (pkg in ignored) return
        // Ignore transient system windows that are not real apps.
        if (pkg !in launchers && !app.catalog.isLaunchable(pkg)) return

        val switched = pkg != current || inOwnUi
        inOwnUi = false
        if (!switched) return

        leave()
        current = pkg
        if (pkg in launchers) { pass = null; return }
        if (pass != pkg) pass = null
        evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        if (!screenOn) return
        val now = System.currentTimeMillis()
        if (!Store.isLimited(pkg)) return

        if (Store.focus(now) != null) return shield(ShieldMode.Focus, pkg)
        if (Store.isDowntime(now)) return shield(ShieldMode.Downtime, pkg)
        if (Store.morningGateRemaining(now) > 0) return shield(ShieldMode.Morning, pkg)

        val allowedMs = (Store.allowedMinutes(pkg) ?: return) * TimeUtil.MINUTE
        val used = app.usage.usedToday(pkg, now)
        val remaining = allowedMs - used
        log("evaluate $pkg used=$used allowed=$allowedMs remaining=$remaining pass=$pass")
        if (remaining <= 0) return shield(ShieldMode.TimesUp, pkg)

        if (Store.pauseOn && pass != pkg) return shield(ShieldMode.Pause, pkg)

        schedule(pkg, remaining)
        setScrollEvents(Store.doomscrollOn)
    }

    private fun schedule(pkg: String, remaining: Long) {
        cancelTimers()
        log("schedule $pkg in $remaining ms")
        val warnAt = remaining - 5 * TimeUtil.MINUTE
        val start = SystemClock.uptimeMillis()

        if (warnAt > 0) {
            worker.postAtTime({
                if (current == pkg && !inOwnUi) {
                    Haptics.play(Haptics.Kind.Warning)
                    val label = app.catalog.label(pkg)
                    main.post { overlays.showPill("5 minutes left on $label", 0xFFFFB340.toInt()) }
                }
            }, timerToken, start + warnAt)
        }

        if (Store.fadeOn) {
            // Ramp the dim over the last five minutes, updating every 20 s.
            val fadeStart = warnAt.coerceAtLeast(0)
            val fadeTick = object : Runnable {
                override fun run() {
                    if (current != pkg || inOwnUi) return
                    val elapsed = SystemClock.uptimeMillis() - start
                    val left = remaining - elapsed
                    val level = 1f - (left.toFloat() / (5 * TimeUtil.MINUTE)).coerceIn(0f, 1f)
                    main.post { overlays.setDim(level) }
                    if (left > 0) worker.postAtTime(this, timerToken, SystemClock.uptimeMillis() + 20_000)
                }
            }
            worker.postAtTime(fadeTick, timerToken, start + fadeStart)
        }

        worker.postAtTime({
            if (current == pkg && !inOwnUi && screenOn) shield(ShieldMode.TimesUp, pkg)
        }, timerToken, start + remaining)
    }

    private fun cancelTimers() {
        worker.removeCallbacksAndMessages(timerToken)
        main.post { overlays.clearDim() }
    }

    private fun leave() {
        cancelTimers()
        setScrollEvents(false)
        scrollStreakStart = 0
    }

    private fun shield(mode: ShieldMode, pkg: String) {
        log("shield $mode $pkg")
        cancelTimers()
        setScrollEvents(false)
        main.post {
            runCatching { startActivity(ShieldActivity.intent(this, mode, pkg)) }
        }
    }

    // ------------------------------------------------------------ doomscroll

    private fun setScrollEvents(on: Boolean) {
        if (scrollEnabled == on) return
        scrollEnabled = on
        main.post {
            val info = serviceInfo ?: return@post
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                (if (on) AccessibilityEvent.TYPE_VIEW_SCROLLED else 0)
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            runCatching { serviceInfo = info }
        }
    }

    private fun onScroll(pkg: String) {
        if (pkg != current || inOwnUi) return
        val t = SystemClock.elapsedRealtime()
        if (t - lastScroll > 6_000) scrollStreakStart = t
        lastScroll = t
        if (t - scrollStreakStart > 3 * TimeUtil.MINUTE && t - lastNudge > 5 * TimeUtil.MINUTE) {
            lastNudge = t
            scrollStreakStart = t
            Store.recordDoomscrollNudge()
            Haptics.play(Haptics.Kind.Warning)
            main.post { overlays.showPill("Still scrolling on purpose?", 0xFFFF7EB6.toInt(), 3200) }
        }
    }

    // ------------------------------------------------------------ screen

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> worker.post {
                    screenOn = false
                    presentHandled = false
                    leave()
                }
                Intent.ACTION_SCREEN_ON -> {
                    val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
                    if (!locked) worker.post { onPresent() } else worker.post { screenOn = true }
                }
                Intent.ACTION_USER_PRESENT -> worker.post { onPresent() }
            }
        }
    }

    private fun onPresent() {
        screenOn = true
        if (presentHandled) return
        presentHandled = true
        val n = Store.recordUnlock()
        val focus = Store.focus()
        if (focus != null) {
            Haptics.play(Haptics.Kind.Sad)
            val left = TimeUtil.formatDuration(focus.remaining())
            main.post { overlays.showPill("Focus is running · $left left", 0xFF8B7CFF.toInt(), 2800) }
        } else if (Store.unlockPillOn) {
            main.postDelayed({ overlays.showPill("Unlock #$n today") }, 350)
        }
        // Re-check the app that was open before the screen went off.
        val pkg = current
        if (pkg != null && pkg !in launchers && !inOwnUi) evaluate(pkg)
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            worker.post { refreshSystemPackages() }
        }
    }

    // ------------------------------------------------------------ API for UI

    private fun allow(pkg: String) = worker.post { pass = pkg }

    /** One of our activities is now in front: pause timers until the user comes back. */
    private fun onOwnUi() = worker.post {
        log("own UI resumed")
        inOwnUi = true
        cancelTimers()
        setScrollEvents(false)
    }

    private fun recheck() = worker.post {
        val pkg = current ?: return@post
        if (!inOwnUi && pkg !in launchers) evaluate(pkg)
    }

    companion object {
        @Volatile var instance: GuardService? = null
            private set

        /** Let [pkg] through until the user leaves it. */
        fun grantPass(pkg: String) { instance?.allow(pkg) }

        /** Limits changed: re-evaluate whatever is in front. */
        fun refresh() { instance?.recheck() }

        /** Called from our activities' onResume. Returning to an app afterwards counts as a fresh switch. */
        fun ownUiResumed() { instance?.onOwnUi() }

        fun isRunning(): Boolean = instance != null

        @Suppress("unused")
        fun isInteractive(context: Context) =
            context.getSystemService(PowerManager::class.java)?.isInteractive == true
    }
}
