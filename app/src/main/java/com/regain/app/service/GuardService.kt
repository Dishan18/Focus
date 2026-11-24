package com.regain.app.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.core.app.ServiceCompat
import com.regain.app.BuildConfig
import com.regain.app.R
import com.regain.app.RegainApp
import com.regain.app.core.Grayscale
import com.regain.app.core.Haptics
import com.regain.app.core.Permissions
import com.regain.app.core.TimeUtil
import com.regain.app.data.Store
import com.regain.app.ui.MainActivity
import com.regain.app.ui.shield.ShieldActivity
import com.regain.app.ui.shield.ShieldMode
import com.regain.app.widget.FocusWidget

/**
 * The engine. A small foreground service that, only while the screen is on,
 * reads the newest entries in Android's usage log about once a second to see
 * which app is in front. It decides whether to let the app through and sets
 * ONE timer for when its time runs out. Everything stops while the screen is
 * off.
 *
 * No accessibility service is used, so banking and UPI apps don't block
 * payments. Gates are shown with the "Display over other apps" permission.
 *
 * Heavy work runs on a small worker thread; views and activity launches go
 * back to the main thread.
 */
class GuardService : Service() {

    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    private val main = Handler(Looper.getMainLooper())
    private lateinit var overlays: Overlays
    private var usm: UsageStatsManager? = null
    private var started = false

    // Worker-thread state.
    private var current: String? = null
    private var pass: String? = null
    private var inOwnUi = false
    /** True between our activity's onResume and onPause: nothing else can be in front. */
    private var ownUiVisible = false
    private var screenOn = true
    private var presentHandled = false
    private var launchers: Set<String> = emptySet()
    private var ignored: Set<String> = emptySet()

    /** Timestamp of the newest usage event already handled. */
    private var lastEventTs = 0L
    private val pollToken = Any()
    private val timerToken = Any()
    private val shieldToken = Any()

