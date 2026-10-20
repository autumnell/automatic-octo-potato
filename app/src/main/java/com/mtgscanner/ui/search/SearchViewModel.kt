package com.mtgscanner.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.ScryfallSet
import com.mtgscanner.data.db.Box
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    /** The last search couldn't reach Scryfall (offline or server error). */
    val failed: Boolean = false,
    /** The sets list couldn't be loaded. */
    val setsFailed: Boolean = false,
    /** Recent searches, newest first. */
    val recent: List<String> = emptyList(),
    /** Cards recently opened from Search, newest first. */
    val viewed: List<Viewed> = emptyList(),
)

/** A card opened from Search, kept for "Recently viewed". */
@kotlinx.serialization.Serializable
data class Viewed(val id: String, val name: String, val image: String? = null)

/** How many copies of a card (any printing) are in the collection, and where most of them are. */
data class Owned(val count: Int, val boxName: String?, val boxCount: Int) {
    /** "You own 3 · Rares", or "You own 3 · 2 boxes". */
    val label: String get() = "You own $count" + when {
        boxCount > 1 -> " · $boxCount boxes"
        boxName != null -> " · $boxName"
        else -> ""
    }
}

/** Owned copies keyed by "o:<oracleId>" and "n:<lowercase name>". */
fun Map<String, Owned>.of(card: ScryfallCard): Owned? =
    card.oracleId?.let { this["o:$it"] } ?: this["n:${card.name.lowercase()}"]

/** Search Scryfall (full search syntax works) and browse sets; add any printing to the collection. */
class SearchViewModel(private val repo: CollectionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui
    private val history = SearchHistory(repo.appContext)

    init {
        com.mtgscanner.data.CardKingdom.init(repo.appContext)
        _ui.update { it.copy(recent = history.recent(), viewed = history.viewed()) }
    }
    val boxes: StateFlow<List<Box>> = repo.observeBoxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cards already in the collection, for "You own 3" and the owned badge on results. */
    val owned: StateFlow<Map<String, Owned>> = combine(repo.observeAll(), repo.observeBoxes()) { cards, boxList ->
        val names = boxList.associate { it.id to it.name }
        fun summarize(rows: List<com.mtgscanner.data.db.CollectionCard>): Owned {
            val byBox = rows.groupBy { it.boxId }.mapValues { (_, r) -> r.sumOf { it.quantity } }
            val top = byBox.maxByOrNull { it.value }?.key
            return Owned(rows.sumOf { it.quantity }, top?.let { names[it] }, byBox.size)
        }
        val out = HashMap<String, Owned>()
        cards.filter { it.oracleId != null }.groupBy { it.oracleId!! }.forEach { (k, rows) -> out["o:$k"] = summarize(rows) }
        cards.groupBy { it.name.lowercase() }.forEach { (k, rows) -> out["n:$k"] = summarize(rows) }
        out.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
            delay(250)
            val list = if (q.isBlank() || q.contains(':')) emptyList()
            else runCatching { repo.api.autocomplete(q.trim()) }.getOrDefault(emptyList())
            _ui.update { it.copy(suggestions = list.take(8)) }
        }
    }

    /** Runs the typed query. Plain words search names; Scryfall syntax like "t:dragon c:r" also works. */
    fun submit(q: String = _ui.value.query) {
        val text = q.trim()
        if (text.isEmpty()) return
        _ui.update { it.copy(query = text, suggestions = emptyList(), recent = history.addRecent(text)) }
        run(text, label = text, order = "name")
    }

    fun pickSuggestion(name: String) {
        _ui.update { it.copy(query = name, suggestions = emptyList(), recent = history.addRecent(name)) }
        run("!\"$name\"", label = name, order = "released", unique = "prints")
    }

    fun openSet(set: ScryfallSet) {
        _ui.update { it.copy(tab = 0, query = "", suggestions = emptyList()) }
        run("e:${set.code}", label = set.name, order = "set")
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
        _ui.update { it.copy(ranQuery = query, ranLabel = label, results = emptyList(), loading = true, hasMore = false, failed = false) }
        searchJob = viewModelScope.launch {
            runCatching { repo.api.searchPage(query, order, 1, unique) }
                .onSuccess { r -> _ui.update { it.copy(results = r.data, hasMore = r.hasMore, loading = false) } }
                .onFailure { e ->
                    // A newer search cancelled this one; it owns the spinner now.
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _ui.update { it.copy(loading = false, failed = true) }
                }
        }
    }

    fun loadMore() {
        val s = _ui.value
        val q = s.ranQuery ?: return
        if (s.loading || !s.hasMore) return
        _ui.update { it.copy(loading = true) }
        searchJob = viewModelScope.launch {
            runCatching { repo.api.searchPage(q, order, page + 1, unique) }
                .onSuccess { r ->
                    page++
                    _ui.update { it.copy(results = it.results + r.data, hasMore = r.hasMore, loading = false) }
                }
                .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e; _ui.update { it.copy(loading = false) } }
        }
    }

    fun clearResults() {
        suggestJob?.cancel()
        searchJob?.cancel()
        _ui.update { it.copy(ranQuery = null, ranLabel = null, results = emptyList(), hasMore = false, query = "", failed = false, suggestions = emptyList(), loading = false) }
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

    fun select(card: ScryfallCard?) = _ui.update {
        if (card == null) it.copy(selected = null)
        else it.copy(selected = card, viewed = history.addViewed(Viewed(card.id, card.name, card.smallImageUrl)))
    }

    /** Runs a recent search again: names open their printings, anything else searches as typed. */
    fun rerun(q: String) {
        if (q.contains(':')) submit(q) else pickSuggestion(q)
    }

    /** Opens a recently viewed card again. */
    fun openViewed(v: Viewed) {
        viewModelScope.launch {
            val card = runCatching { repo.api.byId(v.id) }.getOrNull()
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
            val list = runCatching { repo.api.printings(card) }.getOrDefault(listOf(card))
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
            repo.add(card, foil, qty, target)
            com.mtgscanner.util.Haptics.tick()
            repo.fulfilWish(card.oracleId, qty)
            val boxName = boxes.value.firstOrNull { it.id == target }?.name
            _ui.update { it.copy(message = "Added ${qty}× ${card.name}${if (foil) " (foil)" else ""}${boxName?.let { " to $it" } ?: ""}") }
        }
    }

    fun wishlist() {
        val card = _ui.value.selected ?: return
        _ui.update { it.copy(selected = null) }
        viewModelScope.launch {
            repo.addToWishlist(card)
            com.mtgscanner.util.Haptics.tick()
            _ui.update { it.copy(message = "Added ${card.name} to your wishlist") }
        }
    }

    fun createBox(d: com.mtgscanner.ui.components.BoxDraft) {
        viewModelScope.launch { repo.createBox(d.name, d.kind, d.rules, d.color) }
    }

    fun messageShown() = _ui.update { it.copy(message = null) }
}
