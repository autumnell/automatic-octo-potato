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

    @Test fun splitCatchAllHalvesStayBehindRuleBoxes() {
        val am = BoxInfo(10, BoxRules(nameFrom = "A", nameTo = "M"))
        val nz = BoxInfo(11, BoxRules(nameFrom = "N", nameTo = "Z"))
        // The split halves come first in the order, yet a rare still goes to the rares box.
        val d = Sorting.decide(listOf(am, nz, rares), card(colors = "G").copy(name = "Avenger"))
        assertEquals(2L, d?.boxId)
        assertEquals(SortReason.RULES, d?.reason)
        // Leftovers go to the half whose range fits.
        assertEquals(10L, Sorting.decide(listOf(am, nz, rares), card(colors = "G", rarity = "common").copy(name = "Elf"))?.boxId)
        assertEquals(11L, Sorting.decide(listOf(am, nz, rares), card(colors = "G", rarity = "common").copy(name = "Tree"))?.boxId)
        assertEquals(true, am.rules.isOpen)
        assertEquals(false, rares.rules.isOpen)
        assertEquals(2L, BoxRules.ruleMatch(listOf(10L to am.rules, 2L to rares.rules), card().copy(name = "Avenger")))
    }

    @Test fun fallbackHalfOnlyTakesItsRange() {
        val am = BoxInfo(10, BoxRules(nameFrom = "A", nameTo = "M"), fallback = true)
        val nz = BoxInfo(11, BoxRules(nameFrom = "N", nameTo = "Z"))
        assertEquals(11L, Sorting.decide(listOf(am, nz), card().copy(name = "Opt"))?.boxId)
        assertEquals(10L, Sorting.decide(listOf(am, nz), card().copy(name = "Bolt"))?.boxId)
    }

    @Test fun decksNeverCatchLeftovers() {
        val deck = BoxInfo(6, BoxRules(), kind = "deck")
        assertNull(Sorting.decide(listOf(blue, deck), card(colors = "R")))
        assertEquals(3L, Sorting.decide(listOf(deck, anything), card(colors = "R"))?.boxId)
    }

    @Test fun misplacedMovesOutOfRangeCardsBetweenHalves() {
        val am = BoxInfo(10, BoxRules(nameFrom = "A", nameTo = "M"))
        val nz = BoxInfo(11, BoxRules(nameFrom = "N", nameTo = "Z"))
        data class C(val box: Long, val f: CardFacts)
        val items = listOf(
            C(10, card(rarity = "common").copy(name = "Opt")),
            C(10, card(rarity = "common").copy(name = "Brainstorm")),
            C(10, card().copy(name = "Counterspell")),
        )
        val moves = Sorting.misplaced(items, { it.box }, { it.f }, listOf(am, nz, rares))
        assertEquals(listOf("Opt" to 11L, "Counterspell" to 2L), moves.map { it.item.f.name to it.to })
    }

    @Test fun splitLetterUsesFileLetters() {
        // Digits and symbols file under '#', which stays with the first half and is never a split point.
        assertEquals('M', Sorting.splitLetter(listOf("1996 World Champion" to 5, "\"Ach!\" Hans" to 5, "Mno" to 10)))
        assertNull(Sorting.splitLetter(listOf("1996 World Champion" to 5, "Abc" to 5)))
        // Lower-case and leading punctuation file under the letter, like BoxRules does.
        assertEquals('Z', Sorting.splitLetter(listOf("abc" to 3, "zed" to 3)))
        assertEquals('A', BoxRules.nameLetter("\"Ach! Hans, Run!\""))
        assertEquals('#', BoxRules.nameLetter("+2 Mace"))
    }

    @Test fun positionBetweenNeighbors() {
        val blueRare = card()
        val contents = listOf(
            "Cryptic Command" to blueRare, "Counterspell" to blueRare, "Brainstorm" to blueRare,
            "Lightning Bolt" to card(colors = "R"),
        )
        val p = Sorting.position(Divider.COLOR, "Control Magic", blueRare, contents)
        assertEquals("Blue", p.spot)
        assertEquals("between Brainstorm and Counterspell", p.text())
        assertEquals("Rares → Blue → between Brainstorm and Counterspell", Sorting.where("Rares", p))
        assertEquals("before Brainstorm", Sorting.position(Divider.COLOR, "Arcane Denial", blueRare, contents).text())
        assertEquals("after Cryptic Command", Sorting.position(Divider.COLOR, "Opt", blueRare, contents).text())
        assertEquals("with its copies", Sorting.position(Divider.COLOR, "counterspell", blueRare, contents).text())
        // A red card only sees the red section.
        assertEquals("after Lightning Bolt", Sorting.position(Divider.COLOR, "Shock", card(colors = "R"), contents).text())
        assertEquals("first card", Sorting.position(Divider.NONE, "x", blueRare, emptyList()).text())
        assertEquals("Box → first card", Sorting.where("Box", Sorting.position(Divider.NONE, "x", blueRare, emptyList())))
    }
}
