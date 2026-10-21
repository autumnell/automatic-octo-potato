package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // No price yet (a new set Scryfall hasn't priced) isn't bulk.
        assertEquals(SortReason.RULES, Sorting.decide(listOf(blue, bulk), card(price = null), p)?.reason)
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

    // Item 13/14: spiked cards leave Bulk; unpriced ones stay.
    @Test fun spikedCardsLeaveBulk() {
        data class Item(val id: Int, val box: Long, val f: CardFacts)
        val p = SortPolicy(bulkFloor = 0.5, bulkBoxId = 5)
        val items = listOf(Item(1, 5, card(price = 12.0)), Item(2, 5, card(price = 0.2)), Item(3, 5, card(price = null)), Item(4, 5, card(price = 0.52)))
        val moves = Sorting.misplaced(items, { it.box }, { it.f }, listOf(blue, bulk), p, priceSlack = 0.1)
        // 0.52 is inside the 10% buffer above the floor, so it stays too.
        assertEquals(listOf(1), moves.map { it.item.id })
        assertEquals(1L, moves.single().to)
    }

    @Test fun extrasOnlyLeaveForBulk() {
        data class Item(val id: Int, val box: Long, val f: CardFacts)
        val p = SortPolicy(playsetLimit = 4, extrasBoxId = 4, bulkFloor = 0.5, bulkBoxId = 5)
        val extras = BoxInfo(4, BoxRules())
        val items = listOf(Item(1, 4, card(price = 12.0)), Item(2, 4, card(price = 0.1)))
        val moves = Sorting.misplaced(items, { it.box }, { it.f }, listOf(blue, extras, bulk), p)
        assertEquals(listOf(2 to 5L), moves.map { it.item.id to it.to })
    }

    // Item 75: price buffer and pinned cards.
    @Test fun priceBufferKeepsCardsNearTheLine() {
        data class Item(val id: Int, val box: Long, val f: CardFacts)
        val pricey = BoxInfo(7, BoxRules(minPrice = 5.0))
        val items = listOf(Item(1, 7, card(price = 4.8)), Item(2, 7, card(price = 4.0)))
        val noBuffer = Sorting.misplaced(items, { it.box }, { it.f }, listOf(pricey, anything))
        assertEquals(listOf(1, 2), noBuffer.map { it.item.id })
        val buffered = Sorting.misplaced(items, { it.box }, { it.f }, listOf(pricey, anything), priceSlack = 0.1)
        assertEquals(listOf(2), buffered.map { it.item.id })
        val pinned = Sorting.misplaced(items, { it.box }, { it.f }, listOf(pricey, anything), pinned = { it.id == 2 })
        assertEquals(listOf(1), pinned.map { it.item.id })
    }

    // Item 16: planned moves count cards into boxes, so a box with room for 2 isn't sent 3.
    @Test fun moveAllRespectsCapacity() {
        data class Item(val id: Int, val box: Long, val f: CardFacts)
        val smallBlue = blue.copy(capacity = 2, count = 0)
        val blue2 = BoxInfo(8, BoxRules(colors = setOf("U")))
        val items = (1..3).map { Item(it, 3, card(rarity = "common")) }
        val moves = Sorting.misplaced(items, { it.box }, { it.f }, listOf(smallBlue, blue2, anything))
        assertEquals(listOf(1L, 1L, 8L), moves.map { it.to })
    }

    // Item 15: "Sort out" keeps a card whose own box comes first.
    @Test fun sortOutKeepsCardsThatBelong() {
        val p = SortPolicy()
        assertNull(Sorting.sortOutTarget(listOf(blue, rares, anything), 1, card(), p))
        assertEquals(1L, Sorting.sortOutTarget(listOf(blue, rares, anything), 2, card(), p)?.boxId)
        // Fits nothing else: stays in the Anything box.
        assertNull(Sorting.sortOutTarget(listOf(blue, anything), 3, card(colors = "R"), p))
    }

    // Item 21: a split fallback box with rules keeps leftovers in the right half.
    @Test fun splitFallbackWithRulesKeepsNameRanges() {
        val am = BoxInfo(10, BoxRules(rarities = setOf("mythic"), nameFrom = "A", nameTo = "M"), fallback = true)
        val nz = BoxInfo(11, BoxRules(rarities = setOf("mythic"), nameFrom = "N", nameTo = "Z"))
        assertEquals(11L, Sorting.decide(listOf(am, nz), card(rarity = "common").copy(name = "Opt"))?.boxId)
        assertEquals(10L, Sorting.decide(listOf(am, nz), card(rarity = "common").copy(name = "Bolt"))?.boxId)
        // An unsplit fallback with rules still takes everything.
        val fb = BoxInfo(12, BoxRules(rarities = setOf("mythic")), fallback = true)
        assertEquals(12L, Sorting.decide(listOf(fb), card(rarity = "common").copy(name = "Opt"))?.boxId)
    }

    // Item 22: one name key everywhere.
    @Test fun nameKeyFoldsAccentsAndThe() {
        assertEquals("eowyn, lady of rohan", NameKey.of("Éowyn, Lady of Rohan"))
        assertEquals("wandering emperor", NameKey.of("The Wandering Emperor"))
        assertEquals("aether vial", NameKey.of("Æther Vial"))
        assertEquals('W', NameKey.letter("The Wandering Emperor"))
        assertEquals('E', NameKey.letter("Éowyn"))
        assertEquals('T', NameKey.letter("Theros Beyond Death"))
        assertEquals("W", Sorting.spot(Divider.LETTER, "The Wandering Emperor", card()))
        assertEquals('W', BoxRules.nameLetter("The Wandering Emperor"))
        assertTrue(BoxRules(nameFrom = "N", nameTo = "Z").inNameRange("The Wandering Emperor"))
    }

    // Item 81: two-level dividers and number order.
    @Test fun twoLevelLayout() {
        val l = BoxLayout.of("color>letter")
        assertEquals(Divider.COLOR, l.first)
        assertEquals(Divider.LETTER, l.second)
        assertEquals("color>letter", l.key)
        assertEquals(Divider.COLOR, Divider.of("color>letter"))
        assertEquals("set>#", BoxLayout(Divider.SET, byNumber = true).key)
        assertEquals("", BoxLayout().key)
        assertEquals("Blue → C", Sorting.spot(l, "Counterspell", card()))
        val labels = listOf("Red → A", "Blue → Z", "Blue → C", "White → M")
        assertEquals(listOf("White → M", "Blue → C", "Blue → Z", "Red → A"), labels.sortedWith(Sorting.spotOrder(l)))
        val contents = listOf("Brainstorm" to card(), "Counterspell" to card(), "Cryptic Command" to card(), "Opt" to card())
        assertEquals("Rares → Blue → C → between Counterspell and Cryptic Command",
            Sorting.where("Rares", Sorting.position(l, "Crop Rotation", card(), contents)))
    }

    @Test fun numberOrderInsideASetDivider() {
        val l = BoxLayout.of("set>#")
        fun n(num: String) = card().copy(number = num)
        val contents = listOf("Zap" to n("3"), "Apple" to n("40"), "Moon" to n("12a"))
        val p = Sorting.position(l, "Bolt", n("12"), contents)
        assertEquals("MH3", p.spot)
        assertEquals("between Zap and Moon", p.text())
    }

    @Test fun labelTabs() {
        assertEquals(27, Sorting.tabs(BoxLayout(Divider.LETTER), emptyList()).size)
        assertEquals(listOf("MH3", "ZEN"), Sorting.tabs(BoxLayout(Divider.SET), listOf("a" to card(set = "zen"), "b" to card())))
        assertTrue(Sorting.tabs(BoxLayout(), listOf("a" to card())).isEmpty())
    }

    // Item 82: boxes that can never get a card.
    @Test fun deadRules() {
        val boxes = listOf(
            Triple(1L, BoxRules(rarities = setOf("rare", "mythic")), false),
            Triple(2L, BoxRules(rarities = setOf("rare"), colors = setOf("U")), false),
            Triple(3L, BoxRules(colors = setOf("U")), false),
            Triple(4L, BoxRules(), false),
        )
        assertEquals(mapOf(2L to 1L), RuleCheck.deadBoxes(boxes))
        // A box with a card limit lets cards through once full.
        assertTrue(RuleCheck.deadBoxes(boxes.map { if (it.first == 1L) it.copy(third = true) else it }).isEmpty())
        // Color boxes for every color catch everything after them.
        val colors = listOf("W", "U", "B", "R", "G", "M", "C").mapIndexed { i, c -> Triple(i.toLong(), BoxRules(colors = setOf(c)), false) }
        assertEquals(mapOf(99L to null), RuleCheck.deadBoxes(colors + Triple(99L, BoxRules(rarities = setOf("rare")), false)))
    }
}
