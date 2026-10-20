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

    fun init(context: Context) {
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        prefs = p
        mode = runCatching { ThemeMode.valueOf(p.getString("mode", null) ?: "DARK") }.getOrDefault(ThemeMode.DARK)
        accent = Color(p.getInt("accent", defaultAccent.toArgb()))
        if (p.contains("custom")) customAccent = Color(p.getInt("custom", 0))
    }

    fun updateMode(m: ThemeMode) {
        mode = m
        prefs?.edit()?.putString("mode", m.name)?.apply()
    }

    fun updateAccent(c: Color, custom: Boolean = false) {
        accent = c
        prefs?.edit()?.putInt("accent", c.toArgb())?.apply()
        if (custom) {
            customAccent = c
            prefs?.edit()?.putInt("custom", c.toArgb())?.apply()
        }
    }
}
