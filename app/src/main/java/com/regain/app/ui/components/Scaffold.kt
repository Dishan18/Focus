package com.regain.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme

/**
 * iOS-style screen: a large title that scrolls away, and a compact translucent
 * nav bar whose small title fades in once the large one is gone.
 */
@Composable
fun LargeTitleScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    bottomPadding: Int = 110,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    val collapsed by remember {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 90 }
    }
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(180), label = "bar")

    Box(modifier.fillMaxSize().background(RColors.Background)) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding.dp),
        ) {
            item(key = "__title") {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 44.dp, start = 20.dp, end = 20.dp, bottom = 6.dp),
                ) {
                    if (subtitle != null) {
                        RText(subtitle.uppercase(), style = RTheme.type.footnote, color = RColors.Secondary)
                    }
                    RText(title, style = RTheme.type.largeTitle)
                }
            }
            content()
        }

        // Compact nav bar.
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = barAlpha }
                    .background(Color(0xE6101012)),
            ) {
                Column {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                        RText(title, style = RTheme.type.headline, align = TextAlign.Center)
                    }
                    Hairline(0.dp)
                }
            }
        }
        // Bar buttons stay visible in both states.
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(44.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

/** Round translucent nav-bar button. */
@Composable
fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, tint: Color = RColors.Accent) {
    Box(
        Modifier
            .padding(4.dp)
            .pressable(pressedScale = 0.88f, onClick = onClick)
            .background(Color(0x1FFFFFFF), androidx.compose.foundation.shape.CircleShape)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) { RIcon(icon, tint = tint, size = 20.dp) }
}
