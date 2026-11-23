package com.regain.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.regain.app.RegainApp
import com.regain.app.core.TimeUtil
import com.regain.app.data.Store
import com.regain.app.ui.components.AppIcon
import com.regain.app.ui.components.BarButton
import com.regain.app.ui.components.Bloom
import com.regain.app.ui.components.Card
import com.regain.app.ui.components.Hairline
import com.regain.app.ui.components.LargeTitleScreen
import com.regain.app.ui.components.ProgressBar
import com.regain.app.ui.components.RIcon
import com.regain.app.ui.components.RIcons
import com.regain.app.ui.components.RText
import com.regain.app.ui.components.Section
import com.regain.app.ui.components.SpacedRow
import com.regain.app.ui.components.StatTile
import com.regain.app.ui.components.pressable
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TodayScreen(onOpenSettings: () -> Unit, onOpenLimits: () -> Unit, onOpenRecap: () -> Unit) {
    val week = rememberWeek()
    val today = week?.today
    val total = today?.totalMs ?: 0
    val goalMs = Store.dailyGoalMin * TimeUtil.MINUTE
    val health = if (today == null) 0.6f else (1f - total.toFloat() / goalMs).coerceIn(0f, 1f)
    val streak = streak(total)
    val limits = Store.limits()
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

    LargeTitleScreen(
        title = "Today",
        subtitle = date,
        trailing = { BarButton(RIcons.Settings, onOpenSettings) },
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Card {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Bloom(health, petals = streak + 3, modifier = Modifier.size(210.dp), wilted = today != null && total > goalMs)
                }
                Spacer(Modifier.height(8.dp))
                RText(
                    if (today == null) "—" else TimeUtil.formatDuration(total),
                    style = RTheme.type.title1, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center,
                )
                RText(
                    "of your ${TimeUtil.formatDuration(goalMs)} daily goal",
                    style = RTheme.type.subhead, color = RColors.Secondary,
                    modifier = Modifier.fillMaxWidth(), align = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                ProgressBar(
                    total.toFloat() / goalMs,
                    if (total > goalMs) RColors.Coral else RColors.Mint,
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    RIcon(RIcons.Flame, tint = if (streak > 0) RColors.Amber else RColors.Tertiary, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    RText(
                        when (streak) {
                            0 -> "Your Bloom is wilting. It recovers tomorrow."
                            1 -> "On track today"
                            else -> "$streak-day streak under your goal"
                        },
                        style = RTheme.type.footnote, color = RColors.Secondary,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SpacedRow {
                StatTile("Unlocks", "${maxOf(Store.unlocksToday(), today?.unlocks ?: 0)}", Modifier.weight(1f))
                StatTile("Closed", "${Store.reclaimsToday()}", Modifier.weight(1f), accent = RColors.Mint)
                StatTile("Extensions", "${Store.extensionsUsedTotal()}/${Store.MAX_EXTENSIONS_PER_DAY}", Modifier.weight(1f), accent = RColors.Amber)
            }
        }

        // Weekly recap: offered on Sundays and Mondays.
        val dow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        if (dow == java.util.Calendar.SUNDAY || dow == java.util.Calendar.MONDAY) {
            item {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.pressable(pressedScale = 0.98f, onClick = onOpenRecap)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RIcon(RIcons.Sparkle, tint = RColors.Pink, size = 26.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            RText("Your week in review", style = RTheme.type.headline)
                            RText("Tap to play", style = RTheme.type.footnote, color = RColors.Secondary)
                        }
                        RIcon(RIcons.Chevron, tint = RColors.Tertiary, size = 18.dp)
                    }
                }
            }
        }

        // In real terms.
        if (week != null) {
            val limitedWeek = week.days.sumOf { d -> d.apps.filter { it.pkg in limits }.sumOf { it.timeMs } }
            val topWeek = week.days.flatMap { it.apps }.groupBy { it.pkg }
                .mapValues { (_, v) -> v.sumOf { it.timeMs } }.maxByOrNull { it.value }
            val (subject, ms) = when {
                limitedWeek > 0 -> "your limited apps" to limitedWeek
                topWeek != null -> RegainApp.instance.catalog.label(topWeek.key) to topWeek.value
                else -> null to 0L
            }
            if (subject != null && ms > 30 * TimeUtil.MINUTE) {
                item { RealTerms(subject, ms) }
            }
        }

        item {
            Section(header = "Most used today") {
                val apps = today?.apps?.take(6).orEmpty()
                if (apps.isEmpty()) {
                    RText(
                        if (today == null) "Loading…" else "Nothing yet today. Nice and quiet.",
                        color = RColors.Secondary, modifier = Modifier.padding(16.dp),
                    )
                }
                apps.forEachIndexed { i, a ->
                    if (i > 0) Hairline(62.dp)
                    val limit = limits[a.pkg]
                    val allowed = Store.allowedMinutes(a.pkg)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable(pressedScale = 0.985f, onClick = onOpenLimits)
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(a.pkg, 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RText(RegainApp.instance.catalog.label(a.pkg), Modifier.weight(1f), maxLines = 1)
                                RText(TimeUtil.formatDuration(a.timeMs), color = RColors.Secondary)
                            }
                            if (limit != null && allowed != null) {
                                Spacer(Modifier.height(6.dp))
                                val f = a.timeMs.toFloat() / (allowed * TimeUtil.MINUTE)
                                ProgressBar(f, if (f >= 1f) RColors.Coral else if (f > 0.8f) RColors.Amber else RColors.Accent, height = 4.dp)
                            } else {
                                RText("${a.opens} opens", style = RTheme.type.footnote, color = RColors.Tertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealTerms(subject: String, ms: Long) {
    val hours = ms / TimeUtil.HOUR.toFloat()
    val items = listOf(
        Triple("%.1f".format(hours / 2f), "movies", RColors.Pink),
        Triple("%.1f".format(hours / 6f), "books", RColors.Teal),
        Triple("${(ms / (30 * TimeUtil.MINUTE))}", "30-min walks", RColors.Mint),
    )
    Column {
        Spacer(Modifier.height(12.dp))
        Card {
            RText("THIS WEEK, IN REAL TERMS", style = RTheme.type.caption, color = RColors.Secondary)
            Spacer(Modifier.height(4.dp))
            RText(
                "${TimeUtil.formatDuration(ms)} on $subject is about",
                style = RTheme.type.subhead, color = RColors.Label,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                items.forEach { (n, what, c) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        RText(n, style = RTheme.type.title1, color = c)
                        RText(what, style = RTheme.type.footnote, color = RColors.Secondary)
                    }
                }
            }
        }
    }
}
