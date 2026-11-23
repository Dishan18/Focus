package com.regain.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.regain.app.core.Haptics
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme
import kotlin.math.abs

private val ItemHeight = 40.dp
private const val VISIBLE = 5

/**
 * iOS-style wheel: snaps to rows, fades and shrinks rows away from the centre,
 * and ticks a haptic for every row that passes.
 */
@Composable
fun Wheel(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    width: Dp = 72.dp,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, items.lastIndex))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val itemPx = with(androidx.compose.ui.platform.LocalDensity.current) { ItemHeight.toPx() }

    // The centred row: whichever item's middle is closest to the viewport middle.
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - mid) }?.index
                ?.coerceIn(0, items.lastIndex) ?: selected
        }
    }
    var lastTick by remember { mutableIntStateOf(selected) }
    LaunchedEffect(state) {
        snapshotFlow { centered }.collect { i ->
            if (i != lastTick) { lastTick = i; Haptics.play(Haptics.Kind.Tick) }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling -> if (!scrolling) onSelect(centered) }
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        modifier = Modifier.width(width).height(ItemHeight * VISIBLE),
        contentPadding = PaddingValues(vertical = ItemHeight * (VISIBLE / 2)),
    ) {
        items(items.size) { i ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ItemHeight)
                    .graphicsLayer {
                        val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
                        val center = (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2f
                        val dist = if (info == null) 2f else abs((info.offset + info.size / 2f) - center) / itemPx
                        alpha = (1f - dist * 0.32f).coerceIn(0.15f, 1f)
                        val s = (1f - dist * 0.08f).coerceIn(0.8f, 1f)
                        scaleX = s; scaleY = s
                        rotationX = (dist * 18f).coerceAtMost(60f) * if ((info?.offset ?: 0) + itemPx / 2 < center) 1 else -1
                    },
                contentAlignment = Alignment.Center,
            ) {
                RText(items[i], style = RTheme.type.title3, align = TextAlign.Center)
            }
        }
    }
}

/** Hour : minute AM/PM picker built from three wheels over a highlight bar. */
@Composable
fun TimeWheel(minuteOfDay: Int, onChange: (Int) -> Unit) {
    val hours = (1..12).map { it.toString() }
    val minutes = (0 until 60 step 5).map { "%02d".format(it) }
    val ampm = listOf("AM", "PM")
    val h24 = minuteOfDay / 60
    var h12 by remember { mutableIntStateOf(if (h24 % 12 == 0) 11 else h24 % 12 - 1) }
    var m by remember { mutableIntStateOf((minuteOfDay % 60) / 5) }
    var pm by remember { mutableIntStateOf(if (h24 >= 12) 1 else 0) }
    fun emit() {
        val hour12 = h12 + 1
        val hour = (hour12 % 12) + if (pm == 1) 12 else 0
        onChange(hour * 60 + m * 5)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(ItemHeight)
                .background(Color(0x1FFFFFFF), RoundedCornerShape(10.dp)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Wheel(hours, h12, { h12 = it; emit() })
            RText(":", style = RTheme.type.title3, color = RColors.Secondary)
            Wheel(minutes, m, { m = it; emit() })
            Wheel(ampm, pm, { pm = it; emit() })
        }
    }
}
