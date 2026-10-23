package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auto-sort order: which check wins (tokens, bulk, extras, rules, fallback, open boxes), and order inside a box. */
class SortOrderTest {
    private fun card(colors: String = "U", price: Double? = 2.0, rarity: String = "rare", type: String = "Instant", set: String = "mh3", name: String = "") =
        CardFacts(colors, rarity, type, emptySet(), false, price, set, name = name)

    private val faerie = card(type = "Token Creature — Faerie", price = 0.1)

    private val tokens = BoxInfo(9, BoxRules(tokens = true))
    private val blue = BoxInfo(1, BoxRules(colors = setOf("U")))
    private val bulk = BoxInfo(5, BoxRules(maxPrice = 1.0))
    private val extras = BoxInfo(6, BoxRules(colors = setOf("U")))
    private val anything = BoxInfo(3, BoxRules())
    private val policy = SortPolicy(playsetLimit = 4, extrasBoxId = 6, bulkFloor = 0.5, bulkBoxId = 5)
    private val all = listOf(tokens, blue, bulk, extras, anything)

    @Test fun checksRunInOrder() {
        // Tokens first, even when cheap.
        assertEquals(SortDecision(9, SortReason.RULES), Sorting.decide(all, faerie, policy))
        // Then the bulk floor, ahead of extras and rules.
        assertEquals(SortDecision(5, SortReason.BULK), Sorting.decide(all, card(price = 0.1), policy, ownedInMain = 9))
        // Then the playset limit.
        assertEquals(SortDecision(6, SortReason.EXTRA), Sorting.decide(all, card(), policy, ownedInMain = 4))
        assertEquals(SortDecision(1, SortReason.RULES), Sorting.decide(all, card(), policy, ownedInMain = 3))
        // A card with no price yet isn't bulk.
        assertEquals(SortDecision(1, SortReason.RULES), Sorting.decide(all, card(price = null), policy))
        // Nothing with rules fits: the open box takes it.
        assertEquals(SortDecision(3, SortReason.FALLBACK), Sorting.decide(all, card(colors = "R"), policy))
    }

    @Test fun noBoxFits() {
        assertNull(Sorting.decide(listOf(blue), card(colors = "R")))
        assertNull(Sorting.decide(emptyList(), card()))
        // A tokens box doesn't take real cards.
        assertNull(Sorting.decide(listOf(tokens), card()))
    }

    @Test fun fullBoxesStillAnswer() {
        val fullTokens = tokens.copy(capacity = 1, count = 1)
        assertEquals(SortDecision(9, SortReason.RULES, boxFull = true), Sorting.decide(listOf(fullTokens), faerie))
        val openA = BoxInfo(3, BoxRules(), capacity = 1, count = 1)
        val openB = BoxInfo(7, BoxRules(), capacity = 1, count = 1)
        assertEquals(SortDecision(3, SortReason.FALLBACK, boxFull = true), Sorting.decide(listOf(openA, openB), card()))
        assertEquals(7L, Sorting.decide(listOf(openA, openB.copy(count = 0)), card())?.boxId)
        assertFalse(BoxInfo(1, BoxRules(), capacity = 0, count = 500).isFull)
    }

    @Test fun fallbackBeatsOpenBoxes() {
        val greens = BoxInfo(8, BoxRules(colors = setOf("G")), fallback = true)
        // Listed after the open box, and its rules don't fit, but it's the fallback.
        assertEquals(SortDecision(8, SortReason.FALLBACK), Sorting.decide(listOf(anything, greens), card(colors = "R")))
    }

    @Test fun mainBoxes() {
        assertTrue(Sorting.isMainBox(blue, policy))
        assertFalse(Sorting.isMainBox(bulk, policy))
        assertFalse(Sorting.isMainBox(extras, policy))
        assertFalse(Sorting.isMainBox(BoxInfo(4, BoxRules(), kind = "trade"), policy))
        assertFalse(Sorting.isMainBox(BoxInfo(4, BoxRules(), kind = "deck"), policy))
    }

    @Test fun tokensLeaveOtherBoxesAndDecksStayPut() {
        data class Item(val id: Int, val box: Long, val f: CardFacts)
        val deck = BoxInfo(20, BoxRules(colors = setOf("R")), kind = "deck")
        val items = listOf(Item(1, 1, faerie), Item(2, 20, card()), Item(3, 1, card()))
        val moves = Sorting.misplaced(items, { it.box }, { it.f }, listOf(tokens, blue, deck))
        assertEquals(listOf(1 to 9L), moves.map { it.item.id to it.to })
    }

