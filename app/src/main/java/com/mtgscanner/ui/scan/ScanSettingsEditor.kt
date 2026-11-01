package com.mtgscanner.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.mtgscanner.data.label
import kotlinx.coroutines.launch
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

/** Auto-add, skip rules and feedback: the scanner's settings sheet. Foil is on the scan bar. */
@Composable
fun ScanSettingsEditor(modifier: Modifier = Modifier) {
    val s by ScanPrefs.settings.collectAsStateWithLifecycle()
    val update = ScanPrefs::update

    Column(modifier) {
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
            HorizontalDivider(color = Palette.Rule)
        }

        Heading("When a card is added")
        SoundPills(s.sound) { v -> update { it.copy(sound = v) } }
        if (s.autoAdd && s.unsure == UnsureAction.ASIDE) {
            Heading("When a card is set aside")
            SoundPills(s.asideSound) { v -> update { it.copy(asideSound = v) } }
        }
        Toggle("Vibrate", null, s.vibrate) { on -> update { it.copy(vibrate = on) } }
        Toggle("Flash the frame", "In the box's color.", s.flash) { on -> update { it.copy(flash = on) } }
        Toggle("Say the box", "Stack mode reads out where each card goes.", s.speakBox) { on -> update { it.copy(speakBox = on) } }
        Toggle("Auto flashlight", "On when it's too dark.", s.autoTorch) { on -> update { it.copy(autoTorch = on) } }

        Heading("Camera")
        Toggle("Sharper frames", "Reads small print better. Uses a bit more battery.", s.sharperFrames) { on -> update { it.copy(sharperFrames = on) } }
        Toggle("Centering check", "Shows how evenly each card's border is printed, like 55/45.", s.centering) { on -> update { it.copy(centering = on) } }
        Toggle("Spot proxies", "Says when a card doesn't look like its real printing.", s.proxyCheck) { on -> update { it.copy(proxyCheck = on) } }
        Toggle("Voice commands", "Say foil, not foil, undo, set aside, skip, or a number of copies.", s.voice) { on -> update { it.copy(voice = on) } }
        Text("Zoom", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        Text("Hold the phone farther back for less blur and glare.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Row(Modifier.padding(top = 6.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1f, 1.5f, 2f).forEach { z ->
                val label = if (z == 1f) "1×" else if (z == 2f) "2×" else "1.5×"
                SelectPill(label, s.zoom == z, { update { it.copy(zoom = z) } })
            }
        }
    }
}

@Composable
private fun SoundPills(selected: ScanSound, onPick: (ScanSound) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Previews made a tone generator; free it when the settings close (the scanner makes its own again).
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ScanFeedback.release() } }
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
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Text, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
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

    /** A short soft tick, for a card skipped in stack mode. */
    fun tick(context: android.content.Context) {
        runCatching {
            val t = tones ?: android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 70).also { tones = it }
            t.startTone(ScanSound.CLICK.tone, 40)
        }
    }

    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false

    /** Says [text] out loud, cutting off whatever was being said. The voice is made on first use. */
    fun speak(context: android.content.Context, text: String) {
        if (text.isBlank()) return
        runCatching {
            val t = tts
            if (t == null) {
                var made: android.speech.tts.TextToSpeech? = null
                made = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                    ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS
                    if (ttsReady) runCatching { made?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "box") }
                }
                tts = made
            } else if (ttsReady) {
                t.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "box")
            }
        }
    }

    fun release() {
        runCatching { tones?.release() }
        tones = null
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }
}
