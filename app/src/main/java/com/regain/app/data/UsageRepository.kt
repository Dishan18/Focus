package com.regain.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.regain.app.core.TimeUtil

/**
 * Reads screen time straight from Android's own usage log, so Focus never
 * runs a second tracker in the background.
 *
 * Foreground time is rebuilt from RESUMED / PAUSED events per activity, so
 * activity switches inside one app are not double counted or lost.
 */
class UsageRepository(private val context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)

    data class AppUsage(val pkg: String, val timeMs: Long, val opens: Int)

    data class DayUsage(
        val dayStart: Long,
        val apps: List<AppUsage>,
        val unlocks: Int,
    ) {
        val totalMs: Long get() = apps.sumOf { it.timeMs }
        fun timeOf(pkg: String): Long = apps.firstOrNull { it.pkg == pkg }?.timeMs ?: 0
    }

    /** Packages that are "the phone" rather than an app: launchers, system UI, us. */
    private val ignored: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = context.packageManager
            .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
        launchers.toSet() + setOf(context.packageName, "com.android.systemui", "android")
    }

    /** Only apps with a home-screen icon count; setup wizards and system services don't. */
    private val launchable = HashMap<String, Boolean>()

    private fun counts(pkg: String): Boolean {
        if (pkg in ignored) return false
        return synchronized(launchable) {
            launchable.getOrPut(pkg) { context.packageManager.getLaunchIntentForPackage(pkg) != null }
        }
    }

    /** Foreground time today for one package; cheap enough to call on app switch. */
    fun usedToday(pkg: String, now: Long = System.currentTimeMillis()): Long =
        compute(TimeUtil.startOfDay(now), now, onlyPkg = pkg).firstOrNull()?.apps?.firstOrNull()?.timeMs ?: 0

    fun today(now: Long = System.currentTimeMillis()): DayUsage =
        compute(TimeUtil.startOfDay(now), now).first()

    /** The last [days] days, oldest first, today last. */
    fun lastDays(days: Int, now: Long = System.currentTimeMillis()): List<DayUsage> {
        val todayStart = TimeUtil.startOfDay(now)
        val start = TimeUtil.startOfDay(todayStart - (days - 1) * TimeUtil.DAY + TimeUtil.HOUR)
        return compute(start, now)
    }

    /**
     * One pass over the event log. Sessions are split across midnight so each
     * day gets exactly its own share.
     */
    private fun compute(start: Long, end: Long, onlyPkg: String? = null): List<DayUsage> {
        // Day boundaries.
        val dayStarts = mutableListOf<Long>()
        var d = TimeUtil.startOfDay(start)
        while (d <= end) {
            dayStarts += d
            d = TimeUtil.startOfDay(d + TimeUtil.DAY + TimeUtil.HOUR) // DST-safe step
        }
        val time = Array(dayStarts.size) { HashMap<String, Long>() }
        val opens = Array(dayStarts.size) { HashMap<String, Int>() }
        val unlocks = IntArray(dayStarts.size)

        fun dayIndex(t: Long): Int {
            var i = dayStarts.size - 1
            while (i > 0 && t < dayStarts[i]) i--
            return i
        }

        fun addTime(pkg: String, from: Long, to: Long) {
            var a = from.coerceAtLeast(start)
            while (a < to) {
                val i = dayIndex(a)
                val dayEnd = if (i + 1 < dayStarts.size) dayStarts[i + 1] else Long.MAX_VALUE
                val b = minOf(to, dayEnd)
                time[i][pkg] = (time[i][pkg] ?: 0L) + (b - a)
                a = b
            }
        }

        // Look back a little so an app already open at `start` is caught.
        val events = usm?.queryEvents(start - 2 * TimeUtil.HOUR, end) ?: return emptyDays(dayStarts)
        val e = UsageEvents.Event()
        // pkg -> resumed activity classes; pkg -> session start.
        val resumed = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()

        fun closeAll(t: Long) {
            for ((pkg, s) in since) addTime(pkg, s, t)
            since.clear(); resumed.clear()
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val t = e.timeStamp
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (onlyPkg != null && pkg != onlyPkg) continue
                    if (onlyPkg == null && !counts(pkg)) continue
                    val set = resumed.getOrPut(pkg) { HashSet() }
                    if (set.isEmpty()) {
                        since[pkg] = t
                        if (t >= start) {
                            val i = dayIndex(t)
                            opens[i][pkg] = (opens[i][pkg] ?: 0) + 1
                        }
                    }
                    set += e.className ?: ""
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val set = resumed[pkg] ?: continue
                    set -= e.className ?: ""
                    if (set.isEmpty()) {
                        since.remove(pkg)?.let { s -> addTime(pkg, s, t) }
                        resumed.remove(pkg)
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> closeAll(t)
                UsageEvents.Event.KEYGUARD_HIDDEN -> if (t >= start) unlocks[dayIndex(t)]++
            }
        }
        closeAll(end)

        return dayStarts.indices.map { i ->
            DayUsage(
                dayStart = dayStarts[i],
                apps = time[i].map { (p, ms) -> AppUsage(p, ms, opens[i][p] ?: 0) }
                    .filter { it.timeMs >= 1000 }
                    .sortedByDescending { it.timeMs },
                unlocks = unlocks[i],
            )
        }
    }

    private fun emptyDays(starts: List<Long>) = starts.map { DayUsage(it, emptyList(), 0) }
}
