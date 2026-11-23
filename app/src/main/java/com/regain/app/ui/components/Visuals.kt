package com.regain.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.regain.app.ui.theme.RColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------- aurora

@Immutable
data class AuroraPalette(val a: Color, val b: Color, val c: Color, val d: Color)

object Auroras {
    val Focus = AuroraPalette(Color(0xFF6C4DFF), Color(0xFF1FB5C9), Color(0xFF2E5BFF), Color(0xFFB04DFF))
    val Calm = AuroraPalette(Color(0xFF1BAE8F), Color(0xFF2D7DFF), Color(0xFF6FE3C8), Color(0xFF3B3BB8))
    val Warning = AuroraPalette(Color(0xFFFF7A45), Color(0xFFFF3D6E), Color(0xFFFFB340), Color(0xFF8A2BE2))
    val Night = AuroraPalette(Color(0xFF2B1A6B), Color(0xFF14365E), Color(0xFF4B2A8F), Color(0xFF0E4D57))
    val Reward = AuroraPalette(Color(0xFF34E0A1), Color(0xFF8B7CFF), Color(0xFF40C8E0), Color(0xFF7CFFB2))
}

/**
 * Slow, drifting mesh-like gradient made of four soft radial blobs on black.
 * Colours cross-fade when the palette changes. Only draws while on screen,
 * and a few radial gradients are cheap for the GPU.
 */
@Composable
fun Aurora(
    palette: AuroraPalette,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    speed: Float = 1f,
) {
    val a by animateColorAsState(palette.a, tween(1200), label = "a")
    val b by animateColorAsState(palette.b, tween(1200), label = "b")
    val c by animateColorAsState(palette.c, tween(1200), label = "c")
    val d by animateColorAsState(palette.d, tween(1200), label = "d")
    val level by animateFloatAsState(intensity, tween(900), label = "lvl")

    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween((26_000 / speed).toInt(), easing = LinearEasing)),
        label = "t",
    )

    Canvas(modifier.fillMaxSize()) {
        drawRect(Color.Black)
        val w = size.width
        val h = size.height
        val r = maxOf(w, h)
        fun blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
            val center = Offset(cx * w, cy * h)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = alpha * level), color.copy(alpha = 0f)),
                    center = center, radius = radius,
                ),
                radius = radius, center = center,
            )
        }
        blob(a, 0.25f + 0.18f * sin(t), 0.22f + 0.12f * cos(t * 2), r * 0.62f, 0.75f)
        blob(b, 0.80f + 0.15f * cos(t + 1.3f), 0.38f + 0.18f * sin(t + 0.7f), r * 0.55f, 0.6f)
        blob(c, 0.45f + 0.22f * sin(t * 2 + 2.1f), 0.78f + 0.10f * cos(t + 2.4f), r * 0.6f, 0.55f)
        blob(d, 0.65f + 0.2f * cos(t * 3 + 0.4f) * 0.5f, 0.12f + 0.1f * sin(t * 2 + 3f), r * 0.4f, 0.45f)
        // Soft vignette keeps text readable.
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), startY = h * 0.45f, endY = h),
        )
    }
}

// ---------------------------------------------------------------- bloom

/**
 * "Bloom": the living mark. A soft organic flower whose petal count follows the
 * streak and whose fullness follows how well today is going (health 0..1).
 */
@Composable
fun Bloom(health: Float, petals: Int, modifier: Modifier = Modifier) {
    val h by animateFloatAsState(health.coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = 40f), label = "health")
    val transition = rememberInfiniteTransition(label = "bloom")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "spin")
    val breathe by transition.animateFloat(
        0.96f, 1.04f, infiniteRepeatable(tween(4200), RepeatMode.Reverse), label = "breathe",
    )
    val warm = lerp(RColors.Amber, RColors.Mint, h)
    val cool = lerp(RColors.Coral, RColors.Accent, h)

    Canvas(modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val maxR = min(size.width, size.height) / 2
        val base = maxR * (0.48f + 0.26f * h) * breathe
        val amp = 0.05f + 0.16f * h
        val n = petals.coerceIn(3, 12)

        drawCircle(
            Brush.radialGradient(listOf(warm.copy(alpha = 0.35f * (0.4f + h)), Color.Transparent), Offset(cx, cy), maxR),
            radius = maxR, center = Offset(cx, cy),
        )
        rotate(spin, Offset(cx, cy)) {
            for (layer in 0..2) {
                val scale = 1f - layer * 0.22f
                val path = Path()
                val steps = 180
                for (i in 0..steps) {
                    val th = (i.toFloat() / steps) * 2f * PI.toFloat()
                    val rr = base * scale * (1f + amp * sin(n * th + layer * 0.9f))
                    val x = cx + rr * cos(th)
                    val y = cy + rr * sin(th)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(
                    path,
                    Brush.linearGradient(
                        listOf(warm.copy(alpha = 0.55f - layer * 0.12f), cool.copy(alpha = 0.6f - layer * 0.12f)),
                        start = Offset(cx - base, cy - base), end = Offset(cx + base, cy + base),
                    ),
                )
            }
        }
        drawCircle(Color.White.copy(alpha = 0.9f), radius = maxR * 0.05f, center = Offset(cx, cy))
    }
}

private fun lerp(a: Color, b: Color, f: Float) = Color(
    red = a.red + (b.red - a.red) * f,
    green = a.green + (b.green - a.green) * f,
    blue = a.blue + (b.blue - a.blue) * f,
    alpha = a.alpha + (b.alpha - a.alpha) * f,
)

// ---------------------------------------------------------------- ring

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 10.dp,
    colors: List<Color> = listOf(RColors.Mint, RColors.Accent, RColors.Pink),
    track: Color = Color(0x22FFFFFF),
    animate: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    val p by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        if (animate) spring(dampingRatio = 0.85f, stiffness = 60f) else tween(0), label = "ring",
    )
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = min(size.width, size.height) - sw
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arcSize = Size(d, d)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            if (p > 0f) {
                rotate(-90f) {
                    drawArc(
                        Brush.sweepGradient(colors + colors.first()),
                        0f, 360f * p, false, topLeft, arcSize,
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}
