package com.mtgscanner.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.undoWishes
import com.mtgscanner.data.wishSnapshot
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.ScryfallSet
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.placeName
import com.mtgscanner.data.observePlaces
import com.mtgscanner.data.addToDeckList
import com.mtgscanner.data.copiesOf
import com.mtgscanner.data.deckById
import com.mtgscanner.data.deckSlotId
import com.mtgscanner.data.db.Deck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import com.mtgscanner.data.searchOnPhone
import com.mtgscanner.data.nameSuggestions
import com.mtgscanner.data.printingsFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUi(
    val tab: Int = 0,
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    /** The query actually run, shown as the results heading. */
    val ranQuery: String? = null,
    val ranLabel: String? = null,
    val results: List<ScryfallCard> = emptyList(),
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val grid: Boolean = true,
    val selected: ScryfallCard? = null,
    val showPrintings: Boolean = false,
    val printings: List<ScryfallCard>? = null,
    val sets: List<ScryfallSet>? = null,
    val setFilter: String = "",
    val message: String? = null,
    /** The last search failed: offline, server error, or a query Scryfall rejected. */
    val failed: Boolean = false,
    /** Scryfall's own words when it rejected the query; null when it couldn't be reached. */
    val error: String? = null,
    /** Loading the next page failed; shows a retry button under the results. */
    val moreFailed: Boolean = false,
    /** The sets list couldn't be loaded. */
    val setsFailed: Boolean = false,
    /** Recent searches, newest first. */
    val recent: List<RecentSearch> = emptyList(),
    /** Cards recently opened from Search, newest first. */
    val viewed: List<Viewed> = emptyList(),
    /** Extra Scryfall filters from the filter sheet. */
    val filters: SearchFilters = SearchFilters(),
    /** The results came from the card list on the phone (Scryfall hasn't answered yet, or can't be reached). */
    val fromPhone: Boolean = false,
)

/** A card opened from Search, kept for "Recently viewed". */
@kotlinx.serialization.Serializable
data class Viewed(val id: String, val name: String, val image: String? = null)

/**
 * How many copies of a card are in the collection, and where most of them are. [count] is every printing;
 * [foil] of them are foil. [boxId] is the box holding the most copies; [rowId] is the biggest row in it, opened by
 * "You own".
 */
data class Owned(
    val count: Int, val boxName: String?, val boxCount: Int, val boxId: Long? = null, val rowId: Long? = null,
    val foil: Int = 0,
) {
    /** "You own 3 · Rares", or "You own 3 · 2 boxes". */
    val label: String get() = "You own $count" + where

    private val where: String get() = when {
        boxCount > 1 -> " · $boxCount boxes"
        boxName != null -> " · $boxName"
        else -> ""
    }

    /** "2 · 1 foil" style count, foils said apart. */
    val countLabel: String get() = when {
        foil == 0 -> "$count"
        foil == count -> "$count foil"
        else -> "$count · $foil foil"
    }
}

/** This printing's copies and the card's copies in every printing, for "You own" lines. */
data class OwnedPrinting(val printing: Owned?, val anyPrinting: Owned?) {
    /** Copies of this exact printing (0 when only other printings are owned). */
    val count: Int get() = printing?.count ?: 0
    /** Copies in other printings. */
    val others: Int get() = (anyPrinting?.count ?: 0) - count

    /** "You own 2 of this printing (1 foil) · Rares · 3 more in other printings", or null when none at all. */
    val label: String?
        get() {
            val p = printing
            val more = if (others > 0) "$others in other printings" else null
            if (p == null) return more?.let { "You own $it" }
            val foils = when {
                p.foil == 0 -> ""
                p.foil == p.count -> ", foil"
                else -> " (${p.foil} foil)"
            }
            return "You own ${p.count} of this printing$foils" + (if (p.boxCount > 1) " · ${p.boxCount} boxes" else p.boxName?.let { " · $it" } ?: "") +
                (more?.let { " · $it" } ?: "")
        }

    /** The row to open from "You own": this printing's when owned, else any. */
    val rowId: Long? get() = printing?.rowId ?: anyPrinting?.rowId
    val boxId: Long? get() = printing?.boxId ?: anyPrinting?.boxId
}

/** Owned copies keyed by "o:<oracleId>", "n:<lowercase name>" and "p:<scryfall id>" (one printing). */
fun Map<String, Owned>.of(card: ScryfallCard): Owned? =
    card.oracleId?.let { this["o:$it"] } ?: this["n:${card.name.lowercase()}"]

