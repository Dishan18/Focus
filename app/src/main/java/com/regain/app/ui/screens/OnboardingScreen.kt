package com.regain.app.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.regain.app.core.Haptics
import com.regain.app.core.Permissions
import com.regain.app.ui.components.Aurora
import com.regain.app.ui.components.Auroras
import com.regain.app.ui.components.Bloom
import com.regain.app.ui.components.GlassButton
import com.regain.app.ui.components.PrimaryButton
import com.regain.app.ui.components.QuietButton
import com.regain.app.ui.components.RIcon
import com.regain.app.ui.components.RIcons
import com.regain.app.ui.components.RText
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme

private const val STEPS = 5

@Composable
fun OnboardingScreen(onDone: () -> Unit, returning: Boolean) {
    val ctx = LocalContext.current
    var usage by remember { mutableStateOf(Permissions.hasUsageAccess(ctx)) }
    var access by remember { mutableStateOf(Permissions.hasAccessibility(ctx)) }
    var battery by remember { mutableStateOf(Permissions.isIgnoringBatteryOptimizations(ctx)) }
    LifecycleResumeEffect(Unit) {
        val u = Permissions.hasUsageAccess(ctx)
        val a = Permissions.hasAccessibility(ctx)
        if ((u && !usage) || (a && !access)) Haptics.play(Haptics.Kind.Success)
        usage = u; access = a
        battery = Permissions.isIgnoringBatteryOptimizations(ctx)
        onPauseOrDispose { }
    }

    var step by remember { mutableIntStateOf(if (returning) (if (!usage) 1 else 2) else 0) }
    var forward by remember { mutableStateOf(true) }
    fun go(to: Int) { forward = to > step; step = to }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Aurora(if (step == STEPS - 1) Auroras.Reward else Auroras.Focus, intensity = 0.85f)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            // Step dots.
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                repeat(STEPS) { i ->
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (i == step) 22.dp else 6.dp)
                            .background(if (i <= step) Color.White else Color(0x44FFFFFF), CircleShape),
                    )
                }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 300f)) { it * dir / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(250)) { -it * dir / 3 } + fadeOut(tween(200)))
                },
                label = "onboard",
                modifier = Modifier.weight(1f),
            ) { s ->
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (s) {
                        0 -> Welcome { go(1) }
                        1 -> PermissionStep(
                            icon = RIcons.Insights,
                            title = "See your screen time",
                            body = "Focus reads Android's own usage log to know how long you spend in each app. It's how limits work, and it never leaves your phone.",
                            granted = usage,
                            grantLabel = "Allow usage access",
                            hint = "Find Focus in the list and switch it on.",
                            onGrant = { Permissions.launch(ctx, Permissions.usageAccessIntent(ctx), Permissions.usageAccessFallbackIntent()) },
                            onNext = { go(2) },
                        )
                        2 -> AccessibilityStep(
                            granted = access,
                            onGrant = { Permissions.launch(ctx, Permissions.accessibilityIntent()) },
                            onAppInfo = { Permissions.launch(ctx, Permissions.appDetailsIntent(ctx)) },
                            onNext = { go(3) },
                        )
                        3 -> BatteryStep(
                            unrestricted = battery,
                            onBattery = { Permissions.launch(ctx, Permissions.batteryIntent(ctx), Permissions.appDetailsIntent(ctx)) },
                            onAppInfo = { Permissions.launch(ctx, Permissions.appDetailsIntent(ctx)) },
                            onNext = { go(4) },
                        )
                        else -> Done(canFinish = usage && access, onFinish = onDone, onBack = { go(if (!usage) 1 else 2) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Welcome(onNext: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Bloom(0.9f, petals = 7, modifier = Modifier.size(240.dp))
    Spacer(Modifier.height(24.dp))
    RText("Focus", style = RTheme.type.largeTitle.copy(fontSize = RTheme.type.largeTitle.fontSize * 1.3f))
    Spacer(Modifier.height(8.dp))
    RText("Take your time back, gently.", style = RTheme.type.title3, color = RColors.Secondary)
    Spacer(Modifier.weight(1.2f))
    PrimaryButton("Get started", onNext, haptic = Haptics.Kind.Success)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ColumnScope.StepHeader(icon: ImageVector, title: String, body: String, granted: Boolean) {
    Spacer(Modifier.weight(0.7f))
    Box(
        Modifier.size(92.dp).background(if (granted) Color(0x3334E0A1) else Color(0x22FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) { RIcon(if (granted) RIcons.Check else icon, tint = if (granted) RColors.Mint else Color.White, size = 42.dp) }
    Spacer(Modifier.height(24.dp))
    RText(title, style = RTheme.type.title1, align = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    RText(body, color = RColors.Secondary, align = TextAlign.Center)
}

@Composable
private fun ColumnScope.PermissionStep(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    grantLabel: String,
    hint: String,
    onGrant: () -> Unit,
    onNext: () -> Unit,
) {
    StepHeader(icon, title, body, granted)
    Spacer(Modifier.weight(1f))
    if (granted) {
        PrimaryButton("Continue", onNext, haptic = Haptics.Kind.Success)
    } else {
        RText(hint, style = RTheme.type.footnote, color = RColors.Secondary, align = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(grantLabel, onGrant)
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ColumnScope.AccessibilityStep(granted: Boolean, onGrant: () -> Unit, onAppInfo: () -> Unit, onNext: () -> Unit) {
    StepHeader(
        RIcons.Shield,
        "Let Focus step in",
        "To show the pause and time's-up screens over an app, Focus uses an accessibility service. It only sees which app is open, never what's on screen or what you type.",
        granted,
    )
    if (!granted && Build.VERSION.SDK_INT >= 33) {
        Spacer(Modifier.height(18.dp))
        Instructions(
            "Says “Restricted setting”?",
            listOf(
                "Android blocks this for apps installed from outside the Play Store.",
                "Tap Open App info below, then ⋮ in the top-right corner.",
                "Choose “Allow restricted settings” and confirm.",
                "Come back here and tap Turn on again.",
            ),
        )
    }
    Spacer(Modifier.weight(1f))
    if (granted) {
        PrimaryButton("Continue", onNext, haptic = Haptics.Kind.Success)
    } else {
        RText("Open Installed apps (or Downloaded apps) → Focus → turn it on.", style = RTheme.type.footnote, color = RColors.Secondary, align = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Turn on", onGrant)
        Spacer(Modifier.height(8.dp))
        GlassButton("Open App info", onAppInfo)
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ColumnScope.BatteryStep(unrestricted: Boolean, onBattery: () -> Unit, onAppInfo: () -> Unit, onNext: () -> Unit) {
    StepHeader(
        RIcons.Battery,
        "Keep Focus awake",
        "Realme phones close background apps aggressively. Focus uses almost no battery, since it only wakes when you switch apps, but it needs permission to stay.",
        unrestricted,
    )
    Spacer(Modifier.height(18.dp))
    Instructions(
        "On Realme",
        listOf(
            "Open App info → Battery → turn on “Allow background activity”.",
            "Open Recents, swipe down on Focus's card (or tap ⋮) and choose Lock.",
        ),
    )
    Spacer(Modifier.weight(1f))
    if (!unrestricted) {
        PrimaryButton("Allow unrestricted battery", onBattery)
        Spacer(Modifier.height(8.dp))
    }
    GlassButton("Open App info", onAppInfo)
    Spacer(Modifier.height(4.dp))
    QuietButton(if (unrestricted) "Continue" else "I've done this, continue", onNext, color = RColors.Label)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ColumnScope.Done(canFinish: Boolean, onFinish: () -> Unit, onBack: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Bloom(1f, petals = 9, modifier = Modifier.size(200.dp))
    Spacer(Modifier.height(24.dp))
    RText(if (canFinish) "You're all set" else "Almost there", style = RTheme.type.title1)
    Spacer(Modifier.height(10.dp))
    RText(
        if (canFinish) "Add a limit to your first time-sink app. Focus handles the rest."
        else "Usage access and the accessibility service are both needed for limits to work.",
        color = RColors.Secondary, align = TextAlign.Center,
    )
    Spacer(Modifier.weight(1.2f))
    if (canFinish) PrimaryButton("Start", onFinish, haptic = Haptics.Kind.Reward)
    else PrimaryButton("Finish setup", onBack)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Instructions(title: String, lines: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        RText(title, style = RTheme.type.headline)
        Spacer(Modifier.height(8.dp))
        lines.forEachIndexed { i, l ->
            Row(Modifier.padding(vertical = 3.dp)) {
                RText("${i + 1}.", style = RTheme.type.subhead, color = RColors.Accent, modifier = Modifier.width(22.dp))
                RText(l, style = RTheme.type.subhead, color = RColors.Secondary)
            }
        }
    }
}