    private val app get() = application as RegainApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        usm = getSystemService(UsageStatsManager::class.java)
        workerThread = HandlerThread("regain-guard").also { it.start() }
        worker = Handler(workerThread.looper)
        overlays = Overlays(this)
        screenOn = getSystemService(PowerManager::class.java)?.isInteractive != false
        lastEventTs = System.currentTimeMillis()
        worker.post {
            refreshSystemPackages()
            // Safety: never leave the screen gray from a previous run.
            Grayscale.off(this)
            scheduleWindDown()
            if (screenOn) startPolling()
        }

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            val ok = runCatching {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification(),
                    if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
                )
            }.isSuccess
            if (!ok) {
                started = false
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        main.removeCallbacksAndMessages(null)
        overlays.clearAll()
        Grayscale.off(this)
        if (::workerThread.isInitialized) workerThread.quitSafely()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm?.getNotificationChannel(CHANNEL) == null) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Focus is active", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Keeps your limits working. You can hide this."
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Focus is active")
            .setContentText("Your limits are on.")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    // ------------------------------------------------------------ watching

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!screenOn) return
            runCatching { poll() }.onFailure { log("poll failed: $it") }
            worker.postAtTime(this, pollToken, SystemClock.uptimeMillis() + POLL_MS)
        }
    }

    private fun startPolling() {
        worker.removeCallbacksAndMessages(pollToken)
        // Don't replay a whole night of events on wake: an hour is plenty.
        val now = System.currentTimeMillis()
        if (lastEventTs < now - TimeUtil.HOUR || lastEventTs > now + TimeUtil.MINUTE) lastEventTs = now - TimeUtil.HOUR
        worker.postAtTime(pollRunnable, pollToken, SystemClock.uptimeMillis())
    }

    private fun stopPolling() = worker.removeCallbacksAndMessages(pollToken)

    /**
     * Reads usage events newer than the last one handled and reacts to the
     * newest app that came to the front. Only the final state matters, so
     * quick hops through several apps don't flash gates for each of them.
     */
    private fun poll() {
        val usm = usm ?: return
        val now = System.currentTimeMillis()
        // The clock went backwards (time zone or manual change): start over.
        if (lastEventTs > now + TimeUtil.MINUTE) lastEventTs = now - 2_000
        val events = usm.queryEvents(lastEventTs - 5_000, now + 60_000) ?: return
        val e = UsageEvents.Event()
        var candidate: String? = null
        var newest = lastEventTs
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val t = e.timeStamp
            if (t <= lastEventTs) continue
            if (t > newest) newest = t
            val pkg = e.packageName ?: continue
            candidate = when {
                // Our own screens are tracked through activity lifecycle
                // (see ownUiResumed); anything before them is stale.
                pkg == packageName -> null
                isRealApp(pkg) -> pkg
                else -> candidate
            }
        }
        lastEventTs = newest
        candidate?.let { onWindow(it) }
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

    /** Transient system windows (keyboards, dialogs, setup screens) aren't app switches. */
    private fun isRealApp(pkg: String): Boolean {
        if (pkg in ignored) return false
        return pkg in launchers || app.catalog.isLaunchable(pkg)
    }

    private fun log(msg: String) { if (BuildConfig.DEBUG) Log.d("RegainGuard", msg) }

    private fun onWindow(pkg: String) {
        log("window $pkg current=$current inOwnUi=$inOwnUi pass=$pass")
        // Late events from the app underneath a gate must not re-trigger it.
        if (ownUiVisible) return

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

        // Focus blocks limited apps plus Google search and Chrome.
        if (Store.focus(now) != null && Store.blockedInFocus(pkg)) return shield(ShieldMode.Focus, pkg)

        val budgets = Store.budgetsFor(pkg)
        if (budgets.isEmpty()) return

        if (Store.isDowntime(now)) return shield(ShieldMode.Downtime, pkg)
        if (Store.morningGateRemaining(now) > 0) return shield(ShieldMode.Morning, pkg)

        // The tightest budget wins (own limit or a group it belongs to).
        var remaining = Long.MAX_VALUE
        var tightest = budgets.first()
        for (b in budgets) {
            val allowedMs = (Store.allowedMinutes(b.key) ?: continue) * TimeUtil.MINUTE
            val left = allowedMs - app.usage.usedToday(b.pkgs, now)
            if (left < remaining) { remaining = left; tightest = b }
        }
        if (remaining == Long.MAX_VALUE) return
        log("evaluate $pkg budget=${tightest.key} remaining=$remaining pass=$pass")
        if (remaining <= 0) return shield(ShieldMode.TimesUp, pkg, tightest.key)

        if (Store.pauseOn && pass != pkg) return shield(ShieldMode.Pause, pkg)

        schedule(pkg, remaining, tightest.key)
    }

    private fun schedule(pkg: String, remaining: Long, key: String) {
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
                    main.post {
                        overlays.setDim(level)
                        if (Store.grayscaleOn) Grayscale.on(this@GuardService)
                    }
                    if (left > 0) worker.postAtTime(this, timerToken, SystemClock.uptimeMillis() + 20_000)
                }
            }
            worker.postAtTime(fadeTick, timerToken, start + fadeStart)
        }

        worker.postAtTime({
            if (current == pkg && !inOwnUi && screenOn) shield(ShieldMode.TimesUp, pkg, key)
        }, timerToken, start + remaining)
    }

    private fun cancelTimers() {
        worker.removeCallbacksAndMessages(timerToken)
        main.post {
            overlays.clearDim()
            Grayscale.off(this@GuardService)
        }
    }

    private fun leave() {
        cancelTimers()
        worker.removeCallbacksAndMessages(shieldToken)
        main.post { overlays.removeCover() }
    }

    /**
     * Shows a gate. A black cover goes up first so the app disappears at once
     * (and so Android sees a visible Focus window, which allows the launch).
     * If the gate still hasn't appeared, we try again, then offer a button.
     */
    private fun shield(mode: ShieldMode, pkg: String, key: String = pkg) {
        log("shield $mode $pkg $key")
        cancelTimers()
        worker.removeCallbacksAndMessages(shieldToken)
        val launch = {
            runCatching { startActivity(ShieldActivity.intent(this, mode, pkg, key)) }
                .onFailure { log("shield launch failed: $it") }
        }
        main.post {
            overlays.showCover()
            launch()
        }
        worker.postAtTime({
            if (!ownUiVisible && current == pkg) { log("shield retry"); main.post { launch() } }
        }, shieldToken, SystemClock.uptimeMillis() + 1_500)
        worker.postAtTime({
            if (!ownUiVisible && current == pkg) {
                log("shield fallback")
                val label = app.catalog.label(pkg)
                main.post { overlays.showCoverButton("$label is paused by Focus") { launch() } }
            }
        }, shieldToken, SystemClock.uptimeMillis() + 3_000)
    }

    // ------------------------------------------------------------ wind-down

    private val windToken = Any()

    /** One gentle reminder at bedtime while the phone is in use. */
    private fun scheduleWindDown() {
        worker.removeCallbacksAndMessages(windToken)
        if (!Store.windDownOn || !screenOn) return
        val now = System.currentTimeMillis()
        val minute = TimeUtil.minuteOfDay(now)
        val bed = Store.bedtime
        if (TimeUtil.inWindow(minute, bed, (bed + 180) % 1440)) {
            if (now - Store.windDownLastShown > 12 * TimeUtil.HOUR) remindWindDown()
            return
        }
        val inMin = ((bed - minute + 1440) % 1440).toLong()
        val secs = java.util.Calendar.getInstance().get(java.util.Calendar.SECOND)
        val delay = inMin * TimeUtil.MINUTE - secs * 1000L
        worker.postAtTime({ scheduleWindDown() }, windToken, SystemClock.uptimeMillis() + delay.coerceAtLeast(1000))
    }

    private fun remindWindDown() {
        Store.windDownLastShown = System.currentTimeMillis()
        Haptics.play(Haptics.Kind.Breath)
        val time = TimeUtil.formatMinuteOfDay(Store.bedtime)
        main.postDelayed({ overlays.showPill("It's past $time · time to wind down", 0xFF5B4BDB.toInt(), 3600) }, 600)
    }

    // ------------------------------------------------------------ screen

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> worker.post {
                    screenOn = false
                    presentHandled = false
                    stopPolling()
                    leave()
                    worker.removeCallbacksAndMessages(windToken)
                }
                Intent.ACTION_SCREEN_ON -> {
                    val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
                    if (!locked) worker.post { onPresent() } else worker.post { screenOn = true; startPolling() }
                }
                Intent.ACTION_USER_PRESENT -> worker.post { onPresent() }
            }
        }
    }

    private fun onPresent() {
        if (!screenOn) { screenOn = true; startPolling() }
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
        scheduleWindDown()
        FocusWidget.requestUpdate(this, throttle = true)
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
        ownUiVisible = true
        worker.removeCallbacksAndMessages(shieldToken)
        cancelTimers()
        // Our screen is drawn now; lift the cover from over it.
        main.postDelayed({ overlays.removeCover() }, 120)
    }

    private fun onOwnUiPaused() = worker.post { ownUiVisible = false }

    private fun recheck() = worker.post {
        val pkg = current ?: return@post
        if (!inOwnUi && pkg !in launchers) evaluate(pkg)
    }

    companion object {
        private const val CHANNEL = "guard"
        private const val NOTIFICATION_ID = 7
        private const val POLL_MS = 750L

        @Volatile var instance: GuardService? = null
            private set

        /**
         * Starts the engine if setup is complete. Safe to call often and from
         * anywhere: Android may refuse a start from the background, which is
         * fine because the next app open starts it again.
         */
        fun start(context: Context) {
            if (instance != null) return
            val ctx = context.applicationContext
            if (!Permissions.hasUsageAccess(ctx) || !Permissions.canDrawOverlays(ctx)) return
            runCatching { ctx.startForegroundService(Intent(ctx, GuardService::class.java)) }
        }

        /** Let [pkg] through until the user leaves it. */
        fun grantPass(pkg: String) { instance?.allow(pkg) }

        /** Limits changed: re-evaluate whatever is in front. */
        fun refresh() { instance?.recheck() }

        /** Called from our activities' onResume. Returning to an app afterwards counts as a fresh switch. */
        fun ownUiResumed() { instance?.onOwnUi() }

        /** Called from our activities' onPause. */
        fun ownUiPaused() { instance?.onOwnUiPaused() }

        fun isRunning(): Boolean = instance != null
    }
}
