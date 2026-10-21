package com.mtgscanner.ui.collection

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.db.Box
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
import com.mtgscanner.data.Placement
import com.mtgscanner.data.numberBinderSlots
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.Divider
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.facts
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.changeSinceAdded
import com.mtgscanner.data.db.roleList
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import com.mtgscanner.data.db.nearMintPrice
import java.time.LocalDate

@Serializable
enum class SortBy(val label: String) {
    RECENT("Recently added"), NAME("Name"), PRICE("Price"), MANA("Mana value"), SET("Set & number"), COLOR("Color"), GAIN("Price change"),
    ;

    val isPrice: Boolean get() = this == PRICE || this == GAIN
}

@Serializable
enum class GroupBy(val label: String) { NONE("No grouping"), SET("Set"), COLOR("Color"), TYPE("Card type"), RARITY("Rarity") }

val COLOR_FILTERS = listOf("W", "U", "B", "R", "G", "C", "M")
val RARITIES = listOf("common", "uncommon", "rare", "mythic")
val CARD_TYPES = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land", "Battle")

@Serializable
data class Filters(
    val query: String = "",
    val colors: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val set: String? = null,
    val foilOnly: Boolean = false,
    /** Scryfall community tags such as Removal or Ramp. */
    val roles: Set<String> = emptySet(),
    val treatments: Set<String> = emptySet(),
    /** Your own tags. */
    val userTags: Set<String> = emptySet(),
    val sort: SortBy = SortBy.RECENT,
    val group: GroupBy = GroupBy.NONE,
) {
    val activeCount: Int get() = colors.size + rarities.size + types.size + roles.size + treatments.size + userTags.size + (if (set != null) 1 else 0) + (if (foilOnly) 1 else 0)
}

data class BrowseUi(
    val title: String = "Collection",
    val groups: List<Pair<String, List<CollectionCard>>> = emptyList(),
    val shownCards: Int = 0,
    val shownValue: Double = 0.0,
    val totalCards: Int = 0,
    val sets: List<Pair<String, String>> = emptyList(),
    /** Roles found on cards in view, for the filter sheet. */
    val roles: List<String> = emptyList(),
    val treatments: List<String> = emptyList(),
    /** Your own tags on cards in view. */
    val userTags: List<String> = emptyList(),
    val boxes: List<Box> = emptyList(),
    /** Ids of every card shown, for "select all". */
    val shownIds: List<Long> = emptyList(),
    val loaded: Boolean = false,
    /** Every card, unfiltered, to say where a card sits in its box. */
    val all: List<CollectionCard> = emptyList(),
    /** This box is a binder, so it has pages. */
    val binder: Boolean = false,
    /** The binder's cards, ignoring filters, for the page view. */
    val binderCards: List<CollectionCard> = emptyList(),
    /** Binder cards without a slot yet. */
    val unslotted: Int = 0,
)

/** The card list for one box, or the whole collection when [boxId] is -1. */
@OptIn(FlowPreview::class)
class BrowseViewModel(private val repo: CollectionRepository, private val boxId: Long) : ViewModel() {
    private val prefs: SharedPreferences? = try {
        repo.appContext.getSharedPreferences("browse", Context.MODE_PRIVATE)
    } catch (e: Exception) { null }

    private val json = Json { ignoreUnknownKeys = true }
    private val stickyKey = "filters-$boxId"

    /** Opened from Home or a smart list with its own filters, rather than plain. */
    private val launched: Boolean = boxId < 0 && (BrowseLaunch.filters != null || BrowseLaunch.sort != null || BrowseLaunch.color != null)

    /** A smart list's name, when one was opened. */
    private val listTitle: String? = if (boxId < 0) BrowseLaunch.takeTitle() else null

    /** The last filters used on this box, so they're back next time (sticky filters). */
    private fun loadSticky(): Filters? = try {
        prefs?.getString(stickyKey, null)?.let { json.decodeFromString(Filters.serializer(), it) }
    } catch (e: Exception) { null }

    private val _filters = MutableStateFlow(if (launched) BrowseLaunch.take() else loadSticky() ?: Filters())
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

    /** A box shown as a fanned stack of cards, one at a time. */
    private val _stack = MutableStateFlow(boxId >= 0 && pref("stack", false))
    val stack: StateFlow<Boolean> = _stack
    fun toggleStack() {
        _stack.value = !_stack.value
        setPref("stack", _stack.value)
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
        val box: Box?,
        val inScope: List<CollectionCard>,
        /** Lower-case search text per card, same order as [inScope]. */
        val hay: List<String>,
        val totalCards: Int,
        val sets: List<Pair<String, String>>,
        val roles: List<String>,
        val treatments: List<String>,
        val userTags: List<String>,
    )

