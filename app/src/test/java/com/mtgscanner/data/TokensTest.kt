package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokensTest {
    private fun facts(type: String, colors: String = "W", price: Double? = 0.1, set: String = "tmom") =
        CardFacts(colors, "common", type, emptySet(), false, price, set)

    @Test fun tellsTokensEmblemsAndArtCards() {
        assertTrue(CardExtras.isExtra("Token Creature — Soldier"))
        assertTrue(CardExtras.isExtra("Legendary Token Artifact — Equipment"))
        assertTrue(CardExtras.isExtra("Emblem — Ajani"))
        assertTrue(CardExtras.isExtra("Card // Card"))
        assertTrue(CardExtras.isExtra("Token Creature — Human // Token Creature — Werewolf"))
        assertTrue(CardExtras.isExtra("Creature — Elf", layout = "token"))
        assertFalse(CardExtras.isExtra("Creature — Elf Druid"))
        // Rules text style words in a subtype don't count.
        assertFalse(CardExtras.isExtra("Artifact — Treasure"))
        assertFalse(CardExtras.isExtra("Legendary Planeswalker — Ajani"))
    }

    @Test fun tokensRuleOnlyTakesTokens() {
        val r = BoxRules(tokens = true)
        assertFalse(r.isCatchAll)
        assertTrue(r.matches(facts("Token Creature — Soldier")))
        assertFalse(r.matches(facts("Creature — Soldier")))
        assertEquals("Tokens & art cards", r.describe())
    }

    @Test fun oldJsonStillReads() {
        val old = BoxRules.fromJson("""{"colors":["W"]}""")
        assertEquals(BoxRules(colors = setOf("W")), old)
        assertFalse(old.tokens)
        val round = BoxRules.fromJson(BoxRules(tokens = true).toJson())
        assertTrue(round.tokens)
        // Unchanged rules write the same JSON as before.
        assertEquals("""{"colors":["W"]}""", BoxRules(colors = setOf("W")).toJson())
    }

    @Test fun tokensBoxWinsOverEarlierBoxesAndBulk() {
        val white = BoxInfo(1, BoxRules(colors = setOf("W")))
        val bulk = BoxInfo(2, BoxRules())
        val tokens = BoxInfo(3, BoxRules(tokens = true))
        val p = SortPolicy(bulkFloor = 0.25, bulkBoxId = 2)
        assertEquals(3L, Sorting.decide(listOf(white, bulk, tokens), facts("Token Creature — Soldier"), p)?.boxId)
        // A real card still follows the order.
        assertEquals(1L, Sorting.decide(listOf(white, bulk, tokens), facts("Creature — Soldier", price = 1.0), p)?.boxId)
        // Without a tokens box nothing changes.
        assertEquals(1L, Sorting.decide(listOf(white, bulk), facts("Token Creature — Soldier", price = 1.0), p)?.boxId)
        assertEquals(3L, BoxRules.pick(listOf(1L to white.rules, 3L to tokens.rules), facts("Token Creature — Soldier")))
    }

    @Test fun tokensBoxIsNeverDead() {
        val dead = RuleCheck.deadBoxes(
            listOf(Triple(1L, BoxRules(colors = setOf("W")), false), Triple(2L, BoxRules(colors = setOf("W"), tokens = true), false)),
        )
        assertTrue(dead.isEmpty())
    }

    private data class P(val set: String, val number: String)

    @Test fun pickTokenPrinting() {
        val list = listOf(P("tmom", "3"), P("tone", "3"), P("tmom", "5"))
        assertEquals(P("tmom", "3") to true, TokenPick.pick(list, "MOM", "0003", { it.set }, { it.number }))
        assertEquals(P("tmom", "5") to true, TokenPick.pick(list, "mom", "5", { it.set }, { it.number }))
        assertEquals(P("tmom", "3") to false, TokenPick.pick(list, "MOM", null, { it.set }, { it.number }))
        assertEquals(P("tmom", "3") to false, TokenPick.pick(list, null, null, { it.set }, { it.number }))
        assertEquals(P("tone", "3") to false, TokenPick.pick(listOf(P("tone", "3")), "XYZ", "3", { it.set }, { it.number }))
        assertNull(TokenPick.pick(emptyList<P>(), "MOM", "3", { it.set }, { it.number }))
    }
}
