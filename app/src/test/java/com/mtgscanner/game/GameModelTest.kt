package com.mtgscanner.game

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameModelTest {
    private val commander = GameState.new(GameFormat.COMMANDER, 4)

    @Test fun startsWithFormatLife() {
        assertEquals(listOf(40, 40, 40, 40), commander.players.map { it.life })
        assertEquals(20, GameState.new(GameFormat.STANDARD, 2).players[0].life)
        assertEquals(30, GameState.new(GameFormat.CUSTOM, 3, life = 30).players[2].life)
    }

    @Test fun quickTapsMergeIntoOneHistoryLineAndOneUndo() {
        var s = Game.life(commander, 0, -1, now = 1000)
        s = Game.life(s, 0, -1, now = 1500)
        s = Game.life(s, 0, -1, now = 2000)
        assertEquals(37, s.players[0].life)
        assertEquals(1, s.history.size)
        assertEquals(-3, s.history[0].delta)
        assertEquals(40, Game.undo(s).players[0].life)
        // A pause starts a new line.
        val later = Game.life(s, 0, -2, now = 10_000)
        assertEquals(2, later.history.size)
        assertEquals(37, Game.undo(later).players[0].life)
    }

    @Test fun commanderDamageAlsoCostsLifeAndKnocksOutAt21() {
        var s = Game.commanderDamage(commander, target = 1, from = 0, delta = 7, now = 0)
        assertEquals(33, s.players[1].life)
        assertEquals(7, s.players[1].damageFrom(0))
        s = Game.commanderDamage(s, 1, 0, 14, now = 10_000)
        assertEquals("21 commander damage", s.outReason(1))
        // Damage from different commanders doesn't add up.
        var t = Game.commanderDamage(commander, 1, 0, 15, now = 0)
        t = Game.commanderDamage(t, 1, 2, 15, now = 10_000)
        assertNull(t.outReason(1))
    }

    @Test fun poisonAndWinner() {
        var s = GameState.new(GameFormat.STANDARD, 2)
        s = Game.poison(s, 1, 10)
        assertEquals("10 poison counters", s.outReason(1))
        assertEquals(0, s.winner)
        assertEquals(0, Game.poison(GameState.new(GameFormat.STANDARD, 2), 0, -1).players[0].poison)
    }

    @Test fun turnsSkipPlayersWhoAreOut() {
        var s = Game.nextTurn(commander, now = 0)
        assertEquals(0, s.active)
        s = Game.life(s, 1, -40, now = 1)
        s = Game.nextTurn(s, now = 10_000)
        assertEquals(2, s.active)
        s = Game.nextTurn(s, now = 20_000)
        s = Game.nextTurn(s, now = 30_000)
        assertEquals(0, s.active)
        assertEquals(2, s.turn)
    }

    @Test fun commanderTaxAndSaving() {
        var s = Game.counter(commander, 0, "Commander cast", 1, now = 0)
        s = Game.counter(s, 0, "Commander cast", 1, now = 10_000)
        assertEquals(4, s.players[0].commanderTax)
        val json = Json { ignoreUnknownKeys = true }
        val back = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), s))
        assertEquals(s.players, back.players)
        assertEquals(emptyList<GameState>(), back.undoStack)
    }
}
