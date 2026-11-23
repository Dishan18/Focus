package com.regain.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.regain.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Dark-only palette modelled on iOS system colours, with our own accents. */
@Immutable
object RColors {
    val Background = Color(0xFF000000)
    val Grouped = Color(0xFF1C1C1E)
    val GroupedRaised = Color(0xFF2C2C2E)
    val Fill = Color(0xFF3A3A3C)
    val Separator = Color(0x5C545458)
    val Label = Color(0xFFFFFFFF)
    val Secondary = Color(0x99EBEBF5)
    val Tertiary = Color(0x4DEBEBF5)

    val Accent = Color(0xFF8B7CFF)      // soft violet
    val AccentDeep = Color(0xFF5B4BDB)
    val Mint = Color(0xFF63E6BE)
    val Teal = Color(0xFF40C8E0)
    val Amber = Color(0xFFFFB340)
    val Coral = Color(0xFFFF6961)
    val Pink = Color(0xFFFF7EB6)
    val Blue = Color(0xFF64A8FF)
    val Green = Color(0xFF30D158)
}

@Immutable
data class RType(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val display: TextStyle,
)

/** Inter (variable font, SIL OFL) for a crisp, iOS-like feel. */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val family = FontFamily(
    inter(100), inter(200), inter(300), inter(400), inter(500), inter(600), inter(700),
)

val DefaultType = RType(
    largeTitle = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.012).em),
    title1 = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em),
    title2 = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.008).em),
    title3 = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp),
    headline = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.004).em),
    body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.004).em),
    callout = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp),
    subhead = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    footnote = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em),
    display = TextStyle(fontFamily = family, fontWeight = FontWeight.Thin, fontSize = 76.sp, lineHeight = 80.sp, letterSpacing = (-0.03).em),
)

val LocalType = staticCompositionLocalOf { DefaultType }

object RTheme {
    val type: RType
        @Composable get() = LocalType.current
}

@Composable
fun RegainTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalType provides DefaultType, content = content)
}
