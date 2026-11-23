package com.regain.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.regain.app.RegainApp
import com.regain.app.core.Haptics
import com.regain.app.core.TimeUtil
import com.regain.app.data.Store
import com.regain.app.ui.components.AppIcon
import com.regain.app.ui.components.Aurora
import com.regain.app.ui.components.AuroraPalette
import com.regain.app.ui.components.Auroras
import com.regain.app.ui.components.BarButton
import com.regain.app.ui.components.Bloom
import com.regain.app.ui.components.RIcons
import com.regain.app.ui.components.RText
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SLIDE_MS = 5000

/** "Your week": four auto-advancing story slides over a shifting aurora. Tap right/left to skip. */
@Composable
fun RecapScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val week = rememberWeek()
    var slide by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val slides = 4

    LaunchedEffect(slide, week != null) {
        if (week == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SLIDE_MS, easing = LinearEasing))
        if (slide < slides - 1) slide++ else onClose()
    }
    LaunchedEffect(slide) { Haptics.play(Haptics.Kind.Tick) }

    val palette: AuroraPalette = when (slide) {
        0 -> Auroras.Focus
        1 -> Auroras.Warning
        2 -> Auroras.Calm
        else -> Auroras.Reward
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    if (o.x > size.width * 0.35f) {
                        if (slide < slides - 1) slide++ else onClose()
                    } else if (slide > 0) slide--
                }
            },
    ) {
        Aurora(palette, speed = 1.6f)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            // Story progress bars.
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(slides) { i ->
                    val f = when {
                        i < slide -> 1f
                        i == slide -> progress.value
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color(0x44FFFFFF))) {
                        Box(Modifier.fillMaxWidth(f).height(3.dp).background(Color.White))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RText("YOUR WEEK", style = RTheme.type.caption, color = Color(0xCCFFFFFF), modifier = Modifier.padding(start = 10.dp).weight(1f))
                BarButton(RIcons.Close, onClose, tint = Color.White)
            }

            if (week == null) return@Column
            AnimatedContent(
                targetState = slide,
                transitionSpec = {
                    (fadeIn(tween(450)) + slideInVertically(spring(dampingRatio = 0.8f)) { it / 6 } + scaleIn(initialScale = 0.96f)) togetherWith
                        fadeOut(tween(200))
                },
                label = "recap",
                modifier = Modifier.weight(1f),
            ) { s ->
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (s) {
                        0 -> TotalSlide(week)
                        1 -> TopAppSlide(week)
                        2 -> WinsSlide(week)
                        else -> BloomSlide(week)
                    }
                }
            }
        }
    }
}

@Composable
private fun Big(text: String, color: Color = Color.White) =
    RText(text, style = RTheme.type.display.copy(fontWeight = FontWeight.Light), color = color, align = TextAlign.Center)

@Composable
private fun Line(text: String) =
    RText(text, style = RTheme.type.title3, color = Color(0xE6FFFFFF), align = TextAlign.Center)

@Composable
private fun Small(text: String) =
    RText(text, style = RTheme.type.subhead, color = Color(0xB3FFFFFF), align = TextAlign.Center)

@Composable
private fun TotalSlide(week: WeekData) {
    val total = week.days.sumOf { it.totalMs }
    val active = week.days.count { it.totalMs > 0 }.coerceAtLeast(1)
    val unlocks = week.days.sumOf { it.unlocks }
    Line("You spent")
    Spacer(Modifier.height(8.dp))
    Big(TimeUtil.formatDuration(total))
    Spacer(Modifier.height(8.dp))
    Line("on your phone this week")
    Spacer(Modifier.height(28.dp))
    Small("That's ${TimeUtil.formatDuration(total / active)} a day" + if (unlocks > 0) ", across $unlocks unlocks." else ".")
}

@Composable
private fun TopAppSlide(week: WeekData) {
    val top = week.days.flatMap { it.apps }.groupBy { it.pkg }
        .mapValues { (_, v) -> v.sumOf { it.timeMs } to v.sumOf { it.opens } }
        .maxByOrNull { it.value.first }
    if (top == null) {
        Line("A quiet week.")
        Small("No single app took over your time.")
        return
    }
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 200f)) }
    Line("Your most-used app")
    Spacer(Modifier.height(22.dp))
    Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) { AppIcon(top.key, 96.dp) }
    Spacer(Modifier.height(16.dp))
    RText(RegainApp.instance.catalog.label(top.key), style = RTheme.type.title1, align = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Small("${TimeUtil.formatDuration(top.value.first)} · opened ${top.value.second} times")
    Spacer(Modifier.height(24.dp))
    val hours = top.value.first / TimeUtil.HOUR.toFloat()
    if (hours >= 1f) Small("About ${"%.1f".format(hours / 2f)} movies' worth.")
}

@Composable
private fun WinsSlide(week: WeekData) {
    val counters = week.days.map { Store.countersFor(TimeUtil.dayKey(it.dayStart)) }
    val closed = counters.sumOf { it.reclaims }
    val ext = counters.sumOf { it.extensions }
    Line("You chose to close an app")
    Spacer(Modifier.height(8.dp))
    Big("$closed", RColors.Mint)
    Spacer(Modifier.height(8.dp))
    Line(if (closed == 1) "time" else "times")
    Spacer(Modifier.height(28.dp))
    Small(
        "You took $ext extension${if (ext == 1) "" else "s"} and focused for " +
            TimeUtil.formatDuration(Store.focusMinutesTotal() * TimeUtil.MINUTE) + " in total.",
    )
}

@Composable
private fun BloomSlide(week: WeekData) {
    val goal = Store.dailyGoalMin * TimeUtil.MINUTE
    // Only days with recorded usage count; a fresh install shouldn't read as "0 of 7".
    val tracked = week.days.filter { it.totalMs > 0 }
    val under = tracked.count { it.totalMs <= goal }
    val best = tracked.minByOrNull { it.totalMs }
    val s = streak(week.today.totalMs)
    val health = if (tracked.isEmpty()) 0.8f else under.toFloat() / tracked.size
    Bloom(health = health, petals = s + 3, modifier = Modifier.size(220.dp))
    Spacer(Modifier.height(18.dp))
    Line(
        when {
            tracked.isEmpty() -> "Your first week starts now"
            tracked.size == 1 -> if (under == 1) "Under your goal so far" else "Over your goal so far"
            else -> "$under of ${tracked.size} days under your goal"
        },
    )
    Spacer(Modifier.height(10.dp))
    if (best != null) {
        val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(best.dayStart))
        Small("Calmest day: $day, ${TimeUtil.formatDuration(best.totalMs)}.")
    }
    Spacer(Modifier.height(6.dp))
    Small(if (s > 1) "You're on a $s-day streak. Keep it blooming." else "A new week, a new bloom.")
}
