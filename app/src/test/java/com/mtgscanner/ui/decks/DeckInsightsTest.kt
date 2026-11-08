package com.mtgscanner.ui.decks

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckInsightsTest {
    private fun land(name: String, n: Int = 1, text: String = "") = InsightCard(name, n, 0.0, "Basic Land — $name", text, "")
    private fun spell(name: String, cmc: Double = 3.0, text: String = "", cost: String = "{2}{G}", n: Int = 1, commander: Boolean = false) =
        InsightCard(name, n, cmc, "Creature — Elf", text, cost, commander)

    private fun filler(count: Int, cmc: Double = 3.0, cost: String = "{2}{G}") = List(count) { spell("Filler $it", cmc, cost = cost) }

    @Test fun plainDeckIsCore() {
        val b = BracketMath.of(filler(60) + land("Forest", 40), BracketMath.GAME_CHANGERS, emptyList())
        assertEquals(2, b.level)
        assertEquals("Core", b.label)
        assertTrue(b.reasons.isEmpty())
    }

    @Test fun aGameChangerOrTwoCardComboMakesItUpgraded() {
        val cards = filler(60) + land("Forest", 39) + spell("Rhystic Study", 3.0)
        assertEquals(3, BracketMath.of(cards, BracketMath.GAME_CHANGERS, emptyList()).level)
        val combo = BracketMath.of(filler(60) + land("Forest", 40), emptySet(), listOf(listOf("Dramatic Reversal", "Isochron Scepter")))
        assertEquals(3, combo.level)
        assertEquals(listOf("1 two-card combo"), combo.reasons)
    }

    @Test fun onlyCombosThatEndGamesCount() {
        assertTrue(BracketMath.endsGames(listOf("Infinite colorless mana")))
        assertTrue(BracketMath.endsGames(listOf("Win the game")))
        assertTrue(!BracketMath.endsGames(listOf("Draw a card", "Add one mana")))
        assertTrue(!BracketMath.endsGames(emptyList()))
    }

    @Test fun manyGameChangersOrLandDenialMakeItOptimized() {
        val gc = listOf("Demonic Tutor", "Vampiric Tutor", "Rhystic Study", "Cyclonic Rift").map { spell(it) }
        assertEquals(4, BracketMath.of(filler(56) + gc + land("Forest", 40), BracketMath.GAME_CHANGERS, null).level)
        val mld = spell("Armageddon", 4.0, "Destroy all lands.")
        val b = BracketMath.of(filler(59) + mld + land("Forest", 40), emptySet(), null)
        assertEquals(4, b.level)
        assertEquals(listOf("Armageddon"), b.massLandDenial)
    }

    @Test fun scryfallTagListsDecideWhenPresent() {
        val tags = BracketTags.parse("""{"tutor":["Fabricate"],"mld":["Armageddon"]}""")
        val cards = filler(57) + spell("Fabricate", 3.0, "Search your library for an artifact card.") +
            spell("Diabolic Tutor", 4.0, "Search your library for a card.") + spell("Armageddon", 4.0, "Destroy all lands.") + land("Forest", 40)
        val b = BracketMath.of(cards, emptySet(), null, tags)
        assertEquals(listOf("Fabricate"), b.tutors)
        assertEquals(listOf("Armageddon"), b.massLandDenial)
    }

    @Test fun emptyTagListsFallBackToCardText() {
        assertEquals(null, BracketTags.parse("""{"tutor":[],"mld":["Armageddon"]}"""))
        assertEquals(null, BracketTags.parse("""{"mld":["Armageddon"]}"""))
    }

    @Test fun taggedRampIsNotATutorButCreatureOrLandIs() {
        val tags = BracketTags.parse("""{"tutor":["Cultivate","Traverse the Ulvenwald"],"mld":["Armageddon"]}""")
        val cards = filler(58) +
            spell("Cultivate", 3.0, "Search your library for up to two basic land cards, reveal those cards, put one onto the battlefield tapped and the other into your hand, then shuffle.") +
            spell("Traverse the Ulvenwald", 1.0, "Search your library for a basic land card, reveal it, put it into your hand, then shuffle.\nDelirium — If there are four or more card types among cards in your graveyard, instead search your library for a creature or land card, reveal it, put it into your hand, then shuffle.") +
            land("Forest", 40)
        assertEquals(listOf("Traverse the Ulvenwald"), BracketMath.of(cards, emptySet(), null, tags).tutors)
    }

    @Test fun rampIsNotATutor() {
        assertEquals(false, BracketMath.isTutor("Search your library for a Plains, Island, Swamp, or Mountain card, put it onto the battlefield, then shuffle."))
        assertEquals(false, BracketMath.isTutor("Search your library for up to two basic land cards, reveal those cards, put one onto the battlefield tapped and the other into your hand, then shuffle."))
        assertEquals(false, BracketMath.isTutor("Search your library for a Forest or Plains card, put it onto the battlefield, then shuffle."))
        assertEquals(true, BracketMath.isTutor("Search your library for an artifact card, reveal it, put it into your hand, then shuffle."))
        assertEquals(true, BracketMath.isTutor("Search your library for a card, put that card into your hand, then shuffle."))
    }

    @Test fun destroyingOneChosenTypeIsNotLandDenial() {
        val season = spell(
            "Season of Gathering", 6.0,
            "Choose up to five {P} worth of modes. You may choose the same mode more than once.\n" +
                "{P} — Put a +1/+1 counter on a creature you control. It gains vigilance and trample until end of turn.\n" +
                "{P}{P} — Choose artifact or enchantment. Destroy all permanents of the chosen type.\n" +
                "{P}{P}{P} — Draw cards equal to the greatest power among creatures you control.",
        )
        val jokulhaups = spell("Jokulhaups", 6.0, "Destroy all artifacts, creatures, and lands. They can't be regenerated.")
        val b = BracketMath.of(filler(58) + season + jokulhaups + land("Forest", 40), emptySet(), null)
        assertEquals(listOf("Jokulhaups"), b.massLandDenial)
    }

    @Test fun tutorsAndExtraTurnsAreFoundByText() {
        val tutors = List(4) { spell("Tutor $it", 2.0, "Search your library for a card, put it into your hand, then shuffle.") }
        val ramp = spell("Rampant Growth", 2.0, "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
        val turns = List(2) { spell("Time $it", 6.0, "Take an extra turn after this one.") }
        val b = BracketMath.of(filler(54) + tutors + ramp + turns + land("Forest", 39), emptySet(), emptyList())
        assertEquals(4, b.tutors.size)
        assertEquals(2, b.extraTurns.size)
        assertEquals(3, b.level)
    }

    @Test fun twoFacedGameChangersMatchByFrontName() {
        val b = BracketMath.of(filler(60) + spell("Tergrid, God of Fright // Tergrid's Lantern"), setOf("Tergrid, God of Fright // Tergrid's Lantern"), emptyList())
        assertEquals(1, b.gameChangers.size)
    }

    @Test fun karstenLandCountForCommander() {
        // 63 spells at 3 mana and a 3-mana commander: 31.42 + 3.13 × 3 = 40.8, so 41 lands.
        val cards = filler(63) + spell("Lead", 3.0, commander = true) + land("Forest", 36)
        val m = ManaBaseMath.of(cards, setOf('G'))
        assertEquals(36, m.lands)
        assertEquals(41, m.suggested)
        assertEquals(listOf("Add 5 lands"), m.advice.take(1))
        assertEquals(36, m.sources['G'])
    }

    @Test fun cheapRampLowersTheCount() {
        val ramp = List(10) { spell("Signet $it", 2.0, "{T}: Add {G}.") }
        val m = ManaBaseMath.of(filler(53) + ramp + spell("Lead", 3.0, commander = true) + land("Forest", 36), setOf('G'))
        // Average (53×3 + 10×2 + 3) / 64 = 2.84; 31.42 + 8.89 − 2.8 = 37.5, so 38 (rounded).
        assertEquals(38, m.suggested)
    }

    @Test fun shortColorIsCalledOut() {
        val green = filler(40)
        val blue = filler(23, cost = "{2}{U}").mapIndexed { i, c -> c.copy(name = "Blue $i") }
        val cards = green + blue + land("Forest", 30) + land("Island", 6) + InsightCard("Command Tower", 1, 0.0, "Land", "{T}: Add one mana of any color in your commander's color identity.", "")
        val m = ManaBaseMath.of(cards, setOf('G', 'U'))
        assertEquals(31, m.sources['G'])
        assertEquals(7, m.sources['U'])
        assertTrue(m.advice.any { it.endsWith("blue sources") })
    }

    @Test fun dualAndFetchLandsCountForBothColors() {
        val shock = InsightCard("Breeding Pool", 1, 0.0, "Land — Forest Island", "({T}: Add {G} or {U}.)", "")
        val fetch = InsightCard("Misty Rainforest", 1, 0.0, "Land", "{T}, Pay 1 life, Sacrifice Misty Rainforest: Search your library for a Forest or Island card, put it onto the battlefield, then shuffle.", "")
        assertEquals(setOf('G', 'U'), ManaBaseMath.colorsOf(shock, setOf('G', 'U')))
        assertEquals(setOf('G', 'U'), ManaBaseMath.colorsOf(fetch, setOf('G', 'U', 'B')))
    }

    @Test fun goldfishIsSeededAndSensible() {
        val cards = filler(62) + spell("Lead", 4.0, commander = true) + land("Forest", 37)
        val a = GoldfishMath.of(cards, games = 2000, random = Random(7))!!
        val b = GoldfishMath.of(cards, games = 2000, random = Random(7))!!
        assertEquals(a, b)
        // 37 lands in 99: about 87% of 7s hold 2 to 5 lands; turn 1 needs one land in 7 (about 96%).
        assertTrue(a.keepable in 0.8..0.93)
        assertTrue(a.landDrop[0] in 0.93..0.99)
        assertTrue(a.landDrop[0] >= a.landDrop[1] && a.landDrop[1] >= a.landDrop[2] && a.landDrop[2] >= a.landDrop[3])
        assertEquals(4, a.commanderCost)
        assertEquals(a.landDrop[3], a.commanderOnCurve!!, 1e-9)
    }

    @Test fun goldfishOnTheDrawSeesMoreCards() {
        val cards = filler(36) + land("Forest", 24)
        val play = GoldfishMath.of(cards, games = 3000, random = Random(3), onPlay = true)!!
        val draw = GoldfishMath.of(cards, games = 3000, random = Random(3), onPlay = false)!!
        assertTrue(draw.landDrop[3] > play.landDrop[3])
        assertNull(play.commanderOnCurve)
    }

    @Test fun goldfishNeedsADeck() {
        assertNull(GoldfishMath.of(filler(10)))
    }

    @Test fun compareSplitsCardsAndCopies() {
        val d = DeckCompare.diff(
            listOf("Sol Ring" to 1, "Forest" to 10, "Fire // Ice" to 1),
            listOf("Sol Ring" to 1, "Forest" to 8, "Island" to 2, "Fire/Ice" to 1),
        )
        assertEquals(listOf("Forest" to 2), d.onlyA)
        assertEquals(listOf("Island" to 2), d.onlyB)
        assertEquals(listOf("Fire // Ice", "Forest", "Sol Ring"), d.both)
        assertNotNull(d)
    }
}
