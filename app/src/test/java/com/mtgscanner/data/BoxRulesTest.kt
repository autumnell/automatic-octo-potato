package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxRulesTest {
    private fun card(
        colors: String = "R", rarity: String = "common", type: String = "Instant",
        treatments: Set<String> = emptySet(), foil: Boolean = false, price: Double? = 0.1, set: String = "M11",
    ) = CardFacts(colors, rarity, type, treatments, foil, price, set)

    @Test fun emptyRulesTakeAnything() {
        assertTrue(BoxRules().isCatchAll)
        assertTrue(BoxRules().matches(card()))
    }

    @Test fun colorRules() {
        val red = BoxRules(colors = setOf("R"))
        assertTrue(red.matches(card(colors = "R")))
        assertFalse(red.matches(card(colors = "RG")))
        assertTrue(BoxRules(colors = setOf("M")).matches(card(colors = "RG")))
        assertTrue(BoxRules(colors = setOf("C")).matches(card(colors = "")))
    }

    @Test fun landsGoToALandsBoxNotColorless() {
        val colorless = BoxRules(colors = setOf("C"))
        val lands = BoxRules(types = setOf("Land"))
        val forest = card(colors = "", type = "Basic Land — Forest")
        assertFalse(colorless.matches(forest))
        assertTrue(lands.matches(forest))
        assertTrue(colorless.matches(card(colors = "", type = "Artifact — Equipment")))
        // In Auto-sort order, a colorless box ahead of the lands box still leaves it the lands.
        val boxes = listOf("Colorless" to colorless, "Lands" to lands)
        assertEquals("Lands", BoxRules.pick(boxes, forest, fallback = "Colorless"))
        assertEquals("Colorless", BoxRules.pick(boxes, card(colors = "", type = "Artifact"), fallback = "Colorless"))
        // The color binders template now has a box for them.
        assertTrue(BOX_TEMPLATES.first { it.name == "Color binders" }.boxes.any { LandsBox.isLandsBox(it.rules) })
        // A lands box after every color box isn't dead: color boxes don't take lands.
        val colorBoxes = listOf("W", "U", "B", "R", "G", "M", "C").map { Triple(it, BoxRules(colors = setOf(it)), false) }
        assertFalse("L" in RuleCheck.deadBoxes(colorBoxes + Triple("L", lands, false)))
    }

    @Test fun bulkPreset() {
        val bulk = BOX_PRESETS.first { it.name == "Bulk" }.rules
        assertTrue(bulk.matches(card(rarity = "common", price = 0.2)))
        assertFalse(bulk.matches(card(rarity = "rare", price = 0.2)))
        assertFalse(bulk.matches(card(rarity = "uncommon", price = 3.0)))
    }

    @Test fun treatmentsFinishAndPrice() {
        assertTrue(BoxRules(treatments = setOf("borderless")).matches(card(treatments = setOf("borderless"))))
        assertFalse(BoxRules(treatments = setOf("borderless")).matches(card()))
        assertFalse(BoxRules(finish = "foil").matches(card(foil = false)))
        assertTrue(BoxRules(minPrice = 5.0).matches(card(price = 7.0)))
        assertFalse(BoxRules(minPrice = 5.0).matches(card(price = null)))
    }

    @Test fun pickPrefersSpecificBoxesThenCatchAll() {
        val boxes = listOf(1L to BoxRules(), 2L to BoxRules(colors = setOf("R")), 3L to BoxRules(rarities = setOf("rare")))
        assertEquals(2L, BoxRules.pick(boxes, card(colors = "R")))
        assertEquals(3L, BoxRules.pick(boxes, card(colors = "U", rarity = "rare")))
        assertEquals(1L, BoxRules.pick(boxes, card(colors = "U")))
        assertNull(BoxRules.pick(listOf(2L to BoxRules(colors = setOf("R"))), card(colors = "U")))
    }

    @Test fun pickFollowsOrderThenFallback() {
        val boxes = listOf(5L to BoxRules(rarities = setOf("rare")), 2L to BoxRules(colors = setOf("R")), 1L to BoxRules())
        // A red rare goes to whichever box comes first in the order.
        assertEquals(5L, BoxRules.pick(boxes, card(colors = "R", rarity = "rare")))
        assertEquals(2L, BoxRules.pick(boxes.reversed(), card(colors = "R", rarity = "rare")))
        // Nothing fits: the fallback beats the catch-all box.
        assertEquals(9L, BoxRules.pick(boxes, card(colors = "U"), fallback = 9L))
        assertEquals(1L, BoxRules.pick(boxes, card(colors = "U")))
        assertEquals(9L, BoxRules.pick(listOf(2L to BoxRules(colors = setOf("R"))), card(colors = "U"), fallback = 9L))
    }

    @Test fun overlapFindsCardsTakenFirst() {
        val cards = listOf(card(colors = "R", rarity = "rare"), card(colors = "R"), card(colors = "U", rarity = "rare"))
        val ahead = listOf("Red" to BoxRules(colors = setOf("R")), "Any" to BoxRules())
        val o = BoxRules.overlap(BoxRules(rarities = setOf("rare")), ahead, cards)
        assertEquals(listOf(0, 2), o.fits)
        assertEquals(listOf("Red" to listOf(0)), o.takenBy)
        val none = BoxRules.overlap(BoxRules(rarities = setOf("rare")), emptyList<Pair<String, BoxRules>>(), cards)
        assertTrue(none.takenBy.isEmpty())
    }

    @Test fun jsonRoundTrip() {
        val r = BoxRules(colors = setOf("W", "U"), treatments = setOf("showcase"), minPrice = 2.5)
        assertEquals(r, BoxRules.fromJson(r.toJson()))
        assertEquals("", BoxRules().toJson())
        assertEquals(BoxRules(), BoxRules.fromJson("garbage"))
    }

    @Test fun describe() {
        assertEquals("Any card", BoxRules().describe())
        assertEquals("Red · Rare · $5+", BoxRules(colors = setOf("R"), rarities = setOf("rare"), minPrice = 5.0).describe())
    }

    @Test fun ruleMatchSkipsCatchAllBoxes() {
        val boxes = listOf(1L to BoxRules(), 2L to BoxRules(colors = setOf("R")), 3L to BoxRules(minPrice = 10.0))
        assertEquals(2L, BoxRules.ruleMatch(boxes, card(colors = "R")))
        assertEquals(3L, BoxRules.ruleMatch(boxes, card(colors = "U", price = 12.0)))
        assertEquals(null, BoxRules.ruleMatch(boxes, card(colors = "U", price = 1.0)))
    }

    private fun facts(
        identity: String = "", cmc: Double = 0.0, power: String? = null, toughness: String? = null,
        formats: Set<String> = emptySet(), reserved: Boolean = false,
    ) = card().copy(colorIdentity = identity, cmc = cmc, power = power, toughness = toughness, formats = formats, reserved = reserved)

    @Test fun colorIdentityRules() {
        val izzet = BoxRules(identity = setOf("U", "R"))
        assertTrue(izzet.matches(facts(identity = "UR")))
        assertFalse(izzet.matches(facts(identity = "U")))
        val within = izzet.copy(identityMode = "within")
        assertTrue(within.matches(facts(identity = "U")))
        assertTrue(within.matches(facts(identity = "")))
        assertFalse(within.matches(facts(identity = "UB")))
        assertTrue(BoxRules(identity = setOf("C")).matches(facts(identity = "")))
        assertFalse(BoxRules(identity = setOf("C")).matches(facts(identity = "G")))
        assertEquals("Identity UR", izzet.describe())
        assertEquals("Within UR", within.describe())
    }

    @Test fun manaValuePowerToughness() {
        val cheap = BoxRules(maxCmc = 2)
        assertTrue(cheap.matches(facts(cmc = 2.0)))
        assertFalse(cheap.matches(facts(cmc = 3.0)))
        val big = BoxRules(minPower = 4)
        assertTrue(big.matches(facts(power = "5", toughness = "5")))
        assertFalse(big.matches(facts(power = "3")))
        assertFalse(big.matches(facts(power = "*")))
        assertFalse(big.matches(facts()))
        assertTrue(BoxRules(minToughness = 1, maxToughness = 1).matches(facts(power = "1+*", toughness = "1")))
        assertEquals("MV ≤2", cheap.describe())
        assertEquals("MV 2–3 · Power 4+", BoxRules(minCmc = 2, maxCmc = 3, minPower = 4).describe())
        assertEquals(1, BoxRules.statValue("1+*"))
        assertEquals(-1, BoxRules.statValue("-1"))
        assertNull(BoxRules.statValue("*"))
    }

    @Test fun formatAndReservedRules() {
        val pauper = BoxRules(formats = setOf("pauper"))
        assertTrue(pauper.matches(facts(formats = setOf("pauper", "commander"))))
        assertFalse(pauper.matches(facts(formats = setOf("commander"))))
        assertEquals("Pauper legal", pauper.describe())
        assertTrue(BoxRules(reserved = true).matches(facts(reserved = true)))
        assertFalse(BoxRules(reserved = true).matches(facts()))
        assertEquals("Reserved List", BoxRules(reserved = true).describe())
    }

    @Test fun newRulesAreNotCatchAllAndRoundTrip() {
        val all = listOf(
            BoxRules(identity = setOf("G")), BoxRules(minCmc = 1), BoxRules(maxCmc = 1), BoxRules(minPower = 1), BoxRules(maxPower = 1),
            BoxRules(minToughness = 1), BoxRules(maxToughness = 1), BoxRules(formats = setOf("modern")), BoxRules(reserved = true),
        )
        all.forEach { assertFalse(it.isCatchAll); assertEquals(it, BoxRules.fromJson(it.toJson())) }
        // Rules saved before these fields existed still load.
        assertEquals(BoxRules(colors = setOf("R")), BoxRules.fromJson("""{"colors":["R"]}"""))
        assertTrue(BoxRules(identityMode = "within").isCatchAll)
    }

    @Test fun templates() {
        assertTrue(BOX_TEMPLATES.any { it.name == "Rares and bulk" && it.boxes.any { b -> b.fallback } })
        val sets = BoxTemplate.setBoxes(listOf("mh3" to 40, "blb" to 10, "dmu" to 0), max = 1)
        assertEquals(listOf("MH3", "Other sets"), sets.boxes.map { it.name })
        assertEquals(setOf("MH3"), sets.boxes.first().rules.sets)
        assertTrue(sets.boxes.last().rules.isCatchAll)
    }
}
