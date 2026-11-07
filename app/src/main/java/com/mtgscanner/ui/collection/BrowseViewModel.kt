package com.mtgscanner.ui.collection

import com.mtgscanner.data.db.totalEur
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.isDeckSlot
import com.mtgscanner.data.db.placeName
import com.mtgscanner.data.db.deckPlaceName
import com.mtgscanner.data.deckSlotId
import com.mtgscanner.data.observePlaces
import com.mtgscanner.data.db.userTagList
import com.mtgscanner.ui.components.Undo
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import com.mtgscanner.data.MoveLine
import kotlinx.coroutines.flow.drop
import kotlinx.serialization.json.Json
import com.mtgscanner.data.Binder
import com.mtgscanner.data.numberBinderSlots
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.BoxLayout
import com.mtgscanner.data.CardOrder
import com.mtgscanner.data.NameKey
import com.mtgscanner.data.bulkEdit
import com.mtgscanner.data.setPinned
import com.mtgscanner.data.setPocketPerCopy
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.facts
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.changeSinceAdded
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import com.mtgscanner.data.LoanMath
import com.mtgscanner.data.ScryfallQueryException
import com.mtgscanner.data.observeLoans
import com.mtgscanner.data.search.ScryfallQuery
import com.mtgscanner.data.search.ScryfallQueryCache
import com.mtgscanner.data.search.RemoteAsk
import com.mtgscanner.data.search.SearchEnv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest

@Serializable
enum class SortBy(val label: String, val short: String = label) {
    RECENT("Recently added", "Newest"), NAME("Name"), PRICE("Price"), MANA("Mana value", "Mana"), SET("Set & number", "Set"), COLOR("Color"),
    GAIN("Price change", "Change"),
    ;

    val isPrice: Boolean get() = this == PRICE || this == GAIN
}

@Serializable
enum class GroupBy(val label: String) { NONE("No grouping"), SET("Set"), COLOR("Color"), TYPE("Card type"), RARITY("Rarity") }

val COLOR_FILTERS = CardColors.PIPS
val RARITIES = listOf("common", "uncommon", "rare", "mythic")
val CARD_TYPES = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land", "Battle")

@Serializable
data class Filters(
    val query: String = "",
    val colors: Set<String> = emptySet(),
    /** How picked colors match, as on Scryfall. Filters saved before this read as Including. */
    val colorMatch: ColorMatch = ColorMatch.INCLUDING,
    /** Match commander color identity instead of the card's colors. */
    val byIdentity: Boolean = false,
    val rarities: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val set: String? = null,
    val foilOnly: Boolean = false,
    val treatments: Set<String> = emptySet(),
    /** Your own tags. */
    val userTags: Set<String> = emptySet(),
    val sort: SortBy = SortBy.RECENT,
    val group: GroupBy = GroupBy.NONE,
    /** Home stat tile filters ([com.mtgscanner.ui.home.TileFlags]: commander, bulk, sale…); all must match. */
    val flags: Set<String> = emptySet(),
    /** Only cards added at or after this time in ms (Home's "This week"); 0 for any. */
    val addedSince: Long = 0L,
    /** Only cards kept in this deck: the id of its card row (see DeckSlots.kt); null for any. */
    val deckBox: Long? = null,
) {
    val activeCount: Int get() = colors.size + rarities.size + types.size + treatments.size + userTags.size + (if (set != null) 1 else 0) + (if (foilOnly) 1 else 0) +
        flags.size + (if (addedSince > 0) 1 else 0) + (if (deckBox != null) 1 else 0)
}

