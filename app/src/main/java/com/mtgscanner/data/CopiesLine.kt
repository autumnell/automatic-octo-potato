package com.mtgscanner.data

/** The card page's one line on where every copy of a card is: "3 copies: Rares ×2, Atraxa deck ×1". Pure, for tests. */
object CopiesLine {
    /** A box, or a deck (named by the deck, not its card row). */
    data class Spot(val name: String, val deck: Boolean = false)

    /**
     * [copies] is (box id, copies) for each row of the card, every printing. Places with the most copies
     * come first. Null when there's nothing to say: no copies, or just this one row.
     */
    fun text(copies: List<Pair<Long, Int>>, spots: Map<Long, Spot>): String? {
        val rows = copies.filter { it.second > 0 }
        if (rows.size < 2) return null
        val counts = LinkedHashMap<String, Int>()
        for ((id, n) in rows) {
            val s = spots[id]
            val label = when {
                s == null -> "Unsorted"
                s.deck -> "${s.name} deck"
                else -> s.name
            }
            counts[label] = (counts[label] ?: 0) + n
        }
        val total = rows.sumOf { it.second }
        val parts = counts.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ×${it.value}" }
        return "$total ${if (total == 1) "copy" else "copies"}: $parts"
    }
}
