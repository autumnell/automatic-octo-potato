package com.mtgscanner.ui.home

/** A section of the Home screen that can be shown, hidden and moved. */
enum class HomeSection(val key: String, val label: String) {
    NEWEST("newest", "Newest cards"),
    TILES("tiles", "Stat tiles"),
    TO_SORT("sort", "To be sorted"),
    GOALS("goals", "Goals"),
    CARD_OF_DAY("daycard", "Card of the day"),
    RECENT("recent", "Recently added"),
    MOVERS("movers", "Price moves this week"),
    VALUABLE("valuable", "Most valuable"),
    GAINERS("gainers", "Biggest gains"),
    BOX_VALUES("boxes", "Value by box"),
    BULK("bulk", "Bulk estimate"),
    COLORS("colors", "Colors"),
    PAIRS("pairs", "Color pairs"),
    ALERTS("alerts", "Price alerts"),
    ;

    companion object {
        fun of(key: String): HomeSection? = entries.firstOrNull { it.key == key }
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
