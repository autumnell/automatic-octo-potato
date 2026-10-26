package com.mtgscanner.ui.home

import com.mtgscanner.ui.components.formatPrimary
import com.mtgscanner.ui.components.primaryValue
import com.mtgscanner.data.db.totalEur
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.GoalProgress
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PendingScan
import com.mtgscanner.data.db.ValueSnapshot
import com.mtgscanner.data.db.changeSinceAdded
import com.mtgscanner.data.db.priceWhenAdded
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.data.observeGoalProgress
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.collection.BrowseLaunch
import com.mtgscanner.ui.collection.BrowseViewModel
import com.mtgscanner.ui.collection.Filters
import com.mtgscanner.ui.collection.GroupBy
import com.mtgscanner.ui.collection.SavedSearchStore
import com.mtgscanner.ui.collection.SortBy
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.cardPriceShort
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.work.CardAlertStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import com.mtgscanner.ui.components.RollingText
import com.mtgscanner.ui.theme.PriceStyle
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.theme.ThemeSettings
import com.mtgscanner.ui.theme.AccentMath
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.ui.detail.sharedCard

data class HomeUi(
    val loaded: Boolean = false,
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
    val totalValue: Double = 0.0,
    /** [totalValue] in euros. */
    val totalEur: Double = 0.0,
    val sets: Int = 0,
    val foils: Int = 0,
    val recent: List<CollectionCard> = emptyList(),
    /** What the cards were worth when added (only cards with a known price then). */
    val valueAtAdd: Double = 0.0,
    /** Today's value of those same cards. */
    val valueNowTracked: Double = 0.0,
    val history: List<ValueSnapshot> = emptyList(),
    /** Biggest gains since added, for the "Since added" side of Price moves. */
    val gainers: List<CollectionCard> = emptyList(),
    val boxCount: Int = 0,
    /** Art crop of the most valuable card, shown faintly behind the value card. */
    val heroArt: String? = null,
    /** The color (W/U/B/R/G) with the most copies by color identity, for "Accent from my collection". */
    val colorLetter: Char? = null,
    /** Copies by card color ([ColorSplit.KEYS]), for the color bar; colors with none are left out. */
    val colorSplit: List<Pair<String, Int>> = emptyList(),
)

/** Worked out from price history: this week's movers. */
data class PriceData(val movers: List<PriceMover> = emptyList())

