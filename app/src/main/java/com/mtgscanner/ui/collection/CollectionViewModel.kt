package com.mtgscanner.ui.collection

import com.mtgscanner.ui.decks.DeckOwnership
import com.mtgscanner.util.CardNames
import com.mtgscanner.data.cheapestPrinting
import com.mtgscanner.data.DeckCheck
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.undoWishes
import com.mtgscanner.data.wishSnapshot
import com.mtgscanner.ui.components.moneySuffix
import com.mtgscanner.ui.components.formatMoneyShort
import com.mtgscanner.data.db.totalEur
import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mtgscanner.data.BoxArt
import com.mtgscanner.data.BoxChange
import com.mtgscanner.data.planBoxes
import com.mtgscanner.data.saveBoxChanges
import com.mtgscanner.data.undoBoxChanges
import com.mtgscanner.data.BoxCovers
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.BoxStats
import com.mtgscanner.data.BoxLayout
import com.mtgscanner.data.BoxTemplate
import com.mtgscanner.data.Divider
import com.mtgscanner.data.RuleCheck
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.Found
import com.mtgscanner.data.LastChecked
import com.mtgscanner.data.LastCheckedMath
import com.mtgscanner.data.findCard
import com.mtgscanner.data.boxToDeck
import com.mtgscanner.ui.home.SortItem
import com.mtgscanner.ui.home.cardsToSort
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.BoxDraft
import com.mtgscanner.ui.components.BoxEditorSheet
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.panelRow
import com.mtgscanner.ui.decks.DecksTab
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.StatTile
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OverviewUi(
    val totalCards: Int = 0,
    val totalValue: Double = 0.0,
    /** [totalValue] in euros. */
    val totalEur: Double = 0.0,
    val boxes: List<BoxSummary> = emptyList(),
    /** Value change and cover art per box id. */
    val stats: Map<Long, BoxStats> = emptyMap(),
)

class CollectionViewModel(private val repo: CollectionRepository) : ViewModel() {
    private val covers = BoxCovers(repo.appContext)

    init { MoveList.init(repo.appContext) }

    val ui: StateFlow<OverviewUi> = combine(repo.observeAll(), repo.observeBoxSummaries(), covers.covers) { all, boxes, cv ->
        OverviewUi(all.sumOf { it.quantity }, all.sumOf { it.totalPrice() }, all.sumOf { it.totalEur() }, boxes, BoxArt.stats(all, cv))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUi())

    /** Price history by printing, and what it was loaded for: the day, the latest snapshot and the printings owned. */
    private var pointsCache: Pair<Any, Map<String, List<com.mtgscanner.data.db.PricePoint>>>? = null

