package com.mtgscanner.scan

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/** The saved scan settings. */
object ScanPrefs {
    private var prefs: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(ScanSettings())
    val settings: StateFlow<ScanSettings> = _settings

    fun init(context: Context) {
        val p = context.getSharedPreferences("scan", Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString("current", null)
            ?.let { runCatching { json.decodeFromString(ScanSettings.serializer(), it) }.getOrNull() }
        _settings.value = saved ?: run {
            // Carry over the old "Sound on add" switch.
            val oldSound = context.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("scanSound", true)
            ScanSettings(sound = if (oldSound) ScanSound.BEEP else ScanSound.NONE)
        }
        // Scan presets are gone: drop what older versions saved.
        if (p.contains("presets") || p.contains("activePreset")) p.edit().remove("presets").remove("activePreset").apply()
    }

    fun update(change: (ScanSettings) -> ScanSettings) {
        val s = change(_settings.value)
        _settings.value = s
        prefs?.edit()?.putString("current", json.encodeToString(ScanSettings.serializer(), s))?.apply()
    }

    /** Remembers the box picked in the scan header. */
    fun setBox(id: Long) {
        val s = _settings.value.copy(boxId = id)
        _settings.value = s
        prefs?.edit()?.putString("current", json.encodeToString(ScanSettings.serializer(), s))?.apply()
    }
}