class HomeViewModel(private val repo: CollectionRepository) : ViewModel() {
    val ui: StateFlow<HomeUi> = combine(repo.observeAll(), repo.observeSnapshots(), repo.observeBoxes()) { all, history, boxes ->
        HomeSummary.of(all).copy(history = history, boxCount = boxes.size)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    /** Cards to move and unknown scans, for "To be sorted". */
    val toSort: StateFlow<ToSortUi> = combine(repo.observeAll(), repo.observeBoxes(), repo.observePending()) { cards, boxes, pending ->
        ToSortUi(
            items = runCatching { repo.cardsToSort(cards, boxes) }.getOrDefault(emptyList()),
            unknown = pending.filter { it.unknown },
            boxNames = boxes.associate { it.id to it.name },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ToSortUi())

    /** Price history, and what it was loaded for: the day, the latest snapshot and the printings owned. */
    private var pointsCache: Pair<Any, List<com.mtgscanner.data.db.PricePoint>>? = null

    /**
     * Recomputed when cards change or prices refresh (a refresh writes a new snapshot). The price
     * history table is only re-read when the day, the snapshots or the set of printings owned change,
     * not on every move or quantity edit.
     */
    val prices: StateFlow<PriceData> = combine(repo.observeAll(), repo.observeSnapshots()) { all, snaps -> all to snaps }
        .map { (all, snaps) ->
            val today = java.time.LocalDate.now()
            val key = listOf(today.toString(), snaps.size, snaps.maxByOrNull { it.day }, all.mapTo(HashSet()) { it.scryfallId })
            val points = pointsCache?.takeIf { it.first == key }?.second
                ?: runCatching { repo.dao.pricePointsSince(today.minusDays(120).toString()) }.getOrNull()?.also { pointsCache = key to it }
                ?: emptyList()
            val weekAgo = today.minusDays(7).toString()
            PriceData(HomeMath.movers(all, points, weekAgo))
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PriceData())

    private val searches = SavedSearchStore(repo.appContext)

    /** Saved search names, for the "Saved list" tile. */
    val listNames: StateFlow<List<String>> = searches.saved.map { l -> l.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The saved search the "Saved list" tile counts, or null when that tile isn't shown. */
    private val tileList = snapshotFlow { HomePrefs.tileList.takeIf { TileMetric.CUSTOM in HomePrefs.tiles } }

    /** Numbers for the stat tiles. */
    val tiles: StateFlow<TileData> = combine(repo.observeAll(), repo.observeBoxes(), repo.observeWishlist(), searches.saved, tileList) { all, boxes, wish, saved, listName ->
        TileData(
            values = TileMath.counts(all, boxes.size, wish.sumOf { it.wanted }, System.currentTimeMillis()),
            lists = run {
                // Only the one search a tile shows is counted: matching every saved search costs a pass each.
                val s = saved.firstOrNull { it.name == listName } ?: return@run emptyMap()
                // box: in a saved search needs the box names.
                val boxNames = boxes.associate { it.id to it.name }
                val env = com.mtgscanner.data.search.SearchEnv(boxName = { boxNames[it] })
                mapOf(s.name to all.filter { (s.boxId == null || it.boxId == s.boxId) && BrowseViewModel.matches(it, s.filters, null, env) }.sumOf { it.quantity })
            },
            eur = TileMath.eurCounts(all),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TileData())

    /** Per-card price alerts, with the card each one is for. */
    val alerts: StateFlow<List<AlertRow>> = run {
        CardAlertStore.load(repo.appContext)
        combine(CardAlertStore.alerts, repo.observeAll()) { list, all -> alertRows(list, all) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /** Decks whose played cards are all owned, by id and name, for the "is complete" note. */
    val completeDecks: StateFlow<List<Pair<Long, String>>> = combine(repo.observeDecks(), repo.observeOwnedByName()) { decks, owned ->
        decks.filter { com.mtgscanner.ui.decks.DeckOwnership.complete(com.mtgscanner.data.ParsedDecks.of(it), owned) }
            .map { it.id to it.name }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A saved search's filters, by name. */
    fun savedFilters(name: String): Filters? = searches.saved.value.firstOrNull { it.name == name }?.filters

    val goals: StateFlow<List<GoalProgress>> = repo.observeGoalProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Moves every card in "To be sorted" to its box, with Undo. */
    fun sortAll() = viewModelScope.launch {
        val items = toSort.value.items
        if (items.isEmpty()) return@launch
        // Moving merges into rows already in the target box, so undo restores a snapshot instead of moving back.
        val before = repo.snapshot(items.map { it.card.id })
        val n = repo.applyMoves(items.map { it.card.id to it.to }).size
        Undo.offer(if (n == 1) "Moved 1 card" else "Moved $n cards") { repo.restore(before) }
    }

    /** Cards matching [text]; null when Scryfall couldn't be reached (offline), so it isn't shown as "No match". */
    suspend fun searchCards(text: String): List<ScryfallCard>? {
        val q = text.trim()
        if (q.length < 2) return emptyList()
        val search = runCatching { repo.api.search(q) }
        val found = search.getOrNull().orEmpty().take(20)
        if (found.isNotEmpty()) return found
        val named = runCatching { repo.api.named(q) }
        named.getOrNull()?.let { return listOf(it) }
        return if (search.isFailure && named.isFailure) null else emptyList()
    }

    /** Adds the card picked for an unknown scan and clears the scan. */
    fun resolveUnknown(scan: PendingScan, card: ScryfallCard) = viewModelScope.launch {
        val foil = scan.foil && card.hasFoil || !card.hasNonFoil
        val box = if (scan.boxId != CardBox.AUTO_SORT && repo.box(scan.boxId) != null) scan.boxId
        else repo.pickBox(card, foil) ?: repo.anythingBox()
        val qty = scan.quantity.coerceAtLeast(1)
        val row = repo.add(card, foil, qty, box, lang = if (card.lang != "en") card.lang else scan.lang ?: "en")
        repo.deletePending(scan.id)
        Undo.offer("Added ${card.name}") {
            repo.remove(row, qty)
            repo.dao.addPending(scan)
        }
    }

    fun deleteUnknown(scan: PendingScan) = viewModelScope.launch {
        repo.deletePending(scan.id)
        // Put the same scan back, quantity and all.
        Undo.offer("Scan deleted") { repo.dao.addPending(scan) }
    }
}
