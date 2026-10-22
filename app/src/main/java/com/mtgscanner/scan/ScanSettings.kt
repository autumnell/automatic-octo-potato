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

/** Everything that controls scanning. Saved. */
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
    /** Turn the flashlight on by itself when the picture stays too dark, and off again when it's bright. */
    val autoTorch: Boolean = true,
    /** Stack mode: say the box name out loud for each card. */
    val speakBox: Boolean = false,
) {
    /** Whether a match is sure enough to add without asking. */
    fun accepts(exact: Boolean, confident: Boolean): Boolean = when (level) {
        AutoAddLevel.EXACT -> exact
        AutoAddLevel.CONFIDENT -> confident || exact
        AutoAddLevel.ANY -> true
    }

    /** Why a card should be skipped, or null to keep it. */
    fun skipReason(typeLine: String, layout: String?, price: Double?, pricesShown: Boolean): String? =
        skipRules(pricesShown)?.reason(typeLine, layout, price)

    /** The skip rules in force, or null when none are on. The price floor only counts while prices are shown. */
    fun skipRules(pricesShown: Boolean): SkipRules? =
        SkipRules(skipBasics, skipTokens, skipUnder.takeIf { pricesShown }).takeIf { it.any }
}

/** Which cards to skip. Saved with offline scans so they're skipped the same way when looked up later. */
@Serializable
data class SkipRules(val basics: Boolean = false, val tokens: Boolean = false, val under: Double? = null) {
    val any: Boolean get() = basics || tokens || under != null

    /** Why a card should be skipped, or null to keep it. */
    fun reason(typeLine: String, layout: String?, price: Double?): String? = when {
        basics && typeLine.startsWith("Basic") && typeLine.contains("Land") -> "basic land"
        tokens && (typeLine.contains("Token") || layout == "token" || layout == "double_faced_token" || layout == "emblem") -> "token"
        // A card with no price isn't known to be cheap, so it's kept.
        under != null && price != null && price < under -> "under $" + "%.2f".format(java.util.Locale.US, under)
        else -> null
    }

    fun encode(): String = skipJson.encodeToString(serializer(), this)

    companion object {
        /** The rules saved by [encode]; null for an empty or unreadable string, or when none are on. */
        fun decode(s: String): SkipRules? =
            if (s.isBlank()) null else runCatching { skipJson.decodeFromString(serializer(), s) }.getOrNull()?.takeIf { it.any }
    }
}

private val skipJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
