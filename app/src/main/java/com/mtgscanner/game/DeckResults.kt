package com.mtgscanner.game

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A deck's games: wins, losses and draws (games saved with nobody picked as the winner). */
data class DeckResult(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0) {
    val games: Int get() = wins + losses + draws
    /** Whole percent of games won, or null before the first game. */
    val winRate: Int? get() = if (games == 0) null else wins * 100 / games
    operator fun plus(o: DeckResult) = DeckResult(wins + o.wins, losses + o.losses, draws + o.draws)
    operator fun minus(o: DeckResult) = DeckResult(wins - o.wins, losses - o.losses, draws - o.draws)
}

/**
 * Win rates for decks. Games from the Game tab count for the deck each seat picked; on top of that, a deck's numbers
 * can be set by hand, saved as the difference from what the games say (so later games still add on).
 */
object DeckResults {
    private const val FILE = "deck_results"

    /** What the saved games say about one deck. A game without a winner counts as a draw for every deck in it. */
    fun tally(records: List<GameRecord>, deckId: Long): DeckResult {
        var w = 0; var l = 0; var d = 0
        for (r in records) {
            // Every seat that played the deck: the same deck at two seats wins if either seat won.
            val seats = r.decks.indices.filter { r.decks[it] == deckId }
            if (seats.isEmpty()) continue
            when {
                r.winner == null -> d++
                seats.any { r.players.getOrNull(it)?.trim().equals(r.winner.trim(), ignoreCase = true) } -> w++
                else -> l++
            }
        }
        return DeckResult(w, l, d)
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun records(ctx: Context): List<GameRecord> =
        ctx.applicationContext.getSharedPreferences("game_records", Context.MODE_PRIVATE).getString("records", null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(GameRecord.serializer()), it) }.getOrNull() }.orEmpty()

    private fun adjust(ctx: Context, deckId: Long): DeckResult {
        val v = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(deckId.toString(), null) ?: return DeckResult()
        val n = v.split(',').map { it.toIntOrNull() ?: 0 }
        return DeckResult(n.getOrElse(0) { 0 }, n.getOrElse(1) { 0 }, n.getOrElse(2) { 0 })
    }

    /** A deck's numbers: its games plus any change made by hand. */
    fun of(ctx: Context, deckId: Long): DeckResult = combine(tally(records(ctx), deckId), adjust(ctx, deckId))

    /**
     * Games plus the hand-set difference, never below zero: if games the difference was worked out against are
     * deleted later, the numbers bottom out instead of going negative. Pure, for tests.
     */
    fun combine(games: DeckResult, byHand: DeckResult): DeckResult = (games + byHand).let {
        DeckResult(it.wins.coerceAtLeast(0), it.losses.coerceAtLeast(0), it.draws.coerceAtLeast(0))
    }

    /** Sets a deck's numbers by hand; games played after this still add to them. */
    fun set(ctx: Context, deckId: Long, result: DeckResult) {
        val diff = result - tally(records(ctx), deckId)
        val p = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (diff == DeckResult()) p.remove(deckId.toString()) else p.putString(deckId.toString(), "${diff.wins},${diff.losses},${diff.draws}")
        p.apply()
    }
}