/** [card]'s own printing (as scanned or added) apart from the card's other printings. */
fun Map<String, Owned>.printingOf(card: ScryfallCard): OwnedPrinting = OwnedPrinting(this["p:${card.id}"], of(card))

/** Search Scryfall (full search syntax works) and browse sets; add any printing to the collection. */
class SearchViewModel(private val repo: CollectionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui
    private val history = SearchHistory(repo.appContext)

    init {
        com.mtgscanner.data.CardKingdom.loadSaved(repo.appContext)
        _ui.update { it.copy(recent = history.recent(), viewed = history.viewed()) }
    }
    val boxes: StateFlow<List<Box>> = repo.observeBoxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Decks, so a card can go straight into one. */
    val decks: StateFlow<List<Deck>> = repo.observeDecks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cards already in the collection, for "You own 3" and the owned badge on results. */
    val owned: StateFlow<Map<String, Owned>> = combine(repo.observeAll(), repo.observePlaces()) { cards, places ->
        val names = places.associate { it.id to it.placeName() }
        fun summarize(rows: List<com.mtgscanner.data.db.CollectionCard>): Owned {
            val byBox = rows.groupBy { it.boxId }.mapValues { (_, r) -> r.sumOf { it.quantity } }
            val top = byBox.maxByOrNull { it.value }?.key
            val row = rows.filter { it.boxId == top }.maxByOrNull { it.quantity }
            return Owned(rows.sumOf { it.quantity }, top?.let { names[it] }, byBox.size, top, row?.id, foil = rows.filter { it.foil }.sumOf { it.quantity })
        }
        val out = HashMap<String, Owned>()
        cards.groupBy { it.scryfallId }.forEach { (k, rows) -> out["p:$k"] = summarize(rows) }
        cards.filter { it.oracleId != null }.groupBy { it.oracleId!! }.forEach { (k, rows) -> out["o:$k"] = summarize(rows) }
        cards.groupBy { it.name.lowercase() }.forEach { (k, rows) -> out["n:$k"] = summarize(rows) }
        out.toMap()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Different collector numbers owned per set code (lowercase), for the Sets tab. */
    val ownedBySet: StateFlow<Map<String, Int>> = repo.observeAll().map { cards ->
        cards.groupBy { it.setCode.lowercase() }.mapValues { (_, rows) -> rows.map { it.collectorNumber.lowercase() }.distinct().size }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var page = 1
    private var order = "name"

    fun setTab(t: Int) {
        _ui.update { it.copy(tab = t) }
        if (t == 1 && _ui.value.sets == null) loadSets()
    }

    fun setQuery(q: String) {
        _ui.update { it.copy(query = q) }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            if (q.isBlank() || q.contains(':')) { _ui.update { it.copy(suggestions = emptyList()) }; return@launch }
            // Names from the card list on the phone show at once; Scryfall's (which knows tokens and very new cards) follow.
            val mine = repo.nameSuggestions(q.trim())
            if (mine.isNotEmpty()) _ui.update { it.copy(suggestions = mine) }
            delay(250)
            val list = runCatching { repo.api.autocomplete(q.trim(), extras = true) }.getOrDefault(emptyList())
            if (list.isNotEmpty() || mine.isEmpty()) _ui.update { it.copy(suggestions = list.take(8)) }
        }
    }

    /** Runs the typed query. Plain words search names; Scryfall syntax like "t:dragon c:r" also works. */
    fun submit(q: String = _ui.value.query) {
        val text = q.trim()
        val f = _ui.value.filters
        if (text.isEmpty() && !f.narrows) return
        _ui.update { it.copy(query = text, suggestions = emptyList(), recent = history.addRecent(RecentSearch(text, f))) }
        run(f.combine(text), label = text.ifEmpty { "Filtered" }, order = f.order, unique = f.unique)
    }

    fun setFilters(f: SearchFilters) = _ui.update { it.copy(filters = f) }

    fun pickSuggestion(name: String) {
        _ui.update { it.copy(query = name, suggestions = emptyList(), recent = history.addRecent(RecentSearch(name, exactName = true))) }
        // Extras too, so a token picked from the suggestions is found.
        run(exactNameQuery(name) + " include:extras", label = name, order = "released", unique = "prints")
    }

    fun openSet(set: ScryfallSet) {
        _ui.update { it.copy(tab = 0, query = "", suggestions = emptyList()) }
        // A token or art card set shows nothing without include:extras.
        val extras = if (set.setType in com.mtgscanner.data.CardExtras.SET_TYPES) " include:extras" else ""
        run("e:${set.code}$extras", label = set.name, order = "set")
    }

    private var unique = "cards"
    private var lastRun: Triple<String, String, String>? = null

    /** Runs the last search again, after it failed. */
    fun retry() {
        val (q, label, order) = lastRun ?: return
        run(q, label, order, unique)
    }

    private fun run(query: String, label: String, order: String, unique: String = "cards") {
        this.order = order
        this.unique = unique
        page = 1
        suggestJob?.cancel()
        searchJob?.cancel()
        lastRun = Triple(query, label, order)
        _ui.update { it.copy(ranQuery = query, ranLabel = label, results = emptyList(), loading = true, hasMore = false, failed = false, error = null, moreFailed = false, fromPhone = false) }
        searchJob = viewModelScope.launch {
            // The card list on the phone answers too: shown if Scryfall is slow, kept if it can't be reached.
            val local = async { runCatching { repo.searchOnPhone(query, order, unique) }.getOrNull() }
            val early = launch {
                delay(PHONE_FIRST_MS)
                val mine = local.await()
                if (!mine.isNullOrEmpty()) _ui.update { if (it.ranQuery == query && it.results.isEmpty()) it.copy(results = mine, fromPhone = true) else it }
            }
            runCatching { repo.api.searchPage(query, order, 1, unique) }
                .onSuccess { r -> early.cancel(); _ui.update { it.copy(results = r.data, hasMore = r.hasMore, loading = false, fromPhone = false) } }
                .onFailure { e ->
                    // A newer search cancelled this one; it owns the spinner now.
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    early.cancel()
                    val msg = (e as? com.mtgscanner.data.ScryfallQueryException)?.message
                    val mine = if (msg == null) local.await() else null
                    if (!mine.isNullOrEmpty()) _ui.update { it.copy(results = mine, hasMore = false, loading = false, fromPhone = true) }
                    else _ui.update { it.copy(results = emptyList(), loading = false, failed = true, error = msg, fromPhone = false) }
                }
        }
    }

    fun loadMore() {
        val s = _ui.value
        val q = s.ranQuery ?: return
        if (s.loading || !s.hasMore || s.fromPhone) return
        _ui.update { it.copy(loading = true, moreFailed = false) }
        searchJob = viewModelScope.launch {
            runCatching { repo.api.searchPage(q, order, page + 1, unique) }
                .onSuccess { r ->
                    page++
                    _ui.update { it.copy(results = (it.results + r.data).distinctBy { c -> c.id }, hasMore = r.hasMore, loading = false) }
                }
                .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e; _ui.update { it.copy(loading = false, moreFailed = true) } }
        }
    }

    fun clearResults() {
        suggestJob?.cancel()
        searchJob?.cancel()
        _ui.update { it.copy(ranQuery = null, ranLabel = null, results = emptyList(), hasMore = false, query = "", failed = false, error = null, moreFailed = false, suggestions = emptyList(), loading = false) }
    }

    private fun loadSets() {
        _ui.update { it.copy(sets = null, setsFailed = false) }
        viewModelScope.launch {
            runCatching { repo.api.sets() }
                .onSuccess { list -> _ui.update { it.copy(sets = list, setsFailed = list.isEmpty()) } }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _ui.update { it.copy(sets = emptyList(), setsFailed = true) }
                }
        }
    }

    fun retrySets() = loadSets()

    fun setSetFilter(q: String) = _ui.update { it.copy(setFilter = q) }
    fun toggleGrid() = _ui.update { it.copy(grid = !it.grid) }
    fun setGrid(on: Boolean) = _ui.update { it.copy(grid = on) }

    fun select(card: ScryfallCard?) = _ui.update {
        if (card == null) it.copy(selected = null)
        else it.copy(selected = card, viewed = history.addViewed(Viewed(card.id, card.name, card.smallImageUrl)))
    }

    /** Runs a recent search again: picked names open their printings, typed searches run with their filters. */
    fun rerun(r: RecentSearch) {
        if (r.exactName) { pickSuggestion(r.text); return }
        _ui.update { it.copy(query = r.text, filters = r.filters) }
        submit(r.text)
    }

    /** Opens a recently viewed card again. */
    fun openViewed(v: Viewed) {
        viewModelScope.launch {
            val card = runCatching { repo.api.byId(v.id) }.getOrNull()
                ?: withContext(Dispatchers.IO) { repo.localCards.byId(v.id) }
            if (card != null) select(card) else _ui.update { it.copy(message = "Can't reach Scryfall") }
        }
    }

    fun clearHistory() {
        history.clear()
        _ui.update { it.copy(recent = emptyList(), viewed = emptyList()) }
    }

    fun openPrintings() {
        val card = _ui.value.selected ?: return
        _ui.update { it.copy(showPrintings = true, printings = null) }
        viewModelScope.launch {
            val list = try {
                repo.printingsFor(card)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(message = "Can't load printings") }
                listOf(card)
            }
            _ui.update { it.copy(printings = list) }
        }
    }

    fun pickPrinting(card: ScryfallCard) = _ui.update { it.copy(selected = card, showPrintings = false, printings = null) }
    fun closePrintings() = _ui.update { it.copy(showPrintings = false, printings = null) }

    val lastBoxId: Long get() = repo.lastBoxId

    fun add(foil: Boolean, qty: Int, boxId: Long) {
        val card = _ui.value.selected ?: return
        _ui.update { it.copy(selected = null) }
        viewModelScope.launch {
            val target = if (boxId == Box.AUTO_SORT) repo.pickBox(card, foil) else boxId
            if (target == null) {
                _ui.update { it.copy(message = "No box's rules fit ${card.name}. Pick a box instead.") }
                return@launch
            }
            repo.lastBoxId = boxId
            val rowId = repo.add(card, foil, qty, target)
            // Adding counts toward the wishlist; Undo puts the wish back as it was.
            val wish = repo.wishlistItem(card.oracleId)
            repo.fulfilWish(card.oracleId, qty)
            val boxName = boxes.value.firstOrNull { it.id == target }?.name
            val msg = "Added ${qty}× ${card.name}${if (foil) " (foil)" else ""}${boxName?.let { " to $it" } ?: ""}" +
                (if (wish != null) " · off wishlist" else "")
            com.mtgscanner.ui.components.Undo.offer(msg) {
                repo.remove(rowId, qty)
                if (wish != null) repo.restoreWish(wish)
            }
        }
    }

    fun wishlist() {
        val card = _ui.value.selected ?: return
        _ui.update { it.copy(selected = null) }
        viewModelScope.launch {
            val ids = listOfNotNull(card.oracleId)
            val before = repo.wishSnapshot(ids)
            repo.addToWishlist(card)
            // Undo.offer gives the tick.
            com.mtgscanner.ui.components.Undo.offer("Added ${card.name} to your wishlist") { repo.undoWishes(ids, before) }
        }
    }

    /** Makes a box; [onCreated] gets its id, so the add sheet can pick it. */
    fun createBox(d: com.mtgscanner.ui.components.BoxDraft, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch { onCreated(repo.createBox(d.name, d.kind, d.rules, d.color)) }
    }

    /** Makes an empty deck; [onCreated] gets its id. */
    fun createDeck(name: String, onCreated: (Long) -> Unit = {}) {
        val clean = name.trim().ifEmpty { return }
        viewModelScope.launch { runCatching { repo.saveDeck(0, clean, "") }.getOrNull()?.let(onCreated) }
    }

    /**
     * Adds copies to the collection, kept in a deck. The deck's list grows only when the deck now holds more
     * copies than it lists, so adding cards a list already names doesn't count them twice.
     */
    fun addToDeck(foil: Boolean, qty: Int, deckId: Long) {
        val card = _ui.value.selected ?: return
        _ui.update { it.copy(selected = null) }
        viewModelScope.launch {
            val deck = repo.deckById(deckId) ?: run { _ui.update { it.copy(message = "That deck is gone") }; return@launch }
            val slot = repo.deckSlotId(deckId)
            val inDeck = repo.copiesOf(card.oracleId, card.name).filter { it.boxId == slot }.sumOf { it.quantity }
            val extra = (inDeck + qty - com.mtgscanner.scan.DeckText.count(deck.text, card.name)).coerceAtLeast(0)
            val rowId = repo.add(card, foil, qty, slot)
            if (extra > 0) repo.addToDeckList(deckId, card.name, extra)
            val wish = repo.wishlistItem(card.oracleId)
            repo.fulfilWish(card.oracleId, qty)
            val msg = "Added ${qty}× ${card.name}${if (foil) " (foil)" else ""} to ${deck.name}" + (if (wish != null) " · off wishlist" else "")
            com.mtgscanner.ui.components.Undo.offer(msg) {
                repo.remove(rowId, qty)
                if (extra > 0) repo.addToDeckList(deckId, card.name, -extra)
                if (wish != null) repo.restoreWish(wish)
            }
        }
    }

    fun messageShown() = _ui.update { it.copy(message = null) }

    private companion object {
        /** How long Scryfall gets before the phone's own results show. */
        const val PHONE_FIRST_MS = 600L
    }
}

/** Scryfall query for every printing of exactly [name]; double quotes in the name are escaped. */
fun exactNameQuery(name: String): String = "!\"" + name.replace("\"", "\\\"") + "\""