data class BrowseUi(
    val title: String = "Collection",
    val groups: List<Pair<String, List<CollectionCard>>> = emptyList(),
    val shownCards: Int = 0,
    val shownValue: Double = 0.0,
    /** [shownValue] in euros. */
    val shownEur: Double = 0.0,
    val totalCards: Int = 0,
    val sets: List<Pair<String, String>> = emptyList(),
    val treatments: List<String> = emptyList(),
    /** Your own tags on cards in view. */
    val userTags: List<String> = emptyList(),
    val boxes: List<Box> = emptyList(),
    /** Decks holding cards in view: (card row id, deck name), for the Deck filter. */
    val decks: List<Pair<Long, String>> = emptyList(),
    /** Ids of every card shown, for "select all". */
    val shownIds: List<Long> = emptyList(),
    val loaded: Boolean = false,
    /** Every card, unfiltered, to say where a card sits in its box. */
    val all: List<CollectionCard> = emptyList(),
    /** This box is a binder, so it has pages. */
    val binder: Boolean = false,
    /** A box (binder or not) can be shown as pages: a plain box in the order its cards sit. */
    val pagesView: Boolean = false,
    /** The binder's cards, ignoring filters, for the page view. */
    val binderCards: List<CollectionCard> = emptyList(),
    /** Binder cards without a slot yet, or sharing a pocket with another card. */
    val unslotted: Int = 0,
    /** This binder gives each copy its own pocket. */
    val pocketPerCopy: Boolean = false,
    /** How the box is laid out inside. */
    val layout: BoxLayout = BoxLayout(),
    /** A short line under the search box about Scryfall syntax: read as text, waiting on Scryfall… */
    val searchNote: String? = null,
)

/** The card list for one box, or the whole collection when [boxId] is -1. */
@OptIn(FlowPreview::class)
class BrowseViewModel(private val repo: CollectionRepository, private val boxId: Long, embedded: Boolean = false) : ViewModel() {
    private val prefs: SharedPreferences? = try {
        repo.appContext.getSharedPreferences("browse", Context.MODE_PRIVATE)
    } catch (e: Exception) { null }

    private val json = Json { ignoreUnknownKeys = true }
    private val stickyKey = "filters-$boxId"

    /**
     * Opened from Home or a smart list with its own filters, rather than plain. Collection's All tab never takes
     * these: they're meant for the Browse page opened right after it.
     */
    private val launched: Boolean = !embedded && (BrowseLaunch.pendingFor(boxId) ||
        (boxId < 0 && BrowseLaunch.filters == null && (BrowseLaunch.sort != null || BrowseLaunch.color != null)))

    /** A smart list's name, when one was opened. */
    private val listTitle: String? = if (launched) BrowseLaunch.takeTitle() else null

    /** The last filters used on this box, so they're back next time (sticky filters). */
    private fun loadSticky(): Filters? = try {
        prefs?.getString(stickyKey, null)?.let { json.decodeFromString(Filters.serializer(), it) }
    } catch (e: Exception) { null }

    private val sticky: Filters? = if (launched) null else loadSticky()
    private val hadSticky = sticky != null
    private val _filters = MutableStateFlow(if (launched) BrowseLaunch.take() else sticky ?: Filters())
    val filters: StateFlow<Filters> = _filters

    private fun pref(key: String, default: Boolean): Boolean =
        try { prefs?.getBoolean(key, default) ?: default } catch (e: Exception) { default }

    private fun setPref(key: String, value: Boolean) {
        try { prefs?.edit()?.putBoolean(key, value)?.apply() } catch (e: Exception) { }
    }

    /** Grid or list, remembered between visits. */
    private val _grid = MutableStateFlow(pref("grid", true))
    val grid: StateFlow<Boolean> = _grid
    fun toggleGrid() {
        _grid.value = !_grid.value
        setPref("grid", _grid.value)
    }

    /** Compact rows: more cards per screen. */
    private val _dense = MutableStateFlow(pref("dense", false))
    val dense: StateFlow<Boolean> = _dense
    fun toggleDense() {
        _dense.value = !_dense.value
        setPref("dense", _dense.value)
        if (_dense.value && _grid.value) toggleGrid()
    }

    /** Grid, then list, then compact one-line rows. */
    fun cycleView() {
        when {
            _grid.value -> { toggleGrid(); if (_dense.value) { _dense.value = false; setPref("dense", false) } }
            !_dense.value -> toggleDense()
            else -> { _dense.value = false; setPref("dense", false); toggleGrid() }
        }
    }

    /** Pinching out: compact rows → list → pictures. */
    fun bigger() {
        when {
            _grid.value -> Unit
            _dense.value -> { _dense.value = false; setPref("dense", false) }
            else -> toggleGrid()
        }
    }

