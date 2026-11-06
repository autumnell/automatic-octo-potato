package com.mtgscanner.ui.decks.playtest

import kotlin.random.Random

/** A card as the playtest table knows it: enough to draw it and to let the simple opponent play it. */
data class PlayCard(
    val name: String,
    val imageUrl: String? = null,
    val typeLine: String = "",
    val cmc: Double = 0.0,
    val power: String? = null,
    val toughness: String? = null,
    val token: Boolean = false,
) {
    val isLand: Boolean get() = "Land" in typeLine
    val isCreature: Boolean get() = "Creature" in typeLine
    /** Instants and sorceries go to the graveyard when cast; everything else stays on the battlefield. */
    val isPermanent: Boolean get() = typeLine.isNotBlank() && "Instant" !in typeLine && "Sorcery" !in typeLine
}

/**
 * A deck to play: the cards shuffled into the library, and commanders that start in the command zone. [format] is the
 * deck's format id ("modern", "commander"; "" when none is set), which sets the starting life.
 */
data class PlayDeck(val name: String, val cards: List<PlayCard>, val commanders: List<PlayCard> = emptyList(), val format: String = "")

/**
 * The three rows of a side's battlefield: creatures nearest the opponent, then artifacts, enchantments and the other
 * permanents, then lands nearest you.
 */
enum class Lane(val label: String) {
    CREATURES("Creatures"), OTHERS("Artifacts and enchantments"), LANDS("Lands");

    companion object {
        /** A creature land counts as a creature; a face-down card is a 2/2 creature. */
        fun of(p: Piece): Lane = when {
            p.faceDown || p.card.isCreature -> CREATURES
            p.card.isLand -> LANDS
            else -> OTHERS
        }
    }
}

/** Matching lands shown as one pile on the table. Pure, for tests. */
object Stacks {
    /**
     * [pieces] in piles: plain lands (no counters, face up, not tokens) with the same name and the same tapped state
     * share a pile; everything else is a pile of one. Piles keep the order their first card had.
     */
    fun of(pieces: List<Piece>): List<List<Piece>> {
        val piles = LinkedHashMap<Any, MutableList<Piece>>()
        pieces.forEach { p ->
            val plain = p.card.isLand && !p.card.isCreature && !p.card.token && !p.faceDown && p.counters.isEmpty()
            val key: Any = if (plain) "${p.card.name}|${p.tapped}" else p.id
            piles.getOrPut(key) { mutableListOf() } += p
        }
        return piles.values.toList()
    }
}

enum class Zone(val label: String) {
    LIBRARY("Library"), HAND("Hand"), BATTLEFIELD("Battlefield"), GRAVEYARD("Graveyard"), EXILE("Exile"), COMMAND("Command zone"),
}

/** One card on the table, with an id that stays the same as it moves between zones. */
data class Piece(
    val id: Int,
    val card: PlayCard,
    val tapped: Boolean = false,
    /** Counters by kind ("+1/+1", "Loyalty"…), never zero. */
    val counters: Map<String, Int> = emptyMap(),
    val faceDown: Boolean = false,
    /** A creature that came in this turn: it can't attack yet (the simple opponent respects this). */
    val sick: Boolean = false,
) {
    /** Power with +1/+1 and -1/-1 counters; null when the card has none printed (or "*"). */
    val power: Int? get() = card.power?.toIntOrNull()?.let { it + (counters[PLUS] ?: 0) - (counters[MINUS] ?: 0) }
    val toughness: Int? get() = card.toughness?.toIntOrNull()?.let { it + (counters[PLUS] ?: 0) - (counters[MINUS] ?: 0) }

    companion object {
        const val PLUS = "+1/+1"
        const val MINUS = "-1/-1"
    }
}

/** One player's side of the table. */
data class Side(
    val name: String,
    val zones: Map<Zone, List<Piece>> = Zone.entries.associateWith { emptyList() },
    val life: Int = 20,
    val poison: Int = 0,
    val mulligans: Int = 0,
    /** Times the commander was cast from the command zone, for the tax (2 more each time). */
    val commanderCasts: Int = 0,
    val landPlayed: Boolean = false,
) {
    operator fun get(z: Zone): List<Piece> = zones[z].orEmpty()
    fun with(z: Zone, list: List<Piece>): Side = copy(zones = zones + (z to list))
    fun find(id: Int): Pair<Zone, Piece>? = Zone.entries.firstNotNullOfOrNull { z -> this[z].firstOrNull { it.id == id }?.let { z to it } }
}

