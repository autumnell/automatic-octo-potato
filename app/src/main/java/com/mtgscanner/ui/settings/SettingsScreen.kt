package com.mtgscanner.ui.settings

import com.mtgscanner.ui.theme.Space
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.UnderlineTabs
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.ThemeMode
import com.mtgscanner.ui.theme.ThemeSettings
import com.mtgscanner.ui.theme.PriceStyle
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.components.PlainGroup

private val settingsTabs = listOf("Look", "Prices", "Data")

@Composable
fun SettingsScreen(onBack: () -> Unit, onScanLog: () -> Unit = {}, onTour: () -> Unit = {}) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val dataVm: SettingsViewModel = viewModel(factory = AppViewModels.Factory)
    val message by dataVm.message.collectAsStateWithLifecycle()
    // Collected here, not in the Data tab, so a backup that finishes after switching tabs still reports.
    LaunchedEffect(message) { message?.let { showMessage(it); dataVm.messageShown() } }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(top = Space.s1, end = Space.s4), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { searching = !searching; query = "" }) {
                    Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, if (searching) "Close search" else "Search settings")
                }
            }
            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Find a setting") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Palette.Muted) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s1).focusRequester(focus),
                )
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            }
            val hits = remember(query) { SettingsIndex.search(query) }
            if (searching && query.isNotBlank()) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                    if (hits.isEmpty()) {
                        com.mtgscanner.ui.components.EmptyState("No setting called \"${query.trim()}\"")
                    }
                    hits.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { tab = e.tab; searching = false; query = "" }.padding(horizontal = Space.s4, vertical = Space.s3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(e.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(e.where, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        }
                        HorizontalDivider(color = Palette.Rule, modifier = Modifier.padding(horizontal = Space.s4))
                    }
                }
            } else {
            UnderlineTabs(settingsTabs, tab, { tab = it }, Modifier.padding(top = Space.s1, bottom = Space.s2), horizontalPadding = 16.dp)
            // Each branch is its own call site, so each tab keeps its own scroll position.
            when (tab) {
                0 -> Page { LookTab() }
                1 -> Page {
                    SectionLabel("Prices")
                    PricesSection()
                }
                else -> Page { DataTab(dataVm, onScanLog, onTour, showMessage) }
            }
            }
        }
        com.mtgscanner.ui.components.SlimSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun ColumnScope.Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = Space.s6),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookTab() {
    SectionLabel("Theme")
    Row(Modifier.padding(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        ThemeMode.entries.forEach { m ->
            ModeCard(
                mode = m,
                selected = ThemeSettings.mode == m,
                modifier = Modifier.weight(1f),
                onClick = { ThemeSettings.updateMode(m) },
            )
        }
    }

    Spacer(Modifier.height(Space.s3))
    Card {
        ToggleRow("True black", "Black backgrounds in dark mode", ThemeSettings.trueBlack) { ThemeSettings.updateTrueBlack(it) }
    }

    SectionLabel("Accent color")
    val fromCollection = ThemeSettings.accentFromCollection
    PlainGroup(Modifier.padding(horizontal = Space.s1)) {
        Column {
            ToggleRow(
                "From my collection",
                if (fromCollection) "Now ${colorName(ThemeSettings.collectionColor)}" else "Your most-owned color",
                fromCollection,
            ) { ThemeSettings.updateAccentFromCollection(it) }
            HorizontalDivider(color = Palette.Rule)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
                modifier = Modifier.fillMaxWidth().padding(Space.s3),
            ) {
                // Tapping a swatch turns "From my collection" off.
                ThemeSettings.presets.forEach { p ->
                    Swatch(p.color, selected = !fromCollection && ThemeSettings.accent.toArgb() == p.color.toArgb(), label = p.name) {
                        ThemeSettings.updateAccent(p.color)
                    }
                }
            }
        }
    }

    SectionLabel("Text")
    Card {
        ToggleRow("Big text", "Larger names and prices", AppSettings.bigText) { AppSettings.updateBigText(it) }
        HorizontalDivider(color = Palette.Rule)
        ToggleRow("Vibrate on actions", "A light buzz on add, move, delete and select", AppSettings.haptics) { AppSettings.updateHaptics(it) }
    }

    SectionLabel("Preview")
    Preview()

}

