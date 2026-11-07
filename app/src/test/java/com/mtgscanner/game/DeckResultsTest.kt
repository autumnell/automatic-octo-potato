package com.mtgscanner.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeckResultsTest {
    private fun game(players: List<String>, decks: List<Long?>, winner: String?) =
        GameRecord(GameFormat.COMMANDER, players, winner, 0, 0L, 0L, decks = decks)

    @Test fun countsWinsLossesAndDrawsForTheDecksSeat() {
        val records = listOf(
            game(listOf("Rae", "Sam"), listOf(7L, 9L), "Rae"),
            game(listOf("Rae", "Sam"), listOf(7L, 9L), "sam"),
            game(listOf("Sam", "Rae"), listOf(9L, 7L), "Rae"),
            game(listOf("Rae", "Sam"), listOf(7L, null), null),
            // Older games without decks don't count.
            game(listOf("Rae", "Sam"), emptyList(), "Rae"),
        )
        assertEquals(DeckResult(2, 1, 1), DeckResults.tally(records, 7L))
        assertEquals(DeckResult(1, 2, 0), DeckResults.tally(records, 9L))
        assertEquals(DeckResult(), DeckResults.tally(records, 1L))
    }

    @Test fun sameDeckAtTwoSeatsWinsIfEitherWon() {
        val records = listOf(game(listOf("Rae", "Sam"), listOf(7L, 7L), "Sam"))
        assertEquals(DeckResult(1, 0, 0), DeckResults.tally(records, 7L))
    }

    @Test fun handSetNumbersNeverGoNegative() {
        // Set to 2-0-0 over a 5-0-0 tally (difference -3), then those games were deleted.
        assertEquals(DeckResult(0, 0, 0), DeckResults.combine(DeckResult(), DeckResult(-3, 0, 0)))
        assertEquals(DeckResult(2, 1, 0), DeckResults.combine(DeckResult(5, 1, 0), DeckResult(-3, 0, 0)))
    }

    @Test fun winRate() {
        assertNull(DeckResult().winRate)
        assertEquals(62, DeckResult(5, 3, 0).winRate)
    }

    @Test fun recordKeepsSeatDecks() {
        val g = GameState.new(GameFormat.COMMANDER, 2, names = listOf("Rae", "Sam"))
        val r = g.copy(players = g.players.mapIndexed { i, p -> p.copy(deckId = if (i == 0) 4L else null) }).toRecord(winner = 0, now = 1L)
        assertEquals(listOf(4L, null), r.decks)
        assertEquals("Rae", r.winner)
    }
}