/** The whole table. Every change makes a new one, so Undo is just the previous table. */
data class Table(
    val sides: List<Side>,
    /** Whose turn it is. */
    val active: Int = 0,
    /** Turn number for the player who went first; it goes up when play comes back to them. */
    val turn: Int = 1,
    val nextId: Int = 1,
    /** What happened, newest last; the opponent's plays are written here. */
    val log: List<String> = emptyList(),
) {
    val hasOpponent: Boolean get() = sides.size > 1
}

/** Every move on the playtest table. Pure, so the rules can be tested with a seeded [Random]. */
object Playtest {
    const val HAND = 7

    /**
     * Life for a two-player game of the deck's format: Commander 40, PDH 30, Brawl 25, Duel Commander, Oathbreaker
     * and the constructed formats 20. A deck with no format starts at 40 when it has a commander.
     */
    fun startingLife(deck: PlayDeck): Int = when (deck.format) {
        "commander" -> 40
        "paupercommander" -> 30
        "brawl", "standardbrawl" -> 25
        "" -> if (deck.commanders.isNotEmpty()) 40 else 20
        else -> 20
    }

    /** Shuffles each deck, deals 7 and puts commanders in the command zone. You go first. */
    fun start(you: PlayDeck, opponent: PlayDeck?, random: Random): Table {
        var next = 1
        fun side(d: PlayDeck): Side {
            val library = d.cards.shuffled(random).map { Piece(next++, it) }
            val command = d.commanders.map { Piece(next++, it) }
            val n = minOf(HAND, library.size)
            return Side(d.name, life = startingLife(d))
                .with(Zone.HAND, library.take(n)).with(Zone.LIBRARY, library.drop(n)).with(Zone.COMMAND, command)
        }
        val sides = listOfNotNull(side(you), opponent?.let(::side))
        return Table(sides, nextId = next)
    }

    private fun Table.side(i: Int, f: (Side) -> Side): Table = copy(sides = sides.mapIndexed { j, s -> if (j == i) f(s) else s })

    fun draw(t: Table, side: Int, n: Int = 1): Table = t.side(side) { s ->
        val k = minOf(n, s[Zone.LIBRARY].size)
        s.with(Zone.HAND, s[Zone.HAND] + s[Zone.LIBRARY].take(k)).with(Zone.LIBRARY, s[Zone.LIBRARY].drop(k))
    }

    fun shuffle(t: Table, side: Int, random: Random): Table = t.side(side) { it.with(Zone.LIBRARY, it[Zone.LIBRARY].shuffled(random)) }

    /** Hand and library shuffled together and a new 7 dealt; put cards on the bottom yourself after. */
    fun mulligan(t: Table, side: Int, random: Random): Table = t.side(side) { s ->
        val all = (s[Zone.HAND] + s[Zone.LIBRARY]).shuffled(random)
        val n = minOf(HAND, all.size)
        s.with(Zone.HAND, all.take(n)).with(Zone.LIBRARY, all.drop(n)).copy(mulligans = s.mulligans + 1)
    }

    /**
     * Whether the first mulligan is free, as in Commander games: Commander, PDH and Brawl decks, and a deck with no
     * format that has a commander.
     */
    fun freeMulligan(deck: PlayDeck): Boolean = when (deck.format) {
        "commander", "paupercommander", "brawl", "standardbrawl" -> true
        "" -> deck.commanders.isNotEmpty()
        else -> false
    }

    /** Cards to put on the bottom when keeping, by the London mulligan: one for each mulligan but a free one. */
    fun toBottom(mulligans: Int, free: Boolean): Int = (mulligans - if (free) 1 else 0).coerceIn(0, HAND)

