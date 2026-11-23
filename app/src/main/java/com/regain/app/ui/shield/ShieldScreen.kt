package com.regain.app.ui.shield

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.regain.app.ui.components.Chip
import com.regain.app.ui.components.GlassButton
import com.regain.app.ui.components.PrimaryButton
import com.regain.app.ui.components.ProgressRing
import com.regain.app.ui.components.QuietButton
import com.regain.app.ui.components.RIcon
import com.regain.app.ui.components.RIcons
import com.regain.app.ui.components.RText
import com.regain.app.ui.components.Segmented
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Stage { Main, Extend, Challenge, Emergency, Reward }

private val rewardBrush = Brush.horizontalGradient(listOf(Color(0xFF34E0A1), Color(0xFF1FB5C9)))

@Composable
fun ShieldScreen(mode: ShieldMode, pkg: String, session: Int, actions: ShieldActions) {
    val label = remember(pkg) { RegainApp.instance.catalog.label(pkg) }
    var stage by remember(session) { mutableStateOf(Stage.Main) }
    var extendMinutes by remember(session) { mutableIntStateOf(15) }
    val night = remember(session) { Store.isNight() }

    // Reward -> brief celebration, then home.
    LaunchedEffect(stage) {
        if (stage == Stage.Reward) {
            Haptics.play(Haptics.Kind.Reward)
            delay(1300)
            actions.goHome(reclaimed = true)
        }
    }

    val palette: AuroraPalette = when {
        stage == Stage.Reward -> Auroras.Reward
        mode == ShieldMode.TimesUp && stage == Stage.Main -> Auroras.Warning
        mode == ShieldMode.Downtime || night -> Auroras.Night
        mode == ShieldMode.Morning -> Auroras.Calm
        else -> Auroras.Focus
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Aurora(palette, intensity = if (stage == Stage.Challenge) 0.45f else 1f, speed = if (stage == Stage.Reward) 3f else 1f)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(tween(320)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 300f), initialScale = 0.94f)) togetherWith
                        (fadeOut(tween(180)) + scaleOut(targetScale = 1.04f))
                },
                label = "stage",
                modifier = Modifier.fillMaxSize(),
            ) { s ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (s) {
                        Stage.Reward -> RewardStage()
                        Stage.Challenge -> {
                            Spacer(Modifier.height(36.dp))
                            val level = Store.extensionsUsed(pkg) + 1 + if (night) 1 else 0
                            val solved = {
                                Store.addExtension(pkg, extendMinutes)
                                actions.allow()
                            }
                            if (Store.challengeType == 1) {
                                TypingChallenge(level, onSolved = solved, onCancel = { stage = Stage.Reward })
                            } else {
                                MathChallenge(level, onSolved = solved, onCancel = { stage = Stage.Reward })
                            }
                        }
                        Stage.Extend -> ExtendStage(
                            label = label,
                            minutes = extendMinutes,
                            onMinutes = { extendMinutes = it },
                            onStart = { stage = Stage.Challenge },
                            onQuit = { stage = Stage.Reward },
                        )
                        Stage.Emergency -> EmergencyStage(
                            onDone = {
                                Store.useEmergency(pkg)
                                actions.allow()
                            },
                            onQuit = { stage = Stage.Reward },
                        )
                        Stage.Main -> when (mode) {
                            ShieldMode.TimesUp -> TimesUpStage(
                                pkg, label,
                                onClose = { stage = Stage.Reward },
                                onExtend = { stage = Stage.Extend },
                                onEmergency = { stage = Stage.Emergency },
                            )
                            ShieldMode.Pause -> PauseStage(
                                pkg, label, night,
                                onOpen = { reason ->
                                    Store.logIntent(pkg, reason)
                                    actions.allow()
                                },
                                onQuit = { stage = Stage.Reward },
                            )
                            ShieldMode.Focus -> FocusStage(pkg, label, actions)
                            ShieldMode.Downtime -> InfoStage(
                                pkg,
                                title = "Downtime",
                                body = "$label opens again at ${TimeUtil.formatMinuteOfDay(Store.downtimeEnd)}.\nRest well.",
                                button = "Good night",
                                icon = RIcons.Moon,
                                onDone = { actions.goHome(reclaimed = true) },
                            )
                            ShieldMode.Morning -> {
                                val left = TimeUtil.formatDuration(Store.morningGateRemaining())
                                InfoStage(
                                    pkg,
                                    title = "Good morning",
                                    body = "$label opens in $left.\nStart your day first.",
                                    button = "Okay",
                                    icon = RIcons.Sun,
                                    onDone = { actions.goHome(reclaimed = true) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun AppHeader(pkg: String, label: String) {
    Spacer(Modifier.height(56.dp))
    AppIcon(pkg, 64.dp)
    Spacer(Modifier.height(12.dp))
    RText(label, style = RTheme.type.headline, color = RColors.Secondary)
}

@Composable
private fun ColumnScope.BottomActions(content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.weight(1f))
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun ColumnScope.TimesUpStage(
    pkg: String,
    label: String,
    onClose: () -> Unit,
    onExtend: () -> Unit,
    onEmergency: () -> Unit,
) {
    LaunchedEffect(Unit) { Haptics.play(Haptics.Kind.TimesUp) }
    val allowed = Store.allowedMinutes(pkg) ?: 0
    val used = Store.extensionsUsed(pkg)
    val canExtend = Store.canExtend(pkg)
    val emergency = Store.emergencyAvailable()

    AppHeader(pkg, label)
    Spacer(Modifier.height(36.dp))
    ProgressRing(1f, Modifier.size(150.dp), stroke = 8.dp, colors = listOf(RColors.Amber, RColors.Coral, RColors.Pink)) {
        RIcon(RIcons.Timer, size = 44.dp)
    }
    Spacer(Modifier.height(30.dp))
    RText("Time's up", style = RTheme.type.largeTitle)
    Spacer(Modifier.height(8.dp))
    RText(
        "You've used your ${TimeUtil.formatDuration(allowed * TimeUtil.MINUTE)} on $label today.",
        style = RTheme.type.body, color = RColors.Secondary, align = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 40.dp),
    )

    BottomActions {
        PrimaryButton("Close $label", onClose, brush = rewardBrush, haptic = null)
        when {
            canExtend -> {
                val left = Store.MAX_EXTENSIONS_PER_APP - used
                QuietButton("Extend · $left left today", onExtend)
            }
            emergency -> {
                RText("No extensions left today", style = RTheme.type.footnote, color = RColors.Tertiary)
                QuietButton("Emergency unlock · once a week", onEmergency, color = RColors.Amber)
            }
            else -> RText("That's all for today. See you tomorrow.", style = RTheme.type.footnote, color = RColors.Tertiary)
        }
    }
}

@Composable
private fun ColumnScope.ExtendStage(
    label: String,
    minutes: Int,
    onMinutes: (Int) -> Unit,
    onStart: () -> Unit,
    onQuit: () -> Unit,
) {
    val opts = listOf(5, 10, 15)
    val math = Store.challengeType == 0
    Spacer(Modifier.height(96.dp))
    RText("How much more?", style = RTheme.type.title1)
    Spacer(Modifier.height(10.dp))
    RText(
        if (math) "Answer 5 quick questions in your head to unlock it." else "Type one sentence exactly to unlock it.",
        color = RColors.Secondary, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp),
    )
    Spacer(Modifier.height(32.dp))
    Segmented(
        options = opts.map { "$it min" },
        selected = opts.indexOf(minutes).coerceAtLeast(0),
        onSelect = { onMinutes(opts[it]) },
        modifier = Modifier.padding(horizontal = 40.dp),
    )
    Spacer(Modifier.height(18.dp))
    RText(
        "Shorter is the same effort. Take only what you need.",
        style = RTheme.type.footnote, color = RColors.Tertiary, align = TextAlign.Center,
    )
    BottomActions {
        PrimaryButton("Never mind, close $label", onQuit, brush = rewardBrush, haptic = null)
        GlassButton(if (math) "Start questions" else "Start typing", onStart)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.PauseStage(
    pkg: String,
    label: String,
    night: Boolean,
    onOpen: (String) -> Unit,
    onQuit: () -> Unit,
) {
    // Growing delay: each open today waits longer. Doubled at night.
    val delaySec = remember {
        val opens = Store.gateOpens(pkg)
        Store.recordGateOpen(pkg)
        val ladder = intArrayOf(3, 5, 10, 20, 30)
        val base = if (Store.growingDelayOn) ladder[opens.coerceAtMost(ladder.size - 1)] else 3
        if (night) base * 2 else base
    }
    var left by remember { mutableIntStateOf(delaySec) }
    var reason by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (left > 0) { delay(1000); left-- }
    }

    Spacer(Modifier.height(40.dp))
    BreathingOrb(Modifier.size(200.dp))
    Spacer(Modifier.height(20.dp))
    RText("Why are you opening $label?", style = RTheme.type.title2, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
    Spacer(Modifier.height(18.dp))
    FlowRow(
        Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Store.intentReasons.forEach { r ->
            Chip(r, selected = reason == r, onClick = { reason = r })
        }
    }

    BottomActions {
        PrimaryButton("Never mind", onQuit, brush = rewardBrush, haptic = null)
        val ready = left <= 0 && reason != null
        GlassButton(
            when {
                left > 0 -> "Open $label in ${left}s"
                reason == null -> "Pick a reason to continue"
                else -> "Open $label"
            },
            onClick = { reason?.let(onOpen) },
            enabled = ready,
        )
    }
}

/** Inhale 4 s / exhale 4 s, with a soft tick at each turn. */
@Composable
private fun BreathingOrb(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "breath")
    val s by t.animateFloat(0.62f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "s")
    val inhale = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            inhale.value = true; Haptics.play(Haptics.Kind.Breath); delay(4000)
            inhale.value = false; Haptics.play(Haptics.Kind.Breath); delay(4000)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = s; scaleY = s }) {
            val r = size.minDimension / 2
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xCCB8F2E6), Color(0x998B7CFF), Color(0x002E5BFF)),
                    center = Offset(size.width / 2, size.height / 2), radius = r,
                ),
                radius = r,
            )
        }
        AnimatedContent(inhale.value, transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) }, label = "bt") {
            RText(if (it) "Breathe in" else "Breathe out", style = RTheme.type.headline)
        }
    }
}

