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
}
