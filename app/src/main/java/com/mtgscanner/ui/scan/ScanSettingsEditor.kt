package com.mtgscanner.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.scan.AutoAddLevel
import com.mtgscanner.scan.ScanPrefs
import com.mtgscanner.scan.ScanSound
import com.mtgscanner.scan.UnsureAction
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette

/** Presets, auto-add, skip rules and feedback. Used in the scanner's settings sheet and on the Settings page. */
@Composable
fun ScanSettingsEditor(modifier: Modifier = Modifier, showFoil: Boolean = true) {
    val s by ScanPrefs.settings.collectAsStateWithLifecycle()
    val presets by ScanPrefs.presets.collectAsStateWithLifecycle()
    val active by ScanPrefs.activePreset.collectAsStateWithLifecycle()
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val update = ScanPrefs::update

    Column(modifier) {
        Heading("Presets")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { p -> SelectPill(p.name, p.name == active, { ScanPrefs.apply(p) }) }
            SelectPill("Save current", false, { saving = true }, leading = {
                Icon(Icons.Filled.Add, null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
            })
        }
        active?.let { name ->
            TextButton(onClick = { deleting = name }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Delete “$name”", color = Palette.Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        HorizontalDivider(color = Palette.Outline)

        Toggle("Auto-add", "Add each card as it's read.", s.autoAdd) { on ->
            update { it.copy(autoAdd = on) }
        }
        if (s.autoAdd) {
            Heading("Add by itself when it's")
            AutoAddLevel.entries.forEach { level ->
                Choice(level.label, level.detail, s.level == level) { update { it.copy(level = level) } }
            }
            Heading("When it's not sure")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnsureAction.entries.forEach { a -> SelectPill(a.label, s.unsure == a, { update { it.copy(unsure = a) } }) }
            }
            Text(
                when (s.unsure) {
                    UnsureAction.PAUSE -> "Stop so you can check it."
                    UnsureAction.ASIDE -> "Scanning keeps going. Check the set-aside cards at the end."
                    UnsureAction.SKIP -> "The card isn't added. Scan it again or add it by hand."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )

            Heading("Skip")
            Text("Skipped cards are counted but not added.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Toggle("Basic lands", null, s.skipBasics) { on -> update { it.copy(skipBasics = on) } }
            Toggle("Tokens and emblems", null, s.skipTokens) { on -> update { it.copy(skipTokens = on) } }
            if (AppSettings.showPrices) {
                Text("Cards worth less than", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Double?>(null, 0.10, 0.25, 0.50, 1.0).forEach { v ->
                        val label = if (v == null) "Off" else if (v < 1) "${(v * 100).toInt()}¢" else "$${v.toInt()}"
                        SelectPill(label, s.skipUnder == v, { update { it.copy(skipUnder = v) } })
                    }
                }
            }
            HorizontalDivider(color = Palette.Outline)
        }

        if (showFoil) {
            Toggle("Foil", "Mark the next cards as foil.", s.foil) { on -> update { it.copy(foil = on) } }
            HorizontalDivider(color = Palette.Outline)
        }

        Heading("When a card is added")
        SoundPills(s.sound) { v -> update { it.copy(sound = v) } }
        if (s.autoAdd && s.unsure == UnsureAction.ASIDE) {
            Heading("When a card is set aside")
            SoundPills(s.asideSound) { v -> update { it.copy(asideSound = v) } }
        }
        Toggle("Vibrate", null, s.vibrate) { on -> update { it.copy(vibrate = on) } }
        Toggle("Flash the frame", null, s.flash) { on -> update { it.copy(flash = on) } }
    }

    if (saving) SavePresetDialog(onSave = { ScanPrefs.saveAs(it); saving = false }, onDismiss = { saving = false })
    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Palette.Surface,
            title = { Text("Delete “$name”?") },
            confirmButton = { TextButton(onClick = { ScanPrefs.delete(name); deleting = null }) { Text("Delete", color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SoundPills(selected: ScanSound, onPick: (ScanSound) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScanSound.entries.forEach { sound ->
            SelectPill(sound.label, selected == sound, {
                onPick(sound)
                ScanFeedback.play(ctx, sound)
            })
        }
    }
}

@Composable
private fun SavePresetDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Save as preset") },
        text = {
            Column {
                Text("Saves these settings and the box.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("e.g. Sorting bulk") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Accent, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun Toggle(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent))
    }
}

@Composable
private fun Choice(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Palette.Accent))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}

/** Plays the scan sounds. One shared tone generator so sounds don't stack up. */
object ScanFeedback {
    private var tones: android.media.ToneGenerator? = null

    fun play(context: android.content.Context, sound: ScanSound) {
        if (sound.tone < 0) return
        runCatching {
            val t = tones ?: android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 70).also { tones = it }
            t.startTone(sound.tone, if (sound == ScanSound.DOUBLE) 220 else 110)
        }
    }

    fun release() {
        runCatching { tones?.release() }
        tones = null
    }
}
