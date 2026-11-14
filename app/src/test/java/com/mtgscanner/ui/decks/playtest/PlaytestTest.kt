package com.mtgscanner.ui.decks.playtest

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaytestTest {
    private val forest = PlayCard("Forest", typeLine = "Basic Land — Forest")
    private val bear = PlayCard("Grizzly Bears", typeLine = "Creature — Bear", cmc = 2.0, power = "2", toughness = "2")
    private val bolt = PlayCard("Lightning Bolt", typeLine = "Instant", cmc = 1.0)
    private val giant = PlayCard("Hill Giant", typeLine = "Creature — Giant", cmc = 4.0, power = "3", toughness = "3")
    private val cmdr = PlayCard("Leader", typeLine = "Legendary Creature — Elf", cmc = 3.0, power = "2", toughness = "2")

    private fun deck(vararg cards: Pair<PlayCard, Int>, commanders: List<PlayCard> = emptyList()) =
        PlayDeck("Deck", cards.flatMap { (c, n) -> List(n) { c } }, commanders)

    private fun allIds(s: Side) = Zone.entries.flatMap { s[it] }.map { it.id }

    @Test fun startDealsSevenAndKeepsEveryCard() {
        val t = Playtest.start(deck(forest to 20, bear to 20), null, Random(1))
        val s = t.sides.single()
        assertEquals(7, s[Zone.HAND].size)
        assertEquals(33, s[Zone.LIBRARY].size)
        assertEquals(40, allIds(s).toSet().size)
        assertEquals(20, s.life)
    }

    @Test fun commandersStartInCommandZoneAt40Life() {
        val t = Playtest.start(deck(forest to 50, commanders = listOf(cmdr)), deck(forest to 40), Random(2))
        assertEquals(listOf("Leader"), t.sides[0][Zone.COMMAND].map { it.card.name })
        assertEquals(40, t.sides[0].life)
        assertEquals(20, t.sides[1].life)
        // Ids are unique across both sides.
        assertEquals(91, (allIds(t.sides[0]) + allIds(t.sides[1])).toSet().size)
    }

    @Test fun nextTurnUntapsAndDraws() {
        var t = Playtest.start(deck(forest to 10, bear to 10), null, Random(3))
        val land = t.sides[0][Zone.HAND].first()
        t = Playtest.move(t, 0, land.id, Zone.BATTLEFIELD)
        t = Playtest.toggleTap(t, 0, land.id)
        assertTrue(t.sides[0][Zone.BATTLEFIELD].single().tapped)
        t = Playtest.nextTurn(t)
        assertEquals(2, t.turn)
        assertFalse(t.sides[0][Zone.BATTLEFIELD].single().tapped)
        assertEquals(7, t.sides[0][Zone.HAND].size)
    }

    @Test fun twoSidesTakeTurns() {
        var t = Playtest.start(deck(forest to 20), deck(forest to 20), Random(4))
        t = Playtest.nextTurn(t)
        assertEquals(1, t.active)
        assertEquals(1, t.turn)
        assertEquals(8, t.sides[1][Zone.HAND].size)
        t = Playtest.nextTurn(t)
        assertEquals(0, t.active)
        assertEquals(2, t.turn)
    }

    @Test fun leavingTheBattlefieldClearsCountersAndTokensVanish() {
        var t = Playtest.start(deck(bear to 10), null, Random(5))
        val b = t.sides[0][Zone.HAND].first()
        t = Playtest.move(t, 0, b.id, Zone.BATTLEFIELD)
        t = Playtest.counter(t, 0, b.id, Piece.PLUS, 2)
        assertEquals(4, t.sides[0][Zone.BATTLEFIELD].single().power)
        t = Playtest.move(t, 0, b.id, Zone.GRAVEYARD)
        assertTrue(t.sides[0][Zone.GRAVEYARD].single().counters.isEmpty())

        t = Playtest.token(t, 0, PlayCard("Treasure", typeLine = "Artifact — Treasure"), 2)
        val token = t.sides[0][Zone.BATTLEFIELD].first()
        assertTrue(token.card.token)
        t = Playtest.move(t, 0, token.id, Zone.GRAVEYARD)
        assertEquals(1, t.sides[0][Zone.BATTLEFIELD].size)
        assertEquals(1, t.sides[0][Zone.GRAVEYARD].size)
    }

    @Test fun counterAtZeroIsRemoved() {
        var t = Playtest.start(deck(bear to 10), null, Random(6))
        val b = t.sides[0][Zone.HAND].first()
        t = Playtest.move(t, 0, b.id, Zone.BATTLEFIELD)
        t = Playtest.counter(t, 0, b.id, "Charge", 1)
        t = Playtest.counter(t, 0, b.id, "Charge", -1)
        assertTrue(t.sides[0][Zone.BATTLEFIELD].single().counters.isEmpty())
    }

    @Test fun libraryTopAndBottom() {
        var t = Playtest.start(deck(forest to 7, bear to 3), null, Random(7))
        val h = t.sides[0][Zone.HAND]
        t = Playtest.move(t, 0, h[0].id, Zone.LIBRARY)
        t = Playtest.move(t, 0, h[1].id, Zone.LIBRARY, bottom = true)
        assertEquals(h[0].id, t.sides[0][Zone.LIBRARY].first().id)
        assertEquals(h[1].id, t.sides[0][Zone.LIBRARY].last().id)
    }

    @Test fun mulliganKeepsEveryCard() {
        var t = Playtest.start(deck(forest to 30, bear to 30), null, Random(8))
        val before = allIds(t.sides[0]).sorted()
        t = Playtest.mulligan(t, 0, Random(9))
        assertEquals(1, t.sides[0].mulligans)
        assertEquals(7, t.sides[0][Zone.HAND].size)
        assertEquals(before, allIds(t.sides[0]).sorted())
    }

    @Test fun simplePilotPlaysLandCastsAndAttacks() {
        // The opponent already has two lands and a bear that can attack.
        var t = Playtest.start(deck(forest to 20), deck(forest to 10, bear to 10), Random(10))
        val opp = t.sides[1]
        val lands = opp[Zone.LIBRARY].filter { it.card.isLand }.take(2) + opp[Zone.HAND].filter { it.card.isLand }.take(0)
        lands.forEach { t = Playtest.move(t, 1, it.id, Zone.BATTLEFIELD) }
        val oldBear = (opp[Zone.LIBRARY] + opp[Zone.HAND]).first { it.card.isCreature }
        t = Playtest.move(t, 1, oldBear.id, Zone.BATTLEFIELD)
        t = Playtest.untap(t, 1)
        // Make sure there's a land and a bear in hand.
        val hand = t.sides[1][Zone.HAND]
        if (hand.none { it.card.isLand }) t.sides[1][Zone.LIBRARY].first { it.card.isLand }.let { t = Playtest.move(t, 1, it.id, Zone.HAND) }
        if (t.sides[1][Zone.HAND].none { it.card.isCreature }) t.sides[1][Zone.LIBRARY].first { it.card.isCreature }.let { t = Playtest.move(t, 1, it.id, Zone.HAND) }

        val out = SimplePilot.takeTurn(t.copy(active = 1), 1)
        val bf = out.sides[1][Zone.BATTLEFIELD]
        assertEquals(3, bf.count { it.card.isLand })
        assertTrue(bf.count { it.card.isCreature } >= 2)
        // The bear from last turn attacked; the new one is summoning sick.
        assertEquals(18, out.sides[0].life)
        assertTrue(out.log.last().contains("attacked with 1 for 2"))
    }

    @Test fun simplePilotPaysCommanderTax() {
        var t = Playtest.start(deck(forest to 20), deck(forest to 40, commanders = listOf(cmdr)), Random(11))
        repeat(4) { i ->
            val land = t.sides[1][Zone.LIBRARY].first { it.card.isLand }
            t = Playtest.move(t, 1, land.id, Zone.BATTLEFIELD)
        }
        // Keep the hand free of lands so only the four already out count.
        t.sides[1][Zone.HAND].forEach { t = Playtest.move(t, 1, it.id, Zone.GRAVEYARD) }
        var out = SimplePilot.takeTurn(t.copy(active = 1), 1)
        assertEquals(1, out.sides[1].commanderCasts)
        assertTrue(out.sides[1][Zone.COMMAND].isEmpty())
        // Back to the command zone: now it costs 5, more than the 4 lands.
        val c = out.sides[1][Zone.BATTLEFIELD].first { it.card.name == "Leader" }
        out = Playtest.move(out, 1, c.id, Zone.COMMAND)
        out = Playtest.untap(out, 1)
        out = SimplePilot.takeTurn(out, 1)
        assertEquals(1, out.sides[1][Zone.COMMAND].size)
    }

    @Test fun instantsGoToTheGraveyard() {
        var t = Playtest.start(deck(forest to 20), deck(forest to 10, bolt to 10), Random(12))
        t.sides[1][Zone.HAND].forEach { t = Playtest.move(t, 1, it.id, Zone.GRAVEYARD) }
        val land = t.sides[1][Zone.LIBRARY].first { it.card.isLand }
        t = Playtest.move(t, 1, land.id, Zone.BATTLEFIELD)
        val b = t.sides[1][Zone.LIBRARY].first { it.card.name == "Lightning Bolt" }
        t = Playtest.move(t, 1, b.id, Zone.HAND)
        val out = SimplePilot.takeTurn(t, 1)
        assertTrue(out.sides[1][Zone.GRAVEYARD].any { it.id == b.id })
    }

    @Test fun powerWithoutPrintedNumberIsNull() {
        assertNull(Piece(1, PlayCard("Tarmogoyf", typeLine = "Creature", power = "*", toughness = "1+*")).power)
        assertEquals(3, Piece(1, giant).power)
    }

    @Test fun keepingAfterMulligansPutsCardsOnTheBottom() {
        assertEquals(0, Playtest.toBottom(0, free = false))
        assertEquals(1, Playtest.toBottom(1, free = false))
        assertEquals(0, Playtest.toBottom(1, free = true))
        assertEquals(2, Playtest.toBottom(3, free = true))
        // A 5-card hand from a short library never asks for more than 5, or Keep could never be pressed.
        assertEquals(5, Playtest.toBottom(7, free = false, hand = 5))
        assertTrue(Playtest.freeMulligan(PlayDeck("a", emptyList(), listOf(cmdr), "commander")))
        assertFalse(Playtest.freeMulligan(PlayDeck("a", emptyList(), emptyList(), "modern")))
        assertTrue(Playtest.freeMulligan(PlayDeck("a", emptyList(), listOf(cmdr), "")))

        var t = Playtest.start(deck(forest to 20, bear to 20), null, Random(9))
        t = Playtest.mulligan(t, 0, Random(9))
        val two = t.sides[0][Zone.HAND].take(2).map { it.id }
        t = Playtest.bottom(t, 0, two)
        val s = t.sides[0]
        assertEquals(5, s[Zone.HAND].size)
        assertEquals(two, s[Zone.LIBRARY].takeLast(2).map { it.id })
        assertEquals(40, allIds(s).toSet().size)
    }

    @Test fun battlefieldRowsByType() {
        assertEquals(Lane.LANDS, Lane.of(Piece(1, forest)))
        assertEquals(Lane.CREATURES, Lane.of(Piece(2, bear)))
        assertEquals(Lane.OTHERS, Lane.of(Piece(3, PlayCard("Sol Ring", typeLine = "Artifact"))))
        assertEquals(Lane.OTHERS, Lane.of(Piece(4, PlayCard("Rhystic Study", typeLine = "Enchantment"))))
        assertEquals(Lane.CREATURES, Lane.of(Piece(5, PlayCard("Dryad Arbor", typeLine = "Land Creature — Forest Dryad"))))
        assertEquals(Lane.CREATURES, Lane.of(Piece(6, PlayCard("Steel Overseer", typeLine = "Artifact Creature — Construct"))))
        assertEquals(Lane.CREATURES, Lane.of(Piece(7, forest, faceDown = true)))
    }

    @Test fun matchingLandsShareAPile() {
        val swamp = PlayCard("Swamp", typeLine = "Basic Land — Swamp")
        val pieces = listOf(
            Piece(1, forest), Piece(2, bear), Piece(3, forest), Piece(4, forest, tapped = true), Piece(5, swamp),
            Piece(6, forest), Piece(7, forest, counters = mapOf("Charge" to 1)),
        )
        val piles = Stacks.of(pieces).map { pile -> pile.map { it.id } }
        assertEquals(listOf(listOf(1, 3, 6), listOf(2), listOf(4), listOf(5), listOf(7)), piles)
    }
}
