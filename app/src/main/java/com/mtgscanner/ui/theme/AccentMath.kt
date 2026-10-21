package com.mtgscanner.ui.theme

import androidx.compose.ui.graphics.Color

/** "Accent from my collection": which Magic color the collection leans to, and the accent for it. */
object AccentMath {
    private const val ORDER = "WUBRG"

    /**
     * The most-owned color among W, U, B, R and G, counting copies. A card counts once for each color in
     * its color identity ("WU" adds to both); colorless cards count for none. Ties go to WUBRG order.
     * Null when nothing colored is owned.
     */
    fun dominant(cards: List<Pair<String, Int>>): Char? {
        val counts = IntArray(ORDER.length)
        cards.forEach { (identity, copies) ->
            identity.uppercase().toSet().forEach { c ->
                val i = ORDER.indexOf(c)
                if (i >= 0) counts[i] += copies.coerceAtLeast(0)
            }
        }
        var best = 0
        for (i in 1 until counts.size) if (counts[i] > counts[best]) best = i
        return if (counts[best] > 0) ORDER[best] else null
    }

    /** ARGB accent per color: muted, readable shades, not the raw mana colors. Gold when there's none. */
    fun accentArgb(letter: Char?): Long = when (letter?.uppercaseChar()) {
        'W' -> 0xFFE6D6A3 // parchment
        'U' -> 0xFF4A8FE0 // island blue
        'B' -> 0xFF9B7CC2 // dusk violet (black itself would vanish on a dark background)
        'R' -> 0xFFE2603F // ember red
        'G' -> 0xFF3FAE6B // forest green
        else -> 0xFFE6B422 // Gold, the default accent
    }

    fun accentFor(letter: Char?): Color = Color(accentArgb(letter))
}
