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
    val presets = listOf(
        AccentPreset("Ember", Color(0xFFF28C38)),
        AccentPreset("Flame", Color(0xFFE5533D)),
        AccentPreset("Crimson", Color(0xFFD7263D)),
        AccentPreset("Rose", Color(0xFFE8508A)),
        AccentPreset("Orchid", Color(0xFFC04BD8)),
        AccentPreset("Violet", Color(0xFF8B5CF6)),
        AccentPreset("Indigo", Color(0xFF5B6CF0)),
        AccentPreset("Island", Color(0xFF3B8EEA)),
        AccentPreset("Cyan", Color(0xFF22B8CF)),
        AccentPreset("Teal", Color(0xFF14B8A6)),
        AccentPreset("Forest", Color(0xFF22C55E)),
        AccentPreset("Lime", Color(0xFF9ACD32)),
        AccentPreset("Gold", Color(0xFFE6B422)),
    )

    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf(ThemeMode.DARK)
        private set
    /** Gold is the default accent. */
    val defaultAccent: Color = presets.first { it.name == "Gold" }.color

    var accent by mutableStateOf(defaultAccent)
        private set
    /** The last color made with the picker, kept so it stays one tap away. */
    var customAccent by mutableStateOf<Color?>(null)
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
        if (p.contains("custom")) customAccent = Color(p.getInt("custom", 0))
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

    fun updateAccent(c: Color, custom: Boolean = false) {
        // Picking a color by hand ends "accent from my collection".
        if (accentFromCollection) updateAccentFromCollection(false)
        accent = c
        prefs?.edit()?.putInt("accent", c.toArgb())?.apply()
        if (custom) {
            customAccent = c
            prefs?.edit()?.putInt("custom", c.toArgb())?.apply()
        }
    }
}
