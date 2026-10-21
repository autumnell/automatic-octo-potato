package com.mtgscanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The scan bar's choices for the next cards scanned: copies per card and language.
 * Foil lives in ScanSettings and the set in [SortPrefs.setLock]. Also remembers the scan hints.
 */
object ScanBarPrefs {
    private var prefs: SharedPreferences? = null

    /** Copies added per scan. */
    var quantity by mutableStateOf(1)
        private set
    /** Language recorded for scanned cards, when the card itself doesn't say (lower-case Scryfall code). */
    var lang by mutableStateOf("en")
        private set
    /** Condition recorded for scanned cards ("NM", "LP", "MP", "HP", "DMG"). */
    var condition by mutableStateOf("NM")
        private set
    /** The first-run scan hints have been shown. */
    var hintsSeen by mutableStateOf(true)
        private set

    /** The language to record, or null for English. */
    val langOrNull: String? get() = lang.takeIf { it != "en" }

    /** Safe to call more than once. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("scanBar", Context.MODE_PRIVATE)
        prefs = p
        quantity = p.getInt("qty", 1).coerceIn(1, 99)
        lang = p.getString("lang", "en")?.takeIf { it.isNotBlank() } ?: "en"
        hintsSeen = p.getBoolean("hintsSeen", false)
        condition = p.getString("condition", "NM")?.takeIf { it in CONDITIONS } ?: "NM"
    }

    fun updateCondition(code: String) {
        condition = code.takeIf { it in CONDITIONS } ?: "NM"
        prefs?.edit()?.putString("condition", condition)?.apply()
    }

    /** Conditions offered in the bar, best first. */
    val CONDITIONS = listOf("NM", "LP", "MP", "HP", "DMG")

    fun updateQuantity(n: Int) {
        quantity = n.coerceIn(1, 99)
        prefs?.edit()?.putInt("qty", quantity)?.apply()
    }

    fun updateLang(code: String) {
        lang = code.lowercase().ifBlank { "en" }
        prefs?.edit()?.putString("lang", lang)?.apply()
    }

    fun updateHintsSeen(seen: Boolean) {
        hintsSeen = seen
        prefs?.edit()?.putBoolean("hintsSeen", seen)?.apply()
    }

    /** Quantities offered in the bar. */
    val QUANTITIES = listOf(1, 2, 3, 4, 8)

    /** Languages offered in the bar, as Scryfall codes and short labels. */
    val LANGS = listOf(
        "en" to "EN", "ja" to "JA", "de" to "DE", "fr" to "FR", "it" to "IT", "es" to "ES", "pt" to "PT",
        "ko" to "KO", "ru" to "RU", "zhs" to "ZHS", "zht" to "ZHT",
    )

    fun langLabel(code: String): String = LANGS.firstOrNull { it.first == code }?.second ?: code.uppercase()
}
