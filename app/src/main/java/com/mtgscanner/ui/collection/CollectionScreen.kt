package com.mtgscanner.ui.collection

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.CollectionRepository
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
import com.mtgscanner.data.db.unitPrice
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
)

class CollectionViewModel(private val repo: CollectionRepository) : ViewModel() {
    val ui: StateFlow<OverviewUi> = combine(repo.observeAll(), repo.observeBoxSummaries()) { all, boxes ->
        OverviewUi(all.sumOf { it.quantity }, all.sumOf { it.totalPrice() }, boxes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUi())

    val wishlist: StateFlow<List<WishlistItem>> = repo.observeWishlist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    fun createBox(d: BoxDraft) = viewModelScope.launch { repo.createBox(d.name, d.kind, d.rules, d.color) }
    fun updateBox(id: Long, d: BoxDraft) = viewModelScope.launch { repo.updateBox(id, d.name, d.kind, d.rules, d.color) }

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

    fun removeWish(id: Long) = viewModelScope.launch { repo.removeWish(id) }

    private val allCards: StateFlow<List<com.mtgscanner.data.db.CollectionCard>> = repo.observeAll()
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

/** Collection overview with three tabs: boxes, decks, and lists (wishlist and trade binders). */
@Composable
fun CollectionScreen(
    onBrowse: (boxId: Long) -> Unit,
    onSettings: () -> Unit,
    onOpenDeck: (deckId: Long) -> Unit,
    onOpenCard: (Long) -> Unit = {},
    onTrade: () -> Unit = {},
    onSets: () -> Unit = {},
    onExtras: () -> Unit = {},
    onPacks: () -> Unit = {},
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
                listOf("Collection", "Decks", "Lists").forEachIndexed { i, t -> SelectPill(t, tab == i, { tab = i }) }
            }
            when (tab) {
                0 -> BoxesTab(ui, vm, onBrowse)
                1 -> DecksTab(onOpenDeck, onOpenCard)
                else -> ListsTab(ui, vm, onBrowse, onTrade, onSets, onExtras, onPacks)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BoxesTab(ui: OverviewUi, vm: CollectionViewModel, onBrowse: (Long) -> Unit) {
    var editing by remember { mutableStateOf<BoxSummary?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BoxSummary?>(null) }
    var ordering by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf<BoxSummary?>(null) }
    var sortingNew by remember { mutableStateOf<BoxSummary?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Number of cards", "${ui.totalCards}", Modifier.weight(1f))
            if (AppSettings.showPrices) StatTile("Total value", formatUsd(ui.totalValue), Modifier.weight(1f))
            else StatTile("Boxes", "${ui.boxes.size}", Modifier.weight(1f))
        }

        Row(
            Modifier
                .align(Alignment.End)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onBrowse(-1L) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Browse entire collection", style = MaterialTheme.typography.titleMedium, color = Palette.Muted)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.Text)
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
        ui.boxes.forEach { b ->
            BoxCard(b, onClick = { onBrowse(b.id) }, onEdit = { editing = b }, onDelete = { deleting = b }, onSort = { sorting = b })
        }
        Spacer(Modifier.height(24.dp))
    }

    if (creating) {
        BoxEditorSheet(title = "New box", onSave = { vm.createBox(it); creating = false }, onDismiss = { creating = false })
    }
    editing?.let { b ->
        BoxEditorSheet(
            title = "Edit box",
            initial = BoxDraft(b.name, b.kind, b.color, BoxRules.fromJson(b.rules)),
            editingId = b.id,
            onSave = { vm.updateBox(b.id, it); editing = null },
            onDismiss = { editing = null },
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

@Composable
private fun BoxCard(b: BoxSummary, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onSort: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val rules = remember(b.rules) { BoxRules.fromJson(b.rules) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = Palette.BoxCard,
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(boxIcon(b.kind), null, tint = boxColor(b.color), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(b.name, style = MaterialTheme.typography.titleLarge, color = boxColor(b.color), modifier = Modifier.weight(1f, fill = false))
                    if (b.fallback) {
                        Spacer(Modifier.width(8.dp))
                        Text("Fallback", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                    }
                }
                Text(rules.describe(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (b.cards == 0) "No cards" else "${b.cards} cards",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                    )
                    if (AppSettings.showPrices) Text(formatUsd(b.value), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Box options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit name and rules") }, onClick = { menu = false; onEdit() })
                if (b.cards > 0) DropdownMenuItem(text = { Text("Sort cards out of this box") }, onClick = { menu = false; onSort() })
                DropdownMenuItem(text = { Text("Delete", color = Palette.Danger) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** The wishlist, plus shortcuts to trade binders. */
@Composable
private fun ListsTab(
    ui: OverviewUi, vm: CollectionViewModel, onBrowse: (Long) -> Unit, onTrade: () -> Unit,
    onSets: () -> Unit, onExtras: () -> Unit, onPacks: () -> Unit,
) {
    val wishlist by vm.wishlist.collectAsStateWithLifecycle()
    val trade = ui.boxes.filter { it.kind == "trade" }
    val ctx = LocalContext.current
    fun share(title: String, text: String) {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_SUBJECT, title).putExtra(android.content.Intent.EXTRA_TEXT, text)
        ctx.startActivity(android.content.Intent.createChooser(send, "Share $title"))
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ToolCard(Icons.Filled.SwapHoriz, "Trade calculator", "Compare what you give and get, then save it", onTrade)
            ToolCard(Icons.Filled.GridView, "Set tracker", "See how much of each set you own and what's missing", onSets)
            ToolCard(Icons.Filled.ContentCopy, "Extras finder", "Find copies you don't need and move them to trade", onExtras)
            ToolCard(Icons.Filled.Inventory2, "Pack history", "Every pack you opened and what it was worth", onPacks)
            SectionHeader("Trade binders")
            if (trade.isEmpty()) {
                Text(
                    "Make a box of kind \"Trade binder\" to keep cards you'd trade away. It shows up here.",
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
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SwapHoriz, null, tint = Palette.Accent)
                    Spacer(Modifier.width(12.dp))
                    Text(b.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${b.cards} cards${priceSuffix(b.value)}", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    IconButton(onClick = {
                        val cards = vm.cardsIn(b.id)
                        share(b.name, shareText("${b.name} (for trade)", cards.map { Triple(it.quantity, "${it.name} (${it.setCode} #${it.collectorNumber})", it.unitPrice()) }))
                    }) { Icon(Icons.Filled.Share, "Share") }
                }
            }
        }
        item {
            SectionHeader("Wishlist") {
                if (wishlist.isNotEmpty()) {
                    IconButton(onClick = {
                        share("Wishlist", shareText("My wishlist", wishlist.map { Triple(it.wanted, it.name, it.priceUsd) }))
                    }) { Icon(Icons.Filled.Share, "Share wishlist") }
                }
                if (wishlist.isNotEmpty() && AppSettings.showPrices) {
                    Text(
                        formatUsd(wishlist.sumOf { (it.priceUsd ?: 0.0) * it.wanted }),
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.Gold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            if (wishlist.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.FavoriteBorder,
                    title = "Nothing on your wishlist",
                    body = "Add cards from Search or a deck.",
                )
            }
        }
        items(wishlist, key = { "w${it.id}" }) { w ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CardImage(w.imageUrl, Modifier.width(44.dp), corner = 4.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.name, style = MaterialTheme.typography.titleSmall)
                    Text("Want ${w.wanted}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                if (AppSettings.showPrices) Text(formatUsd(w.priceUsd), style = MaterialTheme.typography.titleSmall, color = Palette.Accent)
                IconButton(onClick = { vm.removeWish(w.id) }) { Icon(Icons.Filled.DeleteOutline, "Remove", tint = Palette.Muted) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
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
