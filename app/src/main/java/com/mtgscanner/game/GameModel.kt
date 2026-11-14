package com.mtgscanner.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/** Ways to play, with their starting life and which trackers matter. */
@Serializable
enum class GameFormat(
    val label: String,
    val life: Int,
    val commanderDamage: Boolean,
    val defaultPlayers: Int,
    val detail: String,
) {
    COMMANDER("Commander", 40, true, 4, "40 life · 21 commander damage"),
    STANDARD("Standard / 60-card", 20, false, 2, "20 life · Standard, Modern, Pioneer…"),
    BRAWL("Brawl", 25, false, 2, "25 life · commander tax, no commander damage"),
    OATHBREAKER("Oathbreaker", 20, false, 4, "20 life"),
    TWO_HEADED("Two-Headed Giant", 30, false, 2, "30 life per team"),
    LIMITED("Draft / Sealed", 20, false, 2, "20 life"),
    CUSTOM("Custom", 20, false, 2, "Pick your own starting life"),
    ;

    /** Has a commander (or oathbreaker) cast from the command zone, so tax matters. */
    val usesCommander: Boolean get() = this == COMMANDER || this == BRAWL || this == OATHBREAKER

    /** Two-Headed Giant teams share a poison total and are out at 15. */
    val poisonLimit: Int get() = if (this == TWO_HEADED) 15 else GameState.POISON_LIMIT
}

@Serializable
data class Player(
    val name: String,
    /** Index into the player colors. */
    val color: Int,
    val life: Int,
    val poison: Int = 0,
    /** Commander damage taken, by [cmdIndex] of the dealer and their commander. */
    val commanderDamage: List<Int> = List(MAX_PLAYERS * 2) { 0 },
    val energy: Int = 0,
    val experience: Int = 0,
    val rad: Int = 0,
    /** Times your commander has been cast from the command zone. Tax is 2 per cast. */
    val commanderCasts: Int = 0,
    val partnerCasts: Int = 0,
    /** Conceded or otherwise out, by hand. */
    val conceded: Boolean = false,
    /** Card art shown behind the life total, or null for a plain panel. */
    val art: String? = null,
    /** The deck they're playing, picked at setup, so its wins and losses are counted; null for none. */
    val deckId: Long? = null,
) {
    val commanderTax: Int get() = commanderCasts * 2
    val partnerTax: Int get() = partnerCasts * 2

    fun damageFrom(from: Int, partner: Boolean = false): Int = commanderDamage.getOrElse(cmdIndex(from, partner)) { 0 }

    companion object {
        const val MAX_PLAYERS = 6
        fun cmdIndex(from: Int, partner: Boolean) = from * 2 + if (partner) 1 else 0
    }
}

/** What happened, for the history list and undo. */
@Serializable
data class GameEvent(val player: Int, val what: String, val delta: Int, val at: Long)

@Serializable
data class GameState(
    val format: GameFormat,
    val players: List<Player>,
    val startingLife: Int = 20,
    /** Track a partner (second commander) for each player. */
    val partners: Boolean = false,
    /** Which seat arrangement the table uses ([TableLayouts]); 0 is the usual one. */
    val layout: Int = 0,
    val turn: Int = 1,
    /** Whose turn it is, or null when turns aren't being tracked. */
    val active: Int? = null,
    val monarch: Int? = null,
    val initiative: Int? = null,
    /** "day", "night", or null before it matters. */
    val dayNight: String? = null,
    /** True once this game has been saved to the stats. */
    val recorded: Boolean = false,
    /** Who took the first turn. A new round starts each time play comes back to them. */
    val firstSeat: Int? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val history: List<GameEvent> = emptyList(),
    /** Earlier states, newest last, for undo. Not saved between runs. */
    @kotlinx.serialization.Transient val undoStack: List<GameState> = emptyList(),
    /** True right after an Undo: the next change gets its own history line and undo step. Not saved. */
    @kotlinx.serialization.Transient val afterUndo: Boolean = false,
) {
    /** Why a player is out, or null while they're still in. */
    fun outReason(i: Int): String? {
        val p = players[i]
        return when {
            p.conceded -> "Conceded"
            p.life <= 0 -> "Out of life"
            p.poison >= format.poisonLimit -> "${format.poisonLimit} poison counters"
            format.commanderDamage && p.commanderDamage.any { it >= COMMANDER_LIMIT } -> "21 commander damage"
            else -> null
        }
    }

    /** Pads lists saved by older versions, which had room for fewer players. */
    fun normalized(): GameState = copy(players = players.map { p ->
        if (p.commanderDamage.size >= Player.MAX_PLAYERS * 2) p
        else p.copy(commanderDamage = p.commanderDamage + List(Player.MAX_PLAYERS * 2 - p.commanderDamage.size) { 0 })
    })

    /** A summary for the stats, with [winner] chosen by the table (or the last player standing). */
    fun toRecord(winner: Int? = this.winner, now: Long = System.currentTimeMillis()) = GameRecord(
        format = format,
        players = players.map { it.name },
        winner = winner?.let { players.getOrNull(it)?.name },
        turns = if (active != null) turn else 0,
        startedAt = startedAt,
        endedAt = now,
        finalLife = players.map { it.life },
        decks = players.map { it.deckId },
    )

    val playersLeft: Int get() = players.indices.count { outReason(it) == null }

    /** The last player standing in a game of two or more. */
    val winner: Int? get() = if (players.size > 1 && playersLeft == 1) players.indices.first { outReason(it) == null } else null

    companion object {
        const val POISON_LIMIT = 10
        const val COMMANDER_LIMIT = 21
        const val MAX_UNDO = 60
        /** Changes to the same thing within this time are merged into one history line. */
        const val MERGE_MS = 2500L

        val NAMES = List(Player.MAX_PLAYERS) { "Player ${it + 1}" }

        fun new(format: GameFormat, count: Int, life: Int = format.life, names: List<String> = NAMES, partners: Boolean = false) = GameState(
            format = format,
            players = List(count.coerceIn(1, Player.MAX_PLAYERS)) { i -> Player(names.getOrElse(i) { NAMES[i] }.ifBlank { NAMES[i] }, i, life) },
            startingLife = life,
            partners = partners,
        )
    }
}

