package com.regain.app.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.regain.app.core.Haptics
import com.regain.app.core.TimeUtil
import com.regain.app.data.Store
import com.regain.app.service.GuardService
import com.regain.app.ui.components.Aurora
import com.regain.app.ui.components.Auroras
import com.regain.app.ui.components.Chip
import com.regain.app.ui.components.PrimaryButton
import com.regain.app.ui.components.ProgressRing
import com.regain.app.ui.components.RIcon
import com.regain.app.ui.components.RIcons
import com.regain.app.ui.components.RSwitch
import com.regain.app.ui.components.RText
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val presets = listOf(15, 25, 45, 60, 90)

@Composable
fun FocusScreen() {
    rememberRefreshKey()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val session = Store.focus(now)
    var justFinished by remember { mutableStateOf(false) }
    var wasRunning by remember { mutableStateOf(session != null) }

    // One tick per second while running; nothing at all while idle.
    LaunchedEffect(session != null) {
        while (Store.focus() != null) {
            delay(1000)
            now = System.currentTimeMillis()
        }
        now = System.currentTimeMillis()
    }
    LaunchedEffect(session == null) {
        if (session == null && wasRunning) {
            justFinished = Store.focusLastDone() > now - 5_000
            if (justFinished) Haptics.play(Haptics.Kind.Reward)
        }
        wasRunning = session != null
    }
    LaunchedEffect(justFinished) { if (justFinished) { delay(4000); justFinished = false } }

    Box(Modifier.fillMaxSize()) {
        Aurora(
            if (justFinished) Auroras.Reward else Auroras.Focus,
            intensity = if (session != null) 1f else 0.7f,
            speed = if (session != null) 0.7f else 1f,
        )
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 44.dp)) {
                RText("Focus", style = RTheme.type.largeTitle)
            }
            AnimatedContent(
                targetState = session != null,
                transitionSpec = { (fadeIn(tween(400)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(250)) },
                label = "focusState",
                modifier = Modifier.weight(1f),
            ) { running ->
                if (running && session != null) RunningFocus(session, now)
                else IdleFocus(justFinished, onStart = { mins, strict ->
                    Store.startFocus(mins, strict)
                    GuardService.refresh()
                    Haptics.play(Haptics.Kind.Success)
                    now = System.currentTimeMillis()
                })
            }
        }
    }
}

@Composable
private fun IdleFocus(justFinished: Boolean, onStart: (Int, Boolean) -> Unit) {
    var minutes by remember { mutableIntStateOf(Store.focusMinutesPreset) }
    var strict by remember { mutableStateOf(Store.focusStrictPref) }
    var flip by remember { mutableStateOf(Store.flipToFocusOn) }

    if (flip) FlipDetector { onStart(minutes, strict) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.6f))
        ProgressRing(0f, Modifier.size(250.dp), stroke = 4.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(minutes, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mins") { m ->
                    RText("$m:00", style = RTheme.type.display.copy(fontWeight = FontWeight.ExtraLight))
                }
                RText(
                    if (justFinished) "Session complete. Well done." else "minutes of calm",
                    style = RTheme.type.footnote, color = if (justFinished) RColors.Mint else RColors.Secondary,
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { m ->
                Chip("$m min", selected = minutes == m, onClick = {
                    minutes = m
                    Store.focusMinutesPreset = m
                })
            }
        }
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .background(Color(0x1AFFFFFF), RoundedCornerShape(18.dp))
                .padding(vertical = 4.dp),
        ) {
            GlassToggle(RIcons.Lock, "Strict", "Can't be ended early", strict) { strict = it; Store.focusStrictPref = it }
            GlassToggle(RIcons.Flip, "Flip to start", "Place the phone face down", flip) { flip = it; Store.flipToFocusOn = it }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(horizontal = 20.dp)) {
            PrimaryButton("Start focus", { onStart(minutes, strict) }, haptic = null)
        }
        Spacer(Modifier.height(10.dp))
        val sessions = Store.focusSessionsCompleted()
        if (sessions > 0) {
            RText(
                "$sessions sessions · ${TimeUtil.formatDuration(Store.focusMinutesTotal() * TimeUtil.MINUTE)} focused so far",
                style = RTheme.type.footnote, color = RColors.Secondary,
            )
        }
    }
}

@Composable
private fun GlassToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RIcon(icon, tint = RColors.Label, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            RText(title, style = RTheme.type.callout)
            RText(subtitle, style = RTheme.type.footnote, color = RColors.Secondary)
        }
        RSwitch(checked, onChange, accent = RColors.Accent)
    }
}

@Composable
private fun RunningFocus(session: Store.FocusSession, now: Long) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.7f))
        ProgressRing(session.progress(now), Modifier.size(270.dp), stroke = 5.dp, animate = false) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RText(TimeUtil.formatClock(session.remaining(now)), style = RTheme.type.display.copy(fontWeight = FontWeight.ExtraLight))
                RText("Limited apps are resting", style = RTheme.type.footnote, color = RColors.Secondary)
            }
        }
        Spacer(Modifier.height(28.dp))
        RText(
            "Put the phone down. Focus will buzz you\nif you pick it back up.",
            style = RTheme.type.subhead, color = RColors.Secondary, align = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        if (session.strict) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RIcon(RIcons.Lock, tint = RColors.Secondary, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                RText("Strict session · no early exit", style = RTheme.type.footnote, color = RColors.Secondary)
            }
        } else {
            HoldToEnd {
                Store.endFocusEarly()
                Haptics.play(Haptics.Kind.Sad)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** Hold 1.5 s to end: ending focus should take a deliberate gesture. */
@Composable
private fun HoldToEnd(onEnd: () -> Unit) {
    val p = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(52.dp)
            .background(Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    Haptics.play(Haptics.Kind.Tick)
                    val job = scope.launch {
                        p.animateTo(1f, tween(((1f - p.value) * 1500).toInt(), easing = LinearEasing))
                        onEnd()
                        p.snapTo(0f)
                    }
                    waitForUpOrCancellation()
                    if (p.value < 1f) {
                        job.cancel()
                        scope.launch { p.animateTo(0f, tween(300)) }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth(p.value)
                .height(52.dp)
                .background(Color(0x40FF6961), RoundedCornerShape(16.dp)),
        )
        RText("Hold to end early", style = RTheme.type.headline, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Listens to the accelerometer only while the Focus tab is visible and idle.
 * Face down (z < -8.5) for 1.2 s starts the session.
 */
@Composable
private fun FlipDetector(onFlip: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var downSince = 0L
        var fired = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (fired) return
                val z = e.values[2]
                val t = SystemClock.elapsedRealtime()
                if (z < -8.5f) {
                    if (downSince == 0L) downSince = t
                    else if (t - downSince > 1200) { fired = true; onFlip() }
                } else downSince = 0L
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (acc != null) sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
}
