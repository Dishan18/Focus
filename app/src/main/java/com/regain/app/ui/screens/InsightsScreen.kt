package com.regain.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.regain.app.RegainApp
import com.regain.app.core.TimeUtil
import com.regain.app.data.Store
import com.regain.app.ui.components.AppIcon
import com.regain.app.ui.components.Card
import com.regain.app.ui.components.Hairline
import com.regain.app.ui.components.LargeTitleScreen
import com.regain.app.ui.components.ProgressBar
import com.regain.app.ui.components.RText
import com.regain.app.ui.components.Section
import com.regain.app.ui.components.SpacedRow
import com.regain.app.ui.components.StatTile
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme
import java.util.Calendar

private val reasonColors = listOf(RColors.Coral, RColors.Amber, RColors.Mint, RColors.Blue, RColors.Pink)

@Composable
fun InsightsScreen() {
    val week = rememberWeek()
    val catalog = RegainApp.instance.catalog

    LargeTitleScreen(title = "Insights", subtitle = "Last 7 days") {
        if (week == null) {
            item { RText("Loading…", color = RColors.Secondary, modifier = Modifier.padding(20.dp)) }
            return@LargeTitleScreen
        }
        val totals = week.days.map { it.totalMs }
        val nonZero = totals.filter { it > 0 }
        val avg = if (nonZero.isEmpty()) 0 else nonZero.sum() / nonZero.size
        val counters = week.days.map { Store.countersFor(TimeUtil.dayKey(it.dayStart)) }
        val weekStart = week.days.first().dayStart

        item {
            Spacer(Modifier.height(8.dp))
            Card {
                RText("DAILY AVERAGE", style = RTheme.type.caption, color = RColors.Secondary)
                RText(TimeUtil.formatDuration(avg), style = RTheme.type.title1)
                Spacer(Modifier.height(16.dp))
                WeekChart(totals, Store.dailyGoalMin * TimeUtil.MINUTE, week.days.map { it.dayStart })
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SpacedRow {
                StatTile("Unlocks", "${week.days.sumOf { it.unlocks }.coerceAtLeast(counters.sumOf { it.unlocks })}", Modifier.weight(1f))
                StatTile("Closed", "${counters.sumOf { it.reclaims }}", Modifier.weight(1f), accent = RColors.Mint)
            }
            Spacer(Modifier.height(10.dp))
            SpacedRow {
                StatTile("Extensions", "${counters.sumOf { it.extensions }}", Modifier.weight(1f), accent = RColors.Amber)
                StatTile("Scroll nudges", "${counters.sumOf { it.doom }}", Modifier.weight(1f), accent = RColors.Pink)
            }
        }

        // Why you open apps.
        val intents = Store.intents(weekStart)
        item {
            Section(header = "Why you opened limited apps") {
                if (intents.isEmpty()) {
                    RText(
                        "Each time you open a limited app, Focus asks why. Your answers show up here.",
                        style = RTheme.type.subhead, color = RColors.Secondary, modifier = Modifier.padding(16.dp),
                    )
                } else {
                    val counts = Store.intentReasons.associateWith { r -> intents.count { it.reason == r } }
                    val top = counts.maxByOrNull { it.value }!!
                    Column(Modifier.padding(16.dp)) {
                        RText(
                            "${(top.value * 100 / intents.size)}% of the time it was “${top.key.lowercase()}”.",
                            style = RTheme.type.headline,
                        )
                        Spacer(Modifier.height(14.dp))
                        Store.intentReasons.forEachIndexed { i, r ->
                            val c = counts[r] ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RText(r, style = RTheme.type.subhead, modifier = Modifier.width(150.dp), maxLines = 1)
                                Box(Modifier.weight(1f)) { ProgressBar(c.toFloat() / intents.size, reasonColors[i], height = 8.dp) }
                                RText("$c", style = RTheme.type.subhead, color = RColors.Secondary, modifier = Modifier.width(36.dp), align = TextAlign.End)
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        // Peak hour, from gate opens.
        if (intents.size >= 5) {
            item {
                val hours = intents.groupingBy {
                    Calendar.getInstance().apply { timeInMillis = it.time }.get(Calendar.HOUR_OF_DAY)
                }.eachCount()
                val peak = hours.maxByOrNull { it.value }!!.key
                Section(header = "Pattern") {
                    RText(
                        "You reach for limited apps most around ${TimeUtil.formatMinuteOfDay(peak * 60)}.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // Top apps this week.
        item {
            val top = week.days.flatMap { it.apps }.groupBy { it.pkg }
                .mapValues { (_, v) -> v.sumOf { it.timeMs } to v.sumOf { it.opens } }
                .entries.sortedByDescending { it.value.first }.take(5)
            Section(header = "Top apps this week") {
                top.forEachIndexed { i, (pkg, v) ->
                    if (i > 0) Hairline(62.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            RText(catalog.label(pkg), maxLines = 1)
                            RText("${v.second} opens · ${TimeUtil.formatDuration(v.first / 7)} a day", style = RTheme.type.footnote, color = RColors.Secondary)
                        }
                        RText(TimeUtil.formatDuration(v.first), color = RColors.Secondary)
                    }
                }
            }
        }

        item {
            Section(header = "Focus") {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        RText("${Store.focusSessionsCompleted()}", style = RTheme.type.title2, color = RColors.Accent)
                        RText("sessions done", style = RTheme.type.footnote, color = RColors.Secondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        RText(TimeUtil.formatDuration(Store.focusMinutesTotal() * TimeUtil.MINUTE), style = RTheme.type.title2, color = RColors.Teal)
                        RText("focused in total", style = RTheme.type.footnote, color = RColors.Secondary)
                    }
                }
            }
        }
    }
}

/** Seven rounded bars that grow in with a spring, plus a dashed goal line. */
@Composable
private fun WeekChart(totals: List<Long>, goalMs: Long, starts: List<Long>) {
    val max = maxOf(totals.maxOrNull() ?: 0, goalMs).coerceAtLeast(1).toFloat() * 1.1f
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 120f)) }
    val labels = starts.map {
        val c = Calendar.getInstance().apply { timeInMillis = it }
        "SMTWTFS"[c.get(Calendar.DAY_OF_WEEK) - 1].toString()
    }
    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = totals.size
            val slot = size.width / n
            val barW = slot * 0.46f
            totals.forEachIndexed { i, t ->
                val h = size.height * (t / max) * grow.value
                val x = slot * i + (slot - barW) / 2
                val isToday = i == n - 1
                val over = t > goalMs
                val brush = Brush.verticalGradient(
                    if (over) listOf(RColors.Coral, Color(0xFFB0465F))
                    else if (isToday) listOf(RColors.Mint, RColors.Accent)
                    else listOf(Color(0xFF6E6AE0), Color(0xFF3E3A9E)),
                )
                drawRoundRect(brush, Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2.6f))
            }
            val gy = size.height - size.height * (goalMs / max)
            drawLine(
                Color(0x80FFFFFF), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                RText(
                    l, style = RTheme.type.caption,
                    color = if (i == labels.size - 1) RColors.Label else RColors.Secondary,
                    align = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
