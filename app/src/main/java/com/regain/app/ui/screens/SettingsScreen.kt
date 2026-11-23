package com.regain.app.ui.screens

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
    rememberRefreshKey() // re-read prefs and permission state on resume

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
                footer = "On Realme, also open App info → Battery → allow background activity, and lock Regain in Recents so it is never closed.",
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
            Section(footer = "Regain has no internet permission. Nothing you do here ever leaves your phone.") {
                ListRow("Version", value = BuildConfig.VERSION_NAME, leading = { IconTile(RIcons.Shield, RColors.Accent) })
            }
            Spacer(Modifier.height(20.dp))
        }
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
    Stepper(
        TimeUtil.formatMinuteOfDay(minute),
        onMinus = { onChange((minute - 30 + 1440) % 1440) },
        onPlus = { onChange((minute + 30) % 1440) },
    )
}
