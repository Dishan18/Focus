package com.regain.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import com.regain.app.ui.screens.LocalHideTabBar
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.regain.app.core.Haptics
import com.regain.app.core.Permissions
import com.regain.app.data.Store
import com.regain.app.ui.components.Hairline
import com.regain.app.ui.components.RIcon
import com.regain.app.ui.components.RIcons
import com.regain.app.ui.components.RText
import com.regain.app.ui.screens.FocusScreen
import com.regain.app.ui.screens.InsightsScreen
import com.regain.app.ui.screens.LimitsScreen
import com.regain.app.ui.screens.OnboardingScreen
import com.regain.app.ui.screens.SettingsScreen
import com.regain.app.ui.screens.TodayScreen
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme
import com.regain.app.ui.theme.RegainTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { RegainTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        com.regain.app.service.GuardService.ownUiResumed()
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Today", RIcons.Today),
    Tab("Limits", RIcons.Limits),
    Tab("Focus", RIcons.Focus),
    Tab("Insights", RIcons.Insights),
)

@Composable
private fun Root() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var onboarded by remember { mutableStateOf(Store.onboarded) }
    // Permissions can be revoked any time; re-check whenever we come back.
    var healthy by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        healthy = Permissions.hasUsageAccess(ctx) && Permissions.hasAccessibility(ctx)
        onPauseOrDispose { }
    }

    if (!onboarded || !healthy) {
        OnboardingScreen(onDone = {
            Store.onboarded = true
            onboarded = true
            healthy = Permissions.hasUsageAccess(ctx) && Permissions.hasAccessibility(ctx)
        }, returning = onboarded)
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = !settingsOpen && tab != 0) { tab = 0 }

    val hideTabs = remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalHideTabBar provides hideTabs) {
    Box(Modifier.fillMaxSize().background(RColors.Background)) {
        AnimatedContent(
            targetState = settingsOpen,
            transitionSpec = {
                if (targetState) {
                    slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it } togetherWith
                        (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(320)))
                } else {
                    (slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(320))) togetherWith
                        slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it }
                }
            },
            label = "push",
        ) { inSettings ->
            if (inSettings) {
                SettingsScreen(onBack = { settingsOpen = false })
            } else {
                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                        label = "tab",
                    ) { t ->
                        when (t) {
                            0 -> TodayScreen(onOpenSettings = { settingsOpen = true }, onOpenLimits = { tab = 1 })
                            1 -> LimitsScreen()
                            2 -> FocusScreen()
                            else -> InsightsScreen()
                        }
                    }
                    val hidden by hideTabs
                    val slide by animateFloatAsState(if (hidden) 1f else 0f, tween(260), label = "tabsHide")
                    TabBar(
                        tab, onSelect = { tab = it },
                        Modifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { translationY = slide * 140.dp.toPx(); alpha = 1f - slide },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Color(0xF00C0C0E))) {
        Hairline(0.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(56.dp),
        ) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                val scale by animateFloatAsState(
                    if (active) 1.08f else 1f,
                    spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "tabScale",
                )
                val tint = if (active) RColors.Accent else Color(0xFF8E8E93)
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (!active) {
                                Haptics.play(Haptics.Kind.Tick)
                                onSelect(i)
                            }
                        }
                        .padding(top = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RIcon(tab.icon, Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, tint = tint, size = 25.dp)
                    Spacer(Modifier.height(3.dp))
                    RText(tab.title, style = RTheme.type.caption.copy(fontSize = androidx.compose.ui.unit.TextUnit(10.5f, androidx.compose.ui.unit.TextUnitType.Sp)), color = tint)
                }
            }
        }
    }
}
