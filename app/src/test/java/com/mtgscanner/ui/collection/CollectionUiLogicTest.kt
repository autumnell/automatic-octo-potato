package com.mtgscanner.ui.collection

import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.MoveLine
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.ui.components.rangeWarnings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionUiLogicTest {
    private fun line(name: String) = MoveLine(1, name, "M10", "A", "B")

    @Test fun undoDropsTheAddedLinesAndKeepsTicks() {
        val a = line("Alpha"); val b = line("Beta"); val c = line("Gamma")
        // a and b were there (b ticked); then a second "a" and c were added and undone.
        val (lines, done) = dropMoveLines(listOf(a, b, a, c), setOf(1), listOf(a, c))
        assertEquals(listOf(a, b), lines)
        assertEquals(setOf(1), done)
    }

    @Test fun undoShiftsTicksPastRemovedLines() {
        val a = line("Alpha"); val b = line("Beta"); val c = line("Gamma")
        val (lines, done) = dropMoveLines(listOf(a, b, c), setOf(0, 2), listOf(b))
        assertEquals(listOf(a, c), lines)
        assertEquals(setOf(0, 1), done)
    }

    @Test fun undoOfLinesNotThereChangesNothing() {
        val a = line("Alpha")
        val (lines, done) = dropMoveLines(listOf(a), setOf(0), listOf(line("Other")))
        assertEquals(listOf(a), lines)
        assertEquals(setOf(0), done)
    }

    @Test fun wishlistTargetsOncePerCardAndSkipsOwned() {
        // (name, oracle id): two printings of Bolt, one of Shock which is owned elsewhere, one with no oracle id.
        val missing = listOf("Bolt" to "o1", "Bolt borderless" to "o1", "Shock" to "o2", "Token" to null)
        val got = wishTargets(missing, setOf("o2")) { it.second }
        assertEquals(listOf("Bolt" to "o1"), got)
    }

    private val card = CollectionCard(
        scryfallId = "abc-123", oracleId = null, name = "Lightning Bolt", setCode = "M10", setName = "Magic 2010",
        collectorNumber = "146", rarity = "common", typeLine = "Instant", manaCost = "{R}", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = "Deals 3 damage", tags = "", priceUsd = 2.5, priceUsdFoil = null, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = "Christopher Rush", foil = false, quantity = 1,
        condition = "NM", boxId = 1, altered = false, userTags = "trade", addedAt = 0, updatedAt = 0,
    )

    @Test fun searchMatchesAnyFieldWithPrecomputedText() {
        val hay = BrowseViewModel.searchText(card)
        for (q in listOf("bolt", "INSTANT", "3 damage", "magic 2010", "rush", "trade")) {
            assertTrue(q, BrowseViewModel.matches(card, Filters(query = q), hay))
            assertTrue(q, BrowseViewModel.matches(card, Filters(query = q)))
        }
        assertFalse(BrowseViewModel.matches(card, Filters(query = "counterspell"), hay))
        // A match can't run across two fields.
        assertFalse(BrowseViewModel.matches(card, Filters(query = "boltinstant"), hay))
    }

    @Test fun deckFilterKeepsOnlyThatDecksCards() {
        // The card sits in box 1; deck 9's cards are in row 9.
        assertTrue(BrowseViewModel.matches(card, Filters(), null))
        assertFalse(BrowseViewModel.matches(card, Filters(deckBox = 9), null))
        assertTrue(BrowseViewModel.matches(card.copy(boxId = 9), Filters(deckBox = 9), null))
        assertEquals(1, Filters(deckBox = 9).activeCount)
    }

    @Test fun searchReadsScryfallSyntax() {
        for (q in listOf("t:instant c:r", "bolt mv=1", "-t:creature tag:trade", "s:m10 or s:2xm", "!\"lightning bolt\"")) {
            assertTrue(q, BrowseViewModel.matches(card, Filters(query = q)))
        }
        for (q in listOf("c:u", "t:creature", "\"bolt lightning\"", "usd>3")) {
            assertFalse(q, BrowseViewModel.matches(card, Filters(query = q)))
        }
        // Text that can't be read as syntax is searched as plain text.
        assertFalse(BrowseViewModel.matches(card, Filters(query = "mv>abc")))
    }

    @Test fun boxEditorWarnsWhenMinAboveMax() {
        assertTrue(rangeWarnings(BoxRules(minPrice = 1.0, maxPrice = 5.0)).isEmpty())
        assertEquals(1, rangeWarnings(BoxRules(minPrice = 5.0, maxPrice = 5.0)).size)
        assertEquals(1, rangeWarnings(BoxRules(minCmc = 4, maxCmc = 2)).size)
        assertTrue(rangeWarnings(BoxRules(minCmc = 2, maxCmc = 2)).isEmpty())
        assertEquals(2, rangeWarnings(BoxRules(minPower = 5, maxPower = 1, minToughness = 3, maxToughness = 0)).size)
    }
}