    private val scope = combine(repo.observeAll(), repo.observeBoxes()) { all, boxes ->
        val inScope = if (boxId < 0) all else all.filter { it.boxId == boxId }
        Scope(
            all = all,
            boxes = boxes,
            box = if (boxId < 0) null else boxes.firstOrNull { it.id == boxId },
            inScope = inScope,
            hay = inScope.map(::searchText),
            totalCards = inScope.sumOf { it.quantity },
            sets = inScope.map { it.setCode to it.setName }.distinct().sortedBy { it.second },
            roles = inScope.flatMap { it.roleList() }.distinct().sortedBy { r -> CollectionRepository.ROLES.indexOfFirst { it.label == r } },
            treatments = inScope.flatMap { it.treatments.split(',') }.filter { it.isNotBlank() }.distinct(),
            userTags = inScope.flatMap { it.userTagList() }.distinct().sortedBy { it.lowercase() },
        )
    }.flowOn(Dispatchers.Default)

    /** The filters, with typing in the search box settling for a moment before the list redoes itself. */
    private var lastQuery = _filters.value.query
    private val applied = _filters.debounce { f ->
        if (f.query != lastQuery) { lastQuery = f.query; 150L } else 0L
    }

    val ui: StateFlow<BrowseUi> = combine(scope, applied) { sc, f ->
        val shown = sc.inScope.indices.filter { matches(sc.inScope[it], f, sc.hay[it]) }.map { sc.inScope[it] }
            .sortedWith(comparator(f.sort))
        val box = sc.box
        // A box with dividers lists its cards by divider, matching the physical box.
        val divider = Divider.of(box?.divider.orEmpty())
        val binder = box != null && Binder.isBinder(box.kind)
        val shownIds = shown.map { it.id }
        // Picked cards the filters now hide are dropped from the selection.
        val shownSet = shownIds.toHashSet()
        _selected.update { sel -> if (sel.all { it in shownSet }) sel else sel.filterTo(HashSet()) { it in shownSet } }
        BrowseUi(
            title = if (boxId < 0) listTitle ?: "All cards" else box?.name ?: "Box",
            groups = when {
                f.group == GroupBy.NONE && divider != Divider.NONE -> bySpot(shown, divider)
                // Long plain lists get headers by letter or set.
                f.group == GroupBy.NONE -> ListSections.sections(shown, f.sort) ?: group(shown, f.group)
                else -> group(shown, f.group)
            },
            shownCards = shown.sumOf { it.quantity },
            shownValue = shown.sumOf { it.totalPrice() },
            totalCards = sc.totalCards,
            sets = sc.sets,
            roles = sc.roles,
            treatments = sc.treatments,
            userTags = sc.userTags,
            boxes = sc.boxes,
            shownIds = shownIds,
            loaded = true,
            all = sc.all,
            binder = binder,
            binderCards = if (binder) sc.inScope.sortedBy { it.binderSlot } else emptyList(),
            unslotted = if (binder) sc.inScope.count { it.binderSlot <= 0 } else 0,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUi())

    /** 30-day price lines by card row id, for the sparklines; only cards with two or more points. */
    val sparks: StateFlow<Map<Long, List<Double>>> = scope.map { it.inScope }
        .distinctUntilChanged()
        .map { cards ->
            val history = PriceTrends.history(repo, cards.mapTo(HashSet()) { it.scryfallId })
            val today = LocalDate.now().toString()
            val out = HashMap<Long, List<Double>>()
            for (c in cards) {
                val pts = PriceTrends.sparkPoints(history[c.scryfallId].orEmpty(), c.foil, c.nearMintPrice(), today)
                if (pts.size >= 2) out[c.id] = pts
            }
            out as Map<Long, List<Double>>
        }
        .catch { emit(emptyMap()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun update(f: (Filters) -> Filters) = _filters.update(f)
    fun clearFilters() = _filters.update { Filters(query = it.query, sort = it.sort, group = it.group) }

    // Saved and recent searches

    private val searches = SavedSearchStore(repo.appContext)
    val saved: StateFlow<List<SavedSearch>> = searches.saved
    val recent: StateFlow<List<String>> = searches.recent

    init {
        // A search word is remembered once you stop typing for a moment.
        viewModelScope.launch {
            _filters.map { it.query }.distinctUntilChanged().debounce(1500).collect { searches.addRecent(it) }
        }
        // Remember the filters (not the search words) for next time, unless opened from a list or Home.
        if (!launched) viewModelScope.launch {
            _filters.map { it.copy(query = "") }.distinctUntilChanged().drop(1).debounce(400).collect { f ->
                try { prefs?.edit()?.putString(stickyKey, json.encodeToString(Filters.serializer(), f))?.apply() } catch (e: Exception) { }
            }
        }
    }

    /** Where a card sits: its box, divider and neighbors, or binder page and slot. */
    fun whereIs(c: CollectionCard): String {
        val u = ui.value
        return Placement.whereIs(c, u.boxes.firstOrNull { it.id == c.boxId }, u.all.filter { it.boxId == c.boxId })
    }

    /** Gives binder slots to cards without one, in name order after the last used slot. */
    fun numberSlots() {
        val ids = ui.value.binderCards.map { it.id }
        val n = ui.value.unslotted
        withUndo(ids, if (n == 1) "Numbered 1 card" else "Numbered $n cards") { repo.numberBinderSlots(boxId) }
    }

    /** Adds the cards about to move to the physical move list; returns the lines added. */
    private fun recordMoves(ids: List<Long>, to: Long): List<MoveLine> {
        val u = ui.value
        val byId = u.boxes.associateBy { it.id }
        val wanted = ids.toHashSet()
        val cards = u.all.filter { it.id in wanted }
        return MoveList.add(MoveList.linesFor(cards.map { it to to }) { id -> byId[id]?.let { Triple(it.name, it.kind, it.divider) } })
    }

    fun saveSearch(name: String) = searches.save(name, _filters.value)
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

    private fun count(ids: List<Long>): String =
        if (ids.size == 1) "1 card" else "${ids.size} cards"

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

    fun deleteOne(id: Long, name: String) = withUndo(listOf(id), "Removed $name") { repo.deleteMany(listOf(id)) }

    companion object {
        /** Everything the search box looks in, lower-case, one field per line so a match can't span two. */
        fun searchText(c: CollectionCard): String =
            listOf(c.name, c.typeLine, c.oracleText, c.setName, c.setCode, c.tags, c.artist.orEmpty(), c.userTags)
                .joinToString("\n") { it.lowercase() }

        fun matches(c: CollectionCard, f: Filters): Boolean = matches(c, f, null)

        /** [hay] is the card's [searchText], when already worked out. */
        fun matches(c: CollectionCard, f: Filters, hay: String?): Boolean {
            if (f.query.isNotBlank()) {
                val q = f.query.trim().lowercase()
                if (!(hay ?: searchText(c)).contains(q)) return false
            }
            if (f.colors.isNotEmpty()) {
                val ok = f.colors.any { sel ->
                    when (sel) {
                        "C" -> c.colors.isEmpty()
                        "M" -> c.colors.length > 1
                        else -> c.colors.contains(sel)
                    }
                }
                if (!ok) return false
            }
            if (f.rarities.isNotEmpty() && c.rarity !in f.rarities) {
                // Specials/bonus rarities count as mythic for filtering.
                if (!(c.rarity !in RARITIES && "mythic" in f.rarities)) return false
            }
            if (f.types.isNotEmpty() && f.types.none { c.typeLine.contains(it, ignoreCase = true) }) return false
            if (f.set != null && c.setCode != f.set) return false
            if (f.foilOnly && !c.foil) return false
            if (f.roles.isNotEmpty() && f.roles.none { it in c.roleList() }) return false
            if (f.treatments.isNotEmpty() && f.treatments.none { it in c.treatments.split(',') }) return false
            if (f.userTags.isNotEmpty() && f.userTags.none { it in c.userTagList() }) return false
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
            SortBy.NAME -> compareBy<CollectionCard> { it.name.lowercase() }
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

        /** Groups cards by the divider they sit behind, front of the box first. */
        fun bySpot(cards: List<CollectionCard>, divider: Divider): List<Pair<String, List<CollectionCard>>> {
            val order = Sorting.spotOrder(divider)
            return cards.groupBy { Sorting.spot(divider, it.name, it.facts()) ?: "" }
                .toList().sortedWith(compareBy<Pair<String, List<CollectionCard>>, String>(order) { it.first })
        }

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
