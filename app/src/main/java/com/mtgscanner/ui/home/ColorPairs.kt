package com.mtgscanner.ui.home

/** Two-color pairs and their guild names, for Browse color filters saved by older versions. */
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

    /** Whether a card with [colors] matches a pair filter key from Browse. */
    fun matches(key: String, colors: String): Boolean = when (key) {
        THREE_PLUS -> colors.uppercase().toSet().count { it in "WUBRG" } >= 3
        else -> keyFor(colors) == key
    }

    fun isPairKey(key: String): Boolean = key == THREE_PLUS || PAIRS.any { it.first == key }
}