/** Every change to a game goes through here, so it can be undone and shows in the history. */
object Game {
    private fun GameState.record(player: Int, what: String, delta: Int, now: Long, next: GameState): GameState {
        val last = history.lastOrNull()
        // A run of quick taps keeps one line and one undo step, unless something else (a turn change, which has
        // no line of its own) was saved as an undo step since that line: then the newest step already holds it.
        // Nor right after an Undo: the line it went back to is an older change, and joining it would leave the new
        // tap without an undo step of its own.
        val merge = last != null && last.player == player && last.what == what && now - last.at < GameState.MERGE_MS &&
            !lastStepHasNoLine() && !afterUndo
        val merged = if (merge) history.dropLast(1) + last!!.copy(delta = last.delta + delta, at = now)
        else history + GameEvent(player, what, delta, now)
        val stack = if (merge) undoStack else (undoStack + this.copy(undoStack = emptyList())).takeLast(GameState.MAX_UNDO)
        return next.copy(history = merged.takeLast(300), undoStack = stack, afterUndo = false)
    }

    /** True when the newest undo step was saved without a history line (turn order, first player). */
    fun GameState.lastStepHasNoLine(): Boolean {
        val step = undoStack.lastOrNull() ?: return false
        return history.isNotEmpty() && step.history.lastOrNull() == history.last()
    }

    /** Saves the current state as an undo step without a history line. */
    private fun GameState.pushUndo(): GameState =
        copy(undoStack = (undoStack + copy(undoStack = emptyList())).takeLast(GameState.MAX_UNDO))

    private fun GameState.updatePlayer(i: Int, f: (Player) -> Player): GameState =
        copy(players = players.mapIndexed { j, p -> if (j == i) f(p) else p })

    fun life(s: GameState, i: Int, delta: Int, now: Long = System.currentTimeMillis()): GameState =
        s.record(i, "Life", delta, now, s.updatePlayer(i) { it.copy(life = it.life + delta) })

    fun poison(s: GameState, i: Int, delta: Int, now: Long = System.currentTimeMillis()): GameState {
        val p = s.players[i]
        val d = (p.poison + delta).coerceAtLeast(0) - p.poison
        if (d == 0) return s
        return s.record(i, "Poison", d, now, s.updatePlayer(i) { it.copy(poison = it.poison + d) })
    }

    /** Commander damage is also life loss, so life goes down with it. */
    fun commanderDamage(s: GameState, target: Int, from: Int, delta: Int, partner: Boolean = false, now: Long = System.currentTimeMillis()): GameState {
        val idx = Player.cmdIndex(from, partner)
        val p = s.players[target]
        val had = p.commanderDamage.getOrElse(idx) { 0 }
        val d = (had + delta).coerceAtLeast(0) - had
        if (d == 0) return s
        val label = "Commander damage from ${s.players[from].name}" + if (partner) " (partner)" else ""
        return s.record(target, label, d, now, s.updatePlayer(target) {
            val list = if (it.commanderDamage.size > idx) it.commanderDamage else it.commanderDamage + List(idx + 1 - it.commanderDamage.size) { 0 }
            it.copy(life = it.life - d, commanderDamage = list.mapIndexed { k, v -> if (k == idx) v + d else v })
        })
    }

