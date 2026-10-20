package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SortingTest {
    private fun card(colors: String = "U", price: Double? = 1.0, rarity: String = "rare", type: String = "Instant", set: String = "mh3") =
        CardFacts(colors, rarity, type, emptySet(), false, price, set)

    private val blue = BoxInfo(1, BoxRules(colors = setOf("U")))
    private val rares = BoxInfo(2, BoxRules(rarities = setOf("rare")))
    private val anything = BoxInfo(3, BoxRules())
    private val trade = BoxInfo(4, BoxRules(), kind = "trade")
    private val bulk = BoxInfo(5, BoxRules())

    @Test fun firstFittingBoxWins() {
        assertEquals(1L, Sorting.decide(listOf(blue, rares, anything), card())?.boxId)
        assertEquals(2L, Sorting.decide(listOf(blue, rares, anything), card(colors = "R"))?.boxId)
    }

    @Test fun fullBoxIsSkippedWhenAnotherFits() {
        val fullBlue = blue.copy(capacity = 10, count = 10)
        val d = Sorting.decide(listOf(fullBlue, rares, anything), card())
        assertEquals(2L, d?.boxId)
        // Only a full box fits: still goes there, flagged.
        val only = Sorting.decide(listOf(fullBlue, anything), card())
        assertEquals(1L, only?.boxId)
        assertEquals(true, only?.boxFull)
    }

    @Test fun bulkFloorBeatsRules() {
        val p = SortPolicy(bulkFloor = 0.25, bulkBoxId = 5)
        assertEquals(5L, Sorting.decide(listOf(blue, bulk), card(price = 0.10), p)?.boxId)
        assertEquals(SortReason.BULK, Sorting.decide(listOf(blue, bulk), card(price = null), p)?.reason)
        assertEquals(1L, Sorting.decide(listOf(blue, bulk), card(price = 0.30), p)?.boxId)
    }

    @Test fun extraCopiesGoToTheExtrasBox() {
        val p = SortPolicy(playsetLimit = 4, extrasBoxId = 4)
        assertEquals(1L, Sorting.decide(listOf(blue, trade), card(), p, ownedInMain = 3)?.boxId)
        val d = Sorting.decide(listOf(blue, trade), card(), p, ownedInMain = 4)
        assertEquals(4L, d?.boxId)
        assertEquals(SortReason.EXTRA, d?.reason)
    }

    @Test fun tradeBindersNeverCatchLeftovers() {
        assertNull(Sorting.decide(listOf(blue, trade), card(colors = "R")))
    }

    @Test fun spotsByDivider() {
        assertEquals("Blue", Sorting.spot(Divider.COLOR, "Counterspell", card()))
        assertEquals("Multicolor", Sorting.spot(Divider.COLOR, "x", card(colors = "UR")))
        assertEquals("Lands", Sorting.spot(Divider.COLOR, "x", card(colors = "", type = "Land")))
        assertEquals("C", Sorting.spot(Divider.LETTER, "counterspell", card()))
        assertEquals("#", Sorting.spot(Divider.LETTER, "1996 World Champion", card()))
        assertEquals("MH3", Sorting.spot(Divider.SET, "x", card()))
        assertEquals("Instant", Sorting.spot(Divider.TYPE, "x", card()))
        assertNull(Sorting.spot(Divider.NONE, "x", card()))
    }

    @Test fun misplacedCardsAfterRuleChange() {
        data class C(val name: String, val box: Long, val f: CardFacts)
        val items = listOf(
            C("red in blue box", 1, card(colors = "R", rarity = "common")),
            C("blue in blue box", 1, card()),
            C("rare in anything", 3, card(colors = "G")),
            C("common in anything", 3, card(colors = "G", rarity = "common")),
            C("in trade", 4, card(colors = "R")),
        )
        val moves = Sorting.misplaced(items, { it.box }, { it.f }, listOf(blue, rares, anything, trade))
        assertEquals(listOf("red in blue box" to 3L, "rare in anything" to 2L), moves.map { it.item.name to it.to })
    }

    @Test fun splitLetterHalvesTheBox() {
        val names = listOf("Abc" to 10, "Bcd" to 10, "Mno" to 10, "Zed" to 10)
        assertEquals('M', Sorting.splitLetter(names))
        assertNull(Sorting.splitLetter(listOf("Abc" to 5)))
    }

    @Test fun nameRangeSplitsABox() {
        val am = BoxRules(nameFrom = "A", nameTo = "M")
        assertEquals(true, am.matches(card().copy(name = "Counterspell")))
        assertEquals(false, am.matches(card().copy(name = "Opt")))
        assertEquals(true, am.matches(card().copy(name = "1996 World Champion")))
        assertEquals(false, am.isCatchAll)
    }
}
