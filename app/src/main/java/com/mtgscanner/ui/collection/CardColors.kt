package com.mtgscanner.ui.collection

import com.mtgscanner.ui.home.ColorPairs
import kotlinx.serialization.Serializable

/** How picked colors match a card, as on Scryfall: c>=, c= and c<=. */
@Serializable
enum class ColorMatch(val label: String, val op: String) {
    /** The card has every picked color, maybe more (Scryfall's plain "c:"). */
    INCLUDING("Including", ">="),
    /** The card has exactly the picked colors. */
    EXACTLY("Exactly", "="),
    /** The card has no colors outside the picks; colorless cards count. */
    AT_MOST("At most", "<="),
}

/**
 * Color filtering, pure for tests. A selection holds pips (W U B R G), "C" for colorless, and keys from
 * older filters and Home links: pair keys ("WU", "3+") and "M" (multicolor, no longer offered).
 */
object CardColors {
    /** The pips the filter sheet offers. */
    val PIPS = listOf("W", "U", "B", "R", "G", "C")
    /** Multicolor, from filters saved before it was removed: two or more colors. */
    const val LEGACY_MULTI = "M"

    private fun letters(s: String): Set<Char> = s.uppercase().filter { it in "WUBRG" }.toSet()

    /**
     * Whether a card with [cardColors] ("WU", "" for colorless) matches [selected] under [mode].
     * Pips follow the mode; "C" also lets colorless cards in. Pair keys and "M" match on their own terms,
     * and must match too when pips are picked as well.
     */
    fun matches(selected: Set<String>, mode: ColorMatch, cardColors: String): Boolean {
        if (selected.isEmpty()) return true
        val card = letters(cardColors)
        val pips = selected.filter { it.length == 1 && it[0] in "WUBRG" }.map { it[0] }.toSet()
        val wantColorless = "C" in selected
        if (pips.isNotEmpty() || wantColorless) {
            val byMode = pips.isNotEmpty() && when (mode) {
                ColorMatch.INCLUDING -> card.containsAll(pips)
                ColorMatch.EXACTLY -> card == pips
                ColorMatch.AT_MOST -> pips.containsAll(card)
            }
            if (!byMode && !(wantColorless && card.isEmpty())) return false
        }
        val others = selected.filter { it == LEGACY_MULTI || ColorPairs.isPairKey(it) }
        if (others.isNotEmpty() && others.none { if (it == LEGACY_MULTI) card.size >= 2 else ColorPairs.matches(it, cardColors) }) return false
        return true
    }

    /**
     * The Scryfall query term for the picked pips: "c>=wu", "id<=bg", "c=c". Null when nothing is picked.
     * [identity] uses commander color identity (id) instead of the card's colors (c).
     */
    fun scryfallTerm(selected: Set<String>, mode: ColorMatch, identity: Boolean): String? {
        val key = if (identity) "id" else "c"
        val pips = "WUBRG".filter { it.toString() in selected }.lowercase()
        val colorless = "C" in selected
        return when {
            pips.isEmpty() && colorless -> "$key=c"
            pips.isEmpty() -> null
            // At most already lets colorless cards in.
            colorless && mode != ColorMatch.AT_MOST -> "($key${mode.op}$pips or $key=c)"
            else -> "$key${mode.op}$pips"
        }
    }
}
