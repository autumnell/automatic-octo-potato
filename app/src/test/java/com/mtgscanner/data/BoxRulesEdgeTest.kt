package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edges of box rules: price bounds, name ranges, rarities, tokens first, and which rules cover others. */
class BoxRulesEdgeTest {
    private fun card(
        colors: String = "R", rarity: String = "common", type: String = "Instant",
        foil: Boolean = false, price: Double? = 0.1, set: String = "M11", name: String = "",
        power: String? = null,
    ) = CardFacts(colors, rarity, type, emptySet(), foil, price, set, name = name, power = power)

    @Test fun priceBounds() {
        val under1 = BoxRules(maxPrice = 1.0)
        assertTrue(under1.matches(card(price = 0.99)))
        // The top of a range is left out, so "Under $1" and "$1+" never both take a card.
        assertFalse(under1.matches(card(price = 1.0)))
        assertTrue(BoxRules(minPrice = 1.0).matches(card(price = 1.0)))
        // A card with no price yet counts as $0.
        assertTrue(under1.matches(card(price = null)))
        assertFalse(BoxRules(minPrice = 1.0).matches(card(price = null)))
    }

    @Test fun priceSlackWidensBothEnds() {
        val fiveUp = BoxRules(minPrice = 5.0)
        assertTrue(fiveUp.matches(card(price = 4.6), priceSlack = 0.1))
        assertFalse(fiveUp.matches(card(price = 4.4), priceSlack = 0.1))
        assertFalse(fiveUp.matches(card(price = 4.6)))
        val under1 = BoxRules(maxPrice = 1.0)
        assertTrue(under1.matches(card(price = 1.05), priceSlack = 0.1))
        assertFalse(under1.matches(card(price = 1.1), priceSlack = 0.1))
    }

    @Test fun specialRaritiesCountAsMythic() {
        assertTrue(BoxRules(rarities = setOf("mythic")).matches(card(rarity = "special")))
        assertTrue(BoxRules(rarities = setOf("mythic")).matches(card(rarity = "bonus")))
        assertFalse(BoxRules(rarities = setOf("rare")).matches(card(rarity = "special")))
    }

    @Test fun setsIgnoreCase() {
        assertTrue(BoxRules(sets = setOf("mh3")).matches(card(set = "MH3")))
        assertTrue(BoxRules(sets = setOf("MH3")).matches(card(set = "mh3")))
        assertFalse(BoxRules(sets = setOf("MH3")).matches(card(set = "MH2")))
    }

    @Test fun finishRules() {
        assertTrue(BoxRules(finish = "foil").matches(card(foil = true)))
        assertFalse(BoxRules(finish = "foil").matches(card(foil = false)))
        assertFalse(BoxRules(finish = "nonfoil").matches(card(foil = true)))
    }

    @Test fun nameRanges() {
        val aToM = BoxRules(nameFrom = "A", nameTo = "M")
        assertTrue(aToM.inNameRange("Lightning Bolt"))
        assertTrue(aToM.inNameRange("Mox Opal"))
        assertFalse(aToM.inNameRange("Opt"))
        // Accents and a leading "The" don't count; digits and symbols fit any range.
        assertTrue(aToM.inNameRange("Æther Vial"))
        assertFalse(aToM.inNameRange("The Wandering Emperor"))
        assertTrue(BoxRules(nameFrom = "N", nameTo = "Z").inNameRange("+2 Mace"))
        assertTrue(BoxRules(nameFrom = "N").inNameRange("Zur the Enchanter"))
        assertFalse(BoxRules(nameTo = "M").inNameRange("zur the enchanter"))
    }

    @Test fun nameRangeAloneIsOpenButNotCatchAll() {
        val half = BoxRules(nameFrom = "A", nameTo = "M")
        assertTrue(half.isOpen)
        assertFalse(half.isCatchAll)
        assertFalse(BoxRules(colors = setOf("U"), nameFrom = "A").isOpen)
    }