    /** Puts [ids] from the hand on the bottom of the library, in that order. */
    fun bottom(t: Table, side: Int, ids: List<Int>): Table =
        ids.fold(t) { acc, id -> if (acc.sides[side][Zone.HAND].any { it.id == id }) move(acc, side, id, Zone.LIBRARY, bottom = true) else acc }

    /**
     * Moves a card to [to], on top (or the bottom of the library with [bottom]). Leaving the battlefield untaps it
     * and drops its counters; a token that leaves the battlefield is gone, as in the rules.
     */
    fun move(t: Table, side: Int, id: Int, to: Zone, bottom: Boolean = false): Table = t.side(side) { s ->
        val (from, p) = s.find(id) ?: return@side s
        val removed = s.with(from, s[from].filterNot { it.id == id })
        if (p.card.token && from == Zone.BATTLEFIELD && to != Zone.BATTLEFIELD) return@side removed
        val moved = when {
            to == Zone.BATTLEFIELD && from != Zone.BATTLEFIELD -> p.copy(tapped = false, sick = p.card.isCreature)
            to != Zone.BATTLEFIELD -> p.copy(tapped = false, counters = emptyMap(), faceDown = false, sick = false)
            else -> p
        }
        val list = removed[to]
        // The library's first card is its top; elsewhere new cards go at the end, where they show last.
        val placed = when {
            to == Zone.LIBRARY && !bottom -> listOf(moved) + list
            else -> list + moved
        }
        val out = removed.with(to, placed)
        if (from == Zone.COMMAND && to == Zone.BATTLEFIELD) out.copy(commanderCasts = out.commanderCasts + 1) else out
    }

    fun toggleTap(t: Table, side: Int, id: Int): Table = t.side(side) { s ->
        s.with(Zone.BATTLEFIELD, s[Zone.BATTLEFIELD].map { if (it.id == id) it.copy(tapped = !it.tapped) else it })
    }

    fun flip(t: Table, side: Int, id: Int): Table = t.side(side) { s ->
        s.with(Zone.BATTLEFIELD, s[Zone.BATTLEFIELD].map { if (it.id == id) it.copy(faceDown = !it.faceDown) else it })
    }

    /** Adds [delta] counters of [kind]; a count that reaches zero is removed. */
    fun counter(t: Table, side: Int, id: Int, kind: String, delta: Int): Table = t.side(side) { s ->
        s.with(Zone.BATTLEFIELD, s[Zone.BATTLEFIELD].map { p ->
            if (p.id != id) p
            else {
                val n = (p.counters[kind] ?: 0) + delta
                p.copy(counters = if (n == 0) p.counters - kind else p.counters + (kind to n))
            }
        })
    }

    /** [count] copies of a token onto the battlefield, untapped. */
    fun token(t: Table, side: Int, card: PlayCard, count: Int = 1): Table {
        var next = t.nextId
        val made = List(count.coerceIn(1, 99)) { Piece(next++, card.copy(token = true), sick = card.isCreature) }
        return t.side(side) { it.with(Zone.BATTLEFIELD, it[Zone.BATTLEFIELD] + made) }.copy(nextId = next)
    }

    fun life(t: Table, side: Int, delta: Int): Table = t.side(side) { it.copy(life = it.life + delta) }

    fun poison(t: Table, side: Int, delta: Int): Table = t.side(side) { it.copy(poison = (it.poison + delta).coerceAtLeast(0)) }

    /** Untaps everything on [side] and clears summoning sickness and the land drop. */
    fun untap(t: Table, side: Int): Table = t.side(side) { s ->
        s.with(Zone.BATTLEFIELD, s[Zone.BATTLEFIELD].map { it.copy(tapped = false, sick = false) }).copy(landPlayed = false)
    }

    /**
     * Passes the turn: play moves to the next side, which untaps and draws. With one side, it's your next turn.
     * The turn number goes up when play comes back to the first player.
     */
    fun nextTurn(t: Table): Table {
        val nextActive = (t.active + 1) % t.sides.size
        val turned = t.copy(active = nextActive, turn = if (nextActive == 0) t.turn + 1 else t.turn)
        return draw(untap(turned, nextActive), nextActive)
    }

    fun logged(t: Table, line: String): Table = t.copy(log = (t.log + line).takeLast(50))
}
