package com.mtgscanner.ui.decks

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckTypesTest {
    @Test fun typesPickTheFirstGroup() {
        assertEquals(DeckType.CREATURE, DeckTypes.of("Artifact Creature — Golem"))
        assertEquals(DeckType.CREATURE, DeckTypes.of("Land Creature — Forest Dryad"))
        assertEquals(DeckType.CREATURE, DeckTypes.of("Legendary Enchantment Creature — God"))
        assertEquals(DeckType.LAND, DeckTypes.of("Basic Land — Swamp"))
        assertEquals(DeckType.ARTIFACT, DeckTypes.of("Artifact"))
        assertEquals(DeckType.SORCERY, DeckTypes.of("Kindred Sorcery — Elf"))
        assertEquals(DeckType.PLANESWALKER, DeckTypes.of("Legendary Planeswalker — Jace"))
        assertEquals(DeckType.INSTANT, DeckTypes.of("Instant // Sorcery"))
        assertEquals(DeckType.OTHER, DeckTypes.of(null))
        assertEquals(DeckType.OTHER, DeckTypes.of("Conspiracy"))
    }

    @Test fun subtypesDontCount() {
        // "Land" after the dash is a subtype word, not the card type.
        assertEquals(DeckType.ENCHANTMENT, DeckTypes.of("Enchantment — Aura Land"))
    }

    @Test fun groupsKeepOrder() {
        val cards = listOf("Forest" to "Basic Land — Forest", "Bear" to "Creature — Bear", "Sol Ring" to "Artifact", "Elf" to "Creature — Elf")
        val g = DeckTypes.group(cards) { it.second }
        assertEquals(listOf(DeckType.CREATURE, DeckType.ARTIFACT, DeckType.LAND), g.map { it.first })
        assertEquals(listOf("Bear", "Elf"), g[0].second.map { it.first })
    }

    @Test fun pilesSplitEvenly() {
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5), listOf(6, 7)), DeckTypes.piles((1..7).toList(), 3))
        assertEquals(listOf(listOf(1), emptyList<Int>(), emptyList()), DeckTypes.piles(listOf(1), 3))
        assertEquals(emptyList<List<Int>>(), DeckTypes.piles(listOf(1), 0))
    }
}