@Composable
private fun ColumnScope.FocusStage(pkg: String, label: String, actions: ShieldActions) {
    val focus = remember { Store.focus() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }
    LaunchedEffect(Unit) { Haptics.play(Haptics.Kind.Sad) }
    val remaining = focus?.remaining(now) ?: 0
    if (focus != null && remaining <= 0) LaunchedEffect(Unit) { actions.allow() }

    AppHeader(pkg, label)
    Spacer(Modifier.height(30.dp))
    ProgressRing(focus?.progress(now) ?: 1f, Modifier.size(220.dp), stroke = 6.dp, animate = false) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RText(TimeUtil.formatClock(remaining), style = RTheme.type.display.copy(fontWeight = FontWeight.ExtraLight))
            RText("left in focus", style = RTheme.type.footnote, color = RColors.Secondary)
        }
    }
    Spacer(Modifier.height(26.dp))
    RText("$label is resting while you focus.", color = RColors.Secondary, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))

    BottomActions {
        PrimaryButton("Back to focus", { actions.goHome(reclaimed = true) }, brush = rewardBrush, haptic = Haptics.Kind.Success)
        // No one-tap exit from inside the blocked app: ending early is a
        // deliberate hold on the Focus tab.
        RText(
            if (focus?.strict == true) "Strict focus can't be ended early"
            else "To end early, hold the button on the Focus tab",
            style = RTheme.type.footnote, color = RColors.Tertiary,
        )
    }
}

