package com.regain.app.core

import java.util.Calendar

object TimeUtil {
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    fun startOfDay(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Day key like 20261123, used to reset daily counters. */
    fun dayKey(now: Long = System.currentTimeMillis()): Int {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return c.get(Calendar.YEAR) * 10_000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }

    /** Week key (year * 100 + week) for weekly caps. */
    fun weekKey(now: Long = System.currentTimeMillis()): Int {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return c.get(Calendar.YEAR) * 100 + c.get(Calendar.WEEK_OF_YEAR)
    }

    fun minuteOfDay(now: Long = System.currentTimeMillis()): Int {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** True if [minute] falls inside [start, end), handling ranges that cross midnight. */
    fun inWindow(minute: Int, start: Int, end: Int): Boolean =
        if (start == end) false
        else if (start < end) minute in start until end
        else minute >= start || minute < end

    /** "2h 14m", "38m", "<1m". */
    fun formatDuration(ms: Long): String {
        val totalMin = ms / MINUTE
        if (totalMin < 1) return "<1m"
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** "12:05" style countdown. */
    fun formatClock(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    fun formatMinuteOfDay(m: Int): String {
        val h = m / 60
        val mm = m % 60
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, mm, ampm)
    }
}
