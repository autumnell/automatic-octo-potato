package com.mtgscanner.ui.decks.playtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.CommanderRules
import com.mtgscanner.data.DeckFormats
import com.mtgscanner.ui.decks.rules
import com.mtgscanner.util.DeckLine
import com.mtgscanner.data.DeckCheck
import com.mtgscanner.data.DeckCommanders
import com.mtgscanner.data.PreconEntry
import com.mtgscanner.data.Precons
import com.mtgscanner.ui.decks.DeckCardInfo
import com.mtgscanner.ui.decks.DeckNames
import com.mtgscanner.ui.decks.deckCardInfo
import com.mtgscanner.util.DecklistParser
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Who the deck plays against, as picked on the setup screen. */
sealed interface Opponent {
    val name: String
    data class OwnDeck(val id: Long, override val name: String) : Opponent
    data class Precon(val entry: PreconEntry) : Opponent { override val name: String get() = entry.name }
}

data class PlaytestUi(
    /** The deck being tested, once its cards are looked up. */
    val deck: PlayDeck? = null,
    val loading: Boolean = true,
    /** Looking up the opponent's cards. */
    val opponentLoading: Boolean = false,
    /** Null on the setup screen; the game once it's started. */
    val table: Table? = null,
    /** Earlier tables, newest last, for Undo. */
    val history: List<Table> = emptyList(),
    val pilot: Pilot = SimplePilot,
    val message: String? = null,
    /** The game has started and you're choosing to keep your opening hand or mulligan. */
    val opening: Boolean = false,
)

/**
 * The playtest for one deck: a setup step to pick an opponent (none, another deck of yours or a precon), then the
 * table. Every move goes through [act] so it can be undone.
 */
class PlaytestViewModel(private val repo: CollectionRepository, private val deckId: Long) : ViewModel() {
    private val _ui = MutableStateFlow(PlaytestUi())
    val ui: StateFlow<PlaytestUi> = _ui
    private val random = Random(System.nanoTime())
    /** Set when the deck's cards couldn't be looked up (offline). */
    private var lookupFailed = false