    fun counter(s: GameState, i: Int, kind: String, delta: Int, now: Long = System.currentTimeMillis()): GameState {
        val p = s.players[i]
        val next = when (kind) {
            "Energy" -> p.copy(energy = (p.energy + delta).coerceAtLeast(0))
            "Experience" -> p.copy(experience = (p.experience + delta).coerceAtLeast(0))
            "Rad" -> p.copy(rad = (p.rad + delta).coerceAtLeast(0))
            "Commander cast" -> p.copy(commanderCasts = (p.commanderCasts + delta).coerceAtLeast(0))
            "Partner cast" -> p.copy(partnerCasts = (p.partnerCasts + delta).coerceAtLeast(0))
            else -> p
        }
        if (next == p) return s
        // Record what actually changed, since counters stop at zero.
        val actual = when (kind) {
            "Energy" -> next.energy - p.energy
            "Experience" -> next.experience - p.experience
            "Rad" -> next.rad - p.rad
            "Commander cast" -> next.commanderCasts - p.commanderCasts
            else -> next.partnerCasts - p.partnerCasts
        }
        return s.record(i, kind, actual, now, s.updatePlayer(i) { next })
    }

    fun concede(s: GameState, i: Int, out: Boolean, now: Long = System.currentTimeMillis()): GameState =
        s.record(i, if (out) "Conceded" else "Back in", 0, now, s.updatePlayer(i) { it.copy(conceded = out) })

    // Names, colors and art aren't undo steps; [undo] keeps them as they are now.
    fun rename(s: GameState, i: Int, name: String): GameState = s.updatePlayer(i) { it.copy(name = name.trim().ifEmpty { it.name }) }
    fun recolor(s: GameState, i: Int, color: Int): GameState = s.updatePlayer(i) { it.copy(color = color) }
    fun setArt(s: GameState, i: Int, art: String?): GameState = s.updatePlayer(i) { it.copy(art = art) }

    /** Passes the turn to the next player still in. A new round starts when play comes back to whoever went first. */
    fun nextTurn(s: GameState, now: Long = System.currentTimeMillis()): GameState {
        val n = s.players.size
        val live = (0 until n).filter { s.outReason(it) == null }
        if (live.isEmpty()) return s
        val from = s.active
        if (from == null) {
            val first = live.firstOrNull { it >= (s.firstSeat ?: 0) } ?: live.first()
            return s.record(first, "Turn", 0, now, s.copy(active = first, firstSeat = first, turn = 1))
        }
        val next = (1..n).map { (from + it) % n }.firstOrNull { it in live } ?: from
        // The round's anchor: whoever went first, or the next player still in after them.
        val start = s.firstSeat ?: 0
        val anchor = (0 until n).map { (start + it) % n }.first { it in live }
        val newRound = next == anchor
        // Each pass is its own undo step, even when play comes straight back to the same player.
        return s.copy(history = (s.history + GameEvent(next, "Turn", 0, now)).takeLast(300), undoStack = (s.undoStack + s.copy(undoStack = emptyList())).takeLast(GameState.MAX_UNDO))
            .copy(active = next, turn = s.turn + if (newRound) 1 else 0, afterUndo = false)
    }

    /** Hands the turn to a player without counting a new round. */
    fun setActive(s: GameState, i: Int?): GameState = if (s.active == i) s else s.pushUndo().copy(active = i)

    /** Sets who goes first: turn 1 starts with them, and each new round starts with them. */
    fun setFirst(s: GameState, i: Int): GameState =
        if (s.active == i && s.firstSeat == i && s.turn == 1) s else s.pushUndo().copy(active = i, firstSeat = i, turn = 1)

    fun monarch(s: GameState, i: Int?, now: Long = System.currentTimeMillis()): GameState =
        s.record(i ?: -1, if (i == null) "No monarch" else "Monarch", 0, now, s.copy(monarch = i))

    fun initiative(s: GameState, i: Int?, now: Long = System.currentTimeMillis()): GameState =
        s.record(i ?: -1, if (i == null) "No initiative" else "Initiative", 0, now, s.copy(initiative = i))

    fun dayNight(s: GameState, value: String?, now: Long = System.currentTimeMillis()): GameState =
        if (value == s.dayNight) s
        else s.record(-1, when (value) { "day" -> "It became day"; "night" -> "It became night"; else -> "Day and night off" }, 0, now, s.copy(dayNight = value))