@Composable
private fun ColumnScope.InfoStage(
    pkg: String,
    title: String,
    body: String,
    button: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDone: () -> Unit,
) {
    AppHeader(pkg, RegainApp.instance.catalog.label(pkg))
    Spacer(Modifier.height(48.dp))
    Box(Modifier.size(96.dp).background(Color(0x1FFFFFFF), CircleShape), contentAlignment = Alignment.Center) {
        RIcon(icon, size = 44.dp)
    }
    Spacer(Modifier.height(26.dp))
    RText(title, style = RTheme.type.largeTitle)
    Spacer(Modifier.height(10.dp))
    RText(body, color = RColors.Secondary, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
    BottomActions { PrimaryButton(button, onDone, brush = rewardBrush, haptic = Haptics.Kind.Success) }
}

@Composable
private fun ColumnScope.RewardStage() {
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
    Spacer(Modifier.weight(1f))
    Box(
        Modifier
            .size(120.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .background(Color(0x33FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) { RIcon(RIcons.Check, size = 60.dp) }
    Spacer(Modifier.height(26.dp))
    RText("Good call.", style = RTheme.type.largeTitle)
    Spacer(Modifier.height(8.dp))
    RText("That's a moment back for yourself.", color = RColors.Secondary)
    Spacer(Modifier.weight(1.3f))
}

/** Hold for 60 s straight. Letting go resets it. */
@Composable
private fun ColumnScope.EmergencyStage(onDone: () -> Unit, onQuit: () -> Unit) {
    val holdMs = 60_000
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var holding by remember { mutableStateOf(false) }

    Spacer(Modifier.height(80.dp))
    RText("Emergency unlock", style = RTheme.type.title1)
    Spacer(Modifier.height(10.dp))
    RText(
        "Press and hold for a full minute to get ${Store.EMERGENCY_MINUTES} minutes.\nYou get one of these a week.",
        color = RColors.Secondary, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 36.dp),
    )
    Spacer(Modifier.height(40.dp))
    ProgressRing(
        progress.value,
        Modifier
            .size(220.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    holding = true
                    Haptics.play(Haptics.Kind.Tick)
                    val job = scope.launch {
                        progress.animateTo(1f, tween(((1f - progress.value) * holdMs).toInt(), easing = LinearEasing))
                        Haptics.play(Haptics.Kind.Success)
                        onDone()
                    }
                    waitForUpOrCancellation()
                    holding = false
                    if (progress.value < 1f) {
                        job.cancel()
                        Haptics.play(Haptics.Kind.Error)
                        scope.launch { progress.animateTo(0f, tween(500)) }
                    }
                }
            },
        stroke = 12.dp, colors = listOf(RColors.Amber, RColors.Coral), animate = false,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val secs = ((1f - progress.value) * holdMs / 1000).toInt()
            RText(if (holding) "${secs}s" else "Hold", style = RTheme.type.title1)
        }
    }
    BottomActions { PrimaryButton("Never mind", onQuit, brush = rewardBrush, haptic = null) }
}
