package com.regain.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.regain.app.core.Haptics
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme

// ---------------------------------------------------------------- text & icons

@Composable
fun RText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = RTheme.type.body,
    color: Color = RColors.Label,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align ?: style.textAlign),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun RIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = RColors.Label, size: Dp = 22.dp) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

// ---------------------------------------------------------------- press feel

/** iOS-style press: a soft spring scale-down, optional tick haptic, no ripple. */
fun Modifier.pressable(
    enabled: Boolean = true,
    haptic: Haptics.Kind? = Haptics.Kind.Tick,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            haptic?.let(Haptics::play)
            onClick()
        }
}

// ---------------------------------------------------------------- grouped lists

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    RText(
        text.uppercase(),
        modifier.padding(start = 32.dp, end = 32.dp, top = 22.dp, bottom = 7.dp),
        style = RTheme.type.footnote,
        color = RColors.Secondary,
    )
}

@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    RText(
        text,
        modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp),
        style = RTheme.type.footnote,
        color = RColors.Secondary,
    )
}

/** An inset grouped card; children are separated by hairlines. */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        if (header != null) SectionHeader(header)
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(RColors.Grouped),
            content = content,
        )
        if (footer != null) SectionFooter(footer)
    }
}

@Composable
fun Hairline(startInset: Dp = 16.dp) {
    Box(
        Modifier
            .padding(start = startInset)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(RColors.Separator),
    )
}

/** Rounded-square tinted icon, like iOS Settings. */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 30.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) { RIcon(icon, tint = Color.White, size = size * 0.62f) }
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    titleColor: Color = RColors.Label,
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.pressable(pressedScale = 0.985f, onClick = onClick) else modifier
    Row(
        m
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            RText(title, color = titleColor, maxLines = 1)
            if (subtitle != null) {
                RText(subtitle, style = RTheme.type.footnote, color = RColors.Secondary, maxLines = 2)
            }
        }
        if (value != null) {
            RText(value, color = RColors.Secondary, maxLines = 1)
            Spacer(Modifier.width(6.dp))
        }
        trailing?.invoke()
        if (showChevron) RIcon(RIcons.Chevron, tint = RColors.Tertiary, size = 18.dp)
    }
}

// ---------------------------------------------------------------- controls

/** iOS switch: 51x31 capsule, spring-animated thumb. */
@Composable
fun RSwitch(checked: Boolean, onChange: (Boolean) -> Unit, accent: Color = RColors.Green) {
    val offset by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium), label = "thumb",
    )
    val track by animateColorAsState(if (checked) accent else RColors.Fill, label = "track")
    Box(
        Modifier
            .size(51.dp, 31.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                Haptics.play(Haptics.Kind.Tick)
                onChange(!checked)
            }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(27.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) = ListRow(title, subtitle = subtitle, leading = leading, trailing = { RSwitch(checked, onChange) })

/** Segmented control with a sliding thumb. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0x3D767680))
            .padding(2.dp),
    ) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(
            segW * selected,
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow), label = "seg",
        )
        Box(
            Modifier
                .offset(x = x)
                .width(segW)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF636366)),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (i != selected) {
                                Haptics.play(Haptics.Kind.Tick)
                                onSelect(i)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    RText(
                        label,
                        style = RTheme.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** − value + stepper. */
@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RText(value, color = RColors.Secondary)
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x3D767680)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperHalf("−", minusEnabled, onMinus)
            Box(Modifier.width(0.5.dp).height(18.dp).background(RColors.Separator))
            StepperHalf("+", plusEnabled, onPlus)
        }
    }
}

@Composable
private fun StepperHalf(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(46.dp)
            .fillMaxHeight()
            .pressable(enabled = enabled, pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        RText(symbol, style = RTheme.type.title3, color = if (enabled) RColors.Label else RColors.Tertiary)
    }
}

/** Big capsule button. Gradient fill makes the "good" choice feel rewarding. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = Brush.horizontalGradient(listOf(RColors.Accent, RColors.AccentDeep)),
    textColor: Color = Color.White,
    haptic: Haptics.Kind? = Haptics.Kind.Tick,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, label = "btnAlpha")
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { this.alpha = alpha }
            .pressable(enabled = enabled, haptic = haptic, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        RText(text, style = RTheme.type.headline, color = textColor)
    }
}

/** Frosted secondary capsule. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, label = "glassAlpha")
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { this.alpha = alpha }
            .pressable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x1FFFFFFF))
            .border(0.5.dp, Color(0x26FFFFFF), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        RText(text, style = RTheme.type.headline)
    }
}

/** Plain text button, used for the deliberately quiet options. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = RColors.Secondary) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { RText(text, style = RTheme.type.subhead, color = color) }
}

/** Selectable chip. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) Color.White else Color(0x1FFFFFFF), label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.Black else RColors.Label, label = "chipFg")
    Box(
        modifier
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { RText(text, style = RTheme.type.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = fg, maxLines = 1) }
}

/** Thin rounded progress bar. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val f by animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow), label = "bar",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Color(0x24FFFFFF)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Card used for hero blocks on the Today and Insights screens. */
@Composable
fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(18.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(RColors.Grouped)
            .padding(padding),
        content = content,
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = RColors.Label) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(RColors.Grouped)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        RText(label, style = RTheme.type.footnote, color = RColors.Secondary, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        RText(value, style = RTheme.type.title2, color = accent, maxLines = 1)
    }
}

@Composable
fun RowScope.Weighted(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f)) { content() }
}

@Composable
fun SpacedRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}
