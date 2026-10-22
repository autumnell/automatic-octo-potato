package com.mtgscanner.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Test

class WishlistSortTest {
    private data class W(val name: String, val price: Double?, val added: Long)

    private val items = listOf(
        W("Sol Ring", 1.5, 300),
        W("ancient tomb", 60.0, 100),
        W("Lightning Bolt", null, 200),
        W("Bolt Bend", 1.5, 400),
    )

    private fun run(q: String, s: WishlistSort) = WishlistSorting.apply(items, q, s, { it.name }, { it.price }, { it.added }).map { it.name }

    @Test fun byNameIgnoresCase() = assertEquals(listOf("ancient tomb", "Bolt Bend", "Lightning Bolt", "Sol Ring"), run("", WishlistSort.NAME))
    @Test fun byPriceHighestFirstNoPriceLast() = assertEquals(listOf("ancient tomb", "Bolt Bend", "Sol Ring", "Lightning Bolt"), run("", WishlistSort.PRICE))
    @Test fun byAddedNewestFirst() = assertEquals(listOf("Bolt Bend", "Sol Ring", "Lightning Bolt", "ancient tomb"), run("", WishlistSort.ADDED))
    @Test fun searchMatchesAnyPartOfTheName() = assertEquals(listOf("Bolt Bend", "Lightning Bolt"), run(" bolt ", WishlistSort.NAME))
    @Test fun noMatches() = assertEquals(emptyList<String>(), run("zzz", WishlistSort.NAME))
}