    /** Pinching in: pictures → list → compact rows. */
    fun smaller() {
        when {
            _grid.value -> { toggleGrid(); if (_dense.value) { _dense.value = false; setPref("dense", false) } }
            !_dense.value -> toggleDense()
        }
    }

    /** Binder page view instead of the list. */
    private val _pages = MutableStateFlow(pref("pages", false))
    val pages: StateFlow<Boolean> = _pages
    fun togglePages() {
        _pages.value = !_pages.value
        setPref("pages", _pages.value)
    }

    /** A one-time tip about long-press and swipe. */
    private val _showTip = MutableStateFlow(!pref("tipDone", false))
    val showTip: StateFlow<Boolean> = _showTip
    fun dismissTip() {
        _showTip.value = false
        setPref("tipDone", true)
    }

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected

    /** What doesn't depend on the filters: the cards in view with their search text, and the filter choices. */
    private class Scope(
        val all: List<CollectionCard>,
        val boxes: List<Box>,
        val decks: List<Pair<Long, String>>,
        val box: Box?,
        val inScope: List<CollectionCard>,
        /** Lower-case search text per card, same order as [inScope]. */
        val hay: List<String>,
        /** Name order key per card ([NameKey]), same order as [inScope]. */
        val nameKeys: List<String>,
        val totalCards: Int,
        val sets: List<Pair<String, String>>,
        val treatments: List<String>,
        val userTags: List<String>,
    )

    /**
     * Search text and name key by row id, reused while the row is unchanged. Most edits bump updatedAt; tags
     * and price refreshes don't, so those are compared too. Only touched on the scope flow's thread.
     */
    private class Cached(val updatedAt: Long, val tags: String, val userTags: String, val name: String, val hay: String, val key: String)
    private val textCache = HashMap<Long, Cached>()

    private fun cached(c: CollectionCard): Cached {
        val old = textCache[c.id]
        if (old != null && old.updatedAt == c.updatedAt && old.tags == c.tags && old.userTags == c.userTags && old.name == c.name) return old
        return Cached(c.updatedAt, c.tags, c.userTags, c.name, searchText(c), NameKey.of(c.name)).also { textCache[c.id] = it }
    }

    private val scope = combine(repo.observeAll(), repo.observePlaces()) { all, places ->
        // Decks' card rows aren't boxes: they're only for the Deck filter and for opening one deck's cards.
        val boxes = places.filter { !it.isDeckSlot }
        val inScope = if (boxId < 0) all else all.filter { it.boxId == boxId }
        val used = inScope.mapTo(HashSet()) { it.boxId }
        val texts = inScope.map(::cached)
        // Rows that are gone don't stay in the cache.
        if (textCache.size > inScope.size + 64) {
            val live = inScope.mapTo(HashSet()) { it.id }
            textCache.keys.retainAll(live)
        }
        Scope(
            all = all,
            boxes = boxes,
            decks = places.filter { it.isDeckSlot && it.id in used }.map { it.id to it.name }.sortedBy { it.second.lowercase() },
            box = if (boxId < 0) null else places.firstOrNull { it.id == boxId },
            inScope = inScope,
            hay = texts.map { it.hay },
            nameKeys = texts.map { it.key },
            totalCards = inScope.sumOf { it.quantity },
            sets = inScope.map { it.setCode to it.setName }.distinct().sortedBy { it.second },
            treatments = inScope.flatMap { it.treatments.split(',') }.filter { it.isNotBlank() }.distinct(),
            userTags = inScope.flatMap { it.userTagList() }.distinct().sortedBy { it.lowercase() },
        )
    }.flowOn(Dispatchers.Default)
        // One pass per change.
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** The filters, with typing in the search box settling for a moment before the list redoes itself. */
    private var lastQuery = _filters.value.query
    private val applied = _filters.debounce { f ->
        if (f.query != lastQuery) { lastQuery = f.query; 150L } else 0L
    }

    /** Bumped when Scryfall answers part of a search, so the list is worked out again. */
    private val remoteTick = MutableStateFlow(0)

    private val loans = repo.observeLoans().catch { emit(emptyList()) }

