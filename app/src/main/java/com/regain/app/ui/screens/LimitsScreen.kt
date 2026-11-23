package com.regain.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.regain.app.RegainApp
import com.regain.app.core.Buddy
import com.regain.app.core.Haptics
import com.regain.app.core.TimeUtil
import com.regain.app.data.AppCatalog
import com.regain.app.data.Store
import com.regain.app.service.GuardService
import com.regain.app.ui.components.AppIcon
import com.regain.app.ui.components.BarButton
import com.regain.app.ui.components.Card
import com.regain.app.ui.components.Chip
import com.regain.app.ui.components.Hairline
import com.regain.app.ui.components.ListRow
import com.regain.app.ui.components.LargeTitleScreen
import com.regain.app.ui.components.PrimaryButton
import com.regain.app.ui.components.ProgressBar
import com.regain.app.ui.components.QuietButton
import com.regain.app.ui.components.RIcon
import com.regain.app.ui.components.RIcons
import com.regain.app.ui.components.RText
import com.regain.app.ui.components.Section
import com.regain.app.ui.components.pressable
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LimitsScreen() {
    val catalog = RegainApp.instance.catalog
    val week = rememberWeek()
    val limits = run { rememberRefreshKey(); Store.limits() }
    var picker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    val groups = run { rememberRefreshKey(); Store.groups() }
    var editingGroup by remember { mutableStateOf<String?>(null) } // group id, "new", or null

    val apps by produceState<List<AppCatalog.AppInfo>>(emptyList()) {
        value = withContext(Dispatchers.IO) { catalog.launchableApps() }
    }
    val suggestions = remember(apps, limits) {
        val installed = apps.map { it.pkg }.toSet()
        catalog.suggested.filter { it in installed && it !in limits }.take(5)
    }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScreen(
            title = "Limits",
            trailing = { BarButton(RIcons.Plus, { picker = true }) },
        ) {
            if (limits.isEmpty() && groups.isEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Card {
                        RIcon(RIcons.Limits, tint = RColors.Accent, size = 34.dp)
                        Spacer(Modifier.height(10.dp))
                        RText("No limits yet", style = RTheme.type.title3)
                        Spacer(Modifier.height(4.dp))
                        RText(
                            "Pick the apps that eat your time and give each a daily budget. Focus steps in when it runs out.",
                            color = RColors.Secondary, style = RTheme.type.subhead,
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton("Add a limit", { picker = true })
                    }
                }
            } else if (limits.isNotEmpty()) {
                item {
                    Section(
                        header = "Daily limits",
                        footer = "When time is up you can extend twice per app (4 a day in total) by 5–15 minutes, after 5 mental-math questions.",
                    ) {
                        limits.entries.sortedBy { catalog.label(it.key).lowercase() }.forEachIndexed { i, (pkg, min) ->
                            if (i > 0) Hairline(62.dp)
                            val used = week?.today?.timeOf(pkg) ?: 0
                            val allowed = (Store.allowedMinutes(pkg) ?: min) * TimeUtil.MINUTE
                            LimitRow(pkg, catalog.label(pkg), used, allowed, Store.scaled(min)) { editing = pkg }
                        }
                    }
                }
            }

            item {
                Section(
                    header = "Groups",
                    footer = "A group shares one daily budget across several apps, like \u201cSocial \u00b7 1h\u201d for all of them together.",
                ) {
                    groups.values.sortedBy { it.name.lowercase() }.forEachIndexed { i, g ->
                        if (i > 0) Hairline(62.dp)
                        val used = week?.today?.let { d -> g.pkgs.sumOf { d.timeOf(it) } } ?: 0
                        val allowed = (Store.allowedMinutes("g:${g.id}") ?: g.minutes) * TimeUtil.MINUTE
                        GroupRow(g, used, allowed) { editingGroup = g.id }
                    }
                    if (groups.isNotEmpty()) Hairline(16.dp)
                    ListRow(
                        "New group",
                        titleColor = RColors.Accent,
                        leading = { RIcon(RIcons.Plus, tint = RColors.Accent, size = 22.dp) },
                        onClick = { editingGroup = "new" },
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                item {
                    Section(header = "Suggested") {
                        suggestions.forEachIndexed { i, pkg ->
                            if (i > 0) Hairline(62.dp)
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(pkg, 34.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    RText(catalog.label(pkg), maxLines = 1)
                                    val t = week?.days?.sumOf { it.timeOf(pkg) } ?: 0
                                    if (t > 0) RText("${TimeUtil.formatDuration(t / 7)} a day this week", style = RTheme.type.footnote, color = RColors.Secondary)
                                }
                                Box(
                                    Modifier
                                        .pressable(haptic = Haptics.Kind.Success) {
                                            Store.setLimit(pkg, 30)
                                            GuardService.refresh()
                                        }
                                        .background(Color(0x298B7CFF), CircleShape)
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                ) { RText("30 min", style = RTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = RColors.Accent) }
                            }
                        }
                    }
                }
            }
        }

        AppPickerSheet(
            visible = picker,
            apps = apps,
            limited = limits.keys,
            onDismiss = { picker = false },
            onPick = { picker = false; editing = it },
        )
        LimitEditorSheet(
            pkg = editing,
            onDismiss = { editing = null },
        )
        GroupEditorSheet(
            groupId = editingGroup,
            apps = apps,
            onDismiss = { editingGroup = null },
        )
    }
}