    @Test fun sortOutSendsCheapCardsToBulk() {
        val boxes = listOf(blue, bulk, anything)
        val p = SortPolicy(bulkFloor = 0.5, bulkBoxId = 5)
        assertEquals(SortDecision(5, SortReason.BULK), Sorting.sortOutTarget(boxes, 1, card(price = 0.1), p))
        assertNull(Sorting.sortOutTarget(boxes, 1, card(price = 3.0), p))
    }

    @Test fun numberOrder() {
        fun key(set: String, n: String) = Sorting.numberKey(card(set = set).copy(number = n))
        assertTrue(key("mh3", "2") < key("mh3", "10"))
        assertTrue(key("mh3", "12") < key("mh3", "12a"))
        assertTrue(key("mh3", "12a") < key("mh3", "13"))
        assertTrue(key("MH2", "300") < key("mh3", "1"))
    }

    @Test fun positionTexts() {
        val c = card()
        assertEquals("first card", Sorting.position(Divider.NONE, "Opt", c, emptyList()).text())
        assertEquals("with its copies", Sorting.position(Divider.NONE, "opt", c, listOf("Opt" to c)).text())
        assertEquals("after Brainstorm", Sorting.position(Divider.NONE, "Opt", c, listOf("Brainstorm" to c)).text())
        assertEquals("before Ponder", Sorting.position(Divider.NONE, "Opt", c, listOf("Ponder" to c)).text())
        // Only cards behind the same divider count.
        val red = card(colors = "R")
        assertEquals("first card", Sorting.position(Divider.COLOR, "Opt", c, listOf("Bolt" to red)).text())
        // A leading "The" is skipped.
        assertEquals("after The Wandering Emperor", Sorting.position(Divider.NONE, "Zur", c, listOf("The Wandering Emperor" to c)).text())
    }

    @Test fun splitHelpers() {
        val names = listOf("Opt" to 2, "Bolt" to 3, "1996 World Champion" to 1, "The Wandering Emperor" to 1)
        assertEquals(4 to 3, Sorting.splitCounts(names, 'N'))
        assertEquals('B', Sorting.splitLetter(listOf("Apple" to 5, "Bolt" to 5)))
        assertNull(Sorting.splitLetter(listOf("Apple" to 5, "Axe" to 5)))
        assertEquals(('B'..'M').toList(), Sorting.splitLetters("A", "M"))
        assertEquals(('O'..'Z').toList(), Sorting.splitLetters("n", "z"))
        assertEquals(25, Sorting.splitLetters(null, null).size)
        assertTrue(Sorting.splitLetters("M", "M").isEmpty())
    }

    @Test fun layoutParsing() {
        assertEquals(listOf(Divider.COLOR), BoxLayout.of("color>color").levels)
        assertEquals("color", BoxLayout.of("color>color").key)
        assertFalse(BoxLayout.of("junk").hasDividers)
        assertEquals("", BoxLayout.of("junk").key)
        assertEquals(BoxLayout(Divider.SET, byNumber = true), BoxLayout.of(" set > # "))
        assertEquals("First letter · by number", BoxLayout.of("letter>#").label())
        assertEquals("Color → First letter", BoxLayout.of("color>letter").label())
        assertEquals("None", BoxLayout().label())
        assertEquals(Divider.NONE, Divider.of(""))
        assertEquals(Divider.NONE, Divider.of("nope"))
    }

    @Test fun dividerOrder() {
        assertEquals(listOf("White", "Blue", "Lands", "???"), listOf("Lands", "Blue", "???", "White").sortedWith(Sorting.spotOrder(Divider.COLOR)))
        assertEquals(listOf("Mythic", "Rare", "Common"), listOf("Common", "Mythic", "Rare").sortedWith(Sorting.spotOrder(Divider.RARITY)))
        assertEquals(listOf("Creature", "Land", "Other"), listOf("Other", "Land", "Creature").sortedWith(Sorting.spotOrder(Divider.TYPE)))
        assertEquals(listOf("#", "A", "Z"), listOf("Z", "#", "A").sortedWith(Sorting.spotOrder(Divider.LETTER)))
    }

    @Test fun tabsForTwoLevelsAreOnlyTheUsedOnes() {
        val l = BoxLayout.of("color>letter")
        val cards = listOf("Lightning Bolt" to card(colors = "R"), "Counterspell" to card(), "Brainstorm" to card())
        assertEquals(listOf("Blue → B", "Blue → C", "Red → L"), Sorting.tabs(l, cards))
        assertEquals(8, Sorting.tabs(BoxLayout(Divider.COLOR), emptyList()).size)
    }
}