@Composable
private fun DataTab(vm: SettingsViewModel, onScanLog: () -> Unit, onTour: () -> Unit, onMessage: (String) -> Unit) {
    val ctx = LocalContext.current
    var versionTaps by remember { mutableIntStateOf(0) }

    DataSection(vm)

    SectionLabel("Decks")
    Card {
        ToggleRow(
            "Skip cards only in decks", "From your cards leaves out cards whose every copy is already in a deck",
            AppSettings.upgradeSkipDeckOnly,
        ) { AppSettings.updateUpgradeSkipDeckOnly(it) }
    }

    if (AppSettings.scanLogUnlocked) {
        SectionLabel("Troubleshooting")
        PlainGroup(Modifier.padding(horizontal = Space.s1)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onScanLog).padding(start = Space.s4, end = Space.s2, top = Space.s2, bottom = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Scan log", style = MaterialTheme.typography.titleSmall)
                    Text("What the scanner read", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { AppSettings.unlockScanLog(false); versionTaps = 0 }) { Text("Hide") }
            }
        }
    }

    SectionLabel("About")
    PlainGroup(Modifier.padding(horizontal = Space.s1)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s2, top = Space.s1, bottom = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildLabel(com.mtgscanner.BuildConfig.BUILD_NUMBER, com.mtgscanner.BuildConfig.BUILD_DATE),
                    style = MaterialTheme.typography.titleSmall,
                    // Tapping the version seven times reveals the scan log.
                    modifier = Modifier.weight(1f).clickable {
                        versionTaps++
                        if (versionTaps >= 7 && !AppSettings.scanLogUnlocked) {
                            AppSettings.unlockScanLog(true)
                            onMessage("Scan log turned on")
                        }
                    },
                )
                TextButton(onClick = onTour) { Text("Show tour") }
            }
            CrashReportRow()
            HorizontalDivider(color = Palette.Rule)
            Text(
                "Data from Scryfall. Unofficial; Magic is © Wizards of the Coast.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
                modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s3),
            )
        }
    }
}

/** "White", "Blue"… for the collection-accent row; "Gold" when there's no colored card yet. */
private fun colorName(letter: Char?): String = when (letter) {
    'W' -> "White"; 'U' -> "Blue"; 'B' -> "Black"; 'R' -> "Red"; 'G' -> "Green"; else -> "Gold"
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(start = Space.s4, top = Space.s4, bottom = Space.s2))
}

@Composable
private fun ModeCard(mode: ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg, icon) = when (mode) {
        ThemeMode.DARK -> Triple(Color(0xFF161513), Color(0xFFECE8E1), Icons.Outlined.DarkMode)
        ThemeMode.LIGHT -> Triple(Color(0xFFF4F4F2), Color(0xFF1C1B19), Icons.Outlined.LightMode)
        ThemeMode.SYSTEM -> Triple(Palette.SurfaceHigh, Palette.Text, Icons.Outlined.PhoneAndroid)
    }
    Row(
        modifier
            .clip(RoundedCornerShape(Corners.Medium))
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Accent else Palette.Outline, RoundedCornerShape(Corners.Medium))
            .clickable(onClick = onClick)
            .background(bg)
            .padding(vertical = Space.s3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Palette.AccentText else fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s2))
        Text(mode.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    // The whole swatch and its label is the tap target (48dp wide and taller), read out by its label.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp).clip(RoundedCornerShape(Corners.Small)).selectable(selected = selected, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(if (selected) 3.dp else 0.dp, if (selected) Palette.Text else Color.Transparent, CircleShape)
                .padding(if (selected) 5.dp else 0.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Outlined.Check, null, tint = if (color.luminance() > 0.55f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(Space.s1))
        // Shrinks with large text instead of cutting the name off.
        com.mtgscanner.ui.components.AutoSizeText(label, MaterialTheme.typography.labelSmall, color = Palette.Muted, minScale = 0.6f)
    }
}

/** A one-line mock-up so the chosen colors can be judged at a glance. */
@Composable
private fun Preview() {
    Surface(Modifier.fillMaxWidth().padding(horizontal = Space.s4), shape = RoundedCornerShape(Corners.Medium), color = Palette.BoxCard) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s3), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Grimoire", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                Text("Collection value", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                Text("$1,234.56", style = PriceStyle.copy(fontSize = PriceStyle.fontSize * 1.3f), color = Palette.Text)
            }
            SelectPill("Selected", true, onClick = {})
        }
    }
}