@Composable
private fun LimitRow(pkg: String, label: String, used: Long, allowed: Long, base: Int, onClick: () -> Unit) {
    val f = used.toFloat() / allowed
    val left = allowed - used
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(pkg, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText(label, Modifier.weight(1f), maxLines = 1)
                RText(
                    if (left <= 0) "Done for today" else "${TimeUtil.formatDuration(left)} left",
                    style = RTheme.type.subhead,
                    color = if (left <= 0) RColors.Coral else RColors.Secondary,
                )
            }
            Spacer(Modifier.height(7.dp))
            ProgressBar(f, if (f >= 1f) RColors.Coral else if (f > 0.8f) RColors.Amber else RColors.Accent, height = 5.dp)
            Spacer(Modifier.height(5.dp))
            val pending = Store.pendingLimits()[pkg]
            RText(
                "${TimeUtil.formatDuration(used)} of ${TimeUtil.formatDuration(base * TimeUtil.MINUTE)}" +
                    (if (allowed > base * TimeUtil.MINUTE) " + ${TimeUtil.formatDuration(allowed - base * TimeUtil.MINUTE)} extended" else "") +
                    when (pending) {
                        null -> ""
                        0 -> " · removed tomorrow"
                        else -> " · ${TimeUtil.formatDuration(pending * TimeUtil.MINUTE)} from tomorrow"
                    },
                style = RTheme.type.footnote, color = RColors.Tertiary,
            )
        }
        Spacer(Modifier.width(8.dp))
        RIcon(RIcons.Chevron, tint = RColors.Tertiary, size = 18.dp)
    }
}

@Composable
private fun AppPickerSheet(
    visible: Boolean,
    apps: List<AppCatalog.AppInfo>,
    limited: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Sheet(visible, onDismiss, fullHeight = true) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RText("Choose an app", style = RTheme.type.title3, modifier = Modifier.weight(1f))
            QuietButton("Cancel", onDismiss, color = RColors.Accent)
        }
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = RTheme.type.body.copy(color = RColors.Label),
            cursorBrush = SolidColor(RColors.Accent),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .background(Color(0x3D767680), RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) RText("Search", color = RColors.Tertiary)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        val filtered = remember(apps, query, limited) {
            apps.filter { it.pkg !in limited && (query.isBlank() || it.label.contains(query, ignoreCase = true)) }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(filtered, key = { it.pkg }) { app ->
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable(pressedScale = 0.985f) { onPick(app.pkg) }
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.pkg, 36.dp)
                        Spacer(Modifier.width(14.dp))
                        RText(app.label, Modifier.weight(1f), maxLines = 1)
                        RIcon(RIcons.Plus, tint = RColors.Accent, size = 20.dp)
                    }
                    Hairline(70.dp)
                }
            }
        }
    }
}