    val ui: StateFlow<BrowseUi> = combine(scope, applied, loans, remoteTick) { sc, f, loanList, _ ->
        val compiled = ScryfallQuery.compile(f.query)
        val env = if (compiled.plain != null) null else {
            val boxNames = sc.boxes.associate { it.id to it.name }
            SearchEnv(boxName = { boxNames[it] }, lent = LoanMath.outByRow(loanList, sc.all).keys)
        }
        val hits = sc.inScope.indices.filter { matches(sc.inScope[it], f, sc.hay[it], env, compiled) }
        // Name order uses the keys worked out once per card, not once per comparison.
        val shown = if (f.sort == SortBy.NAME) hits.sortedBy { sc.nameKeys[it] }.map { sc.inScope[it] }
        else hits.map { sc.inScope[it] }.sortedWith(comparator(f.sort))
        val box = sc.box
        // A box with dividers lists its cards by divider, matching the physical box.
        val layout = BoxLayout.of(box?.divider.orEmpty())
        val binder = box != null && Binder.isBinder(box.kind)
        val perCopy = box?.pocketPerCopy == true
        val shownIds = shown.map { it.id }
        // Picked cards the filters now hide are dropped from the selection.
        val shownSet = shownIds.toHashSet()
        _selected.update { sel -> if (sel.all { it in shownSet }) sel else sel.filterTo(HashSet()) { it in shownSet } }
        BrowseUi(
            title = listTitle ?: if (boxId < 0) "All cards" else box?.placeName() ?: "Box",
            groups = when {
                f.group == GroupBy.NONE && layout.needsFacts -> bySpot(shown, layout, inOrder = layout.order != CardOrder.NAME && f.sort == SortBy.NAME)
                // Long plain lists get headers by letter or set.
                f.group == GroupBy.NONE -> ListSections.sections(shown, f.sort) ?: group(shown, f.group)
                else -> group(shown, f.group)
            },
            shownCards = shown.sumOf { it.quantity },
            shownValue = shown.sumOf { it.totalPrice() },
            shownEur = shown.sumOf { it.totalEur() },
            totalCards = sc.totalCards,
            sets = sc.sets,
            treatments = sc.treatments,
            userTags = sc.userTags,
            boxes = sc.boxes,
            decks = sc.decks,
            shownIds = shownIds,
            loaded = true,
            all = sc.all,
            binder = binder,
            pagesView = box != null,
            binderCards = when {
                binder -> sc.inScope.sortedBy { it.binderSlot }
                // A plain box as pages: front to back, divider by divider, a pocket per card.
                box != null -> boxOrder(sc.inScope, layout).mapIndexed { i, c -> c.copy(binderSlot = i + 1) }
                else -> emptyList()
            },
            unslotted = if (binder) Binder.numberSlots(sc.inScope, { it.id }, { it.name }, { it.binderSlot }, { Binder.span(it.quantity, perCopy) }).size else 0,
            pocketPerCopy = perCopy,
            layout = layout,
            searchNote = searchNote(compiled),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUi())

    fun update(f: (Filters) -> Filters) = _filters.update(f)
    fun clearFilters() = _filters.update { Filters(query = it.query, sort = it.sort, group = it.group) }

    // Saved and recent searches

    private val searches = SavedSearchStore(repo.appContext)
    val saved: StateFlow<List<SavedSearch>> = searches.saved
    val recent: StateFlow<List<String>> = searches.recent

    init {
        // A box with dividers lists A–Z inside each divider, like the real box, until you pick another order.
        if (!launched && boxId >= 0 && !hadSticky) viewModelScope.launch {
            val box = runCatching { repo.box(boxId) }.getOrNull()
            if (box != null && BoxLayout.of(box.divider).needsFacts) _filters.update { if (it.sort == SortBy.RECENT) it.copy(sort = SortBy.NAME) else it }
        }
        // A search word is remembered once you stop typing for a moment.
        viewModelScope.launch {
            _filters.map { it.query }.distinctUntilChanged().debounce(1500).collect { searches.addRecent(it) }
        }
        // Search terms only Scryfall can answer (f:, otag:…) are asked once the typing stops.
        viewModelScope.launch {
            _filters.map { ScryfallQuery.compile(it.query).asks.map { a -> a.first }.distinct() }
                .distinctUntilChanged().debounce(600)
                .collectLatest { asks -> asks.forEach { askScryfall(it) } }
        }
        // Remember the filters (not the search words) for next time, unless opened from a list or Home.
        if (!launched) viewModelScope.launch {
            _filters.map { it.copy(query = "", deckBox = null) }.distinctUntilChanged().drop(1).debounce(400).collect { f ->
                try { prefs?.edit()?.putString(stickyKey, json.encodeToString(Filters.serializer(), f))?.apply() } catch (e: Exception) { }
            }
        }
    }

    /**
     * Asks Scryfall for one part of a search, up to 10 pages, unless it's already answered. Scryfall's own
     * pause between requests is kept by [com.mtgscanner.data.ScryfallApi].
     */
    private suspend fun askScryfall(a: RemoteAsk) {
        if (ScryfallQueryCache.state(a) is ScryfallQueryCache.State.Done) return
        ScryfallQueryCache.put(a, ScryfallQueryCache.State.Loading)
        remoteTick.update { it + 1 }
        val result = try {
            val keys = HashSet<String>()
            var page = 1
            var partial = false
            while (true) {
                val list = repo.api.searchPage(a.query, order = "name", page = page, unique = if (a.prints) "prints" else "cards")
                list.data.forEach { keys += ScryfallQueryCache.keysOf(it.oracleId, it.name, it.set, it.collectorNumber, a.prints) }
                if (!list.hasMore) break
                if (page >= MAX_PAGES) { partial = true; break }
                page++
            }
            ScryfallQueryCache.State.Done(keys, partial)
        } catch (e: CancellationException) {
            ScryfallQueryCache.remove(a)
            throw e
        } catch (e: ScryfallQueryException) {
            ScryfallQueryCache.State.Failed(e.message)
        } catch (e: Exception) {
            ScryfallQueryCache.State.Failed(null)
        }
        ScryfallQueryCache.put(a, result)
        remoteTick.update { it + 1 }
    }

    /** Gives binder slots to cards without one, in name order after the last used slot. */
    fun numberSlots() {
        val ids = ui.value.binderCards.map { it.id }
        val n = ui.value.unslotted
        withUndo(ids, if (n == 1) "Numbered 1 card" else "Numbered $n cards") { repo.numberBinderSlots(boxId) }
    }

    /** Adds the cards about to move to the physical move list; returns the lines added. */
    private fun recordMoves(ids: List<Long>, to: Long, toName: String? = null): List<MoveLine> {
        val u = ui.value
        val byId = u.boxes.associateBy { it.id }
        val wanted = ids.toHashSet()
        val cards = u.all.filter { it.id in wanted }
        return MoveList.add(
            MoveList.linesFor(cards.map { it to to }) { id ->
                byId[id]?.let { Triple(it.name, it.kind, it.divider) } ?: toName?.takeIf { id == to }?.let { Triple(it, "deck", "") }
            },
        )
    }

    /** Decks, for Move. */
    val deckChoices: StateFlow<List<com.mtgscanner.data.db.Deck>> = repo.observeDecks()
        .map { ds -> ds.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Moves cards into a deck (its hidden slot box, see DeckSlots.kt), with Undo. */
    private fun moveIntoDeck(ids: List<Long>, deckId: Long, message: (String) -> String) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val name = deckChoices.value.firstOrNull { it.id == deckId }?.name ?: "deck"
            val slot = repo.deckSlotId(deckId)
            val lines = recordMoves(ids, slot, deckPlaceName(name))
            val before = repo.snapshot(ids)
            repo.moveMany(ids, slot)
            Undo.offer(message(name)) { repo.restore(before); MoveList.remove(lines) }
        }
    }

