package com.regain.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import com.regain.app.core.Buddy
import com.regain.app.core.Grayscale
import com.regain.app.ui.components.QuietButton
import com.regain.app.ui.components.SectionFooter
import com.regain.app.ui.components.TimeWheel
import com.regain.app.ui.components.pressable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.regain.app.BuildConfig
import com.regain.app.core.Haptics
import com.regain.app.core.Permissions
import com.regain.app.core.TimeUtil
import com.regain.app.data.Store
import com.regain.app.ui.components.BarButton
import com.regain.app.ui.components.Hairline
import com.regain.app.ui.components.IconTile
import com.regain.app.ui.components.LargeTitleScreen
import com.regain.app.ui.components.ListRow
import com.regain.app.ui.components.RIcon
import com.regain.app.ui.components.RIcons
import com.regain.app.ui.components.RText
import com.regain.app.ui.components.Section
import com.regain.app.ui.components.Segmented
import com.regain.app.ui.components.Stepper
import com.regain.app.ui.components.SwitchRow
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val refresh = rememberRefreshKey() // re-read prefs and permission state on resume
    val timeSheet = remember { mutableStateOf<TimeEdit?>(null) }
    var smsGranted by remember(refresh) { mutableStateOf(Buddy.hasPermission(ctx)) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        smsGranted = ok
        if (ok) Haptics.play(Haptics.Kind.Success)
    }

    CompositionLocalProvider(LocalTimeSheet provides timeSheet) {
    Box(Modifier.fillMaxSize()) {
    LargeTitleScreen(
        title = "Settings",
        leading = { BarButton(RIcons.Back, onBack) },
        bottomPadding = 40,
    ) {
        item {
            Section(header = "Opening a limited app", footer = "A short pause before the app opens. Each open today waits a little longer, and night doubles it.") {
                SwitchRow("Pause and ask why", Store.pauseOn, { Store.pauseOn = it },
                    leading = { IconTile(RIcons.Question, RColors.Accent) })
                Hairline(60.dp)
                SwitchRow("Growing delay", Store.growingDelayOn, { Store.growingDelayOn = it },
                    leading = { IconTile(RIcons.Timer, RColors.Blue) })
            }
        }
        item {
            Section(header = "When time is up", footer = "Pick how you unlock an extension. Math: 5 questions with 20 seconds each. Typing: copy one sentence exactly.") {
                ListRow("Challenge", leading = { IconTile(RIcons.Hash, RColors.Amber) })
                androidx.compose.foundation.layout.Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    Segmented(listOf("Math", "Typing"), Store.challengeType, { Store.challengeType = it })
                }
            }
        }
        item {
            Section(header = "Nudges") {
                SwitchRow("Fade as time runs out", Store.fadeOn, { Store.fadeOn = it },
                    subtitle = "The screen dims over the last 5 minutes",
                    leading = { IconTile(RIcons.Eye, RColors.Teal) })
                Hairline(60.dp)
                SwitchRow("Doomscroll detector", Store.doomscrollOn, { Store.doomscrollOn = it },
                    subtitle = "A buzz after 3 minutes of nonstop scrolling",
                    leading = { IconTile(RIcons.Wave, RColors.Pink) })
                Hairline(60.dp)
                SwitchRow("Unlock counter", Store.unlockPillOn, { Store.unlockPillOn = it },
                    subtitle = "Shows “Unlock #12 today” each time",
                    leading = { IconTile(RIcons.Phone, RColors.Green) })
                Hairline(60.dp)
                val grayOk = Grayscale.available(ctx)
                SwitchRow("Drain colour", Store.grayscaleOn && grayOk, { if (grayOk) Store.grayscaleOn = it },
                    subtitle = if (grayOk) "The screen turns gray in the last 5 minutes" else "Needs a one-time setup from a computer",
                    leading = { IconTile(RIcons.Eye, RColors.Fill) })
            }
            if (!Grayscale.available(ctx)) {
                SectionFooter("To enable colour drain, connect the phone to a computer once and run:\nadb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS")
            }
        }
        item {
            Section(header = "Schedules") {
                SwitchRow("Morning gate", Store.morningGateOn, { Store.morningGateOn = it },
                    subtitle = "Limited apps stay closed after your first unlock",
                    leading = { IconTile(RIcons.Sun, RColors.Amber) })
                if (Store.morningGateOn) {
                    Hairline(60.dp)
                    ListRow("Keep closed for", trailing = {
                        Stepper(
                            "${Store.morningGateMinutes} min",
                            onMinus = { Store.morningGateMinutes = (Store.morningGateMinutes - 15).coerceAtLeast(15) },
                            onPlus = { Store.morningGateMinutes = (Store.morningGateMinutes + 15).coerceAtMost(180) },
                        )
                    })
                }
                Hairline(60.dp)
                SwitchRow("Night mode", Store.nightOn, { Store.nightOn = it },
                    subtitle = "Harder questions and longer pauses until 5 AM",
                    leading = { IconTile(RIcons.Moon, RColors.AccentDeep) })
                if (Store.nightOn) {
                    Hairline(60.dp)
                    ListRow("Starts at", trailing = {
                        TimeStepper(Store.nightStart) { Store.nightStart = it }
                    })
                }
                Hairline(60.dp)
                SwitchRow("Downtime", Store.downtimeOn, { Store.downtimeOn = it },
                    subtitle = "Limited apps are fully closed",
                    leading = { IconTile(RIcons.Lock, RColors.Coral) })
                if (Store.downtimeOn) {
                    Hairline(60.dp)
                    ListRow("From", trailing = { TimeStepper(Store.downtimeStart) { Store.downtimeStart = it } })
                    Hairline(16.dp)
                    ListRow("Until", trailing = { TimeStepper(Store.downtimeEnd) { Store.downtimeEnd = it } })
                }
                Hairline(60.dp)
                SwitchRow("Wind-down reminder", Store.windDownOn, { Store.windDownOn = it },
                    subtitle = "A gentle buzz at bedtime to put the phone away",
                    leading = { IconTile(RIcons.Sparkle, RColors.Blue) })
                if (Store.windDownOn) {
                    Hairline(60.dp)
                    ListRow("Bedtime", trailing = { TimeStepper(Store.bedtime) { Store.bedtime = it } })
                }
            }
        }
        item {
            Section(header = "Weekends", footer = "Limits on Saturday and Sunday. Applies to apps and groups.") {
                Box(Modifier.padding(16.dp)) {
                    Segmented(listOf("Same", "1.5\u00d7", "2\u00d7"), Store.weekendMode, { Store.weekendMode = it })
                }
            }
        }
        item {
            var number by remember { mutableStateOf(Buddy.number()) }
            val saved = Buddy.number()
            Section(
                header = "Accountability buddy",
                footer = "Your buddy gets a text when you use an emergency unlock, end focus early, or loosen or remove a limit. Changing the number tells the old one. Normal SMS charges may apply.",
            ) {
                SwitchRow("Text my buddy", Store.buddyOn, { Store.buddyOn = it },
                    leading = { IconTile(RIcons.Phone, RColors.Mint) })
                Hairline(60.dp)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RText("Number", Modifier.width(90.dp))
                    BasicTextField(
                        value = number,
                        onValueChange = { v -> number = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(18) },
                        singleLine = true,
                        textStyle = RTheme.type.body.copy(color = RColors.Label),
                        cursorBrush = SolidColor(RColors.Accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box { if (number.isEmpty()) RText("+91…", color = RColors.Tertiary); inner() }
                        },
                    )
                    val clean = number.filter { it.isDigit() || it == '+' }
                    if (clean != saved && clean.length >= 8) {
                        QuietButton("Save", {
                            val old = saved
                            Store.buddyNumber = clean
                            if (old.isNotBlank() && old != clean) Buddy.notify(ctx, "the buddy number was changed to $clean.", to = old)
                            Haptics.play(Haptics.Kind.Success)
                        }, color = RColors.Accent)
                    }
                }
                Hairline(16.dp)
                if (!smsGranted) {
                    ListRow("Allow sending texts", titleColor = RColors.Accent, showChevron = true,
                        onClick = { smsLauncher.launch(android.Manifest.permission.SEND_SMS) })
                } else {
                    ListRow("Send a test text", titleColor = RColors.Accent, onClick = {
                        Buddy.notify(ctx, "this is a test from the Focus app. You're the accountability buddy.")
                        Haptics.play(Haptics.Kind.Success)
                    })
                }
            }
        }
        item {
            Section(header = "Goal") {
                ListRow("Daily screen time", leading = { IconTile(RIcons.Sparkle, RColors.Mint) }, trailing = {
                    Stepper(
                        TimeUtil.formatDuration(Store.dailyGoalMin * TimeUtil.MINUTE),
                        onMinus = { Store.dailyGoalMin = (Store.dailyGoalMin - 15).coerceAtLeast(30) },
                        onPlus = { Store.dailyGoalMin = (Store.dailyGoalMin + 15).coerceAtMost(720) },
                    )
                })
            }
        }
        item {
            var haptics by remember { mutableStateOf(Store.hapticsOn) }
            Section(header = "Feel") {
                SwitchRow("Haptics", haptics, {
                    haptics = it
                    Store.hapticsOn = it
                    Haptics.enabled = it
                    if (it) Haptics.play(Haptics.Kind.Success)
                }, leading = { IconTile(RIcons.Haptic, RColors.Blue) })
            }
        }
        item {
            val usage = Permissions.hasUsageAccess(ctx)
            val access = Permissions.hasAccessibility(ctx)
            val battery = Permissions.isIgnoringBatteryOptimizations(ctx)
            Section(
                header = "Health check",
                footer = "On Realme, also open App info → Battery → allow background activity, and lock Focus in Recents so it is never closed.",
            ) {
                StatusRow("Usage access", usage) {
                    Permissions.launch(ctx, Permissions.usageAccessIntent(ctx), Permissions.usageAccessFallbackIntent())
                }
                Hairline(60.dp)
                StatusRow("Accessibility service", access) { Permissions.launch(ctx, Permissions.accessibilityIntent()) }
                Hairline(60.dp)
                StatusRow("Unrestricted battery", battery) {
                    Permissions.launch(ctx, Permissions.batteryIntent(ctx), Permissions.appDetailsIntent(ctx))
                }
                Hairline(60.dp)
                ListRow("App info", leading = { IconTile(RIcons.Settings, RColors.Fill) }, showChevron = true,
                    onClick = { Permissions.launch(ctx, Permissions.appDetailsIntent(ctx)) })
            }
        }
        item {
            Section(footer = "Focus has no internet permission. Nothing you do here ever leaves your phone.") {
                ListRow("Version", value = BuildConfig.VERSION_NAME, leading = { IconTile(RIcons.Shield, RColors.Accent) })
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    TimeSheet(timeSheet)
    }
    }
}