@Composable
private fun LimitEditorSheet(pkg: String?, onDismiss: () -> Unit) {
    // Keep showing the last app while the sheet animates out.
    var lastPkg by remember { mutableStateOf("") }
    if (pkg != null) lastPkg = pkg
    val existing = remember(pkg) { pkg?.let { Store.limits()[it] } }
    var minutes by remember(pkg) { mutableIntStateOf(existing ?: 30) }

    val ctx = LocalContext.current
    Sheet(pkg != null, onDismiss) {
        val p = lastPkg
        if (p.isEmpty()) return@Sheet
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(10.dp))
            AppIcon(p, 56.dp)
            Spacer(Modifier.height(8.dp))
            RText(RegainApp.instance.catalog.label(p), style = RTheme.type.headline)
            RText("Daily limit", style = RTheme.type.footnote, color = RColors.Secondary)
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−", enabled = minutes > 5) { minutes = (minutes - 5).coerceAtLeast(5) }
                AnimatedContent(
                    targetState = minutes,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(spring(dampingRatio = 0.7f)) { if (up) it else -it } + fadeIn()) togetherWith
                            (slideOutVertically { if (up) -it else it } + fadeOut())
                    },
                    label = "min",
                    modifier = Modifier.width(170.dp),
                ) { m ->
                    RText(
                        TimeUtil.formatDuration(m * TimeUtil.MINUTE),
                        style = RTheme.type.display.copy(fontSize = RTheme.type.largeTitle.fontSize * 1.4f, fontWeight = FontWeight.Light),
                        align = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
                StepButton("+", enabled = minutes < 600) { minutes = (minutes + 5).coerceAtMost(600) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 90).forEach { m ->
                    Chip(TimeUtil.formatDuration(m * TimeUtil.MINUTE), selected = minutes == m, onClick = { minutes = m })
                }
            }
            Spacer(Modifier.height(14.dp))
            val pending = Store.pendingLimits()[p]
            RText(
                when {
                    pending == 0 -> "Removal is scheduled for tomorrow."
                    pending != null -> "Changes to ${TimeUtil.formatDuration(pending * TimeUtil.MINUTE)} tomorrow."
                    existing != null && minutes > existing -> "Loosening a limit takes effect tomorrow."
                    else -> "Tightening takes effect right away."
                },
                style = RTheme.type.footnote, color = RColors.Tertiary,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(if (existing == null) "Set limit" else "Save", {
                val appliedNow = Store.setLimit(p, minutes)
                if (!appliedNow) Buddy.notify(ctx, "raised the ${RegainApp.instance.catalog.label(p)} limit to ${TimeUtil.formatDuration(minutes * TimeUtil.MINUTE)} (from tomorrow).")
                GuardService.refresh()
                onDismiss()
            }, haptic = Haptics.Kind.Success)
            if (existing != null) {
                Spacer(Modifier.height(4.dp))
                if (pending != null) {
                    QuietButton("Keep the current limit", {
                        Store.cancelPending(p)
                        onDismiss()
                    }, color = RColors.Accent)
                } else {
                    QuietButton("Remove limit (from tomorrow)", {
                        Store.removeLimit(p)
                        Buddy.notify(ctx, "removed the ${RegainApp.instance.catalog.label(p)} limit (from tomorrow).")
                        onDismiss()
                    }, color = RColors.Coral)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .pressable(enabled = enabled, pressedScale = 0.85f, onClick = onClick)
            .background(Color(0x26FFFFFF), CircleShape)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { RText(symbol, style = RTheme.type.title2, color = if (enabled) RColors.Label else RColors.Tertiary) }
}


@Composable
private fun GroupRow(g: Store.Group, used: Long, allowed: Long, onClick: () -> Unit) {
    val f = used.toFloat() / allowed.coerceAtLeast(1)
    val left = allowed - used
    val pending = Store.pendingGroups().containsKey(g.id)
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Up to three overlapping icons.
        Box(Modifier.width(34.dp).height(34.dp)) {
            g.pkgs.take(3).forEachIndexed { i, pkg ->
                AppIcon(pkg, 22.dp, Modifier.padding(start = (i * 6).dp, top = (i * 6).dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText(g.name, Modifier.weight(1f), maxLines = 1)
                RText(
                    if (left <= 0) "Done for today" else "${TimeUtil.formatDuration(left)} left",
                    style = RTheme.type.subhead,
                    color = if (left <= 0) RColors.Coral else RColors.Secondary,
                )
            }
            Spacer(Modifier.height(7.dp))
            ProgressBar(f, if (f >= 1f) RColors.Coral else if (f > 0.8f) RColors.Amber else RColors.Teal, height = 5.dp)
            Spacer(Modifier.height(5.dp))
            RText(
                "${g.pkgs.size} apps · ${TimeUtil.formatDuration(used)} of ${TimeUtil.formatDuration(Store.scaled(g.minutes) * TimeUtil.MINUTE)}" +
                    if (pending) " · changes tomorrow" else "",
                style = RTheme.type.footnote, color = RColors.Tertiary,
            )
        }
        Spacer(Modifier.width(8.dp))
        RIcon(RIcons.Chevron, tint = RColors.Tertiary, size = 18.dp)
    }
}

@Composable
private fun GroupEditorSheet(groupId: String?, apps: List<AppCatalog.AppInfo>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val existing = remember(groupId) { groupId?.let { Store.groups()[it] } }
    var name by remember(groupId) { mutableStateOf(existing?.name ?: "Social") }
    var minutes by remember(groupId) { mutableIntStateOf(existing?.minutes ?: 60) }
    var selected by remember(groupId) { mutableStateOf(existing?.pkgs ?: emptySet()) }
    var query by remember(groupId) { mutableStateOf("") }
    val canSave = selected.isNotEmpty() && name.isNotBlank()

    Sheet(groupId != null, onDismiss, fullHeight = true) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            QuietButton("Cancel", onDismiss, color = RColors.Accent)
            Spacer(Modifier.weight(1f))
            RText(if (existing == null) "New group" else "Edit group", style = RTheme.type.headline)
            Spacer(Modifier.weight(1f))
            QuietButton("Save", {
                if (canSave) {
                    val g = Store.Group(existing?.id ?: Store.newGroupId(), name.trim(), minutes, selected)
                    val now = Store.saveGroup(g)
                    if (!now) Buddy.notify(ctx, "loosened the “${g.name}” group limit (from tomorrow).")
                    Haptics.play(Haptics.Kind.Success)
                    GuardService.refresh()
                    onDismiss()
                }
            }, color = if (canSave) RColors.Accent else RColors.Tertiary)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            BasicTextField(
                value = name,
                onValueChange = { if (it.length <= 24) name = it },
                singleLine = true,
                textStyle = RTheme.type.title3.copy(color = RColors.Label),
                cursorBrush = SolidColor(RColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x3D767680), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("Shared daily limit", Modifier.weight(1f))
                StepButton("−", enabled = minutes > 5) { minutes = (minutes - 5).coerceAtLeast(5) }
                RText(
                    TimeUtil.formatDuration(minutes * TimeUtil.MINUTE),
                    style = RTheme.type.headline, align = TextAlign.Center, modifier = Modifier.width(84.dp),
                )
                StepButton("+", enabled = minutes < 600) { minutes = (minutes + 5).coerceAtMost(600) }
            }
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = RTheme.type.body.copy(color = RColors.Label),
                cursorBrush = SolidColor(RColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x3D767680), RoundedCornerShape(11.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) RText("Search apps", color = RColors.Tertiary)
                        inner()
                    }
                },
            )
            RText(
                "${selected.size} selected" + if (existing != null) " · loosening applies tomorrow" else "",
                style = RTheme.type.footnote, color = RColors.Secondary, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
        val filtered = remember(apps, query) {
            apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                .sortedByDescending { it.pkg in (existing?.pkgs ?: emptySet()) }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(filtered, key = { it.pkg }) { app ->
                val on = app.pkg in selected
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable(pressedScale = 0.985f) { selected = if (on) selected - app.pkg else selected + app.pkg }
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.pkg, 34.dp)
                        Spacer(Modifier.width(14.dp))
                        RText(app.label, Modifier.weight(1f), maxLines = 1)
                        Box(
                            Modifier
                                .width(24.dp).height(24.dp)
                                .background(if (on) RColors.Accent else Color(0x26FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) RIcon(RIcons.Check, size = 16.dp) }
                    }
                    Hairline(68.dp)
                }
            }
        }
        if (existing != null) {
            val pending = Store.pendingGroups().containsKey(existing.id)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (pending) {
                    QuietButton("Keep the current group", {
                        Store.cancelPendingGroup(existing.id)
                        onDismiss()
                    }, color = RColors.Accent)
                } else {
                    QuietButton("Delete group (from tomorrow)", {
                        Store.deleteGroup(existing.id)
                        Buddy.notify(ctx, "deleted the “${existing.name}” group limit (from tomorrow).")
                        onDismiss()
                    }, color = RColors.Coral)
                }
            }
        }
    }
}
