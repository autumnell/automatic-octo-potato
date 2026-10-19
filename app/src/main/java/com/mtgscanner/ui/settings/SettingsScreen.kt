package com.mtgscanner.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.ThemeMode
import com.mtgscanner.ui.theme.ThemeSettings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        SectionLabel("Theme")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Column(Modifier.padding(16.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
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

        SectionLabel("Preview")
        Preview()

        SectionLabel("About")
        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
            Column(Modifier.padding(16.dp)) {
                Text("MTG Scanner $version", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Card data, images and prices come from Scryfall. Magic: The Gathering is © Wizards of the Coast; this app is unofficial.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (picking) {
        ColorPickerDialog(
            initial = ThemeSettings.customAccent ?: ThemeSettings.accent,
            onDismiss = { picking = false },
            onPick = { ThemeSettings.updateAccent(it, custom = true); picking = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
private fun ModeCard(mode: ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg, icon) = when (mode) {
        ThemeMode.DARK -> Triple(Color(0xFF16171A), Color(0xFFEDEDEF), Icons.Filled.DarkMode)
        ThemeMode.LIGHT -> Triple(Color(0xFFF4F4F7), Color(0xFF17181C), Icons.Filled.LightMode)
        ThemeMode.SYSTEM -> Triple(Palette.SurfaceHigh, Palette.Text, Icons.Filled.PhoneAndroid)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Accent else Palette.Outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(bg)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) Palette.Accent else fg)
        Spacer(Modifier.height(8.dp))
        Text(mode.label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(if (selected) 3.dp else 0.dp, if (selected) Palette.Text else Color.Transparent, CircleShape)
                .padding(if (selected) 5.dp else 0.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = if (color.luminance() > 0.55f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
    }
}

private val rainbow = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

@Composable
private fun CustomSwatch(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Brush.sweepGradient(rainbow)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, "Pick a custom color", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Pick…", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
    }
}

/** A small mock-up so the chosen colors can be judged at a glance. */
@Composable
private fun Preview() {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(22.dp), color = Color.Transparent) {
        Column(Modifier.background(Palette.HeroGradient).padding(18.dp)) {
            Text("Collection value", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            Text("$1,234.56", style = MaterialTheme.typography.headlineMedium, color = Palette.Accent)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectPill("Selected", true, onClick = {})
                SelectPill("Not selected", false, onClick = {})
            }
            Spacer(Modifier.height(12.dp))
            GradientButton("Scan cards", onClick = {}, modifier = Modifier.fillMaxWidth())
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
