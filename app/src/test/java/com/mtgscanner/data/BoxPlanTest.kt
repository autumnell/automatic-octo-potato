package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxPlanTest {
    private data class C(val id: Int, val box: Long, val rarity: String, val qty: Int = 1)

    private fun facts(c: C) = CardFacts("U", c.rarity, "Creature", emptySet(), false, 1.0, "abc", name = "Card ${c.id}")

    @Test
    fun newRareBoxTakesRaresFromAnything() {
        val anything = BoxPlans.Planned(BoxInfo(1, BoxRules(), count = 5), "Anything")
        val cards = listOf(C(1, 1, "rare", 2), C(2, 1, "common"), C(3, 1, "rare"), C(4, 1, "uncommon"))
        val planned = BoxPlans.boxes(listOf(anything), emptyMap(), listOf(BoxInfo(0, BoxRules(rarities = setOf("rare"))) to "Rares"))
        val moves = Sorting.misplaced(cards, { it.box }, ::facts, planned.map { it.info }, copies = { it.qty })
        val r = BoxPlans.summarize(planned, moves) { it.qty }
        assertEquals(3, r.cardsMoving)
        assertEquals(listOf(2, 3), r.rows.map { it.after })
        assertTrue(r.rows[1].isNew)
        assertEquals("3 cards", BoxPlans.label(r.rows[1], 0))
    }

    @Test
    fun editedRulesSendCardsBack() {
        val rares = BoxPlans.Planned(BoxInfo(1, BoxRules(rarities = setOf("rare")), count = 2), "Rares")
        val any = BoxPlans.Planned(BoxInfo(2, BoxRules(), count = 0, fallback = true), "Anything")
        val cards = listOf(C(1, 1, "rare"), C(2, 1, "rare"))
        val planned = BoxPlans.boxes(listOf(rares, any), mapOf(1L to BoxInfo(1, BoxRules(rarities = setOf("mythic")))), emptyList())
        assertTrue(planned[0].changed)
        val moves = Sorting.misplaced(cards, { it.box }, ::facts, planned.map { it.info })
        val r = BoxPlans.summarize(planned, moves) { 1 }
        assertEquals(listOf(0, 2), r.rows.map { it.after })
        assertEquals(-2, r.rows[0].change)
    }

    @Test
    fun needsBoxes() {
        val row = PlanRow(1, "Rares", false, false, 0, 412, 0)
        assertEquals(1, row.boxesNeeded(800))
        assertEquals(2, row.boxesNeeded(400))
        assertNull(row.boxesNeeded(0))
        assertEquals("412 cards · needs 2 boxes", BoxPlans.label(row, 400))
        val capped = row.copy(capacity = 300)
        assertEquals(112, capped.over)
        assertEquals(2, capped.boxesNeeded(5000))
    }

    @Test
    fun newFallbackOnlyWhenNoneYet() {
        val has = BoxPlans.Planned(BoxInfo(1, BoxRules(), fallback = true), "Anything")
        val planned = BoxPlans.boxes(listOf(has), emptyMap(), listOf(BoxInfo(0, BoxRules(), fallback = true) to "Other"))
        assertEquals(listOf(true, false), planned.map { it.info.fallback })
        val none = BoxPlans.boxes(emptyList(), emptyMap(), listOf(BoxInfo(0, BoxRules(), fallback = true) to "Other", BoxInfo(0, BoxRules(), fallback = true) to "Two"))
        assertEquals(listOf(true, false), none.map { it.info.fallback })
        assertTrue(none[0].info.id != none[1].info.id)
    }
}