    @Test fun statValues() {
        assertEquals(3, BoxRules.statValue("3"))
        assertEquals(1, BoxRules.statValue("1+*"))
        assertEquals(-1, BoxRules.statValue("-1"))
        assertNull(BoxRules.statValue("*"))
        assertNull(BoxRules.statValue(null))
        // A creature with "*" power doesn't fit a power rule.
        assertFalse(BoxRules(minPower = 0).matches(card(type = "Creature", power = "*")))
        assertTrue(BoxRules(minPower = 0).matches(card(type = "Creature", power = "0")))
    }

    @Test fun tokensBoxGoesFirst() {
        val boxes = listOf(1L to BoxRules(colors = setOf("R")), 2L to BoxRules(tokens = true), 3L to BoxRules())
        assertEquals(listOf(2L, 1L, 3L), BoxRules.tokensFirst(boxes) { it.second }.map { it.first })
        assertEquals(2L, BoxRules.pick(boxes, card(type = "Token Creature — Goblin")))
        assertEquals(1L, BoxRules.pick(boxes, card()))
        // A tokens box doesn't take real cards; without a rule fit, the fallback wins over open boxes.
        assertEquals(9L, BoxRules.pick(boxes, card(colors = "U"), fallback = 9L))
        assertEquals(3L, BoxRules.pick(boxes, card(colors = "U")))
    }

    @Test fun coversNarrowerRules() {
        val blue = BoxRules(colors = setOf("U"))
        val blueRares = BoxRules(colors = setOf("U"), rarities = setOf("rare"))
        assertTrue(blue.covers(blueRares))
        assertFalse(blueRares.covers(blue))
        assertTrue(BoxRules().covers(blueRares))
        assertTrue(BoxRules(minPrice = 5.0).covers(BoxRules(minPrice = 10.0)))
        assertFalse(BoxRules(minPrice = 5.0).covers(BoxRules()))
        assertTrue(BoxRules(nameFrom = "A", nameTo = "Z").covers(BoxRules(nameFrom = "A", nameTo = "M")))
        assertFalse(BoxRules(nameFrom = "A", nameTo = "M").covers(BoxRules()))
        assertTrue(BoxRules(finish = "any").covers(BoxRules(finish = "foil")))
        assertFalse(BoxRules(finish = "foil").covers(BoxRules(finish = "nonfoil")))
        assertTrue(BoxRules(types = setOf("creature")).covers(BoxRules(types = setOf("Creature"))))
        val withinEsper = BoxRules(identity = setOf("W", "U", "B"), identityMode = "within")
        assertTrue(withinEsper.covers(BoxRules(identity = setOf("U", "B"))))
        assertFalse(BoxRules(identity = setOf("U", "B")).covers(BoxRules(identity = setOf("U", "B"), identityMode = "within")))
        assertFalse(BoxRules(tokens = true).covers(BoxRules()))
    }

    @Test fun limitedBoxNeverMakesAnotherDead() {
        val dead = RuleCheck.deadBoxes(
            listOf(
                Triple(1L, BoxRules(colors = setOf("U")), true),
                Triple(2L, BoxRules(colors = setOf("U"), rarities = setOf("rare")), false),
                Triple(3L, BoxRules(colors = setOf("U")), false),
                Triple(4L, BoxRules(colors = setOf("U"), types = setOf("Instant")), false),
            ),
        )
        assertEquals(mapOf(4L to 3L), dead)
    }

    @Test fun jsonEdges() {
        assertEquals("", BoxRules().toJson())
        assertEquals(BoxRules(), BoxRules.fromJson(""))
        assertEquals(BoxRules(), BoxRules.fromJson("not json"))
        assertEquals(BoxRules(minPrice = 2.5), BoxRules.fromJson("""{"minPrice":2.5,"someNewField":1}"""))
    }

    @Test fun describeRangesAndNames() {
        assertEquals("$1–$5.50", BoxRules(minPrice = 1.0, maxPrice = 5.5).describe())
        assertEquals("N–Z", BoxRules(nameFrom = "N").describe())
        assertEquals("MV 2–3 · Power 4+ · Toughness ≤2", BoxRules(minCmc = 2, maxCmc = 3, minPower = 4, maxToughness = 2).describe())
        assertEquals("MV 3", BoxRules(minCmc = 3, maxCmc = 3).describe())
        assertEquals("Within WUB", BoxRules(identity = setOf("B", "W", "U"), identityMode = "within").describe())
    }
}
