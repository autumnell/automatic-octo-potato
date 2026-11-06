package com.mtgscanner.ui.decks.playtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.CollectionRepository
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
)

/**
 * The playtest for one deck: a setup step to pick an opponent (none, another deck of yours or a precon), then the
 * table. Every move goes through [act] so it can be undone.
 */
class PlaytestViewModel(private val repo: CollectionRepository, private val deckId: Long) : ViewModel() {
    private val _ui = MutableStateFlow(PlaytestUi())
    val ui: StateFlow<PlaytestUi> = _ui
    private val random = Random(System.nanoTime())

    /** Your other decks, to play against. */
    val otherDecks: StateFlow<List<Opponent.OwnDeck>> = repo.observeDecks()
        .map { all -> all.filter { it.id != deckId && it.text.isNotBlank() }.map { Opponent.OwnDeck(it.id, it.name.ifBlank { "Deck" }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** MTGJSON's precons, commander decks first; null while loading. */
    val precons = MutableStateFlow<List<PreconEntry>?>(null)

    init {
        viewModelScope.launch {
            val deck = loadOwn(deckId)
            _ui.update { it.copy(deck = deck, loading = false, message = if (deck == null) "Couldn't load this deck" else null) }
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
        return build(d.name.ifBlank { "Deck" }, lines.filter { it.section in DeckCheck.PLAYED }.map { it.name to it.quantity }, leads)
    }

    private suspend fun loadPrecon(entry: PreconEntry): PlayDeck? {
        val p = Precons.deck(repo.appContext, entry) ?: return null
        val leads = p.commanders.map { it.name }
        return build(entry.name, p.cards.filter { it.section != "Sideboard" }.map { it.name to it.count }, leads)
    }

    private suspend fun build(name: String, lines: List<Pair<String, Int>>, leads: List<String>): PlayDeck {
        val info = runCatching { repo.deckCardInfo(lines.map { it.first } + leads) }.getOrDefault(emptyMap())
        fun card(n: String) = info[DeckNames.key(n)].toPlayCard(n)
        val leadKeys = leads.map { DeckNames.key(it) }.toSet()
        // A commander listed in the deck too is taken out once per commander.
        val cards = lines.flatMap { (n, q) ->
            val k = DeckNames.key(n)
            List(if (k in leadKeys) (q - 1).coerceAtLeast(0) else q) { card(n) }
        }
        return PlayDeck(name, cards, leads.map(::card))
    }

    /** Starts the game against [opponent] (null to play alone). */
    fun start(opponent: Opponent?, pilot: Pilot) {
        val deck = _ui.value.deck ?: return
        _ui.update { it.copy(opponentLoading = opponent != null, pilot = pilot) }
        viewModelScope.launch {
            val opp = when (opponent) {
                null -> null
                is Opponent.OwnDeck -> loadOwn(opponent.id)
                is Opponent.Precon -> loadPrecon(opponent.entry)
            }
            if (opponent != null && (opp == null || opp.cards.isEmpty())) {
                _ui.update { it.copy(opponentLoading = false, message = "Couldn't load ${opponent.name}") }
                return@launch
            }
            _ui.update { it.copy(opponentLoading = false, table = Playtest.start(deck, opp, random), history = emptyList()) }
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

    fun undo() {
        val h = _ui.value.history
        if (h.isEmpty()) return
        _ui.update { it.copy(table = h.last(), history = h.dropLast(1)) }
    }

    /** Back to the opponent picker with the same deck. */
    fun restart() = _ui.update { it.copy(table = null, history = emptyList()) }

    /** Shows a ready-made game, for the screen pictures. */
    fun show(table: Table) = _ui.update { it.copy(loading = false, table = table, history = emptyList()) }

    fun messageShown() = _ui.update { it.copy(message = null) }

    companion object {
        const val UNDO_STEPS = 100
    }
}

/** A card looked up for a deck, as a playtest card; just the name when it couldn't be looked up. */
fun DeckCardInfo?.toPlayCard(name: String): PlayCard =
    if (this == null) PlayCard(name)
    else PlayCard(this.name, imageUrl, typeLine, cmc, power, toughness)
