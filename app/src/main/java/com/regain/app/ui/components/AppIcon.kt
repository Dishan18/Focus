package com.regain.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.regain.app.RegainApp
import com.regain.app.ui.theme.RColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App icon loaded off the main thread and cached in a small LRU. */
@Composable
fun AppIcon(pkg: String, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp by produceState<ImageBitmap?>(null, pkg, px) {
        value = withContext(Dispatchers.IO) { RegainApp.instance.catalog.icon(pkg, px) }
    }
    val shape = RoundedCornerShape(size * 0.24f)
    Box(modifier.size(size).clip(shape).background(RColors.GroupedRaised)) {
        bmp?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}
