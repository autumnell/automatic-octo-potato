package com.mtgscanner.ui.decks

import com.mtgscanner.util.DeckLine
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckStatsMathTest {
    private fun card(name: String, cmc: Double, cost: String, type: String, colors: String, text: String = "") =
        DeckCardInfo(name, cmc, cost, type, colors, colors, text, null)

    @Test
    fun pipsCountHybridAndGeneric() {
        val p = DeckStatsMath.pips("{2}{W}{U/B}{C}")
        assertEquals(1, p[StatColor.White])
        assertEquals(1, p[StatColor.Blue])
        assertEquals(1, p[StatColor.Black])
        assertEquals(3, p[StatColor.Colorless])
    }

    @Test
    fun producesFromTypesAndText() {
        assertEquals(setOf('U', 'R'), DeckStatsMath.produces("Land — Island Mountain", ""))
        assertEquals(setOf('W', 'U', 'B', 'R', 'G'), DeckStatsMath.produces("Land", "{T}: Add one mana of any color."))
        assertEquals(setOf('C'), DeckStatsMath.produces("Artifact", "{T}: Add {C}{C}."))
        assertEquals(emptySet<Char>(), DeckStatsMath.produces("Instant", "Deal 3 damage to any target."))
    }

    @Test
    fun computeSkipsSideboardAndLands() {
        val info = listOf(
            card("Lightning Bolt", 1.0, "{R}", "Instant", "R"),
            card("Mountain", 0.0, "", "Basic Land — Mountain", ""),
            card("Boros Charm", 2.0, "{R}{W}", "Instant", "RW"),
            card("Duress", 1.0, "{B}", "Sorcery", "B"),
        ).associateBy { DeckStatsMath.key(it.name) }
        val lines = listOf(
            DeckLine(4, "Lightning Bolt"), DeckLine(10, "Mountain"), DeckLine(2, "Boros Charm"),
            DeckLine(2, "Duress", section = "Sideboard"), DeckLine(1, "Nope"),
        )
        val s = DeckStatsMath.compute(lines, info)
        assertEquals(6, s.nonland)
        assertEquals(10, s.lands)
        assertEquals(1, s.unknown)
        assertEquals(4, s.curve[1][StatColor.Red])
        assertEquals(2, s.curve[2][StatColor.Multi])
        assertEquals(10, s.production[StatColor.Red])
        assertEquals(8.0 / 6, s.averageMv, 0.001)
        assertEquals(6, s.pips[StatColor.Red])
    }
}
