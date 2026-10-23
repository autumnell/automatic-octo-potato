package com.mtgscanner.ui.collection

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListSectionsTest {
    private var nextId = 1L
    private fun card(name: String, set: String = "m10") = CollectionCard(
        id = nextId++, scryfallId = name, oracleId = name, name = name, setCode = set, setName = "Set $set",
        collectorNumber = "1", rarity = "common", typeLine = "Instant", manaCost = "", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = "", tags = "", priceUsd = 1.0, priceUsdFoil = null, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = false, quantity = 1,
        boxId = 1, addedAt = 0, updatedAt = 0,
    )

    @Test fun lettersIgnoreAccentsAndPutSymbolsUnderHash() {
        assertEquals("A", ListSections.letterOf("Aether Vial"))
        assertEquals("E", ListSections.letterOf("Éowyn"))
        assertEquals("A", ListSections.letterOf("Æther Flash"))
        assertEquals("#", ListSections.letterOf("1996 World Champion"))
        assertEquals("#", ListSections.letterOf(""))
        assertEquals("S", ListSections.letterOf("  shock"))
    }

    @Test fun shortListsAndOtherSortsHaveNoSections() {
        val few = listOf(card("Bolt"), card("Shock"))
        assertNull(ListSections.sections(few, SortBy.NAME))
        val many = (1..30).map { card("Card $it") }
        assertNull(ListSections.sections(many, SortBy.PRICE))
    }

    @Test fun byNameMakesLetterSectionsInOrder() {
        val cards = (List(10) { card("Alpha $it") } + List(12) { card("Bolt $it") } + List(3) { card("Zap $it") })
        val s = ListSections.sections(cards, SortBy.NAME)!!
        assertEquals(listOf("A", "B", "Z"), s.map { it.first })
        assertEquals(listOf(10, 12, 3), s.map { it.second.size })
    }

    @Test fun bySetKeepsRuns() {
        val cards = List(15) { card("x$it", "dom") } + List(10) { card("y$it", "war") }
        val s = ListSections.sections(cards, SortBy.SET)!!
        assertEquals(listOf("Set dom (DOM)", "Set war (WAR)"), s.map { it.first })
    }

    @Test fun headerIndicesCountHeadersAndCards() {
        val groups = listOf("A" to listOf(1, 2), "B" to listOf(3), "C" to listOf(4, 5, 6))
        assertEquals(listOf(0, 3, 5), ListSections.headerIndices(groups))
        assertEquals(listOf(-1), ListSections.headerIndices(listOf("" to listOf(1, 2))))
    }

    @Test fun sectionAtFindsTheHeaderAbove() {
        val headers = listOf(0, 3, 5)
        assertEquals(0, ListSections.sectionAt(headers, 0))
        assertEquals(0, ListSections.sectionAt(headers, 2))
        assertEquals(1, ListSections.sectionAt(headers, 3))
        assertEquals(2, ListSections.sectionAt(headers, 40))
        assertEquals(-1, ListSections.sectionAt(listOf(-1), 4))
    }

    @Test fun railOnlyForLetterSectionsAndDedupes() {
        val groups = listOf("A" to listOf(1), "B" to listOf(2), "A" to listOf(3))
        val headers = ListSections.headerIndices(groups)
        assertEquals(listOf("A" to 0, "B" to 2), ListSections.rail(groups, headers))
        val sets = listOf("Set dom (DOM)" to listOf(1), "Set war (WAR)" to listOf(2))
        assertTrue(ListSections.rail(sets, ListSections.headerIndices(sets)).isEmpty())
    }

    @Test fun fillStatesAndSegments() {
        assertEquals(BoxFill.OK, BoxFillMath.state(5, 0))
        assertEquals(BoxFill.OK, BoxFillMath.state(89, 100))
        assertEquals(BoxFill.NEAR, BoxFillMath.state(90, 100))
        assertEquals(BoxFill.FULL, BoxFillMath.state(100, 100))
        assertEquals(BoxFill.FULL, BoxFillMath.state(120, 100))
        assertEquals(10, BoxFillMath.segments(800))
        assertEquals(4, BoxFillMath.segments(4))
        assertEquals(0, BoxFillMath.lit(0, 100, 10))
        assertEquals(1, BoxFillMath.lit(1, 100, 10))
        assertEquals(9, BoxFillMath.lit(99, 100, 10))
        assertEquals(10, BoxFillMath.lit(100, 100, 10))
        assertEquals(5, BoxFillMath.lit(50, 100, 10))
    }
}
