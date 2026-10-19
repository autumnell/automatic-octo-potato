package com.mtgscanner.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class SortBy(val label: String) {
    RECENT("Recently added"), NAME("Name"), PRICE("Price"), MANA("Mana value"), SET("Set & number"), COLOR("Color"),
}

enum class GroupBy(val label: String) { NONE("No grouping"), SET("Set"), COLOR("Color"), TYPE("Card type"), RARITY("Rarity") }

val COLOR_FILTERS = listOf("W", "U", "B", "R", "G", "C", "M")
val RARITIES = listOf("common", "uncommon", "rare", "mythic")
val CARD_TYPES = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land", "Battle")

data class Filters(
    val query: String = "",
    val colors: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val set: String? = null,
    val foilOnly: Boolean = false,
    val sort: SortBy = SortBy.RECENT,
    val group: GroupBy = GroupBy.NONE,
) {
    val activeCount: Int get() = colors.size + rarities.size + types.size + (if (set != null) 1 else 0) + (if (foilOnly) 1 else 0)
}

data class BrowseUi(
    val title: String = "Collection",
    val groups: List<Pair<String, List<CollectionCard>>> = emptyList(),
    val shownCards: Int = 0,
    val shownValue: Double = 0.0,
    val totalCards: Int = 0,
    val sets: List<Pair<String, String>> = emptyList(),
    val loaded: Boolean = false,
)

/** The card list for one box, or the whole collection when [boxId] is -1. */
class BrowseViewModel(private val repo: CollectionRepository, private val boxId: Long) : ViewModel() {
    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters

    private val _grid = MutableStateFlow(true)
    val grid: StateFlow<Boolean> = _grid
    fun toggleGrid() { _grid.value = !_grid.value }

    val ui: StateFlow<BrowseUi> = combine(repo.observeAll(), repo.observeBoxes(), _filters) { all, boxes, f ->
        val inScope = if (boxId < 0) all else all.filter { it.boxId == boxId }
        val shown = inScope.filter { matches(it, f) }.sortedWith(comparator(f.sort))
        BrowseUi(
            title = if (boxId < 0) "All cards" else boxes.firstOrNull { it.id == boxId }?.name ?: "Box",
            groups = group(shown, f.group),
            shownCards = shown.sumOf { it.quantity },
            shownValue = shown.sumOf { it.totalPrice() },
            totalCards = inScope.sumOf { it.quantity },
            sets = inScope.map { it.setCode to it.setName }.distinct().sortedBy { it.second },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUi())

    fun update(f: (Filters) -> Filters) = _filters.update(f)
    fun clearFilters() = _filters.update { Filters(query = it.query, sort = it.sort, group = it.group) }

    companion object {
        fun matches(c: CollectionCard, f: Filters): Boolean {
            if (f.query.isNotBlank()) {
                val q = f.query.trim().lowercase()
                val hay = listOf(c.name, c.typeLine, c.oracleText, c.setName, c.setCode, c.tags, c.artist.orEmpty())
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
        }

        fun primaryType(c: CollectionCard): String =
            listOf("Creature", "Planeswalker", "Battle", "Instant", "Sorcery", "Artifact", "Enchantment", "Land")
                .firstOrNull { c.typeLine.contains(it) } ?: "Other"

        private fun colorName(c: CollectionCard): String = when {
            c.colors.isEmpty() -> "Colorless"
            c.colors.length > 1 -> "Multicolor"
            else -> mapOf('W' to "White", 'U' to "Blue", 'B' to "Black", 'R' to "Red", 'G' to "Green")[c.colors[0]] ?: "Colorless"
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
