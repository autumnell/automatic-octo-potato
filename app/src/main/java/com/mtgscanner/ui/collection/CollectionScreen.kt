package com.mtgscanner.ui.collection

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import com.mtgscanner.data.BoxCovers
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.BoxStats
import com.mtgscanner.data.BoxTemplate
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.BoxDraft
import com.mtgscanner.ui.components.BoxEditorSheet
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.decks.DecksTab
import com.mtgscanner.ui.trade.shareText
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.StatTile
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OverviewUi(
    val totalCards: Int = 0,
    val totalValue: Double = 0.0,
    val boxes: List<BoxSummary> = emptyList(),
    /** Value change and cover art per box id. */
    val stats: Map<Long, BoxStats> = emptyMap(),
)

class CollectionViewModel(private val repo: CollectionRepository) : ViewModel() {
    private val covers = BoxCovers(repo.appContext)

    val ui: StateFlow<OverviewUi> = combine(repo.observeAll(), repo.observeBoxSummaries(), covers.covers) { all, boxes, cv ->
        OverviewUi(all.sumOf { it.quantity }, all.sumOf { it.totalPrice() }, boxes, BoxArt.stats(all, cv))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUi())

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
        if (!d.rules.isCatchAll) findMisplaced(quiet = true)
    }

    /** Saves a box; when its rules or kind changed, checks for cards that now belong elsewhere. */
    fun updateBox(id: Long, d: BoxDraft) = viewModelScope.launch {
        val old = repo.box(id)
        repo.updateBox(id, d.name, d.kind, d.rules, d.color, capacity = d.capacity, divider = d.divider)
        if (old == null || BoxRules.fromJson(old.rules) != d.rules || old.kind != d.kind) findMisplaced(quiet = true)
    }

    // Re-sort: cards that should live in another box

    private val _moves = MutableStateFlow<List<Sorting.Move<CollectionCard>>?>(null)
    /** Cards to move after a rule change; null hides the sheet. */
    val moves: StateFlow<List<Sorting.Move<CollectionCard>>?> = _moves

    /** Looks for misplaced cards. [quiet] says nothing when all is well. */
    fun findMisplaced(quiet: Boolean = false) = viewModelScope.launch {
        val found = repo.misplaced()
        if (found.isNotEmpty()) _moves.value = found
        else if (!quiet) _message.value = "All cards in place"
    }

    fun closeMoves() { _moves.value = null }

    /** Adds cards leaving their boxes to the physical move list. */
    private suspend fun recordMoves(moves: List<Pair<CollectionCard, Long>>, show: Boolean) {
        val ids = (moves.map { it.first.boxId } + moves.map { it.second }).distinct()
        val info = ids.mapNotNull { id -> repo.box(id)?.let { id to Triple(it.name, it.kind, it.divider) } }.toMap()
        MoveList.add(MoveList.linesFor(moves) { info[it] }, show)
    }

    /** Makes a whole system of boxes in one go; "Set boxes" is built from the sets you own. */
    fun applyTemplate(t: BoxTemplate) = viewModelScope.launch {
        val tpl = if (t.boxes.isEmpty()) {
            BoxTemplate.setBoxes(repo.allCards().groupBy { it.setCode }.map { (code, cs) -> code to cs.sumOf { it.quantity } })
        } else t
        val hasFallback = repo.boxInfos().any { it.fallback }
        val ids = tpl.boxes.map { p ->
            val id = repo.createBox(p.name, p.kind, p.rules)
            if (p.divider.isNotEmpty()) repo.setBoxLayout(id, 0, p.divider)
            if (p.fallback && !hasFallback) repo.setFallbackBox(id)
            id
        }
        Undo.offer("Made ${ids.size} boxes") { ids.forEach { repo.deleteBox(it, CardBox.AUTO_SORT) } }
        findMisplaced(quiet = true)
    }

    private fun applyMoves(list: List<Sorting.Move<CollectionCard>>) = viewModelScope.launch {
        if (list.isEmpty()) return@launch
        recordMoves(list.map { it.item to it.to }, show = list.size > 1)
        val undo = repo.applyMoves(list.map { it.item.id to it.to })
        val n = list.sumOf { it.item.quantity }
        Undo.offer(if (n == 1) "Moved 1 card" else "Moved $n cards") { repo.applyMoves(undo) }
        val left = repo.misplaced()
        _moves.value = left.ifEmpty { null }
    }

    fun moveOne(m: Sorting.Move<CollectionCard>) = applyMoves(listOf(m))
    fun moveAll() = applyMoves(_moves.value.orEmpty())

    /** Splits a full box in two by first letter. */
    fun splitBox(b: BoxSummary) = viewModelScope.launch {
        val before = repo.cardsInBox(b.id)
        val newId = repo.splitBox(b.id)
        _message.value = if (newId == null) "Can't split ${b.name}" else "Split ${b.name}"
        if (newId != null) {
            val moved = repo.cardsInBox(newId).map { it.id }.toSet()
            recordMoves(before.filter { it.id in moved }.map { it to newId }, show = true)
        }
    }

    // Smart lists: saved Browse searches that update themselves

    private val searches = SavedSearchStore(repo.appContext)
    val lists: StateFlow<List<SmartList>> = combine(repo.observeAll(), searches.saved) { all, saved ->
        saved.map { s -> SmartList.of(s, all) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteList(s: SavedSearch) {
        searches.delete(s.name)
        Undo.offer("Deleted ${s.name}") { searches.save(s.name, s.filters) }
    }

    /** Saves the Auto-sort order, first to last. */
    fun reorder(ids: List<Long>) = viewModelScope.launch { repo.reorderBoxes(ids) }
    fun setFallback(id: Long?) = viewModelScope.launch { repo.setFallbackBox(id) }

    /**
     * [moveTo] null deletes the cards too; [CardBox.AUTO_SORT] sorts them into the other boxes;
     * [TO_ANYTHING] moves them to a box that takes any card, making one if needed.
     */
    fun deleteBox(id: Long, moveTo: Long?) = viewModelScope.launch {
        val target = if (moveTo == TO_ANYTHING) repo.anythingBox(except = id) else moveTo
        repo.deleteBox(id, target)
        _message.value = when (moveTo) {
            null -> "Box and its cards deleted"
            TO_ANYTHING -> "Box deleted. Its cards are in your Anything box"
            else -> "Box deleted"
        }
    }

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

    private val allCards: StateFlow<List<CollectionCard>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun cardsIn(boxId: Long) = allCards.value.filter { it.boxId == boxId }.sortedBy { it.name }

    fun refreshPrices() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            _message.value = runCatching { repo.refreshPrices() }
                .fold({ "Updated prices for $it cards" }, { "Couldn't refresh prices: ${it.message}" })
            _refreshing.value = false
        }
    }

    fun export(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _message.value = runCatching {
                val cards = repo.allCards()
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.bufferedWriter()?.use { CsvExporter.write(cards, it) }
                }
                cards.size
            }.fold({ "Exported $it entries" }, { "Export failed: ${it.message}" })
        }
    }

    fun messageShown() { _message.value = null }
}

