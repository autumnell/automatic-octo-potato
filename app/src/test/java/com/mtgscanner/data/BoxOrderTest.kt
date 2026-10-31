package com.mtgscanner.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoxOrderTest {
    private fun card(
        colors: String = "U", price: Double? = 1.0, type: String = "Instant", set: String = "mh3",
        cmc: Double = 2.0, identity: String = colors, number: String = "1", foil: Boolean = false,
    ) = CardFacts(colors, "rare", type, emptySet(), foil, price, set, colorIdentity = identity, cmc = cmc, number = number)

    @After fun resetColors() {
        Sorting.colorOrder = Sorting.DEFAULT_COLOR_ORDER
    }

    @Test fun threeLevelsAndOrderRoundTrip() {
        val l = BoxLayout.of("color>type>cmc>$")
        assertEquals(listOf(Divider.COLOR, Divider.TYPE, Divider.MANA), l.levels)
        assertEquals(CardOrder.PRICE, l.order)
        assertEquals("color>type>cmc>$", l.key)
        // A fourth level is dropped.
        assertEquals(3, BoxLayout.of("color>type>cmc>rarity").levels.size)
        // Price order alone still needs card facts to place a card.
        assertEquals(true, BoxLayout.of("$").needsFacts)
        assertEquals(false, BoxLayout.of("").needsFacts)
    }

    @Test fun presetsAreFoundByKey() {
        assertEquals("Color → Type → A–Z", SortPreset.of("color>type")?.label)
        assertEquals("Set → Number", SortPreset.of(" set > # ")?.label)
        assertNull(SortPreset.of("foil>color"))
    }

    @Test fun colorPairsAndIdentityNames() {
        assertEquals("Azorius", Sorting.spot(Divider.PAIRS, "x", card(colors = "UW")))
        assertEquals("Jund", Sorting.spot(Divider.PAIRS, "x", card(colors = "GRB")))
        assertEquals("Five colors", Sorting.spot(Divider.PAIRS, "x", card(colors = "WUBRG")))
        assertEquals("Lands", Sorting.spot(Divider.PAIRS, "x", card(colors = "", type = "Land")))
        assertEquals("Simic", Sorting.spot(Divider.IDENTITY, "x", card(colors = "", identity = "GU", type = "Land")))
        // Mono colors, then every pair where Multicolor sits, then colorless.
        val tabs = listOf("Esper", "Colorless", "Azorius", "Blue").sortedWith(Sorting.spotOrder(Divider.PAIRS))
        assertEquals(listOf("Blue", "Azorius", "Esper", "Colorless"), tabs)
    }

    @Test fun manaPriceAndFoilSpots() {
        assertEquals("3", Sorting.spot(Divider.MANA, "x", card(cmc = 3.0)))
        assertEquals("7+", Sorting.spot(Divider.MANA, "x", card(cmc = 9.0)))
        assertEquals("Lands", Sorting.spot(Divider.MANA, "x", card(cmc = 0.0, type = "Basic Land")))
        assertEquals("$5–20", Sorting.spot(Divider.PRICE, "x", card(price = 7.5)))
        assertEquals("No price", Sorting.spot(Divider.PRICE, "x", card(price = null)))
        assertEquals("Foil", Sorting.spot(Divider.FOIL, "x", card(foil = true)))
        assertEquals(Sorting.PRICE_BANDS, Sorting.PRICE_BANDS.reversed().sortedWith(Sorting.spotOrder(Divider.PRICE)))
    }

    @Test fun releaseOrderUsesSetDates() {
        SetDates.note("lea", "1993-08-05")
        SetDates.note("mh3", "2024-06-14")
        assertEquals(listOf("LEA", "MH3", "ZZZ"), listOf("ZZZ", "MH3", "LEA").sortedWith(Sorting.spotOrder(Divider.RELEASE)))
    }

    @Test fun priceOrderPlacesDearestFirst() {
        val l = BoxLayout.of("$")
        val contents = listOf("Cheap" to card(price = 0.5), "Pricey" to card(price = 30.0))
        val p = Sorting.position(l, "Middle", card(price = 4.0), contents)
        assertEquals("between Pricey and Cheap", p.text())
    }

    @Test fun ownColorOrderMovesDividers() {
        Sorting.colorOrder = Sorting.fullColorOrder(listOf("Green", "White"))
        assertEquals(listOf("Green", "White", "Blue"), listOf("Blue", "White", "Green").sortedWith(Sorting.spotOrder(Divider.COLOR)))
        // Unknown names are dropped and missing ones come back.
        assertEquals(Sorting.DEFAULT_COLOR_ORDER, Sorting.fullColorOrder(listOf("Purple")))
    }

    @Test fun rowsFillFrontFirst() {
        assertNull(Sorting.row(10, 100, rows = 1))
        // 4 rows of a 1000-card box: 250 a row.
        assertEquals(1, Sorting.row(0, 300, rows = 4, capacity = 1000))
        assertEquals(2, Sorting.row(260, 300, rows = 4, capacity = 1000))
        // No capacity: the cards are shared out over the rows.
        assertEquals(2, Sorting.row(50, 100, rows = 2))
        assertEquals(2, Sorting.row(500, 100, rows = 2))
    }

    @Test fun indexCountsCardsInFront() {
        val l = BoxLayout.of("color")
        val contents = listOf("Opt" to card(colors = "U"), "Bolt" to card(colors = "R"), "Swords" to card(colors = "W"))
        assertEquals(1, Sorting.indexIn(l, "Brainstorm", card(colors = "U"), contents))
    }
}