    /** Your other decks of the same format, to play against. */
    val otherDecks: StateFlow<List<Opponent.OwnDeck>> = repo.observeDecks()
        .map { all ->
            val format = all.firstOrNull { it.id == deckId }?.format.orEmpty()
            all.filter { it.id != deckId && it.text.isNotBlank() && PlaytestFormats.sameFormat(format, it.format) }
                .map { Opponent.OwnDeck(it.id, it.name.ifBlank { "Deck" }) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** MTGJSON's precons, commander decks first; null while loading. */
    val precons = MutableStateFlow<List<PreconEntry>?>(null)

    init {
        viewModelScope.launch {
            val deck = loadOwn(deckId)
            val why = when {
                deck != null -> null
                lookupFailed -> "Couldn't look up the cards. Check the connection and open the playtest again"
                else -> "Couldn't load this deck"
            }
            _ui.update { it.copy(deck = deck, loading = false, message = why) }
        }
    }

    fun loadPrecons() {
        if (!precons.value.isNullOrEmpty()) return
        viewModelScope.launch {
            precons.value = Precons.list(repo.appContext).orEmpty().sortedByDescending { it.isCommander }
        }
    }

    /** A saved deck as cards to play: commanders apart, played sections only, one entry per copy. */
    private suspend fun loadOwn(id: Long): PlayDeck? {
        val d = repo.observeDecks().first().firstOrNull { it.id == id } ?: return null
        val lines = DecklistParser.parse(d.text)
        val leads = (DeckCommanders.decode(d.commanders).takeUnless { it.isEmpty } ?: DeckCommanders.fromLines(lines)).commanders
        val played = lines.filter { it.section in DeckCheck.PLAYED }.map { it.name to it.quantity }
        // Without the cards' details nothing is a land and the opponent plays blank cards, so don't start at all.
        val info = com.mtgscanner.util.suspendRunCatching { repo.deckCardInfo(played.map { it.first } + leads) }.getOrNull()
            ?.takeIf { it.isNotEmpty() || played.isEmpty() } ?: run { lookupFailed = true; return null }
        return build(d.name.ifBlank { "Deck" }, played, leads, info, d.format)
    }

    /**
     * A precon as cards to play in [format]. Its cards are checked against the format first: an illegal precon gives
     * null and [illegal] says why.
     */
    private suspend fun loadPrecon(entry: PreconEntry, format: String, illegal: (String) -> Unit): PlayDeck? {
        val p = Precons.deck(repo.appContext, entry) ?: return null
        val leads = p.commanders.map { it.name }
        val played = p.cards.filter { it.section != "Sideboard" }.map { it.name to it.count }
        val info = com.mtgscanner.util.suspendRunCatching { repo.deckCardInfo(p.cards.map { it.name }, format) }.getOrDefault(emptyMap())
        if (format.isNotEmpty()) {
            val lines = p.cards.map { DeckLine(it.count, it.name, section = it.section) }
            val issues = CommanderRules.issues(lines, format, DeckCommanders(leads)) { n -> info[DeckNames.key(n)]?.rules() }
            if (!issues.isEmpty) {
                illegal("${entry.name} isn't legal in ${DeckFormats.of(format).label}")
                return null
            }
        }
        return build(entry.name, played, leads, info, format)
    }

    private fun build(name: String, lines: List<Pair<String, Int>>, leads: List<String>, info: Map<String, DeckCardInfo>, format: String): PlayDeck {
        fun card(n: String) = info[DeckNames.key(n)].toPlayCard(n)
        val leadKeys = leads.map { DeckNames.key(it) }.toSet()
        // A commander listed in the deck too is taken out once per commander.
        val cards = lines.flatMap { (n, q) ->
            val k = DeckNames.key(n)
            List(if (k in leadKeys) (q - 1).coerceAtLeast(0) else q) { card(n) }
        }
        return PlayDeck(name, cards, leads.map(::card), format)
    }

    /** Starts the game against [opponent] (null to play alone). */
    fun start(opponent: Opponent?, pilot: Pilot) {
        val deck = _ui.value.deck ?: return
        _ui.update { it.copy(opponentLoading = opponent != null, pilot = pilot) }
        viewModelScope.launch {
            var refused: String? = null
            val opp = when (opponent) {
                null -> null
                is Opponent.OwnDeck -> loadOwn(opponent.id)
                is Opponent.Precon -> loadPrecon(opponent.entry, deck.format) { why -> refused = why }
            }
            refused?.let { why ->
                _ui.update { it.copy(opponentLoading = false, message = why) }
                return@launch
            }
            if (opponent != null && (opp == null || opp.cards.isEmpty())) {
                _ui.update { it.copy(opponentLoading = false, message = "Couldn't load ${opponent.name}") }
                return@launch
            }
            _ui.update { it.copy(opponentLoading = false, table = Playtest.start(deck, opp, random), history = emptyList(), opening = true) }
        }
    }

    /** Applies one move, keeping the table before it for Undo. */
    fun act(move: (Table) -> Table) {
        val t = _ui.value.table ?: return
        val next = move(t)
        if (next == t) return
        _ui.update { it.copy(table = next, history = (it.history + t).takeLast(UNDO_STEPS)) }
    }

    /** Moves that need the shared random. */
    fun shuffle(side: Int) = act { Playtest.shuffle(it, side, random) }
    fun mulligan(side: Int) = act { Playtest.mulligan(it, side, random) }

    /**
     * Ends the turn. Against an opponent that plays itself, its whole turn happens here and play comes back to you,
     * all as one step for Undo.
     */
    fun nextTurn() = act { t ->
        val passed = Playtest.nextTurn(t)
        val pilot = _ui.value.pilot
        if (passed.active != 0 && pilot != HandPilot) Playtest.nextTurn(pilot.takeTurn(passed, passed.active)) else passed
    }

    /** A new opening 7 before the game starts; not a step for Undo. */
    fun mulliganOpening() = _ui.update { u -> u.copy(table = u.table?.let { Playtest.mulligan(it, 0, random) }) }

    /** Cards to put on the bottom when you keep now. */
    fun toBottom(): Int {
        val u = _ui.value
        val deck = u.deck ?: return 0
        return Playtest.toBottom(u.table?.sides?.get(0)?.mulligans ?: 0, Playtest.freeMulligan(deck))
    }

    /** Keeps the hand, with [bottom] (the hand's card ids) put on the bottom, and starts play. */
    fun keep(bottom: List<Int>) = _ui.update { u ->
        val t = u.table ?: return@update u
        val m = t.sides[0].mulligans
        val kept = Playtest.bottom(t, 0, bottom)
        val line = if (m == 0) "You kept 7" else "You kept ${kept.sides[0][Zone.HAND].size} after ${if (m == 1) "1 mulligan" else "$m mulligans"}"
        u.copy(table = Playtest.logged(kept, line), history = emptyList(), opening = false)
    }

    fun undo() {
        val h = _ui.value.history
        if (h.isEmpty()) return
        _ui.update { it.copy(table = h.last(), history = h.dropLast(1)) }
    }

    /** Back to the opponent picker with the same deck. */
    fun restart() = _ui.update { it.copy(table = null, history = emptyList(), opening = false) }

    /** Shows a ready-made game, for the screen pictures. */
    fun show(table: Table, opening: Boolean = false) = _ui.update { it.copy(loading = false, table = table, history = emptyList(), opening = opening) }

    fun messageShown() = _ui.update { it.copy(message = null) }

    companion object {
        const val UNDO_STEPS = 100
    }
}

/** A card looked up for a deck, as a playtest card; just the name when it couldn't be looked up. */
fun DeckCardInfo?.toPlayCard(name: String): PlayCard =
    if (this == null) PlayCard(name)
    else PlayCard(this.name, imageUrl, typeLine, cmc, power, toughness)