/** A time row being edited in the wheel sheet. */
class TimeEdit(val minute: Int, val onChange: (Int) -> Unit)

private val LocalTimeSheet = staticCompositionLocalOf<MutableState<TimeEdit?>> { mutableStateOf(null) }

@Composable
private fun TimeSheet(state: MutableState<TimeEdit?>) {
    val edit = state.value
    var last by remember { mutableStateOf<TimeEdit?>(null) }
    if (edit != null) last = edit
    var picked by remember(edit) { mutableIntStateOf(edit?.minute ?: 0) }
    Sheet(edit != null, onDismiss = { state.value = null }) {
        val e = last ?: return@Sheet
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            QuietButton("Cancel", { state.value = null }, color = RColors.Accent)
            Spacer(Modifier.weight(1f))
            QuietButton("Done", {
                e.onChange(picked)
                Haptics.play(Haptics.Kind.Success)
                state.value = null
            }, color = RColors.Accent)
        }
        key(e) { TimeWheel(e.minute) { picked = it } }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, onFix: () -> Unit) {
    ListRow(
        title,
        leading = { IconTile(if (ok) RIcons.Check else RIcons.Close, if (ok) RColors.Green else RColors.Coral) },
        trailing = {
            if (!ok) RText("Fix", color = RColors.Accent, style = RTheme.type.body)
            else RIcon(RIcons.Check, tint = RColors.Green, size = 18.dp)
        },
        onClick = if (ok) null else onFix,
    )
}

@Composable
private fun TimeStepper(minute: Int, onChange: (Int) -> Unit) {
    val sheet = LocalTimeSheet.current
    Box(
        Modifier
            .pressable { sheet.value = TimeEdit(minute, onChange) }
            .background(Color(0x3D767680), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { RText(TimeUtil.formatMinuteOfDay(minute), style = RTheme.type.body) }
}
