package com.regain.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.regain.app.RegainApp
import com.regain.app.core.TimeUtil
import com.regain.app.data.Store
import com.regain.app.data.UsageRepository
import com.regain.app.ui.theme.RColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ticks whenever the screen resumes or Focus's own data changes. */
@Composable
fun rememberRefreshKey(): Long {
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val changes by Store.changes.collectAsState()
    return changes * 1000 + resumes
}

data class WeekData(val days: List<UsageRepository.DayUsage>) {
    val today get() = days.last()
}

/** Last 7 days of usage, loaded off the main thread. */
@Composable
fun rememberWeek(): WeekData? {
    val key = rememberRefreshKey()
    val state by produceState<WeekData?>(null, key) {
        value = withContext(Dispatchers.IO) {
            val days = RegainApp.instance.usage.lastDays(7)
            days.forEach { Store.recordDayTotal(TimeUtil.dayKey(it.dayStart), it.totalMs) }
            WeekData(days)
        }
    }
    return state
}

/** Consecutive days at or under the daily goal, counting back from yesterday, plus today if on track. */
fun streak(todayMs: Long): Int {
    val goal = Store.dailyGoalMin * TimeUtil.MINUTE
    val totals = Store.dayTotals()
    var n = 0
    var t = TimeUtil.startOfDay() - TimeUtil.HOUR
    for (i in 0 until 90) {
        val v = totals[TimeUtil.dayKey(t)] ?: break
        if (v == 0L || v > goal) break
        n++
        t = TimeUtil.startOfDay(t) - TimeUtil.HOUR
    }
    return n + if (todayMs <= goal) 1 else 0
}

// ---------------------------------------------------------------- sheet

/** Set true by sheets so the tab bar slides away underneath them. */
val LocalHideTabBar = staticCompositionLocalOf { mutableStateOf(false) }

/** iOS-style bottom sheet: dimmed backdrop, spring slide-up, drag down to close. */
@Composable
fun Sheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    fullHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hideTabs = LocalHideTabBar.current
    LaunchedEffect(visible) { hideTabs.value = visible }
    DisposableEffect(Unit) { onDispose { hideTabs.value = false } }
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { it },
            exit = slideOutVertically(tween(240)) { it },
        ) {
            var drag by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(visible) { drag = 0f }
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (fullHeight) Modifier.fillMaxHeight(0.9f) else Modifier)
                    .graphicsLayer { translationY = drag }
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(RColors.Grouped)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { if (drag > 220f) onDismiss() else drag = 0f },
                            onDragCancel = { drag = 0f },
                        ) { _, dy -> drag = (drag + dy).coerceAtLeast(0f) }
                    }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding(),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(Color(0x66FFFFFF)))
                }
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}
