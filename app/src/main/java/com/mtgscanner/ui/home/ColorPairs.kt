package com.mtgscanner.ui.home

/** Two-color pairs and their guild names, in the usual order: allied pairs first, then enemy pairs. */
object ColorPairs {
    val PAIRS: List<Pair<String, String>> = listOf(
        "WU" to "Azorius", "UB" to "Dimir", "BR" to "Rakdos", "RG" to "Gruul", "GW" to "Selesnya",
        "WB" to "Orzhov", "UR" to "Izzet", "BG" to "Golgari", "RW" to "Boros", "GU" to "Simic",
    )
    /** Key for cards of three or more colors. */
    const val THREE_PLUS = "3+"

    /** The pair key ("WU", "GW"…) a two-color card belongs to, whatever order its colors are stored in. */
    fun keyFor(colors: String): String? {
        val set = colors.uppercase().toSet()
        if (set.size != 2) return null
        return PAIRS.firstOrNull { it.first.toSet() == set }?.first
    }

    fun name(key: String): String = PAIRS.firstOrNull { it.first == key }?.second ?: "Three or more colors"

    /**
     * Copies per pair for multicolor cards, in [PAIRS] order, then [THREE_PLUS]. Each entry is (key, copies).
     * [cards] is (colors, quantity) for every card; mono-color and colorless cards are skipped.
     */
    fun count(cards: List<Pair<String, Int>>): List<Pair<String, Int>> {
        val out = LinkedHashMap<String, Int>()
        PAIRS.forEach { out[it.first] = 0 }
        out[THREE_PLUS] = 0
        cards.forEach { (colors, qty) ->
            val n = colors.uppercase().toSet().count { it in "WUBRG" }
            val key = when {
                n == 2 -> keyFor(colors.filter { it.uppercaseChar() in "WUBRG" })
                n >= 3 -> THREE_PLUS
                else -> null
            } ?: return@forEach
            out[key] = (out[key] ?: 0) + qty
        }
        return out.toList()
    }

    /** Whether a card with [colors] matches a pair filter key from Browse. */
    fun matches(key: String, colors: String): Boolean = when (key) {
        THREE_PLUS -> colors.uppercase().toSet().count { it in "WUBRG" } >= 3
        else -> keyFor(colors) == key
    }

    fun isPairKey(key: String): Boolean = key == THREE_PLUS || PAIRS.any { it.first == key }
}
