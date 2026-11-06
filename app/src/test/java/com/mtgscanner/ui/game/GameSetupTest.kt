package com.mtgscanner.ui.game

import com.mtgscanner.game.Game
import com.mtgscanner.game.GameFormat
import com.mtgscanner.game.GameState
import com.mtgscanner.game.TableLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSetupTest {
    @Test fun onlyTwoToFourPlayersPickASeating() {
        assertEquals(listOf(1, 2, 2, 2, 1, 1), (1..6).map { TableLayouts.count(it) })
        assertEquals(0, TableLayouts.valid(5, 1))
        assertEquals(1, TableLayouts.valid(4, 1))
    }

    @Test fun lastSetupKeepsTheSeatingAndReadsOldSaves() {
        val s = LastSetup(GameFormat.COMMANDER, 4, 40, true, layout = 1)
        assertEquals(s, LastSetup.parse(s.encode()))
        // Saved before seatings existed: four fields, the usual seating.
        assertEquals(LastSetup(GameFormat.STANDARD, 2, 20, false, 0), LastSetup.parse("STANDARD|2|20|false"))
    }

    @Test fun seatsFaceTheirPlayers() {
        // One on each side: top turned over, right and left turned in, bottom upright.
        assertEquals(listOf(180, 270, 0, 90), (0 until 4).map { seatRotation(4, it, 1) })
        assertEquals(listOf(90, 270, 270, 90), (0 until 4).map { seatRotation(4, it) })
        assertEquals(listOf(0, 0), (0 until 2).map { seatRotation(2, it, 1) })
    }

    @Test fun restartKeepsTheSeating() {
        val g = GameState.new(GameFormat.COMMANDER, 4).copy(layout = 1)
        assertEquals(1, Game.restart(g).layout)
    }

    @Test fun tourSkipsCommanderDamageWhenItDoesntCount() {
        assertTrue(tourPages(true, 4).any { it.title == "Commander damage" })
        assertTrue(tourPages(false, 2).none { it.title == "Commander damage" })
        assertTrue(tourPages(true, 1).none { it.title == "Commander damage" })
    }
}