/** Collection overview with four tabs: boxes, decks, wishlist, and tools (with trade binders). */
@Composable
fun CollectionScreen(
    onBrowse: (boxId: Long) -> Unit,
    onSettings: () -> Unit,
    onOpenDeck: (deckId: Long) -> Unit,
    onOpenCard: (Long) -> Unit = {},
    /** Opens the trade screen for a trade binder. */
    onTrade: (binderId: Long) -> Unit = {},
    onSets: () -> Unit = {},
    onExtras: () -> Unit = {},
    vm: CollectionViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(ctx.contentResolver, uri)
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    // Home's "See all" lands here when it can't open Browse itself.
    LaunchedEffect(Unit) {
        if (BrowseLaunch.openAll) {
            BrowseLaunch.openAll = false
            onBrowse(-1L)
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Collection", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::refreshPrices) { Icon(Icons.Filled.Refresh, "Refresh prices") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Settings, backup & import") },
                            leadingIcon = { Icon(Icons.Filled.Settings, null) },
                            onClick = { menu = false; onSettings() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            onClick = {
                                menu = false
                                exportLauncher.launch("mtg-collection-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.csv")
                            },
                        )
                    }
                }
            }
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Palette.Accent)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Boxes", "Decks", "Wishlist", "Tools").forEachIndexed { i, t -> SelectPill(t, tab == i, { tab = i }) }
            }
            MoveListHost()
            when (tab) {
                0 -> BoxesTab(ui, vm, onBrowse, onTrade)
                1 -> DecksTab(onOpenDeck)
                2 -> WishlistTab(vm)
                else -> ToolsTab(ui, vm, onBrowse, onTrade, onSets, onExtras, onOpenCard)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    val moves by vm.moves.collectAsStateWithLifecycle()
    moves?.let { list ->
        ResortSheet(list, ui.boxes, onMove = vm::moveOne, onMoveAll = vm::moveAll, onDismiss = vm::closeMoves)
    }
}

