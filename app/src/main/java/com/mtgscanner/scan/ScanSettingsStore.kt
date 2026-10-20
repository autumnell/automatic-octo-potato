package com.mtgscanner.scan

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** The saved scan settings and presets, shared by the scanner and the Settings page. */
object ScanPrefs {
    private var prefs: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(ScanSettings())
    val settings: StateFlow<ScanSettings> = _settings

    private val _presets = MutableStateFlow(DEFAULT_PRESETS)
    val presets: StateFlow<List<ScanPreset>> = _presets

    /** The preset last applied, cleared as soon as a setting is changed by hand. */
    private val _activePreset = MutableStateFlow<String?>(null)
    val activePreset: StateFlow<String?> = _activePreset

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
        _presets.value = p.getString("presets", null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(ScanPreset.serializer()), it) }.getOrNull() }
            ?: DEFAULT_PRESETS
        _activePreset.value = p.getString("activePreset", null)
    }

    fun update(change: (ScanSettings) -> ScanSettings) {
        set(change(_settings.value), preset = null)
    }

    private fun set(s: ScanSettings, preset: String?) {
        _settings.value = s
        _activePreset.value = preset
        prefs?.edit()
            ?.putString("current", json.encodeToString(ScanSettings.serializer(), s))
            ?.putString("activePreset", preset)
            ?.apply()
    }

    /** Remembers the box picked in the scan header, without counting as a change to the preset. */
    fun setBox(id: Long) {
        val s = _settings.value.copy(boxId = id)
        _settings.value = s
        prefs?.edit()?.putString("current", json.encodeToString(ScanSettings.serializer(), s))?.apply()
    }

    /** Applies a preset. A preset without a box keeps the box picked now. */
    fun apply(preset: ScanPreset) {
        val keepBox = preset.settings.boxId ?: _settings.value.boxId
        set(preset.settings.copy(boxId = keepBox), preset.name)
    }

    /** Saves the current settings under [name], replacing a preset with the same name. */
    fun saveAs(name: String) {
        val clean = name.trim().ifEmpty { return }
        val list = _presets.value.filterNot { it.name.equals(clean, ignoreCase = true) } + ScanPreset(clean, _settings.value)
        savePresets(list)
        _activePreset.value = clean
        prefs?.edit()?.putString("activePreset", clean)?.apply()
    }

    fun delete(name: String) {
        savePresets(_presets.value.filterNot { it.name == name })
        if (_activePreset.value == name) _activePreset.value = null
    }

    private fun savePresets(list: List<ScanPreset>) {
        _presets.value = list
        prefs?.edit()?.putString("presets", json.encodeToString(ListSerializer(ScanPreset.serializer()), list))?.apply()
    }

    val DEFAULT_PRESETS: List<ScanPreset>
        get() = listOf(
            ScanPreset(
                "Sorting bulk",
                ScanSettings(
                    autoAdd = true, level = AutoAddLevel.CONFIDENT, unsure = UnsureAction.ASIDE, boxId = -2L,
                    skipTokens = true, sound = ScanSound.BEEP,
                ),
            ),
            ScanPreset("Valuing a binder", ScanSettings(autoAdd = false, unsure = UnsureAction.PAUSE, sound = ScanSound.NONE, flash = false)),
            ScanPreset(
                "Fast and careful",
                ScanSettings(autoAdd = true, level = AutoAddLevel.EXACT, unsure = UnsureAction.PAUSE, sound = ScanSound.CHIME),
            ),
        )
}
