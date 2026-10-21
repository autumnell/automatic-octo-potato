package com.mtgscanner.data

/** Narrowing a card's printings when the footer couldn't be read. Pure Kotlin for tests. */
object PrintingPick {
    /** The year of a Scryfall date ("1997-04-01"), or null. */
    fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()

    /**
     * Printings released within a year of the copyright [year], those from that very year first (a set out
     * early in the year often carries the year before), newest first within each. Empty when none fit.
     */
    fun <T> byYear(items: List<T>, year: Int, released: (T) -> String?): List<T> =
        items.mapNotNull { item -> yearOf(released(item))?.let { y -> Triple(item, y, released(item).orEmpty()) } }
            .filter { kotlin.math.abs(it.second - year) <= 1 }
            .sortedWith(compareBy<Triple<T, Int, String>> { it.second != year }.thenByDescending { it.third })
            .map { it.first }

    /** Newest first; undated last. */
    fun <T> newestFirst(items: List<T>, released: (T) -> String?): List<T> =
        items.sortedByDescending { released(it).orEmpty() }
}
