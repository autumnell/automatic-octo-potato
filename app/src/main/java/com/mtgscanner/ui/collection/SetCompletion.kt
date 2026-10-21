package com.mtgscanner.ui.collection

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which printings count toward finishing a set. The main set is the numbered cards up to the set's printed
 * size (the "/281" on the card); showcase, borderless, extended art, promos and other extras are variants.
 */
object MainSet {
    /** Treatments that make a printing a variant when the set's printed size isn't known. */
    private val VARIANTS = setOf("borderless", "showcase", "extendedart", "promo")

    /**
     * Whether a printing is part of the main set. [printedSize] is the set's printed size when known; then any
     * plain number up to it counts. Without it, numbered cards without a variant treatment count.
     * Numbers with letters or symbols ("123a", "45★", "12p") are always variants.
     */
    fun isMain(collectorNumber: String, treatments: Collection<String>, printedSize: Int?): Boolean {
        val n = collectorNumber.trim().toIntOrNull() ?: return false
        if (n <= 0) return false
        if (printedSize != null && printedSize > 0) return n <= printedSize
        return treatments.none { it.trim().lowercase() in VARIANTS }
    }

    /** Treatments as stored on a collection row: comma separated. */
    fun treatmentsOf(stored: String): List<String> = stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Cards needed to finish a set: its printed size for the main set, else every card in it. */
    fun total(cardCount: Int, printedSize: Int?, includeVariants: Boolean): Int =
        if (includeVariants || printedSize == null || printedSize <= 0) cardCount else printedSize
}

/** The "Include variants" choice for the set tracker, saved on the phone and shared by its screens. Off by default. */
object SetPrefs {
    private var prefs: SharedPreferences? = null
    private val _includeVariants = MutableStateFlow(false)

    fun includeVariants(context: Context): StateFlow<Boolean> {
        if (prefs == null) {
            val p = context.applicationContext.getSharedPreferences("sets", Context.MODE_PRIVATE)
            prefs = p
            _includeVariants.value = p.getBoolean("includeVariants", false)
        }
        return _includeVariants
    }

    fun setIncludeVariants(on: Boolean) {
        _includeVariants.value = on
        prefs?.edit()?.putBoolean("includeVariants", on)?.apply()
    }
}
