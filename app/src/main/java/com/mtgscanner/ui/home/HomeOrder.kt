package com.mtgscanner.ui.home

/** A section of the Home screen that can be shown, hidden and moved. */
enum class HomeSection(val key: String, val label: String) {
    NEWEST("newest", "Newest cards"),
    TILES("tiles", "Stat tiles"),
    TO_SORT("sort", "To be sorted"),
    VIEWED("viewed", "Recently viewed"),
    GOALS("goals", "Goals"),
    RECENT("recent", "Recently added"),
    MOVERS("movers", "Price moves"),
    ALERTS("alerts", "Price alerts"),
    ;

    companion object {
        /** Sections folded into another: "Biggest gains" is now the "Since added" side of Price moves. */
        private val MERGED = mapOf("gainers" to "movers")

        /** The section saved as [key]; null for a section that was removed (Card of the day, Colors…). */
        fun of(key: String): HomeSection? = (MERGED[key] ?: key).let { k -> entries.firstOrNull { it.key == k } }
    }
}

/** Which Home sections show, in order, from what was saved. */
object HomeOrder {
    /** A new install: the newest cards' art first, then the tiles. "Recently added" would repeat the strip, so it starts hidden. */
    val DEFAULT: List<HomeSection> = HomeSection.entries.filter { it != HomeSection.RECENT }

    /**
     * [saved] is the saved order (null when never saved), [known] the sections that existed when it was saved.
     * Sections added since show up at the end, except the newest-cards strip, which goes first once, with the stat
     * tiles (always shown at the top before they were a section) right under it.
     */
    fun load(saved: String?, known: String?): List<HomeSection> {
        if (saved == null) return DEFAULT
        val shown = saved.split(',').mapNotNull { HomeSection.of(it.trim()) }.distinct()
        val knownKeys = known.orEmpty().split(',').map { it.trim() }.toSet()
        val list = shown.toMutableList()
        if (HomeSection.NEWEST.key !in knownKeys && HomeSection.NEWEST !in list) list.add(0, HomeSection.NEWEST)
        if (HomeSection.TILES.key !in knownKeys && HomeSection.TILES !in list) {
            val i = list.indexOf(HomeSection.NEWEST)
            list.add(if (i >= 0) i + 1 else 0, HomeSection.TILES)
        }
        val added = HomeSection.entries.filter { it.key !in knownKeys && it !in list }
        return list + added
    }
}
