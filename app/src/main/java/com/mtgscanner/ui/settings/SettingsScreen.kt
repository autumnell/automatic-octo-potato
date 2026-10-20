package com.mtgscanner.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.ThemeMode
import com.mtgscanner.ui.theme.ThemeSettings

private val settingsTabs = listOf("Look", "Prices", "Scanning", "Sorting", "Data")

@Composable
fun SettingsScreen(onBack: () -> Unit, onScanLog: () -> Unit = {}, onTour: () -> Unit = {}) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val dataVm: SettingsViewModel = viewModel(factory = AppViewModels.Factory)
    val message by dataVm.message.collectAsStateWithLifecycle()
    // Collected here, not in the Data tab, so a backup that finishes after switching tabs still reports.
    LaunchedEffect(message) { message?.let { showMessage(it); dataVm.messageShown() } }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                settingsTabs.forEachIndexed { i, t -> SelectPill(t, tab == i, { tab = i }) }
            }
            // Each branch is its own call site, so each tab keeps its own scroll position.
            when (tab) {
                0 -> Page { LookTab() }
                1 -> Page {
                    SectionLabel("Prices")
                    PricesSection()
                    if (AppSettings.showPrices) {
                        Spacer(Modifier.height(10.dp))
                        ConditionPricesSection()
                    }
                }
                2 -> Page {
                    SectionLabel("Scanning")
                    ScanningSection()
                }
                3 -> Page {
                    SectionLabel("Sorting")
                    SortingSection()
                }
                else -> Page { DataTab(dataVm, onScanLog, onTour, showMessage) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
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
            .padding(bottom = 24.dp),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookTab() {
    var picking by remember { mutableStateOf(false) }

    SectionLabel("Theme")
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { m ->
            ModeCard(
                mode = m,
                selected = ThemeSettings.mode == m,
                modifier = Modifier.weight(1f),
                onClick = { ThemeSettings.updateMode(m) },
            )
        }
    }

    SectionLabel("Accent color")
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column(Modifier.padding(12.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ThemeSettings.presets.forEach { p ->
                    Swatch(p.color, selected = ThemeSettings.accent.toArgb() == p.color.toArgb(), label = p.name) {
                        ThemeSettings.updateAccent(p.color)
                    }
                }
                ThemeSettings.customAccent?.let { c ->
                    Swatch(c, selected = ThemeSettings.accent.toArgb() == c.toArgb(), label = "Custom") {
                        ThemeSettings.updateAccent(c)
                    }
                }
                CustomSwatch(onClick = { picking = true })
            }
        }
    }

    SectionLabel("Text")
    Card { ToggleRow("Big text", "Larger names and prices", AppSettings.bigText) { AppSettings.updateBigText(it) } }

    SectionLabel("Preview")
    Preview()

    if (picking) {
        ColorPickerDialog(
            initial = ThemeSettings.customAccent ?: ThemeSettings.accent,
            onDismiss = { picking = false },
            onPick = { ThemeSettings.updateAccent(it, custom = true); picking = false },
        )
    }
}

@Composable
private fun DataTab(vm: SettingsViewModel, onScanLog: () -> Unit, onTour: () -> Unit, onMessage: (String) -> Unit) {
    val ctx = LocalContext.current
    var versionTaps by remember { mutableIntStateOf(0) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }

    SectionLabel("Your data")
    DataSection(vm)

    if (AppSettings.scanLogUnlocked) {
        SectionLabel("Troubleshooting")
        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onScanLog).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "MTG Scanner $version",
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
            HorizontalDivider(color = Palette.Outline)
            Text(
                "Data from Scryfall. Unofficial; Magic is © Wizards of the Coast.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
}

@Composable
private fun ModeCard(mode: ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg, icon) = when (mode) {
        ThemeMode.DARK -> Triple(Color(0xFF16171A), Color(0xFFEDEDEF), Icons.Filled.DarkMode)
        ThemeMode.LIGHT -> Triple(Color(0xFFF4F4F7), Color(0xFF17181C), Icons.Filled.LightMode)
        ThemeMode.SYSTEM -> Triple(Palette.SurfaceHigh, Palette.Text, Icons.Filled.PhoneAndroid)
    }
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Accent else Palette.Outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(bg)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Palette.Accent else fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(mode.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(if (selected) 3.dp else 0.dp, if (selected) Palette.Text else Color.Transparent, CircleShape)
                .padding(if (selected) 5.dp else 0.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = if (color.luminance() > 0.55f) Color.Black else Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val rainbow = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

@Composable
private fun CustomSwatch(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Brush.sweepGradient(rainbow)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, "Pick a custom color", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("Pick…", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
    }
}

/** A one-line mock-up so the chosen colors can be judged at a glance. */
@Composable
private fun Preview() {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
        Row(Modifier.background(Palette.HeroGradient).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Collection value", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                Text("$1,234.56", style = MaterialTheme.typography.titleLarge, color = Palette.Accent)
            }
            SelectPill("Selected", true, onClick = {})
        }
    }
}

/** Hue bar plus a saturation/brightness square, with a hex box for exact colors. */
@Composable
private fun ColorPickerDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, sat, value)
    var hexText by remember { mutableStateOf(toHex(color)) }
    var editingHex by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        icon = { Icon(Icons.Filled.Colorize, null, tint = color) },
        title = { Text("Custom accent") },
        text = {
            Column {
                SatValSquare(hue, sat, value) { s, v -> sat = s; value = v; editingHex = false }
                Spacer(Modifier.height(14.dp))
                HueBar(hue) { hue = it; editingHex = false }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color))
                    Spacer(Modifier.width(12.dp))
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("#", color = Palette.Muted, style = MaterialTheme.typography.bodyLarge)
                        BasicTextField(
                            value = if (editingHex) hexText else toHex(color),
                            onValueChange = { t ->
                                val clean = t.filter { it.isLetterOrDigit() }.take(6).uppercase()
                                hexText = clean
                                editingHex = true
                                parseHex(clean)?.let { c ->
                                    FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }.let { a ->
                                        hue = a[0]; sat = a[1]; value = a[2]
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                            cursorBrush = SolidColor(Palette.Accent),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Use color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SatValSquare(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(14.dp))) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        fun update(o: Offset) = onChange((o.x / w).coerceIn(0f, 1f), 1f - (o.y / h).coerceIn(0f, 1f))
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(w, h) { detectTapGestures { update(it) } }
                .pointerInput(w, h) { detectDragGestures { change, _ -> update(change.position) } },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val c = Offset(sat * size.width, (1f - value) * size.height)
            drawCircle(Color.White, radius = 11.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.4f), radius = 13.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))) {
        val w = constraints.maxWidth.toFloat()
        fun update(x: Float) = onChange((x / w).coerceIn(0f, 1f) * 359.9f)
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(w) { detectTapGestures { update(it.x) } }
                .pointerInput(w) { detectDragGestures { change, _ -> update(change.position.x) } },
        ) {
            drawRect(Brush.horizontalGradient(rainbow))
            val x = hue / 360f * size.width
            drawCircle(Color.White, radius = size.height / 2 - 2.dp.toPx(), center = Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
        }
    }
}

private fun toHex(c: Color): String = String.format("%06X", c.toArgb() and 0xFFFFFF)

private fun parseHex(s: String): Color? =
    if (s.length == 6) s.toLongOrNull(16)?.let { Color(0xFF000000 or it) } else null
