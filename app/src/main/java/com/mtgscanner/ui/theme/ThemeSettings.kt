package com.mtgscanner.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("System") }

data class AccentPreset(val name: String, val color: Color)

/** Appearance choices from the Settings page, saved on the phone and readable from any composable. */
object ThemeSettings {
    /** A few muted accents that sit with the warm ink look; Oxblood is the brand and the default. */
    val presets = listOf(
        AccentPreset("Oxblood", Color(0xFF9E3A33)),
        AccentPreset("Ink blue", Color(0xFF3E5C7A)),
        AccentPreset("Moss", Color(0xFF5E6B3A)),
        AccentPreset("Ochre", Color(0xFFA87A2A)),
        AccentPreset("Plum", Color(0xFF6E4560)),
    )

    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf(ThemeMode.DARK)
        private set
    /** Oxblood red is the default accent. */
    val defaultAccent: Color = presets.first { it.name == "Oxblood" }.color
    /** [defaultAccent] as shown in light mode: a little darker. */
    val defaultAccentLight: Color = Color(0xFF8A2F29)
    /** The default before the "Small step" look; a saved accent still on it moves to the new default once. */
    private val oldDefaultAccent: Color = Color(0xFFE6B422)

    var accent by mutableStateOf(defaultAccent)
        private set

    /** Dark mode on pure black (OLED): backgrounds black, panels near-black. */
    var trueBlack by mutableStateOf(false)
        private set

    /** Accent follows the color the collection has most of, instead of [accent]. */
    var accentFromCollection by mutableStateOf(false)
        private set

    /** The collection's most-owned color (W, U, B, R or G), saved so the accent is right at start-up. */
    var collectionColor by mutableStateOf<Char?>(null)
        private set

    /** The accent in use: the collection's color when that option is on, else the chosen [accent]. */
    val activeAccent: Color
        get() = if (accentFromCollection) AccentMath.accentFor(collectionColor) else accent

    fun init(context: Context) {
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        prefs = p
        mode = runCatching { ThemeMode.valueOf(p.getString("mode", null) ?: "DARK") }.getOrDefault(ThemeMode.DARK)
        accent = Color(p.getInt("accent", defaultAccent.toArgb()))
        if (!p.getBoolean("smallStepLook", false)) {
            // One time: the old default gold becomes the new default red.
            if (accent.toArgb() == oldDefaultAccent.toArgb()) {
                accent = defaultAccent
                p.edit().putInt("accent", defaultAccent.toArgb()).apply()
            }
            p.edit().putBoolean("smallStepLook", true).apply()
        }
        if (!p.getBoolean("mutedAccents", false)) {
            // One time: the bright swatches and the color wheel are gone, so an accent not in the muted set
            // goes back to Oxblood.
            if (presets.none { it.color.toArgb() == accent.toArgb() }) {
                accent = defaultAccent
                p.edit().putInt("accent", defaultAccent.toArgb()).apply()
            }
            p.edit().putBoolean("mutedAccents", true).remove("custom").apply()
        }
        trueBlack = p.getBoolean("trueBlack", false)
        accentFromCollection = p.getBoolean("accentFromCollection", false)
        collectionColor = p.getString("collectionColor", null)?.firstOrNull()
    }

    fun updateTrueBlack(v: Boolean) {
        trueBlack = v
        prefs?.edit()?.putBoolean("trueBlack", v)?.apply()
    }

    fun updateAccentFromCollection(v: Boolean) {
        accentFromCollection = v
        prefs?.edit()?.putBoolean("accentFromCollection", v)?.apply()
    }

    /** Called when the collection changes; [letter] is null for no colored cards. */
    fun noteCollectionColor(letter: Char?) {
        if (letter == collectionColor) return
        collectionColor = letter
        prefs?.edit()?.putString("collectionColor", letter?.toString())?.apply()
    }

    fun updateMode(m: ThemeMode) {
        mode = m
        prefs?.edit()?.putString("mode", m.name)?.apply()
    }

    fun updateAccent(c: Color) {
        // Picking a color by hand ends "accent from my collection".
        if (accentFromCollection) updateAccentFromCollection(false)
        accent = c
        prefs?.edit()?.putInt("accent", c.toArgb())?.apply()
    }
}