@Composable
private fun BoxesTab(ui: OverviewUi, vm: CollectionViewModel, onBrowse: (Long) -> Unit, onTrade: (Long) -> Unit) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    var splitting by remember { mutableStateOf<BoxSummary?>(null) }
    var editing by remember { mutableStateOf<BoxSummary?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BoxSummary?>(null) }
    var ordering by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf<BoxSummary?>(null) }
    var sortingNew by remember { mutableStateOf<BoxSummary?>(null) }
    var covering by remember { mutableStateOf<BoxSummary?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val colors = remember(ui.boxes) { boxStripeColors(ui.boxes) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ValueRing(ui.boxes, ui.stats, colors, ui.totalCards, ui.totalValue)

        Surface(
            onClick = { onBrowse(-1L) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(18.dp),
            color = Palette.Surface,
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = Palette.Accent)
                Spacer(Modifier.width(12.dp))
                Text("Browse entire collection", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }

        SectionHeader("Boxes") {
            if (ui.boxes.size > 1) TextButton(onClick = { ordering = true }) {
                Icon(Icons.Filled.AutoFixHigh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Auto-sort order")
            }
            TextButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New")
            }
        }
        if (ui.boxes.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Inventory2,
                title = "No boxes yet",
                body = "Create a box to start scanning.",
                action = { GradientButton("Create a box", { creating = true }, icon = Icons.Filled.Add) },
            )
        }
        // A search field once the list gets long.
        val searching = ui.boxes.size > 6
        if (searching) SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), placeholder = "Search boxes")
        val shown = if (searching && query.isNotBlank()) ui.boxes.filter { it.name.contains(query.trim(), ignoreCase = true) } else ui.boxes
        if (searching && shown.isEmpty()) {
            Text("No boxes match", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
        shown.forEach { b ->
            BoxCard(
                b, ui.stats[b.id], colors[b.id] ?: boxColor(b.color),
                onClick = { onBrowse(b.id) }, onCover = { covering = b }, onEdit = { editing = b }, onDelete = { deleting = b }, onSort = { sorting = b },
                onSplit = { splitting = b }, onTrade = { onTrade(b.id) },
            )
        }
        SmartListsSection(lists, onOpen = { s ->
            BrowseLaunch.filters = s.filters
            BrowseLaunch.title = s.name
            onBrowse(-1L)
        }, onDelete = vm::deleteList)
        Spacer(Modifier.height(24.dp))
    }

    if (creating) {
        BoxEditorSheet(
            title = "New box", onSave = { vm.createBox(it); creating = false }, onDismiss = { creating = false }, layout = true,
            onTemplate = { vm.applyTemplate(it); creating = false },
        )
    }
    editing?.let { b ->
        BoxEditorSheet(
            title = "Edit box",
            initial = BoxDraft(b.name, b.kind, b.color, BoxRules.fromJson(b.rules), b.capacity, b.divider),
            editingId = b.id,
            onSave = { vm.updateBox(b.id, it); editing = null },
            onDismiss = { editing = null },
            layout = true,
        )
    }
    sorting?.let { b ->
        SortBoxDialog(
            b, hasOtherRules = ui.boxes.any { it.id != b.id && !BoxRules.fromJson(it.rules).isCatchAll },
            onOthers = { vm.sortBox(b.id, b.name); sorting = null },
            onNew = { sortingNew = b; sorting = null },
            onDismiss = { sorting = null },
        )
    }
    sortingNew?.let { b ->
        BoxEditorSheet(
            title = "Sort out of ${b.name}",
            onSave = { vm.sortIntoNew(b.id, b.name, it); sortingNew = null },
            onDismiss = { sortingNew = null },
        )
    }
    splitting?.let { b ->
        AlertDialog(
            onDismissRequest = { splitting = null },
            containerColor = Palette.Surface,
            title = { Text("Split ${b.name}?") },
            text = { Text("Makes two boxes by first letter, like A–M and N–Z.") },
            confirmButton = { TextButton(onClick = { vm.splitBox(b); splitting = null }) { Text("Split") } },
            dismissButton = { TextButton(onClick = { splitting = null }) { Text("Cancel") } },
        )
    }
    covering?.let { b ->
        CoverPickerDialog(
            b.name, vm.cardsIn(b.id), ui.stats[b.id]?.coverCardId,
            onPick = { vm.setCover(b.id, it); covering = null },
            onDismiss = { covering = null },
        )
    }
    if (ordering) AutoSortOrderSheet(ui.boxes, vm::reorder, vm::setFallback, onDismiss = { ordering = false })
    deleting?.let { b -> DeleteBoxDialog(b, ui.boxes.filter { it.id != b.id }, onDismiss = { deleting = null }) { moveTo ->
        vm.deleteBox(b.id, moveTo); deleting = null
    } }
}

