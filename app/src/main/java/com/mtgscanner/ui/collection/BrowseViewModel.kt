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
)

/** The card list for one box, or the whole collection when [boxId] is -1. */
@OptIn(FlowPreview::class)
class BrowseViewModel(private val repo: CollectionRepository, private val boxId: Long) : ViewModel() {
    private val _filters = MutableStateFlow(if (boxId < 0) BrowseLaunch.take() else Filters())
    val filters: StateFlow<Filters> = _filters

    /** A smart list's name, when one was opened. */
    private val listTitle: String? = if (boxId < 0) BrowseLaunch.takeTitle() else null

    private val prefs: SharedPreferences? = try {
        repo.appContext.getSharedPreferences("browse", Context.MODE_PRIVATE)
    } catch (e: Exception) { null }

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

    /** A one-time tip about long-press and swipe. */
    private val _showTip = MutableStateFlow(!pref("tipDone", false))
    val showTip: StateFlow<Boolean> = _showTip
    fun dismissTip() {
        _showTip.value = false
        setPref("tipDone", true)
    }

    val ui: StateFlow<BrowseUi> = combine(repo.observeAll(), repo.observeBoxes(), _filters) { all, boxes, f ->
        val inScope = if (boxId < 0) all else all.filter { it.boxId == boxId }
        val shown = inScope.filter { matches(it, f) }.sortedWith(comparator(f.sort))
        val box = if (boxId < 0) null else boxes.firstOrNull { it.id == boxId }
        // A box with dividers lists its cards by divider, matching the physical box.
        val divider = Divider.of(box?.divider.orEmpty())
        BrowseUi(
            title = if (boxId < 0) listTitle ?: "All cards" else box?.name ?: "Box",
            groups = if (f.group == GroupBy.NONE && divider != Divider.NONE) bySpot(shown, divider) else group(shown, f.group),
            shownCards = shown.sumOf { it.quantity },
            shownValue = shown.sumOf { it.totalPrice() },
            totalCards = inScope.sumOf { it.quantity },
            sets = inScope.map { it.setCode to it.setName }.distinct().sortedBy { it.second },
            roles = inScope.flatMap { it.roleList() }.distinct().sortedBy { r -> CollectionRepository.ROLES.indexOfFirst { it.label == r } },
            treatments = inScope.flatMap { it.treatments.split(',') }.filter { it.isNotBlank() }.distinct(),
            userTags = inScope.flatMap { it.userTagList() }.distinct().sortedBy { it.lowercase() },
            boxes = boxes,
            shownIds = shown.map { it.id },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUi())

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
    }

    fun saveSearch(name: String) = searches.save(name, _filters.value)
    fun deleteSaved(name: String) = searches.delete(name)
    fun applySaved(s: SavedSearch) { _filters.value = s.filters }
    fun clearRecent() = searches.clearRecent()

    // Picking several cards

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected

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
    private fun withUndo(ids: List<Long>, message: String, change: suspend () -> Unit) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val before = repo.snapshot(ids)
            change()
            Undo.offer(message) { repo.restore(before) }
        }
    }

    private fun count(ids: List<Long>): String =
        if (ids.size == 1) "1 card" else "${ids.size} cards"

    fun moveSelected(boxId: Long) {
        val ids = _selected.value.toList()
        clearSelection()
        withUndo(ids, "Moved ${count(ids)} to ${boxName(boxId)}") { repo.moveMany(ids, boxId) }
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

    fun moveOne(id: Long, boxId: Long) = withUndo(listOf(id), "Moved to ${boxName(boxId)}") { repo.moveToBox(id, boxId) }
    fun deleteOne(id: Long, name: String) = withUndo(listOf(id), "Removed $name") { repo.deleteMany(listOf(id)) }

    companion object {
        fun matches(c: CollectionCard, f: Filters): Boolean {
            if (f.query.isNotBlank()) {
                val q = f.query.trim().lowercase()
                val hay = listOf(c.name, c.typeLine, c.oracleText, c.setName, c.setCode, c.tags, c.artist.orEmpty(), c.userTags)
                if (hay.none { it.lowercase().contains(q) }) return false
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
