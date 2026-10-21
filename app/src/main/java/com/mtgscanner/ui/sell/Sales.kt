package com.mtgscanner.ui.sell

import java.util.Locale

/**
 * Sales are kept in each card's history as "sold" events, with a detail like "×2 · $3.50 each".
 * The running total earned is read back from those details.
 */
object Sales {
    const val KIND = "sold"
    private val DETAIL = Regex("""×(\d+) · \$(\d+(?:\.\d+)?) each""")

    fun detail(qty: Int, each: Double): String = "×$qty · \$${String.format(Locale.US, "%.2f", each)} each"

    /** What one sale brought in, or null when the detail isn't a sale's. */
    fun amount(detail: String): Double? {
        val m = DETAIL.find(detail) ?: return null
        val qty = m.groupValues[1].toIntOrNull() ?: return null
        val each = m.groupValues[2].toDoubleOrNull() ?: return null
        return qty * each
    }

    /** Total earned from events given as (kind, detail). */
    fun earned(events: List<Pair<String, String>>): Double =
        events.filter { it.first == KIND }.sumOf { amount(it.second) ?: 0.0 }

    /** Parses a typed price like "3.5", "$3,50"; null when it isn't a price. */
    fun parsePrice(text: String): Double? =
        text.replace(',', '.').trim().removePrefix("$").trim().toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }
}
