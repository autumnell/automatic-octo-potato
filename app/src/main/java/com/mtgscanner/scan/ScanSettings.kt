package com.mtgscanner.scan

import kotlinx.serialization.Serializable

/** How sure the scanner must be before it adds a card by itself. */
enum class AutoAddLevel(val label: String, val detail: String) {
    EXACT("Exact printing only", "Set code and number read, or artwork matched"),
    CONFIDENT("Confident name", "The name clearly matches a real card"),
    ANY("Any good match", "Fastest; check the list afterwards"),
}

/** What to do with a card the scanner isn't sure about. */
enum class UnsureAction(val label: String) { PAUSE("Pause and ask"), ASIDE("Set aside to check later"), SKIP("Skip it") }

/** Tones are android.media.ToneGenerator TONE_PROP_* values. */
enum class ScanSound(val label: String, val tone: Int) {
    BEEP("Beep", 24),
    CHIME("Chime", 25),
    CLICK("Click", 27),
    DOUBLE("Double beep", 28),
    NONE("None", -1),
}

/** Everything that controls scanning. Saved, and can be stored as named presets. */
@Serializable
data class ScanSettings(
    val autoAdd: Boolean = false,
    val level: AutoAddLevel = AutoAddLevel.CONFIDENT,
    val unsure: UnsureAction = UnsureAction.PAUSE,
    val foil: Boolean = false,
    /** Box to add to; null keeps whatever is picked in the header. Box.AUTO_SORT for auto-sort. */
    val boxId: Long? = null,
    val skipBasics: Boolean = false,
    val skipTokens: Boolean = false,
    /** Skip cards cheaper than this (USD). */
    val skipUnder: Double? = null,
    val sound: ScanSound = ScanSound.BEEP,
    val asideSound: ScanSound = ScanSound.CLICK,
    val vibrate: Boolean = true,
    val flash: Boolean = true,
) {
    /** Whether a match is sure enough to add without asking. */
    fun accepts(exact: Boolean, confident: Boolean): Boolean = when (level) {
        AutoAddLevel.EXACT -> exact
        AutoAddLevel.CONFIDENT -> confident || exact
        AutoAddLevel.ANY -> true
    }

    /** Why a card should be skipped, or null to keep it. */
    fun skipReason(typeLine: String, layout: String?, price: Double?, pricesShown: Boolean): String? = when {
        skipBasics && typeLine.startsWith("Basic") && typeLine.contains("Land") -> "basic land"
        skipTokens && (typeLine.contains("Token") || layout == "token" || layout == "double_faced_token" || layout == "emblem") -> "token"
        pricesShown && skipUnder != null && (price ?: 0.0) < skipUnder -> "under $" + "%.2f".format(java.util.Locale.US, skipUnder)
        else -> null
    }
}

@Serializable
data class ScanPreset(val name: String, val settings: ScanSettings)