    /**
     * The collection's value change over the last week from price history, or null without a week of it.
     * A price refresh writes a snapshot, so it's worked out again then too. The history table is only
     * re-read when the day, the snapshots or the printings owned change, not on every move or edit.
     */
    val weekChange: StateFlow<Double?> = combine(repo.observeAll(), repo.observeSnapshots()) { all, snaps -> all to snaps }
        .map { (all, snaps) ->
            val today = java.time.LocalDate.now()
            val weekAgo = today.minusDays(WeekChange.DAYS)
            val oldest = weekAgo.minusDays(WeekChange.SLACK_DAYS)
            val key = listOf(today.toString(), snaps.size, snaps.maxByOrNull { it.day }, all.mapTo(HashSet()) { it.scryfallId })
            val points = pointsCache?.takeIf { it.first == key }?.second
                ?: runCatching { repo.dao.pricePointsSince(oldest.toString()) }.getOrNull()
                    ?.groupBy { it.scryfallId }?.also { pointsCache = key to it }
                ?: emptyMap()
            val rows = all.filter { !it.proxy }.map { c ->
                WeekChange.Row(c.nearMintPrice(), c.quantity, historyPairs(points[c.scryfallId].orEmpty(), c.foil))
            }
            WeekChange.total(rows, weekAgo.toString(), oldest.toString())
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** [cardId] null goes back to the box's most valuable card. */
    fun setCover(boxId: Long, cardId: Long?) = covers.set(boxId, cardId)

    val wishlist: StateFlow<List<WishlistItem>> = repo.observeWishlist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    fun createBox(d: BoxDraft) = viewModelScope.launch {
        val id = repo.createBox(d.name, d.kind, d.rules, d.color)
        if (d.capacity > 0 || d.divider.isNotEmpty()) repo.setBoxLayout(id, d.capacity, d.divider)
    }

    /** Saves a box. Cards that now belong elsewhere show in the "to sort" strip. */
    fun updateBox(id: Long, d: BoxDraft) = viewModelScope.launch {
        repo.updateBox(id, d.name, d.kind, d.rules, d.color, capacity = d.capacity, divider = d.divider)
    }

    /** "To be sorted": cards the rules send to another box, and cards in catch-all boxes that fit a real one. */
    val toSort: StateFlow<List<SortItem>> = combine(repo.observeAll(), repo.observeBoxes()) { cards, boxes ->
        runCatching { repo.cardsToSort(cards, boxes) }.getOrDefault(emptyList())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Boxes as stored, for the shelf spot of cards found by the search. */
    val boxRows: StateFlow<List<CardBox>> = repo.observeBoxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cards whose name contains [query], each with its box, divider and spot. */
    suspend fun findCards(query: String): List<Found> = withContext(Dispatchers.Default) { repo.findCard(query) }

    /** Copies of a card spread over boxes (see BoxCleanup.kt). */
    suspend fun spread(byPrinting: Boolean): List<SpreadRow> = runCatching { repo.spreadCopies(byPrinting) }.getOrDefault(emptyList())

    /** Cards under the bulk floor still in a value box, with where they go now. */
    suspend fun cheapInValue(): List<SortItem> = runCatching { repo.cheapInValueBoxes() }.getOrDefault(emptyList())

    /** Every card, by name, for sharing the whole collection. */
    suspend fun allCards(): List<CollectionCard> = repo.allCards().sortedBy { it.name }

    /** Adds cards leaving their boxes to the physical move list; returns the lines added. */
    private suspend fun recordMoves(moves: List<Pair<CollectionCard, Long>>, show: Boolean): List<com.mtgscanner.data.MoveLine> {
        val ids = (moves.map { it.first.boxId } + moves.map { it.second }).distinct()
        val info = ids.mapNotNull { id -> repo.box(id)?.let { id to Triple(it.name, it.kind, it.divider) } }.toMap()
        return MoveList.add(MoveList.linesFor(moves) { info[it] }, show)
    }

    /** A whole system of boxes, tried as a plan first; "Set boxes" is built from the sets you own. */
    fun tryTemplate(t: BoxTemplate) = viewModelScope.launch {
        val tpl = if (t.boxes.isEmpty()) {
            BoxTemplate.setBoxes(repo.allCards().groupBy { it.setCode }.map { (code, cs) -> code to cs.sumOf { it.quantity } })
        } else t
        tryPlan(tpl.name, tpl.boxes.map { p -> BoxChange(null, p.name, p.kind, p.rules, divider = p.divider, fallback = p.fallback) })
    }

    // Box plans: see where every card would go before boxes change

    private val _plan = MutableStateFlow<PlanUi?>(null)
    /** The plan being tried; null hides its sheet. */
    val plan: StateFlow<PlanUi?> = _plan

    fun tryPlan(title: String, changes: List<BoxChange>) {
        val start = PlanUi(title, changes, null)
        _plan.value = start
        viewModelScope.launch {
            val r = runCatching { repo.planBoxes(changes) }.getOrNull()
            // Discarded (or replaced) while it was worked out.
            if (_plan.value !== start) return@launch
            if (r == null) {
                _plan.value = null
                _message.value = "Couldn't work out the plan"
            } else {
                _plan.value = start.copy(result = r)
            }
        }
    }

    fun discardPlan() { _plan.value = null }

    /** Saves the plan's boxes and moves every card that now belongs elsewhere, with one Undo for all of it. */
    fun applyPlan() = viewModelScope.launch {
        val p = _plan.value ?: return@launch
        _plan.value = null
        val undo = repo.saveBoxChanges(p.changes)
        val moves = repo.misplaced()
        val before = repo.snapshot(moves.map { it.item.id })
        val added = if (moves.isEmpty()) emptyList() else recordMoves(moves.map { it.item to it.to }, show = true)
        if (moves.isNotEmpty()) repo.applyMoves(moves.map { it.item.id to it.to })
        val n = moves.sumOf { it.item.quantity }
        Undo.offer(
            when (n) {
                0 -> "Boxes saved"
                1 -> "Plan applied · 1 card moved"
                else -> "Plan applied · $n cards moved"
            },
        ) {
            repo.restore(before)
            MoveList.remove(added)
            repo.undoBoxChanges(undo)
        }
    }

    /** Moves cards from "to sort" to their boxes, onto the move list, with Undo. */
    fun sortCards(list: List<SortItem>) = viewModelScope.launch {
        if (list.isEmpty()) return@launch
        // Moves can merge rows, so Undo puts back a snapshot rather than moving rows back.
        val before = repo.snapshot(list.map { it.card.id })
        val added = recordMoves(list.map { it.card to it.to }, show = list.size > 1)
        repo.applyMoves(list.map { it.card.id to it.to })
        val n = list.sumOf { it.card.quantity }
        Undo.offer(if (n == 1) "Moved 1 card" else "Moved $n cards") {
            repo.restore(before)
            MoveList.remove(added)
        }
    }

    /** Splits a full box in two by first letter; the second box starts at [letter] (null picks an even split). */
    fun splitBox(b: BoxSummary, letter: Char? = null) = viewModelScope.launch {
        val undo = repo.boxUndo(b.id)
        val before = repo.cardsInBox(b.id)
        val newId = repo.splitBox(b.id, letter)
        if (newId == null || undo == null) {
            _message.value = "Can't split ${b.name}"
            return@launch
        }
        val moved = repo.cardsInBox(newId).map { it.id }.toSet()
        val added = recordMoves(before.filter { it.id in moved }.map { it to newId }, show = true)
        Undo.offer("Split ${b.name}") {
            repo.undoBox(undo)
            MoveList.remove(added)
        }
    }

    // Smart lists: saved Browse searches that update themselves

    private val searches = SavedSearchStore(repo.appContext)
    val lists: StateFlow<List<SmartList>> = combine(repo.observeAll(), searches.saved, repo.observeBoxes()) { all, saved, boxes ->
        // box: in a saved search needs the box names.
        val boxNames = boxes.associate { it.id to it.name }
        val env = com.mtgscanner.data.search.SearchEnv(boxName = { boxNames[it] })
        saved.map { s -> SmartList.of(s, all, env) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteList(s: SavedSearch) {
        searches.delete(s.name)
        Undo.offer("Deleted ${s.name}") { searches.save(s.name, s.filters, s.boxId) }
    }

    /** Saves the Auto-sort order, first to last. */
    fun reorder(ids: List<Long>) = viewModelScope.launch { repo.reorderBoxes(ids) }
    fun setFallback(id: Long?) = viewModelScope.launch { repo.setFallbackBox(id) }

    /**
     * [moveTo] null deletes the cards too; [CardBox.AUTO_SORT] sorts them into the other boxes;
     * [TO_ANYTHING] moves them to a box that takes any card, making one if needed.
     */
    fun deleteBox(id: Long, moveTo: Long?) = viewModelScope.launch {
        val undo = repo.boxUndo(id)
        val target = if (moveTo == TO_ANYTHING) repo.anythingBox(except = id) else moveTo
        repo.deleteBox(id, target)
        val msg = when (moveTo) {
            null -> "Box and cards deleted"
            TO_ANYTHING -> "Box deleted. Cards in Anything"
            else -> "Box deleted"
        }
        if (undo == null) _message.value = msg else Undo.offer(msg) { repo.undoBox(undo) }
    }

    /** Moves every card in a box into a deck ([deckId]), or a new deck named [name] when it's 0, with Undo. */
    fun moveBoxToDeck(boxId: Long, deckId: Long, name: String) = viewModelScope.launch {
        val r = repo.boxToDeck(boxId, deckId, name) ?: return@launch
        Undo.offer(if (deckId == 0L) "Made deck $name" else "Moved to the deck") { r.second() }
    }

    fun observeDecks() = repo.observeDecks()

    /** Sorts a catch-all box: cards that fit another box's rules move there. */
    fun sortBox(id: Long, name: String) = viewModelScope.launch {
        val before = repo.snapshot(repo.cardsInBox(id).map { it.id })
        val moved = repo.sortBox(id)
        if (moved == 0) _message.value = "None of the cards in $name fit another box's rules. Make a box with rules first."
        else Undo.offer("Sorted $moved cards out of $name") { repo.restore(before) }
    }

    /** Makes a new box from [d] and moves the cards in [sourceId] that fit its rules into it. */
    fun sortIntoNew(sourceId: Long, name: String, d: BoxDraft) = viewModelScope.launch {
        val before = repo.snapshot(repo.cardsInBox(sourceId).map { it.id })
        val newId = repo.createBox(d.name, d.kind, d.rules, d.color)
        val moved = repo.sortInto(sourceId, newId, d.rules)
        if (moved == 0) _message.value = "Made ${d.name}, but no cards in $name fit its rules"
        else Undo.offer("Moved $moved cards from $name to ${d.name}") { repo.restore(before); repo.deleteBox(newId, CardBox.AUTO_SORT) }
    }

    companion object {
        const val TO_ANYTHING = -3L
    }

    fun removeWish(item: WishlistItem) = viewModelScope.launch {
        repo.removeWish(item.id)
        Undo.offer("Removed ${item.name}") { repo.restoreWish(item) }
    }

    fun setWanted(item: WishlistItem, wanted: Int) = viewModelScope.launch { repo.setWishWanted(item, wanted) }

    /** Card names from Scryfall for the wishlist's add box. */
    suspend fun suggestCards(text: String): List<String> =
        runCatching { repo.api.autocomplete(text.trim()) }.getOrDefault(emptyList())

    /** Adds [qty] copies of the named card to the wishlist, priced by its cheapest printing, with Undo. */
    fun wishByName(name: String, qty: Int = 1) = viewModelScope.launch {
        val found = runCatching { repo.api.named(name) }.getOrNull()
        val oracle = found?.oracleId
        if (oracle == null) { _message.value = "Couldn't find $name"; return@launch }
        val card = repo.cheapestPrinting(found)
        val before = repo.wishSnapshot(listOf(oracle))
        repo.addToWishlist(card, qty)
        Undo.offer("Added ${if (qty > 1) "$qty× " else ""}${card.name} to wishlist") { repo.undoWishes(listOf(oracle), before) }
    }

    /** The wishlist row being marked "Got it", and its printings (null while loading). */
    private val _gotIt = MutableStateFlow<Pair<WishlistItem, List<ScryfallCard>?>?>(null)
    val gotIt: StateFlow<Pair<WishlistItem, List<ScryfallCard>?>?> = _gotIt

    /** "Got it": pick the printing, then one copy goes into the collection and off the wishlist. */
    fun startGotIt(item: WishlistItem) {
        _gotIt.value = item to null
        viewModelScope.launch {
            val list = runCatching { repo.api.printingsOf(item.oracleId, item.name) }.getOrDefault(emptyList())
            if (_gotIt.value?.first?.id != item.id) return@launch
            if (list.isEmpty()) { _gotIt.value = null; _message.value = "Couldn't reach Scryfall"; return@launch }
            _gotIt.value = item to list
        }
    }

    fun cancelGotIt() { _gotIt.value = null }

    fun gotItPrinting(card: ScryfallCard) {
        val item = _gotIt.value?.first ?: return
        _gotIt.value = null
        viewModelScope.launch {
            // A printing that only comes in foil is added as foil.
            val foil = !card.hasNonFoil
            // No box's rules fit: the Anything box, made if needed, so the card never lands in a deleted box.
            val target = repo.pickBox(card, foil) ?: repo.anythingBox()
            val rowId = repo.add(card, foil, 1, target)
            val wish = repo.wishlistItem(item.oracleId)
            repo.fulfilWish(item.oracleId, 1)
            Undo.offer("Added ${card.name} · off wishlist") {
                repo.remove(rowId, 1)
                if (wish != null) repo.restoreWish(wish)
            }
        }
    }

    /** For each wishlist card (by [CardNames.key]), the decks whose lists play it and aren't complete, A to Z. */
    val wishDecks: StateFlow<Map<String, List<String>>> = combine(repo.observeDecks(), repo.observeOwnedByName()) { decks, owned ->
        val map = HashMap<String, MutableList<String>>()
        decks.sortedBy { it.name.lowercase() }.forEach { d ->
            val lines = com.mtgscanner.data.ParsedDecks.of(d)
            if (DeckOwnership.complete(lines, owned)) return@forEach
            lines.filter { it.section in DeckCheck.PLAYED }.map { CardNames.key(it.name) }.distinct()
                .forEach { k -> map.getOrPut(k) { mutableListOf() }.add(d.name) }
        }
        map
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** A box's cards by name, read when needed (cover picker, binder share). */
    suspend fun cardsIn(boxId: Long): List<CollectionCard> = repo.cardsInBox(boxId).sortedBy { it.name }

    fun refreshPrices() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            _message.value = runCatching { repo.refreshPrices() }
                .fold({ "Updated prices for $it cards" }, { "Couldn't refresh prices: ${it.message}" })
            _refreshing.value = false
        }
    }

    /** Sets a box's dividers (one or two levels) and the order inside. */
    fun setLayout(b: BoxSummary, key: String) = viewModelScope.launch {
        repo.setBoxLayout(b.id, b.capacity, key)
        Undo.offer("Dividers saved") { repo.setBoxLayout(b.id, b.capacity, b.divider) }
    }

    /** Printable box and divider labels as a PDF. */
    fun exportLabels(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _message.value = runCatching {
                val boxes = repo.observeBoxes().first()
                val cards = repo.allCards()
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { BoxLabels.write(boxes, cards, it) } ?: 0
                }
            }.fold({ "Saved $it labels" }, { "Labels failed: ${it.message}" })
        }
    }

    fun messageShown() { _message.value = null }
}
