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
)

/** Search Scryfall (full search syntax works) and browse sets; add any printing to the collection. */
class SearchViewModel(private val repo: CollectionRepository) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui
    val boxes: StateFlow<List<Box>> = repo.observeBoxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        _ui.update { it.copy(query = text, suggestions = emptyList()) }
        run(text, label = text, order = "name")
    }

    fun pickSuggestion(name: String) {
        _ui.update { it.copy(query = name, suggestions = emptyList()) }
        run("!\"$name\"", label = name, order = "released", unique = "prints")
    }

    fun openSet(set: ScryfallSet) {
        _ui.update { it.copy(tab = 0, query = "", suggestions = emptyList()) }
        run("e:${set.code}", label = set.name, order = "set")
    }

    private var unique = "cards"

    private fun run(query: String, label: String, order: String, unique: String = "cards") {
        this.order = order
        this.unique = unique
        page = 1
        suggestJob?.cancel()
        searchJob?.cancel()
        _ui.update { it.copy(ranQuery = query, ranLabel = label, results = emptyList(), loading = true, hasMore = false) }
        searchJob = viewModelScope.launch {
            runCatching { repo.api.searchPage(query, order, 1, unique) }
                .onSuccess { r -> _ui.update { it.copy(results = r.data, hasMore = r.hasMore, loading = false) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, message = "Search failed: ${e.message}") } }
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
                .onFailure { _ui.update { it.copy(loading = false) } }
        }
    }

    fun clearResults() = _ui.update { it.copy(ranQuery = null, ranLabel = null, results = emptyList(), hasMore = false, query = "") }

    private fun loadSets() {
        viewModelScope.launch {
            runCatching { repo.api.sets() }
                .onSuccess { list -> _ui.update { it.copy(sets = list) } }
                .onFailure { e -> _ui.update { it.copy(sets = emptyList(), message = "Couldn't load sets: ${e.message}") } }
        }
    }

    fun setSetFilter(q: String) = _ui.update { it.copy(setFilter = q) }
    fun toggleGrid() = _ui.update { it.copy(grid = !it.grid) }

    fun select(card: ScryfallCard?) = _ui.update { it.copy(selected = card) }

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

    fun add(foil: Boolean, qty: Int, boxId: Long) {
        val card = _ui.value.selected ?: return
        _ui.update { it.copy(selected = null) }
        viewModelScope.launch {
            repo.add(card, foil, qty, boxId)
            _ui.update { it.copy(message = "Added ${qty}× ${card.name}${if (foil) " (foil)" else ""}") }
        }
    }

    fun messageShown() = _ui.update { it.copy(message = null) }
}
