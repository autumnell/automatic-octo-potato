package com.mtgscanner.game

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test fun sixPlayersTrackCommanderDamageFromEveryone() {
        var g = GameState.new(GameFormat.COMMANDER, 6, partners = true)
        assertEquals(6, g.players.size)
        g = Game.commanderDamage(g, 0, 5, 7, partner = true)
        assertEquals(7, g.players[0].damageFrom(5, true))
        assertEquals(33, g.players[0].life)
    }

    @Test fun oldSavesWithShortListsStillWork() {
        val old = GameState.new(GameFormat.COMMANDER, 4).let { g ->
            g.copy(players = g.players.map { it.copy(commanderDamage = List(8) { 0 }) })
        }
        // Damage from a seat the old list had no room for doesn't crash.
        val hit = Game.commanderDamage(old, 0, 3, 4, partner = true)
        assertEquals(4, hit.players[0].damageFrom(3, true))
        assertEquals(12, old.normalized().players[0].commanderDamage.size)
    }

    @Test fun undoKeepsAGameCountedOnce() {
        val g = GameState.new(GameFormat.STANDARD, 2)
        val won = Game.life(g, 1, -20).copy(recorded = true)
        assertTrue(Game.undo(won).recorded)
    }

    @Test fun dayAndNightCanBeUndone() {
        val g = GameState.new(GameFormat.STANDARD, 2)
        val day = Game.dayNight(g, "day")
        assertEquals("day", day.dayNight)
        assertNull(Game.undo(day).dayNight)
    }

    @Test fun statsCountWinsPerPlayer() {
        fun rec(winner: String?, vararg names: String) = GameRecord(GameFormat.COMMANDER, names.toList(), winner, 0, 0, 60_000)
        val stats = GameStats.byPlayer(listOf(rec("Rae", "Rae", "Kenji"), rec("kenji", "rae", "Kenji"), rec("Rae", "Rae", "Sam"), rec(null, "Sam")))
        assertEquals("Rae", stats[0].name)
        assertEquals(2, stats[0].wins)
        assertEquals(3, stats[0].games)
        assertEquals(1, stats.first { it.name == "Kenji" }.wins)
        assertEquals(0, stats.first { it.name == "Sam" }.wins)
        assertEquals(1L, GameStats.averageMinutes(listOf(rec(null, "a"))))
    }

    @Test fun recordNamesTheWinner() {
        val g = Game.life(GameState.new(GameFormat.STANDARD, 2, names = listOf("Rae", "Kenji")), 1, -20)
        val r = g.toRecord()
        assertEquals("Rae", r.winner)
        assertFalse(r.players.isEmpty())
    }

    @Test fun roundsCountFromWhoeverWentFirst() {
        var s = Game.setFirst(GameState.new(GameFormat.STANDARD, 3), 2)
        assertEquals(1, s.turn)
        s = Game.nextTurn(s, now = 0)   // 0
        s = Game.nextTurn(s, now = 1)   // 1
        assertEquals(1, s.turn)
        s = Game.nextTurn(s, now = 2)   // back to 2: round 2
        assertEquals(2, s.active)
        assertEquals(2, s.turn)
    }

    @Test fun passingSkipsAConcededPlayer() {
        var s = Game.setFirst(GameState.new(GameFormat.STANDARD, 3), 0)
        s = Game.concede(s, 1, true, now = 0)
        s = Game.nextTurn(s, now = 1)
        assertEquals(2, s.active)
    }

    @Test fun twoHeadedGiantPoisonLimitIs15() {
        assertEquals(15, GameFormat.TWO_HEADED.poisonLimit)
        val s = Game.poison(GameState.new(GameFormat.TWO_HEADED, 4), 0, 10, now = 0)
        assertNull(s.outReason(0))
        assertTrue(Game.poison(s, 0, 5, now = 10_000).outReason(0) != null)
    }

    @Test fun brawlUsesCommanderTaxButNotCommanderDamage() {
        assertTrue(GameFormat.BRAWL.usesCommander)
        assertEquals(25, GameFormat.BRAWL.life)
    }

    @Test fun countersRecordWhatActuallyChanged() {
        var s = Game.counter(GameState.new(GameFormat.STANDARD, 2), 0, "Energy", 2, now = 0)
        s = Game.counter(s, 0, "Energy", -5, now = 10_000)
        assertEquals(0, s.players[0].energy)
        assertEquals(-2, s.history.last().delta)
    }

    @Test fun undoKeepsNamesColorsAndArt() {
        var s = Game.life(GameState.new(GameFormat.STANDARD, 2), 0, -3, now = 0)
        s = Game.rename(s, 0, "Rae")
        s = Game.recolor(s, 0, 4)
        s = Game.setArt(s, 0, "art")
        val u = Game.undo(s)
        assertEquals(20, u.players[0].life)
        assertEquals("Rae", u.players[0].name)
        assertEquals(4, u.players[0].color)
        assertEquals("art", u.players[0].art)
    }

    @Test fun pickingFirstAndActiveAreUndoSteps() {
        val g = Game.life(GameState.new(GameFormat.STANDARD, 3), 0, -3, now = 0)
        val first = Game.setFirst(g, 2)
        val back = Game.undo(first)
        assertNull(back.active)
        assertEquals(17, back.players[0].life)
        val active = Game.setActive(first, 1)
        assertEquals(2, Game.undo(active).active)
        assertEquals(17, Game.undo(active).players[0].life)
    }

    @Test fun aTapRightAfterATurnChangeIsItsOwnUndoStep() {
        var s = Game.life(GameState.new(GameFormat.STANDARD, 2), 0, -1, now = 1000)
        s = Game.setActive(s, 1)
        assertTrue(with(Game) { s.lastStepHasNoLine() })
        s = Game.life(s, 0, -1, now = 1500)
        assertEquals(18, s.players[0].life)
        // One Undo takes back the second tap only; the turn change stays.
        val back = Game.undo(s)
        assertEquals(19, back.players[0].life)
        assertEquals(1, back.active)
    }

    @Test fun tableWideHistoryLinesHaveNoPlayer() {
        val g = GameState.new(GameFormat.COMMANDER, 4)
        val m = Game.monarch(Game.monarch(g, 1, now = 0), null, now = 10_000)
        assertEquals("No monarch", m.history.last().what)
        assertEquals(-1, m.history.last().player)
    }

    @Test fun rematchMovesFirstPlayerToTheNextSeat() {
        val started = Game.nextTurn(commander, now = 0)
        val first = started.firstSeat!!
        val again = Game.restart(started)
        assertEquals((first + 1) % 4, again.firstSeat)
        assertEquals(again.firstSeat, again.active)
        assertEquals(listOf(40, 40, 40, 40), again.players.map { it.life })
        // No turns tracked: nobody is picked.
        assertNull(Game.restart(commander).active)
    }
}
