package com.regain.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * A tiny hand-drawn line icon set (24x24, 1.8 stroke). Keeps the APK small
 * and gives a consistent, iOS-like thin-stroke look.
 */
object RIcons {
    private fun icon(name: String, vararg paths: String, filled: Set<Int> = emptySet()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEachIndexed { i, d ->
                if (i in filled) {
                    addPath(pathData = addPathNodes(d), fill = SolidColor(Color.White))
                } else {
                    addPath(
                        pathData = addPathNodes(d),
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = 1.8f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }.build()

    private const val CIRCLE_9 = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"

    val Today = icon("today", CIRCLE_9, "M10.2 12a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0", filled = setOf(1))
    val Limits = icon("limits", "M7 3h10", "M7 21h10", "M8 3c0 5 8 5 8 9s-8 4-8 9", "M16 3c0 5-8 5-8 9s8 4 8 9")
    val Focus = icon("focus", CIRCLE_9, "M7 12a5 5 0 1 0 10 0a5 5 0 1 0 -10 0", "M10.6 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0", filled = setOf(2))
    val Insights = icon("insights", "M5 20v-7", "M12 20V5", "M19 20v-10")
    val Settings = icon(
        "settings", "M4 7h9", "M19 7h1", "M4 17h3", "M13 17h7",
        "M14 7a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0", "M7 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
    )
    val Chevron = icon("chevron", "M9.5 6l6 6-6 6")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val Close = icon("close", "M6.5 6.5l11 11", "M17.5 6.5l-11 11")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7")
    val Moon = icon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Sun = icon(
        "sun", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M12 2.5v2", "M12 19.5v2", "M2.5 12h2", "M19.5 12h2",
        "M5.3 5.3l1.4 1.4", "M17.3 17.3l1.4 1.4", "M5.3 18.7l1.4-1.4", "M17.3 6.7l1.4-1.4",
    )
    val Lock = icon("lock", "M6 11h12v9.5H6z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3")
    val Phone = icon("phone", "M8 2.5h8a1.5 1.5 0 0 1 1.5 1.5v16a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V4A1.5 1.5 0 0 1 8 2.5z", "M11 18.5h2")
    val Flame = icon("flame", "M12 3c1 4 5.5 5.5 5.5 10.5a5.5 5.5 0 0 1-11 0c0-2.5 1.5-4 2.2-5.8 1.4 1.5 1.6 3.3 1.6 3.3S13.5 8 12 3z")
    val Wave = icon("wave", "M3 12c2.2-3.5 4.3-3.5 6 0s3.8 3.5 6 0 3.8-3.5 6 0")
    val Haptic = icon("haptic", "M9 5h6v14H9z", "M5.5 9v6", "M18.5 9v6", "M3 10.5v3", "M21 10.5v3")
    val Question = icon("question", CIRCLE_9, "M9.6 9.4a2.5 2.5 0 1 1 3.4 2.4c-.6.3-1 .8-1 1.5v.4", "M12 16.8v.2")
    val Flip = icon("flip", "M7 4v14", "M4 15l3 3 3-3", "M17 20V6", "M14 9l3-3 3 3")
    val Trash = icon("trash", "M4.5 7h15", "M9.5 7V4.5h5V7", "M6.5 7l1 13h9l1-13")
    val Shield = icon("shield", "M12 3l7.5 3v5.5c0 4.5-3.2 8.2-7.5 9.5-4.3-1.3-7.5-5-7.5-9.5V6z")
    val Timer = icon("timer", "M5 13a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M12 13V9.5", "M10 2.5h4")
    val Battery = icon("battery", "M3 8h15v8H3z", "M21 11v2", "M5.5 10.5v3", "M8.5 10.5v3")
    val Sparkle = icon("sparkle", "M12 3c.6 4.6 3.4 7.4 8 8-4.6.6-7.4 3.4-8 8-.6-4.6-3.4-7.4-8-8 4.6-.6 7.4-3.4 8-8z")
    val Eye = icon("eye", "M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12z", "M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0")
    val Keyboard = icon("keyboard", "M3 6.5h18v11H3z", "M7 10h.01", "M11 10h.01", "M15 10h.01", "M8 14h8")
    val Hash = icon("hash", "M9 4l-2 16", "M17 4l-2 16", "M4.5 9h16", "M3.5 15h16")
    val Backspace = icon("backspace", "M8.5 5.5H20v13H8.5L3 12z", "M11.5 9.5l5 5", "M16.5 9.5l-5 5")
}