    fun moveSelectedToDeck(deckId: Long) {
        val ids = _selected.value.toList()
        clearSelection()
        moveIntoDeck(ids, deckId) { "Moved ${count(ids)} to $it" }
    }

    fun moveOneToDeck(id: Long, deckId: Long) = moveIntoDeck(listOf(id), deckId) { "Moved to $it" }

    /** Saved inside a box, the list remembers the box and counts only its cards. */
    fun saveSearch(name: String) = searches.save(name, _filters.value, boxId.takeIf { it >= 0 })
    fun deleteSaved(name: String) = searches.delete(name)
    fun applySaved(s: SavedSearch) { _filters.value = s.filters }
    fun clearRecent() = searches.clearRecent()

    // Picking several cards


    /** True after tapping Select, so picking mode shows even with nothing picked yet. */
    private val _selecting = MutableStateFlow(false)
    val selecting: StateFlow<Boolean> = _selecting

    fun startSelecting() {
        _selecting.value = true
        if (_showTip.value) dismissTip()
    }
    fun toggleSelect(id: Long) = _selected.update { if (id in it) it - id else it + id }
    fun selectAll() { _selected.value = ui.value.shownIds.toSet() }
    /** Drag-to-select: the selection as it now stands. */
    fun setSelection(ids: Set<Long>) { _selected.value = ids }
    fun clearSelection() {
        _selected.value = emptySet()
        _selecting.value = false
    }

