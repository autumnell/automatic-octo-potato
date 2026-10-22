package com.mtgscanner.ui.decks

/** One played card for the curve: copies, mana value, type line and mana cost. */
data class ShapeCard(val quantity: Int, val cmc: Double, val typeLine: String, val manaCost: String)

/**
 * A deck's mana curve and color split, lands left out.
 * [curve] has [BUCKETS] counts: mana value 0, 1, … 6, and 7 or more.
 * [pips] counts colored mana symbols by color letter (W, U, B, R, G), in WUBRG order, zeros left out.
 */
data class DeckShape(val curve: List<Int>, val pips: List<Pair<Char, Int>>) {
    val spells: Int get() = curve.sum()
    val pipTotal: Int get() = pips.sumOf { it.second }
    val isEmpty: Boolean get() = spells == 0

    companion object {
        const val BUCKETS = 8
    }
}

/** Mana curve and color math for a deck; pure so it can be tested. */
object DeckShapeMath {
    private val SYMBOL = Regex("""\{([^}]+)\}""")

    /** A land on its front face; "Sorcery // Land" spells count as spells. */
    fun isLand(typeLine: String): Boolean = typeLine.substringBefore("//").contains("Land", ignoreCase = true)

    /** The curve bucket for a mana value: 0..6, or 7 for 7 and up. Half values (Little Girl) round down. */
    fun bucket(cmc: Double): Int = cmc.toInt().coerceIn(0, DeckShape.BUCKETS - 1)

    /**
     * Colored symbols in a mana cost, by letter. "{W/U}" counts once for each color, "{G/P}" counts G,
     * "{2/W}" counts W. Generic, {X}, {C} and {S} don't count. A two-faced cost ("{1}{U} // {3}{U}{U}") uses its front.
     */
    fun pips(manaCost: String): Map<Char, Int> {
        val out = HashMap<Char, Int>()
        SYMBOL.findAll(manaCost.substringBefore("//")).forEach { m ->
            m.groupValues[1].uppercase().split('/').map { it.trim() }.filter { it.length == 1 && it[0] in "WUBRG" }
                .distinct().forEach { out[it[0]] = (out[it[0]] ?: 0) + 1 }
        }
        return out
    }

    fun of(cards: List<ShapeCard>): DeckShape {
        val curve = IntArray(DeckShape.BUCKETS)
        val pips = HashMap<Char, Int>()
        for (c in cards) {
            if (c.quantity <= 0 || isLand(c.typeLine)) continue
            curve[bucket(c.cmc)] += c.quantity
            pips(c.manaCost).forEach { (k, v) -> pips[k] = (pips[k] ?: 0) + v * c.quantity }
        }
        return DeckShape(curve.toList(), "WUBRG".mapNotNull { k -> pips[k]?.takeIf { it > 0 }?.let { k to it } })
    }

    /** Whole percents for [parts] that add up to 100 (largest remainder). Empty when the total is 0. */
    fun percents(parts: List<Int>): List<Int> {
        val total = parts.sum()
        if (total <= 0) return emptyList()
        val raw = parts.map { it * 100.0 / total }
        val out = raw.map { it.toInt() }.toMutableList()
        var left = 100 - out.sum()
        raw.indices.sortedByDescending { raw[it] - out[it] }.forEach { i -> if (left > 0) { out[i]++; left-- } }
        return out
    }
}
