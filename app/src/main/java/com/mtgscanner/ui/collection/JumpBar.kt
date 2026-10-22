package com.mtgscanner.ui.collection

/** The A–Z jump bar on long name-sorted lists: which letters it shows and where each one jumps. */
object JumpBar {
    /** Shorter lists scroll fine without it. */
    const val MIN_CARDS = 50

    /** Every letter the bar shows, in order, whether or not the list has cards under it. */
    val LETTERS: List<String> = listOf("#") + ('A'..'Z').map { it.toString() }

    /** Whether the bar shows: sorted by name, a long list, and sections by letter. */
    fun shows(sortedByName: Boolean, cards: Int, rail: List<Pair<String, Int>>): Boolean =
        sortedByName && cards >= MIN_CARDS && rail.isNotEmpty()

    /**
     * The list index to jump to for [letter], given the [rail] of letters present with their first index.
     * A letter with no cards goes to the next letter that has some, or the last one before it. Null for an empty rail.
     */
    fun firstIndex(rail: List<Pair<String, Int>>, letter: String): Int? {
        if (rail.isEmpty()) return null
        rail.firstOrNull { it.first == letter }?.let { return it.second }
        val order = LETTERS.indexOf(letter)
        if (order < 0) return rail.first().second
        rail.firstOrNull { LETTERS.indexOf(it.first) > order }?.let { return it.second }
        return rail.last().second
    }

    /** The letter under a touch at [y] on a bar [height] tall. */
    fun letterAt(y: Float, height: Float): String {
        val i = ((y / height.coerceAtLeast(1f)) * LETTERS.size).toInt().coerceIn(0, LETTERS.lastIndex)
        return LETTERS[i]
    }
}
