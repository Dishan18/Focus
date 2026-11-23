package com.regain.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableLongStateOf
import com.regain.app.core.TimeUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * All of Focus's own state, in one small SharedPreferences file.
 *
 * Screen time itself is NOT stored here: Android already records it, and we
 * read it on demand (see [UsageRepository]). That avoids a second always-on
 * tracker and keeps battery and storage use tiny.
 *
 * [changes] ticks on every write so Compose screens can recompose.
 */
object Store {
    private lateinit var raw: SharedPreferences
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes

    /**
     * Snapshot-state version. Every read goes through [prefs], which reads
     * this, so any composable that reads a setting recomposes when it changes.
     */
    private val version = mutableLongStateOf(0L)

    private val prefs: SharedPreferences
        get() {
            version.longValue
            return raw
        }

    fun init(context: Context) {
        raw = context.getSharedPreferences("regain", Context.MODE_PRIVATE)
    }

    private fun bump() {
        version.longValue++
        _changes.value = _changes.value + 1
    }

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        bump()
    }

    // ------------------------------------------------------------ constants

    const val MAX_EXTENSIONS_PER_APP = 2
    const val MAX_EXTENSIONS_PER_DAY = 4
    const val EMERGENCY_MINUTES = 15

    // ------------------------------------------------------------ settings

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = edit { putBoolean("onboarded", v) }

    var hapticsOn: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = edit { putBoolean("haptics", v) }

    /** 0 = math, 1 = typing. */
    var challengeType: Int
        get() = prefs.getInt("challengeType", 0)
        set(v) = edit { putInt("challengeType", v) }

    var pauseOn: Boolean
        get() = prefs.getBoolean("pauseOn", true)
        set(v) = edit { putBoolean("pauseOn", v) }

    var growingDelayOn: Boolean
        get() = prefs.getBoolean("growingDelay", true)
        set(v) = edit { putBoolean("growingDelay", v) }

    var fadeOn: Boolean
        get() = prefs.getBoolean("fadeOn", true)
        set(v) = edit { putBoolean("fadeOn", v) }

    var doomscrollOn: Boolean
        get() = prefs.getBoolean("doomscroll", true)
        set(v) = edit { putBoolean("doomscroll", v) }

    var unlockPillOn: Boolean
        get() = prefs.getBoolean("unlockPill", true)
        set(v) = edit { putBoolean("unlockPill", v) }

    var morningGateOn: Boolean
        get() = prefs.getBoolean("morningGate", false)
        set(v) = edit { putBoolean("morningGate", v) }

    var morningGateMinutes: Int
        get() = prefs.getInt("morningGateMin", 30)
        set(v) = edit { putInt("morningGateMin", v) }

    var nightOn: Boolean
        get() = prefs.getBoolean("nightOn", true)
        set(v) = edit { putBoolean("nightOn", v) }

    /** Minute of day night mode starts (ends at 5:00). */
    var nightStart: Int
        get() = prefs.getInt("nightStart", 22 * 60)
        set(v) = edit { putInt("nightStart", v) }

    var downtimeOn: Boolean
        get() = prefs.getBoolean("downtimeOn", false)
        set(v) = edit { putBoolean("downtimeOn", v) }

    var downtimeStart: Int
        get() = prefs.getInt("downtimeStart", 23 * 60 + 30)
        set(v) = edit { putInt("downtimeStart", v) }

    var downtimeEnd: Int
        get() = prefs.getInt("downtimeEnd", 7 * 60)
        set(v) = edit { putInt("downtimeEnd", v) }

    /** Daily total screen-time goal in minutes. */
    var dailyGoalMin: Int
        get() = prefs.getInt("dailyGoal", 180)
        set(v) = edit { putInt("dailyGoal", v) }

    fun isNight(now: Long = System.currentTimeMillis()): Boolean =
        nightOn && TimeUtil.inWindow(TimeUtil.minuteOfDay(now), nightStart, 5 * 60)

    fun isDowntime(now: Long = System.currentTimeMillis()): Boolean =
        downtimeOn && TimeUtil.inWindow(TimeUtil.minuteOfDay(now), downtimeStart, downtimeEnd)

    // ------------------------------------------------------------ limits

    /**
     * package -> daily minutes. Tightening a limit applies at once; loosening
     * (raising or removing) waits until tomorrow, so a limit can't be dodged
     * the moment it bites.
     */
    fun limits(): Map<String, Int> {
        applyPendingLimits()
        val o = JSONObject(prefs.getString("limits", "{}") ?: "{}")
        return buildMap { o.keys().forEach { put(it, o.getInt(it)) } }
    }

    /** package -> minutes it becomes tomorrow (0 = removed). */
    fun pendingLimits(): Map<String, Int> {
        val p = JSONObject(prefs.getString("pendingLimits", "{}") ?: "{}")
        return buildMap { p.keys().forEach { put(it, p.getJSONObject(it).getInt("m")) } }
    }

    private fun applyPendingLimits() {
        val raw = prefs.getString("pendingLimits", "{}") ?: "{}"
        if (raw == "{}") return
        val p = JSONObject(raw)
        val today = TimeUtil.dayKey()
        val o = JSONObject(prefs.getString("limits", "{}") ?: "{}")
        var changed = false
        p.keys().asSequence().toList().forEach { pkg ->
            val e = p.getJSONObject(pkg)
            if (e.getInt("d") < today) {
                val m = e.getInt("m")
                if (m <= 0) o.remove(pkg) else o.put(pkg, m)
                p.remove(pkg)
                changed = true
            }
        }
        if (changed) {
            prefs.edit().putString("limits", o.toString()).putString("pendingLimits", p.toString()).apply()
        }
    }

    /** Returns true if applied now, false if scheduled for tomorrow. */
    fun setLimit(pkg: String, minutes: Int): Boolean {
        val o = JSONObject(prefs.getString("limits", "{}") ?: "{}")
        val current = if (o.has(pkg)) o.getInt(pkg) else null
        val p = JSONObject(prefs.getString("pendingLimits", "{}") ?: "{}")
        return if (current != null && minutes > current) {
            p.put(pkg, JSONObject().put("m", minutes).put("d", TimeUtil.dayKey()))
            edit { putString("pendingLimits", p.toString()) }
            false
        } else {
            o.put(pkg, minutes)
            p.remove(pkg)
            edit { putString("limits", o.toString()); putString("pendingLimits", p.toString()) }
            true
        }
    }

    /** Removal also waits until tomorrow. */
    fun removeLimit(pkg: String) {
        val p = JSONObject(prefs.getString("pendingLimits", "{}") ?: "{}")
        p.put(pkg, JSONObject().put("m", 0).put("d", TimeUtil.dayKey()))
        edit { putString("pendingLimits", p.toString()) }
    }

    fun cancelPending(pkg: String) {
        val p = JSONObject(prefs.getString("pendingLimits", "{}") ?: "{}")
        p.remove(pkg)
        edit { putString("pendingLimits", p.toString()) }
    }

    /** True if the app has its own limit or belongs to a group. */
    fun isLimited(pkg: String): Boolean = limits().containsKey(pkg) || groups().values.any { pkg in it.pkgs }

    // ------------------------------------------------------------ groups

    /** A shared daily budget across several apps, e.g. "Social · 1h". */
    data class Group(val id: String, val name: String, val minutes: Int, val pkgs: Set<String>)

    /** A budget is either one app (key = package) or a group (key = "g:<id>"). */
    data class Budget(val key: String, val label: String?, val pkgs: Set<String>, val baseMinutes: Int) {
        val isGroup get() = key.startsWith("g:")
    }

    private fun groupFromJson(id: String, o: JSONObject): Group {
        val arr = o.optJSONArray("pkgs") ?: JSONArray()
        return Group(id, o.optString("name", "Group"), o.optInt("min", 60), (0 until arr.length()).map { arr.getString(it) }.toSet())
    }

    private fun groupToJson(g: Group) = JSONObject()
        .put("name", g.name).put("min", g.minutes).put("pkgs", JSONArray(g.pkgs.toList()))

    fun groups(): Map<String, Group> {
        applyPendingGroups()
        val o = JSONObject(prefs.getString("groups", "{}") ?: "{}")
        return buildMap { o.keys().forEach { id -> put(id, groupFromJson(id, o.getJSONObject(id))) } }
    }

    /** id -> what the group becomes tomorrow (null = deleted). */
    fun pendingGroups(): Map<String, Group?> {
        val p = JSONObject(prefs.getString("pendingGroups", "{}") ?: "{}")
        return buildMap {
            p.keys().forEach { id ->
                val cfg = p.getJSONObject(id).optJSONObject("cfg")
                put(id, cfg?.let { groupFromJson(id, it) })
            }
        }
    }

    private fun applyPendingGroups() {
        val raw = prefs.getString("pendingGroups", "{}") ?: "{}"
        if (raw == "{}") return
        val p = JSONObject(raw)
        val today = TimeUtil.dayKey()
        val o = JSONObject(prefs.getString("groups", "{}") ?: "{}")
        var changed = false
        p.keys().asSequence().toList().forEach { id ->
            val e = p.getJSONObject(id)
            if (e.getInt("d") < today) {
                val cfg = e.optJSONObject("cfg")
                if (cfg == null) o.remove(id) else o.put(id, cfg)
                p.remove(id)
                changed = true
            }
        }
        if (changed) prefs.edit().putString("groups", o.toString()).putString("pendingGroups", p.toString()).apply()
    }

    /**
     * Save a group. Stricter edits (fewer minutes, more apps) apply now;
     * anything looser waits until tomorrow. Returns true if applied now.
     */
    fun saveGroup(g: Group): Boolean {
        val o = JSONObject(prefs.getString("groups", "{}") ?: "{}")
        val p = JSONObject(prefs.getString("pendingGroups", "{}") ?: "{}")
        val current = if (o.has(g.id)) groupFromJson(g.id, o.getJSONObject(g.id)) else null
        val looser = current != null && (g.minutes > current.minutes || !g.pkgs.containsAll(current.pkgs))
        return if (looser) {
            p.put(g.id, JSONObject().put("d", TimeUtil.dayKey()).put("cfg", groupToJson(g)))
            edit { putString("pendingGroups", p.toString()) }
            false
        } else {
            o.put(g.id, groupToJson(g))
            p.remove(g.id)
            edit { putString("groups", o.toString()); putString("pendingGroups", p.toString()) }
            true
        }
    }

    /** Deleting a group also waits until tomorrow. */
    fun deleteGroup(id: String) {
        val p = JSONObject(prefs.getString("pendingGroups", "{}") ?: "{}")
        p.put(id, JSONObject().put("d", TimeUtil.dayKey()).put("cfg", JSONObject.NULL))
        edit { putString("pendingGroups", p.toString()) }
    }

    fun cancelPendingGroup(id: String) {
        val p = JSONObject(prefs.getString("pendingGroups", "{}") ?: "{}")
        p.remove(id)
        edit { putString("pendingGroups", p.toString()) }
    }

    fun newGroupId(): String = System.currentTimeMillis().toString(36)

    /** Every budget that applies to [pkg]: its own limit and any groups it is in. */
    fun budgetsFor(pkg: String): List<Budget> = buildList {
        limits()[pkg]?.let { add(Budget(pkg, null, setOf(pkg), it)) }
        groups().values.filter { pkg in it.pkgs }.forEach { add(Budget("g:${it.id}", it.name, it.pkgs, it.minutes)) }
    }

    fun budget(key: String): Budget? =
        if (key.startsWith("g:")) groups()[key.removePrefix("g:")]?.let { Budget(key, it.name, it.pkgs, it.minutes) }
        else limits()[key]?.let { Budget(key, null, setOf(key), it) }

    // ------------------------------------------------------------ weekends

    /** 0 = same as weekdays, 1 = 1.5x, 2 = 2x on Saturday and Sunday. */
    var weekendMode: Int
        get() = prefs.getInt("weekendMode", 0)
        set(v) = edit { putInt("weekendMode", v) }

    fun isWeekend(now: Long = System.currentTimeMillis()): Boolean {
        val d = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.DAY_OF_WEEK)
        return d == java.util.Calendar.SATURDAY || d == java.util.Calendar.SUNDAY
    }

    /** A base limit adjusted for weekends. */
    fun scaled(minutes: Int, now: Long = System.currentTimeMillis()): Int =
        if (!isWeekend(now)) minutes else when (weekendMode) {
            1 -> (minutes * 1.5f).toInt()
            2 -> minutes * 2
            else -> minutes
        }

    // ------------------------------------------------------------ daily counters

    /** Today's counters; reset automatically when the day changes. */
    private fun today(): JSONObject {
        val key = TimeUtil.dayKey()
        val raw = prefs.getString("day", null)
        val o = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (o != null && o.optInt("key") == key) return o
        // Day rolled over: archive yesterday's summary first.
        if (o != null) archiveDay(o)
        return JSONObject().put("key", key)
    }

    private fun saveToday(o: JSONObject) = edit { putString("day", o.toString()) }

    private fun obj(o: JSONObject, name: String): JSONObject =
        o.optJSONObject(name) ?: JSONObject().also { o.put(name, it) }

    fun extensionsUsed(pkg: String): Int = today().optJSONObject("ext")?.optInt(pkg) ?: 0
    fun extensionsUsedTotal(): Int = today().optInt("extTotal")
    fun extraMinutes(pkg: String): Int = today().optJSONObject("extMin")?.optInt(pkg) ?: 0

    fun canExtend(pkg: String): Boolean =
        extensionsUsed(pkg) < MAX_EXTENSIONS_PER_APP && extensionsUsedTotal() < MAX_EXTENSIONS_PER_DAY

    fun addExtension(pkg: String, minutes: Int) {
        val o = today()
        val ext = obj(o, "ext")
        ext.put(pkg, ext.optInt(pkg) + 1)
        val extMin = obj(o, "extMin")
        extMin.put(pkg, extMin.optInt(pkg) + minutes)
        o.put("extTotal", o.optInt("extTotal") + 1)
        saveToday(o)
    }

    /** Minutes allowed today for [pkg], including extensions. Null if not limited. */
    fun allowedMinutes(key: String): Int? = budget(key)?.let { scaled(it.baseMinutes) + extraMinutes(key) }

    // ------------------------------------------------------------ earned minutes

    /** Minutes earned by finishing focus sessions today (1 per 5 focused, max 20). */
    fun bonusMinutes(): Int = today().optInt("bonus")

    private fun earnBonus(focusedMinutes: Int) {
        if (focusedMinutes <= 0) return
        val o = today()
        o.put("bonus", (o.optInt("bonus") + focusedMinutes / 5).coerceAtMost(MAX_BONUS))
        saveToday(o)
    }

    /** Spend earned minutes on a budget without using an extension. */
    fun spendBonus(key: String, minutes: Int) {
        val o = today()
        val have = o.optInt("bonus")
        val use = minutes.coerceAtMost(have)
        if (use <= 0) return
        o.put("bonus", have - use)
        val extMin = obj(o, "extMin")
        extMin.put(key, extMin.optInt(key) + use)
        saveToday(o)
    }

    const val MAX_BONUS = 20

    // ------------------------------------------------------------ buddy

    var buddyOn: Boolean
        get() = prefs.getBoolean("buddyOn", true)
        set(v) = edit { putBoolean("buddyOn", v) }

    /** Empty = use the build default. */
    var buddyNumber: String
        get() = prefs.getString("buddyNumber", "") ?: ""
        set(v) = edit { putString("buddyNumber", v) }

    // ------------------------------------------------------------ grayscale / wind-down

    var grayscaleOn: Boolean
        get() = prefs.getBoolean("grayscale", false)
        set(v) = edit { putBoolean("grayscale", v) }

    var windDownOn: Boolean
        get() = prefs.getBoolean("windDown", true)
        set(v) = edit { putBoolean("windDown", v) }

    /** Minute of day for the wind-down reminder. */
    var bedtime: Int
        get() = prefs.getInt("bedtime", 23 * 60)
        set(v) = edit { putInt("bedtime", v) }

    var windDownLastShown: Long
        get() = prefs.getLong("windDownShown", 0)
        set(v) = prefs.edit().putLong("windDownShown", v).apply()

    /** Raw access for small helpers (grayscale state) that should not trigger recomposition. */
    internal fun rawPrefs(): SharedPreferences = raw

    /** User chose to close an app at a gate. */
    fun recordReclaim() {
        val o = today()
        o.put("reclaims", o.optInt("reclaims") + 1)
        saveToday(o)
    }

    fun reclaimsToday(): Int = today().optInt("reclaims")

    /** Gate opens per app today, drives the growing delay. */
    fun gateOpens(pkg: String): Int = today().optJSONObject("opens")?.optInt(pkg) ?: 0

    fun recordGateOpen(pkg: String) {
        val o = today()
        val opens = obj(o, "opens")
        opens.put(pkg, opens.optInt(pkg) + 1)
        saveToday(o)
    }

    fun recordUnlock(now: Long = System.currentTimeMillis()): Int {
        val o = today()
        val n = o.optInt("unlocks") + 1
        o.put("unlocks", n)
        if (!o.has("firstUnlock")) o.put("firstUnlock", now)
        saveToday(o)
        return n
    }

    fun unlocksToday(): Int = today().optInt("unlocks")
    fun firstUnlockToday(): Long? = today().let { if (it.has("firstUnlock")) it.optLong("firstUnlock") else null }

    fun doomscrollNudgesToday(): Int = today().optInt("doom")
    fun recordDoomscrollNudge() {
        val o = today()
        o.put("doom", o.optInt("doom") + 1)
        saveToday(o)
    }

    /** Morning gate: ms until limited apps open, or 0 if open. */
    fun morningGateRemaining(now: Long = System.currentTimeMillis()): Long {
        if (!morningGateOn) return 0
        val first = firstUnlockToday() ?: return 0
        val end = first + morningGateMinutes * TimeUtil.MINUTE
        return (end - now).coerceAtLeast(0)
    }

    // ------------------------------------------------------------ history

    /** Store a finished day's summary for weekly insights. Keeps 60 days. */
    private fun archiveDay(o: JSONObject) {
        val h = JSONObject(prefs.getString("history", "{}") ?: "{}")
        h.put(
            o.optInt("key").toString(),
            JSONObject()
                .put("ext", o.optInt("extTotal"))
                .put("reclaims", o.optInt("reclaims"))
                .put("unlocks", o.optInt("unlocks"))
                .put("doom", o.optInt("doom")),
        )
        val keys = h.keys().asSequence().toList().sorted()
        if (keys.size > 60) keys.take(keys.size - 60).forEach { h.remove(it) }
        prefs.edit().putString("history", h.toString()).apply()
    }

    data class DayCounters(val extensions: Int, val reclaims: Int, val unlocks: Int, val doom: Int)

    /** Counters for a given dayKey (today included). */
    fun countersFor(dayKey: Int): DayCounters {
        if (dayKey == TimeUtil.dayKey()) {
            val o = today()
            return DayCounters(o.optInt("extTotal"), o.optInt("reclaims"), o.optInt("unlocks"), o.optInt("doom"))
        }
        val h = JSONObject(prefs.getString("history", "{}") ?: "{}").optJSONObject(dayKey.toString())
            ?: return DayCounters(0, 0, 0, 0)
        return DayCounters(h.optInt("ext"), h.optInt("reclaims"), h.optInt("unlocks"), h.optInt("doom"))
    }

    /** Remember each day's total so streaks survive Android trimming old usage data. */
    fun recordDayTotal(dayKey: Int, totalMs: Long) {
        val t = JSONObject(prefs.getString("totals", "{}") ?: "{}")
        if (t.optLong(dayKey.toString(), -1) == totalMs) return
        t.put(dayKey.toString(), totalMs)
        val keys = t.keys().asSequence().toList().sorted()
        if (keys.size > 90) keys.take(keys.size - 90).forEach { t.remove(it) }
        prefs.edit().putString("totals", t.toString()).apply()
    }

    fun dayTotals(): Map<Int, Long> {
        val t = JSONObject(prefs.getString("totals", "{}") ?: "{}")
        return buildMap { t.keys().forEach { put(it.toInt(), t.getLong(it)) } }
    }

    // ------------------------------------------------------------ intents

    val intentReasons = listOf("Bored", "Habit", "Replying", "Looking something up", "Taking a break")

    fun logIntent(pkg: String, reason: String, now: Long = System.currentTimeMillis()) {
        val arr = JSONArray(prefs.getString("intents", "[]") ?: "[]")
        arr.put(JSONObject().put("t", now).put("p", pkg).put("r", reason))
        // Keep the last 30 days, max 1000 entries.
        val cutoff = now - 30 * TimeUtil.DAY
        val trimmed = JSONArray()
        val start = (arr.length() - 1000).coerceAtLeast(0)
        for (i in start until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optLong("t") >= cutoff) trimmed.put(e)
        }
        edit { putString("intents", trimmed.toString()) }
    }

    data class IntentEntry(val time: Long, val pkg: String, val reason: String)

    fun intents(since: Long): List<IntentEntry> {
        val arr = JSONArray(prefs.getString("intents", "[]") ?: "[]")
        return buildList {
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val t = e.optLong("t")
                if (t >= since) add(IntentEntry(t, e.optString("p"), e.optString("r")))
            }
        }
    }

    // ------------------------------------------------------------ emergency

    fun emergencyAvailable(): Boolean = prefs.getInt("emergencyWeek", 0) != TimeUtil.weekKey()

    fun useEmergency(pkg: String) {
        edit { putInt("emergencyWeek", TimeUtil.weekKey()) }
        val o = today()
        val extMin = obj(o, "extMin")
        extMin.put(pkg, extMin.optInt(pkg) + EMERGENCY_MINUTES)
        saveToday(o)
    }

    // ------------------------------------------------------------ focus

    data class FocusSession(val startedAt: Long, val endsAt: Long, val strict: Boolean) {
        val durationMs get() = endsAt - startedAt
        fun remaining(now: Long = System.currentTimeMillis()) = (endsAt - now).coerceAtLeast(0)
        fun progress(now: Long = System.currentTimeMillis()) =
            ((now - startedAt).toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    var focusMinutesPreset: Int
        get() = prefs.getInt("focusPreset", 25)
        set(v) = edit { putInt("focusPreset", v) }

    var focusStrictPref: Boolean
        get() = prefs.getBoolean("focusStrict", false)
        set(v) = edit { putBoolean("focusStrict", v) }

    var flipToFocusOn: Boolean
        get() = prefs.getBoolean("flipFocus", true)
        set(v) = edit { putBoolean("flipFocus", v) }

    /** The running session, or null. Settles finished sessions as a side effect. */
    fun focus(now: Long = System.currentTimeMillis()): FocusSession? {
        val end = prefs.getLong("focusEnd", 0)
        if (end == 0L) return null
        val s = FocusSession(prefs.getLong("focusStart", 0), end, prefs.getBoolean("focusStrictNow", false))
        if (now >= end) {
            completeFocus(s, completed = true)
            return null
        }
        return s
    }

    fun startFocus(minutes: Int, strict: Boolean, now: Long = System.currentTimeMillis()) = edit {
        putLong("focusStart", now)
        putLong("focusEnd", now + minutes * TimeUtil.MINUTE)
        putBoolean("focusStrictNow", strict)
    }

    fun endFocusEarly(now: Long = System.currentTimeMillis()) {
        val end = prefs.getLong("focusEnd", 0)
        if (end == 0L) return
        val s = FocusSession(prefs.getLong("focusStart", 0), end, false)
        completeFocus(s.copy(endsAt = now), completed = false)
    }

    private fun completeFocus(s: FocusSession, completed: Boolean) {
        val minutes = ((s.endsAt - s.startedAt) / TimeUtil.MINUTE).toInt().coerceAtLeast(0)
        edit {
            remove("focusStart"); remove("focusEnd"); remove("focusStrictNow")
            putInt("focusMinutesTotal", prefs.getInt("focusMinutesTotal", 0) + minutes)
            if (completed) putInt("focusSessions", prefs.getInt("focusSessions", 0) + 1)
            else putInt("focusAbandoned", prefs.getInt("focusAbandoned", 0) + 1)
            putLong("focusLastDone", if (completed) s.endsAt else prefs.getLong("focusLastDone", 0))
            if (completed) putInt("focusLastEarned", (minutes / 5).coerceAtMost(MAX_BONUS))
        }
        if (completed) earnBonus(minutes)
    }

    /** Minutes earned by the last completed session (for the "+5 min earned" message). */
    fun focusLastEarned(): Int = prefs.getInt("focusLastEarned", 0)

    /** Besides limited apps, focus also blocks Google search and Chrome. */
    val focusAlsoBlocks = setOf("com.google.android.googlequicksearchbox", "com.android.chrome")

    fun blockedInFocus(pkg: String): Boolean = pkg in focusAlsoBlocks || isLimited(pkg)

    fun focusSessionsCompleted(): Int = prefs.getInt("focusSessions", 0)
    fun focusMinutesTotal(): Int = prefs.getInt("focusMinutesTotal", 0)
    fun focusLastDone(): Long = prefs.getLong("focusLastDone", 0)
}