    fun undo(s: GameState): GameState {
        val prev = s.undoStack.lastOrNull() ?: return s
        // A game already saved to the stats stays saved, so undoing and replaying the last hit doesn't count it twice.
        // Names, colors and art aren't undo steps, so they stay as they are now.
        val players = prev.players.mapIndexed { j, p ->
            s.players.getOrNull(j)?.let { now -> p.copy(name = now.name, color = now.color, art = now.art) } ?: p
        }
        return prev.copy(players = players, undoStack = s.undoStack.dropLast(1), recorded = s.recorded, afterUndo = true)
    }

    /**
     * Rematch: same players and format, fresh life totals. When turns were tracked, the next seat after
     * last game's first player goes first.
     */
    fun restart(s: GameState): GameState {
        val first = s.firstSeat?.let { (it + 1) % s.players.size }
        return GameState.new(s.format, s.players.size, s.startingLife, s.players.map { it.name }, s.partners)
            .let { g -> g.copy(players = g.players.mapIndexed { i, p -> p.copy(color = s.players[i].color, art = s.players[i].art, deckId = s.players[i].deckId) }) }
            .let { g -> if (first != null) g.copy(active = first, firstSeat = first, turn = 1) else g }
            .copy(layout = s.layout)
    }
}

/** The ways seats can be arranged on the phone for each player count. Pure, for tests. */
object TableLayouts {
    /** How many arrangements [players] can choose from. */
    fun count(players: Int): Int = if (players in 2..4) 2 else 1

    /** A short name for each arrangement, for TalkBack and the setup screen. */
    fun label(players: Int, layout: Int): String = when {
        players == 2 && layout == 1 -> "Side by side"
        players == 2 -> "Face to face"
        players == 3 && layout == 1 -> "One at the end, two at the sides"
        players == 3 -> "Two at the sides, one at the end"
        players == 4 && layout == 1 -> "One on each side"
        players == 4 -> "Two on each side"
        else -> "Around the table"
    }

    /** A layout that exists for [players]; anything else falls back to the usual one. */
    fun valid(players: Int, layout: Int): Int = if (layout in 0 until count(players)) layout else 0
}

/** One finished game, kept for the stats. */
@Serializable
data class GameRecord(
    val format: GameFormat,
    val players: List<String>,
    /** Null when nobody was picked as the winner. */
    val winner: String?,
    /** Rounds played, or 0 when turns weren't tracked. */
    val turns: Int,
    val startedAt: Long,
    val endedAt: Long,
    val finalLife: List<Int> = emptyList(),
    /** Each seat's deck id, in [players] order; empty for games saved before decks could be picked. */
    val decks: List<Long?> = emptyList(),
) {
    val minutes: Long get() = ((endedAt - startedAt) / 60_000).coerceAtLeast(0)
}

/**
 * Reads the saved games one by one, so a game that no longer reads (a renamed format, say) is set aside instead of
 * hiding every other game. Pure, for tests.
 */
object GameRecordsCodec {
    /** What a saved list held: the games that read, the ones that didn't, and whether it was a list at all. */
    class Read(val records: List<GameRecord>, val unreadable: List<JsonElement>, val notAList: Boolean)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(text: String?): Read {
        if (text == null) return Read(emptyList(), emptyList(), notAList = false)
        val items = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?: return Read(emptyList(), emptyList(), notAList = true)
        val read = mutableListOf<GameRecord>()
        val bad = mutableListOf<JsonElement>()
        items.forEach { e -> runCatching { json.decodeFromJsonElement(GameRecord.serializer(), e) }.onSuccess { read += it }.onFailure { bad += e } }
        return Read(read, bad, notAList = false)
    }
}

/** Wins and games per player name, across saved games. */
data class PlayerStats(val name: String, val games: Int, val wins: Int) {
    val winRate: Int get() = if (games == 0) 0 else wins * 100 / games
}

object GameStats {
    /** Most wins first, then most games. Names match case-insensitively. */
    fun byPlayer(records: List<GameRecord>): List<PlayerStats> {
        val games = LinkedHashMap<String, Pair<String, Int>>()
        val wins = HashMap<String, Int>()
        for (r in records) {
            r.players.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.forEach { n ->
                val k = n.lowercase()
                games[k] = (games[k]?.first ?: n) to ((games[k]?.second ?: 0) + 1)
            }
            r.winner?.trim()?.lowercase()?.let { wins[it] = (wins[it] ?: 0) + 1 }
        }
        return games.map { (k, v) -> PlayerStats(v.first, v.second, wins[k] ?: 0) }
            .sortedWith(compareByDescending<PlayerStats> { it.wins }.thenByDescending { it.games }.thenBy { it.name.lowercase() })
    }

    /** Average length in minutes of games that lasted at least a minute, or null. */
    fun averageMinutes(records: List<GameRecord>): Long? =
        records.map { it.minutes }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.toLong()
}