    private fun boxName(id: Long) = ui.value.boxes.firstOrNull { it.id == id }?.name ?: "box"

    /** Runs a change on some cards and offers to undo it. */
    private fun withUndo(ids: List<Long>, message: String, onUndo: () -> Unit = {}, change: suspend () -> Unit) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val before = repo.snapshot(ids)
            change()
            Undo.offer(message) { repo.restore(before); onUndo() }
        }
    }

    /** Copies on the picked rows, so a stack of 4 reads "4 cards". */
    private fun count(ids: List<Long>): String {
        val qty = ui.value.groups.flatMap { it.second }.associate { it.id to it.quantity }
        val n = ids.distinct().sumOf { qty[it] ?: 1 }
        return if (n == 1) "1 card" else "$n cards"
    }

    fun moveSelected(boxId: Long) {
        val ids = _selected.value.toList()
        clearSelection()
        if (ids.isEmpty()) return
        val lines = recordMoves(ids, boxId)
        withUndo(ids, "Moved ${count(ids)} to ${boxName(boxId)}", { MoveList.remove(lines) }) { repo.moveMany(ids, boxId) }
    }

    fun deleteSelected() {
        val ids = _selected.value.toList()
        clearSelection()
        withUndo(ids, "Removed ${count(ids)}") { repo.deleteMany(ids) }
    }

    fun tagSelected(tag: String) {
        val ids = _selected.value.toList()
        clearSelection()
        withUndo(ids, "Tagged ${count(ids)} “${tag.trim()}”") { repo.addTag(ids, tag) }
    }

    /** Lists the picked copies for sale, like "For sale" on a card's page; they show up on the Sell screen. */
    fun sellSelected() {
        val ids = _selected.value.toList()
        clearSelection()
        withUndo(ids, "${count(ids)} for sale") {
            ids.forEach { id -> repo.cardOnce(id)?.let { if (!it.forSale) repo.update(it.copy(forSale = true)) } }
        }
    }

    /** "Stay in this box" for the picked cards: re-sort and "Sort out" leave them alone. */
    fun pinSelected(pin: Boolean) {
        val ids = _selected.value.toList()
        clearSelection()
        withUndo(ids, if (pin) "${count(ids)} stay put" else "${count(ids)} can move") { repo.setPinned(ids, pin) }
    }

    /** Sets condition, language and finish on the picked cards; null leaves one alone. */
    /** Bulk edit: anything left null stays as each card has it. [paid] is per copy. */
    fun editSelected(condition: String?, lang: String?, foil: Boolean?, paid: Double? = null, from: String? = null) {
        val ids = _selected.value.toList()
        clearSelection()
        if (condition == null && lang == null && foil == null && paid == null && from == null) return
        withUndo(ids, "Edited ${count(ids)}") {
            // Paid and "from" first: a finish or condition change can merge rows.
            if (paid != null || from != null) ids.forEach { id ->
                repo.cardOnce(id)?.let { c -> repo.update(c.copy(purchasePrice = paid ?: c.purchasePrice, acquiredFrom = from ?: c.acquiredFrom)) }
            }
            if (condition != null || lang != null || foil != null) repo.bulkEdit(ids, condition, lang, foil)
        }
    }

    /** One binder pocket per copy, or one per card. */
    fun setPocketPerCopy(on: Boolean) {
        if (boxId < 0) return
        viewModelScope.launch {
            repo.setPocketPerCopy(boxId, on)
            Undo.offer(if (on) "One pocket per copy" else "One pocket per card") { repo.setPocketPerCopy(boxId, !on) }
        }
    }

    fun moveOne(id: Long, boxId: Long) {
        val lines = recordMoves(listOf(id), boxId)
        withUndo(listOf(id), "Moved to ${boxName(boxId)}", { MoveList.remove(lines) }) { repo.moveToBox(id, boxId) }
    }
    /** Lists one card for sale (swipe left), with Undo. */
    fun sellOne(c: CollectionCard) {
        if (c.forSale) { Undo.offer("${c.name} is already for sale"); return }
        withUndo(listOf(c.id), "${c.name} for sale") {
            repo.cardOnce(c.id)?.let { if (!it.forSale) repo.update(it.copy(forSale = true)) }
        }
    }

    companion object {
        /** Everything the search box looks in, lower-case, one field per line so a match can't span two. */
        fun searchText(c: CollectionCard): String =
            listOf(c.name, c.typeLine, c.oracleText, c.setName, c.setCode, c.tags, c.artist.orEmpty(), c.userTags)
                .joinToString("\n") { it.lowercase() }

        fun matches(c: CollectionCard, f: Filters): Boolean = matches(c, f, null)

        /** Scryfall pages read for one search term; 175 cards each. */
        private const val MAX_PAGES = 10

        /** The line under the search box for [q], or null when there's nothing to say. */
        fun searchNote(q: com.mtgscanner.data.search.CompiledQuery): String? {
            if (q.invalid) return "Searched as plain text"
            val states = q.asks.map { (a, label) -> label to ScryfallQueryCache.state(a) }
            val bad = states.filter { it.second is ScryfallQueryCache.State.Failed }
            return when {
                bad.any { (it.second as ScryfallQueryCache.State.Failed).message != null } ->
                    "Scryfall can't read " + bad.map { it.first }.distinct().joinToString(", ")
                bad.isNotEmpty() -> "Needs Scryfall for " + bad.map { it.first }.distinct().joinToString(", ")
                states.any { it.second == null || it.second is ScryfallQueryCache.State.Loading } -> "Checking Scryfall…"
                states.any { (it.second as? ScryfallQueryCache.State.Done)?.partial == true } -> "Too many Scryfall hits; some left out"
                else -> null
            }
        }

        /**
         * [hay] is the card's [searchText], when already worked out. Plain words search it as before; Scryfall
         * syntax (t:elf c:g, box:, tag:…) is read by [ScryfallQuery], with [env] for box names, loans and Scryfall's answers.
         * Pass [compiled] (f.query already compiled) when checking many cards, so the query isn't looked up per card.
         */
        fun matches(
            c: CollectionCard,
            f: Filters,
            hay: String?,
            env: SearchEnv? = null,
            compiled: com.mtgscanner.data.search.CompiledQuery? = null,
        ): Boolean {
            if (f.query.isNotBlank()) {
                val q = compiled ?: ScryfallQuery.compile(f.query)
                val plain = q.plain
                if (plain != null) {
                    if (!(hay ?: searchText(c)).contains(plain)) return false
                } else if (!q.matches(c, env ?: SearchEnv())) return false
            }
            if (f.colors.isNotEmpty() && !CardColors.matches(f.colors, f.colorMatch, if (f.byIdentity) c.colorIdentity else c.colors)) return false
            if (f.rarities.isNotEmpty() && c.rarity !in f.rarities) {
                // Specials/bonus rarities count as mythic for filtering.
                if (!(c.rarity !in RARITIES && "mythic" in f.rarities)) return false
            }
            if (f.types.isNotEmpty() && f.types.none { c.typeLine.contains(it, ignoreCase = true) }) return false
            if (f.set != null && c.setCode != f.set) return false
            if (f.foilOnly && !c.foil) return false
            if (f.treatments.isNotEmpty() && f.treatments.none { it in c.treatments.split(',') }) return false
            if (f.userTags.isNotEmpty() && f.userTags.none { it in c.userTagList() }) return false
            if (f.flags.isNotEmpty() && !f.flags.all { com.mtgscanner.ui.home.TileFlags.matches(c, it) }) return false
            if (f.addedSince > 0 && c.addedAt < f.addedSince) return false
            if (f.deckBox != null && c.boxId != f.deckBox) return false
            return true
        }

        private val colorOrder = "WUBRG"
        private fun colorRank(c: CollectionCard): Int = when {
            c.colors.isEmpty() -> if (c.typeLine.contains("Land")) 8 else 7
            c.colors.length > 1 -> 6
            else -> colorOrder.indexOf(c.colors[0]).coerceAtLeast(0)
        }

        fun comparator(sort: SortBy): Comparator<CollectionCard> = when (sort) {
            SortBy.RECENT -> compareByDescending<CollectionCard> { it.addedAt }
            SortBy.NAME -> compareBy<CollectionCard> { NameKey.of(it.name) }
            SortBy.PRICE -> compareByDescending<CollectionCard> { it.unitPrice() ?: -1.0 }.thenBy { it.name }
            SortBy.MANA -> compareBy<CollectionCard> { it.cmc }.thenBy { it.name }
            SortBy.SET -> compareBy<CollectionCard> { it.setName }
                .thenBy { it.collectorNumber.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.collectorNumber }
            SortBy.COLOR -> compareBy<CollectionCard> { colorRank(it) }.thenBy { it.cmc }.thenBy { it.name }
            SortBy.GAIN -> compareByDescending<CollectionCard> { it.changeSinceAdded() ?: Double.NEGATIVE_INFINITY }.thenBy { it.name }
        }

        fun primaryType(c: CollectionCard): String =
            listOf("Creature", "Planeswalker", "Battle", "Instant", "Sorcery", "Artifact", "Enchantment", "Land")
                .firstOrNull { c.typeLine.contains(it) } ?: "Other"

        private fun colorName(c: CollectionCard): String = when {
            c.colors.isEmpty() -> "Colorless"
            c.colors.length > 1 -> "Multicolor"
            else -> mapOf('W' to "White", 'U' to "Blue", 'B' to "Black", 'R' to "Red", 'G' to "Green")[c.colors[0]] ?: "Colorless"
        }

        /**
         * Groups cards by the divider(s) they sit behind, front of the box first. With [inOrder], cards in each
         * group go in the box's card order (collector number or price), as in a box kept that way.
         */
        fun bySpot(cards: List<CollectionCard>, layout: BoxLayout, inOrder: Boolean = false): List<Pair<String, List<CollectionCard>>> {
            val order = Sorting.spotOrder(layout)
            val cardOrder = Sorting.cardOrder(layout.order)
            return cards.groupBy { Sorting.spot(layout, it.name, it.facts()) ?: "" }
                .toList().sortedWith(compareBy<Pair<String, List<CollectionCard>>, String>(order) { it.first })
                .map { (spot, list) -> spot to if (inOrder) list.sortedWith(compareBy(cardOrder) { it.name to it.facts() }) else list }
        }

        /** [cards] in the order they sit in a box: by divider, then by the box's card order. */
        fun boxOrder(cards: List<CollectionCard>, layout: BoxLayout): List<CollectionCard> =
            cards.sortedWith(compareBy(Sorting.boxOrder(layout)) { it.name to it.facts() })

        fun group(cards: List<CollectionCard>, by: GroupBy): List<Pair<String, List<CollectionCard>>> = when (by) {
            GroupBy.NONE -> if (cards.isEmpty()) emptyList() else listOf("" to cards)
            GroupBy.SET -> cards.groupBy { "${it.setName} (${it.setCode})" }.toSortedMap().toList()
            GroupBy.COLOR -> cards.groupBy(::colorName).toList().sortedBy {
                listOf("White", "Blue", "Black", "Red", "Green", "Multicolor", "Colorless").indexOf(it.first)
            }
            GroupBy.TYPE -> cards.groupBy(::primaryType).toList().sortedBy {
                listOf("Creature", "Planeswalker", "Battle", "Instant", "Sorcery", "Artifact", "Enchantment", "Land", "Other").indexOf(it.first)
            }
            GroupBy.RARITY -> cards.groupBy { it.rarity.replaceFirstChar(Char::uppercase) }.toList().sortedBy {
                listOf("Mythic", "Rare", "Uncommon", "Common").indexOf(it.first).let { i -> if (i < 0) 4 else i }
            }
        }
    }
}