@Composable
private fun DeleteBoxDialog(box: BoxSummary, others: List<BoxSummary>, onDismiss: () -> Unit, onDelete: (Long?) -> Unit) {
    // -1 means "delete the cards".
    var choice by remember { mutableStateOf(CollectionViewModel.TO_ANYTHING) }
    var confirmForever by remember { mutableStateOf(false) }
    val anything = others.firstOrNull { it.kind != "trade" && BoxRules.fromJson(it.rules).isCatchAll }
    if (confirmForever) {
        AlertDialog(
            onDismissRequest = { confirmForever = false },
            containerColor = Palette.Surface,
            title = { Text("Permanently delete ${box.cards} cards?") },
            text = { Text("${box.name} and every card in it will be removed from your collection. This can't be undone.") },
            confirmButton = { TextButton(onClick = { onDelete(null) }) { Text("Delete forever", color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = { confirmForever = false }) { Text("Keep them") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Delete ${box.name}?") },
        text = {
            Column {
                if (box.cards == 0) {
                    Text("It's empty.")
                } else {
                    Text("What should happen to its ${box.cards} cards?")
                    Spacer(Modifier.height(8.dp))
                    Choice(
                        if (anything != null) "Move them to ${anything.name} (takes any card)" else "Move them to a new Anything box",
                        choice == CollectionViewModel.TO_ANYTHING,
                    ) { choice = CollectionViewModel.TO_ANYTHING }
                    if (others.isNotEmpty()) {
                        Choice("Sort them into my other boxes by their rules", choice == CardBox.AUTO_SORT) { choice = CardBox.AUTO_SORT }
                        others.filter { it.id != anything?.id }.forEach { o -> Choice("Move them to ${o.name}", choice == o.id) { choice = o.id } }
                    }
                    Choice("Delete them permanently", choice == -1L) { choice = -1L }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    box.cards == 0 -> onDelete(CardBox.AUTO_SORT)
                    choice == -1L -> confirmForever = true
                    else -> onDelete(choice)
                }
            }) { Text("Delete box", color = Palette.Danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Sort a box after the fact, like an Anything box everything was scanned into. */
@Composable
private fun SortBoxDialog(box: BoxSummary, hasOtherRules: Boolean, onOthers: () -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Sort ${box.name}") },
        text = {
            Column {
                Text(
                    "Cards that fit nothing stay here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Surface(onClick = onNew, shape = RoundedCornerShape(14.dp), color = Palette.SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Into a new box", style = MaterialTheme.typography.titleSmall)
                        Text("Set up a box with rules, and the cards that fit move into it", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                }
                if (hasOtherRules) {
                    Spacer(Modifier.height(8.dp))
                    Surface(onClick = onOthers, shape = RoundedCornerShape(14.dp), color = Palette.SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Into my other boxes", style = MaterialTheme.typography.titleSmall)
                            Text("Each card goes to the first box whose rules fit, in Auto-sort order", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Palette.Accent))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A box row: its cover card's art behind, a color stripe, count, value and change since added. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxCard(
    b: BoxSummary, stats: BoxStats?, stripe: Color, onClick: () -> Unit, onCover: () -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit, onSort: () -> Unit,
    onSplit: () -> Unit, onTrade: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val rules = remember(b.rules) { BoxRules.fromJson(b.rules) }
    // Nearly full at 90%.
    val nearlyFull = b.capacity > 0 && b.cards * 10 >= b.capacity * 9
    val art = if (b.cards > 0) stats?.artUrl else null
    val onArt = art != null
    val fg = if (onArt) Color.White else Palette.Text
    val muted = if (onArt) Color.White.copy(alpha = 0.72f) else Palette.Muted
    val shape = RoundedCornerShape(24.dp)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).clip(shape)
                .background(if (onArt) Color(0xFF15161A) else Palette.BoxCard)
                .combinedClickable(onClick = onClick, onLongClick = { if (b.cards > 0) onCover() })
                .drawWithContent {
                    drawContent()
                    drawRect(stripe, size = Size(5.dp.toPx(), size.height))
                },
        ) {
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.66f), Color.Black.copy(alpha = 0.3f))),
                    ),
                )
            }
            Column(Modifier.padding(start = 22.dp, end = 18.dp, top = 16.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(boxIcon(b.kind), null, tint = stripe, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        b.name, style = MaterialTheme.typography.titleLarge, color = if (onArt) Color.White else boxColor(b.color),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (b.fallback) {
                        Spacer(Modifier.width(8.dp))
                        Text("Fallback", style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                }
                Text(rules.describe(), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        when {
                            b.capacity > 0 -> "${b.cards} / ${b.capacity}"
                            b.cards == 0 -> "No cards"
                            else -> "${b.cards} cards"
                        } + (if (b.cards >= b.capacity && b.capacity > 0) " · Full" else ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (nearlyFull) Palette.Gold else fg,
                        modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                    )
                    if (AppSettings.showPrices) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatUsd(b.value), style = MaterialTheme.typography.headlineSmall, color = fg)
                            val pct = stats?.changePercent
                            if (stats != null && pct != null) {
                                Text(
                                    boxChangeLabel(stats.change, pct),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = boxChangeColor(stats.change, onDark = onArt),
                                )
                            }
                        }
                    }
                }
                if (b.capacity > 0) {
                    // How full the box is.
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (onArt) Color.White.copy(alpha = 0.2f) else Palette.Outline)) {
                        Box(
                            Modifier.fillMaxWidth((b.cards.toFloat() / b.capacity).coerceIn(0f, 1f)).height(4.dp)
                                .background(if (nearlyFull) Palette.Gold else stripe),
                        )
                    }
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Box options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                if (b.cards > 0) DropdownMenuItem(text = { Text("Cover card") }, onClick = { menu = false; onCover() })
                if (b.kind == "trade") DropdownMenuItem(text = { Text("Trade") }, onClick = { menu = false; onTrade() })
                if (nearlyFull && b.cards > 1) DropdownMenuItem(text = { Text("Split") }, onClick = { menu = false; onSplit() })
                if (b.cards > 0) DropdownMenuItem(text = { Text("Sort out") }, onClick = { menu = false; onSort() })
                DropdownMenuItem(text = { Text("Delete", color = Palette.Danger) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Opens Android's share sheet with plain text. */
private fun shareSheet(ctx: android.content.Context, title: String, text: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, title).putExtra(android.content.Intent.EXTRA_TEXT, text)
    ctx.startActivity(android.content.Intent.createChooser(send, "Share $title"))
}

/** Tools, plus your trade binders with a share button each. */
@Composable
private fun ToolsTab(
    ui: OverviewUi, vm: CollectionViewModel, onBrowse: (Long) -> Unit, onTrade: (Long) -> Unit,
    onSets: () -> Unit, onExtras: () -> Unit, onOpenCard: (Long) -> Unit,
) {
    val trade = ui.boxes.filter { it.kind == "trade" }
    val ctx = LocalContext.current
    var finding by remember { mutableStateOf(false) }
    var dupes by remember { mutableStateOf(false) }
    if (finding) FindCardSheet(onOpenCard = { finding = false; onOpenCard(it) }, onDismiss = { finding = false })
    if (dupes) DuplicatesSheet(onOpenCard = { dupes = false; onOpenCard(it) }, onDismiss = { dupes = false })
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ToolCard(Icons.Filled.FindInPage, "Find a card", "Which box, which spot", { finding = true })
            ToolCard(Icons.Filled.AutoFixHigh, "Re-sort", "Cards in the wrong box", { vm.findMisplaced() })
            ToolCard(Icons.Filled.Layers, "Duplicates", "Cards in 2+ printings", { dupes = true })
            ToolCard(Icons.Filled.GridView, "Set tracker", "How much of each set you own", onSets)
            ToolCard(Icons.Filled.ContentCopy, "Extras finder", "Spare copies to trade", onExtras)
            SectionHeader("Trade binders")
            if (trade.isEmpty()) {
                Text(
                    "Make a Trade binder box to trade.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        items(trade, key = { "t${it.id}" }) { b ->
            Surface(
                onClick = { onBrowse(b.id) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                color = Palette.Surface,
            ) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SwapHoriz, null, tint = Palette.Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.titleMedium)
                        Text("${b.cards} cards${priceSuffix(b.value)}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    TextButton(onClick = { onTrade(b.id) }) { Text("Trade") }
                    IconButton(enabled = b.cards > 0, onClick = {
                        shareSheet(ctx, b.name, binderShareText(b.name, vm.cardsIn(b.id), AppSettings.showPrices))
                    }) { Icon(Icons.Filled.Share, "Share ${b.name}") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Cards you want. Tap one to change how many; delete offers Undo. */
@Composable
private fun WishlistTab(vm: CollectionViewModel) {
    val wishlist by vm.wishlist.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<WishlistItem?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            if (wishlist.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.FavoriteBorder,
                    title = "Nothing on your wishlist",
                    body = "Add cards from Search or a deck.",
                )
            } else {
                SectionHeader("${wishlist.sumOf { it.wanted }} wanted") {
                    if (AppSettings.showPrices) {
                        Text(
                            formatUsd(wishlist.sumOf { (it.priceUsd ?: 0.0) * it.wanted }),
                            style = MaterialTheme.typography.titleSmall,
                            color = Palette.Gold,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(onClick = {
                        shareSheet(ctx, "Wishlist", shareText("My wishlist", wishlist.map { Triple(it.wanted, it.name, it.priceUsd) }))
                    }) { Icon(Icons.Filled.Share, "Share wishlist") }
                }
            }
        }
        items(wishlist, key = { "w${it.id}" }) { w ->
            Row(
                Modifier.fillMaxWidth().clickable { editing = w }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardImage(w.imageUrl, Modifier.width(44.dp), corner = 4.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.name, style = MaterialTheme.typography.titleSmall)
                    Text("Want ${w.wanted}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                if (AppSettings.showPrices) Text(formatUsd(w.priceUsd), style = MaterialTheme.typography.titleSmall, color = Palette.Accent)
                IconButton(onClick = { vm.removeWish(w) }) { Icon(Icons.Filled.DeleteOutline, "Remove", tint = Palette.Muted) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    editing?.let { w ->
        WantedDialog(w, onSave = { n -> vm.setWanted(w, n); editing = null }, onDismiss = { editing = null })
    }
}

/** A small stepper for how many copies of a wishlist card you want. */
@Composable
private fun WantedDialog(item: WishlistItem, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var n by remember(item.id) { mutableIntStateOf(item.wanted.coerceAtLeast(1)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(item.name) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (n > 1) n-- }, enabled = n > 1) { Icon(Icons.Filled.Remove, "Fewer") }
                Text("Want $n", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                IconButton(onClick = { n++ }) { Icon(Icons.Filled.Add, "More") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(n) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ToolCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = Palette.BoxCard,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Palette.Accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}
