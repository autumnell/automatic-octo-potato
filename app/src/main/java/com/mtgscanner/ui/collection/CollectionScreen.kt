package com.mtgscanner.ui.collection

import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.components.pressScale
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.util.CardNames
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.filled.LocalShipping
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
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.NotificationsNone
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

/** Collection overview with four tabs: boxes, decks, wishlist, and tools (with trade binders). */
@Composable
fun CollectionScreen(
    onBrowse: (boxId: Long) -> Unit,
    onSettings: () -> Unit,
    onOpenDeck: (deckId: Long) -> Unit,
    /** Opens a new deck on its typed list, to paste a decklist into. */
    onPasteDeck: () -> Unit = { onOpenDeck(0L) },
    onOpenCard: (Long) -> Unit = {},
    /** Opens the trade screen for a trade binder. */
    onTrade: (binderId: Long) -> Unit = {},
    onSets: () -> Unit = {},
    onExtras: () -> Unit = {},
    onLoans: () -> Unit = {},
    /** Opens the Sell tool (sell list and Card Kingdom offers). */
    onBuylist: () -> Unit = {},
    vm: CollectionViewModel = viewModel(factory = AppViewModels.Factory),
    /** The tab it opens on, for the screen pictures. */
    startTab: CollectionTab = CollectionTab.ALL,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val weekChange by vm.weekChange.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    remember { CollectionPrefs.load(ctx) }
    val layout = CollectionPrefs.layout
    var editingLayout by remember { mutableStateOf(false) }
    var tabName by rememberSaveable { mutableStateOf(startTab.name) }
    val tab = CollectionTab.entries.firstOrNull { it.name == tabName } ?: CollectionTab.ALL
    // One search for the page: Boxes and Decks each show their own matches, then the cards found.
    var search by rememberSaveable { mutableStateOf("") }
    var scanningLabel by remember { mutableStateOf(false) }
    var sharingAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val labelsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.exportLabels(ctx.contentResolver, uri)
    }
    // The app's own snackbar shows messages, so they don't stack on top of an Undo.
    LaunchedEffect(message) { message?.let { Undo.offer(it); vm.messageShown() } }
    // Home's "See all" lands here when it can't open Browse itself.
    val wishlistAsked by com.mtgscanner.ui.home.WishlistLaunch.pending.collectAsState()
    LaunchedEffect(wishlistAsked) {
        if (wishlistAsked) { tabName = CollectionTab.WISHLIST.name; com.mtgscanner.ui.home.WishlistLaunch.done() }
    }
    LaunchedEffect(Unit) {
        if (BrowseLaunch.openAll) {
            BrowseLaunch.openAll = false
            onBrowse(-1L)
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        // The title, totals and Browse all slide away while a tab's list scrolls; the tabs stay.
        val collapse = com.mtgscanner.ui.components.rememberCollapseState()
        LaunchedEffect(tab) { collapse.show() }
        Column(Modifier.fillMaxSize().statusBarsPadding().nestedScroll(collapse.connection)) {
            // Once the big title has slid away, a small one stays in its place.
            val gone = collapse.fraction > 0.85f
            androidx.compose.animation.AnimatedVisibility(
                visible = gone,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
            ) {
                Text(
                    "Collection", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().clickable { collapse.show() }.padding(horizontal = Space.s4, vertical = Space.s2),
                )
            }
            com.mtgscanner.ui.components.CollapsingHeader(collapse) {
                Row(Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s1, top = Space.s3, bottom = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                    Text("Collection", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    if (ui.boxes.isNotEmpty()) IconButton(onClick = { scanningLabel = true }) { Icon(Icons.Outlined.QrCodeScanner, "Scan a box label", tint = Palette.Icon) }
                    IconButton(onClick = vm::refreshPrices) { Icon(Icons.Outlined.Refresh, "Refresh prices", tint = Palette.Icon) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More", tint = Palette.Icon) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit page") },
                                leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                                onClick = { menu = false; editingLayout = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings, backup & import") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = { menu = false; onSettings() },
                            )
                            if (ui.totalCards > 0) DropdownMenuItem(
                                text = { Text("Share collection") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = { menu = false; sharingAll = true },
                            )
                            if (ui.boxes.isNotEmpty()) DropdownMenuItem(
                                text = { Text("Print box labels") },
                                leadingIcon = { Icon(Icons.Outlined.Print, null) },
                                onClick = { menu = false; labelsLauncher.launch("box-labels.pdf") },
                            )
                        }
                    }
                }
                if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Space.s4), color = Palette.Text)
                if (layout.shows(CollectionPart.STATS)) {
                    val prices = AppSettings.showPrices
                    StatsStrip(
                        cards = "%,d".format(ui.totalCards),
                        value = if (prices) formatMoneyShort(ui.totalValue, ui.totalEur) else null,
                        // History is in dollars, so there's no week change to show in euros.
                        week = if (prices) WeekChange.dollars(weekChange.takeIf { AppSettings.currency != com.mtgscanner.util.PriceCurrency.EUR }) else null,
                        modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
                        cardCount = ui.totalCards,
                        valueNumber = if (prices) com.mtgscanner.ui.components.primaryValue(ui.totalValue, ui.totalEur) else null,
                        valueFormat = { v ->
                            if (AppSettings.currency == com.mtgscanner.util.PriceCurrency.EUR) com.mtgscanner.ui.components.formatEur(v)
                            else formatUsd(v)
                        },
                    )
                }
                // Above the tabs, so it's there on every one. Wishlist and Tools have nothing to match, so typing there
                // moves to All. On All it filters the cards like Browse's own search.
                if (layout.shows(CollectionPart.BROWSE)) SearchField(
                    search,
                    { q ->
                        search = q
                        if (q.isNotBlank() && tab != CollectionTab.BOXES && tab != CollectionTab.DECKS && tab != CollectionTab.ALL) tabName = CollectionTab.ALL.name
                    },
                    Modifier.padding(horizontal = Space.s4, vertical = Space.s1),
                    placeholder = "Search",
                )
            }
            // A hidden tab still shows while it's open (Home's wishlist link lands on it).
            val tabs = CollectionTab.shown(layout).let { if (tab in it) it else it + tab }
            if (tabs.size > 1) {
                com.mtgscanner.ui.components.UnderlineTabs(
                    tabs.map { it.label }, tabs.indexOf(tab), { i -> tabName = tabs[i].name },
                    horizontalPadding = Space.gutter,
                )
            }
            MoveListHost()
            // Pull down to refresh prices, same as the refresh button.
            com.mtgscanner.ui.components.PriceRefreshBox(refreshing, vm::refreshPrices, Modifier.fillMaxSize()) {
            // With the search hidden, text typed earlier would keep filtering with no field to clear it.
            val shownSearch = if (layout.shows(CollectionPart.BROWSE)) search else ""
            when (tab) {
                CollectionTab.BOXES -> BoxesTab(ui, vm, layout, onBrowse, onTrade, onOpenCard, onEditPage = { editingLayout = true }, query = shownSearch, onQuery = { search = it })
                // Every card, as Browse shows them; the page search filters them.
                CollectionTab.ALL -> BrowseScreen(boxId = -1L, onBack = {}, onOpenCard = onOpenCard, onTrade = onTrade, embedded = true, pageQuery = shownSearch, onClearQuery = { search = "" })
                CollectionTab.DECKS -> DecksTab(onOpenDeck, onPasteDeck = onPasteDeck, query = shownSearch, cards = { CardsFound(shownSearch, vm, onBrowse, onOpenCard) })
                CollectionTab.WISHLIST -> WishlistTab(vm)
                CollectionTab.TOOLS -> ToolsTab(ui, vm, onBrowse, onTrade, onSets, onExtras, onLoans, onBuylist, showTrade = layout.shows(CollectionPart.TRADE), onOpenCard = onOpenCard)
            }
            }
        }
    }
    if (editingLayout) EditCollectionDialog(onDismiss = {
        editingLayout = false
        if (tab !in CollectionTab.shown(CollectionPrefs.layout)) tabName = CollectionTab.ALL.name
    })
    if (sharingAll) ShareDialog("My collection", scope, onDismiss = { sharingAll = false }) { vm.allCards().map { it.shareLine() } }
    if (scanningLabel) BoxQrScanSheet(
        onBox = { id ->
            scanningLabel = false
            if (ui.boxes.any { it.id == id }) onBrowse(id) else Undo.offer("That box is gone")
        },
        onDismiss = { scanningLabel = false },
    )
    val plan by vm.plan.collectAsStateWithLifecycle()
    plan?.let { p -> BoxPlanSheet(p, onApply = { vm.applyPlan() }, onDiscard = vm::discardPlan) }
}

/** "12 to sort ›": opens the cards waiting in boxes marked To be sorted. */
@Composable
private fun ToSortChip(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Corners.Medium)).background(Palette.SurfaceHigh).clickable(onClick = onClick)
            .heightIn(min = 32.dp).padding(start = Space.s3, end = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count to sort", style = MaterialTheme.typography.labelLarge.merge(TabularNums), color = Palette.Text)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BoxesTab(
    ui: OverviewUi, vm: CollectionViewModel, layout: CollectionLayout,
    onBrowse: (Long) -> Unit, onTrade: (Long) -> Unit, onOpenCard: (Long) -> Unit, onEditPage: () -> Unit,
    query: String, onQuery: (String) -> Unit,
) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val toSort by vm.toSort.collectAsStateWithLifecycle()
    val boxRows by vm.boxRows.collectAsStateWithLifecycle()
    var sortingOpen by remember { mutableStateOf(false) }
    var shelf by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<BoxSummary?>(null) }
    val scope = rememberCoroutineScope()
    var splitting by remember { mutableStateOf<BoxSummary?>(null) }
    var editing by remember { mutableStateOf<BoxSummary?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BoxSummary?>(null) }
    var toDeck by remember { mutableStateOf<BoxSummary?>(null) }
    var ordering by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf<BoxSummary?>(null) }
    var sortingNew by remember { mutableStateOf<BoxSummary?>(null) }
    var covering by remember { mutableStateOf<BoxSummary?>(null) }
    var laying by remember { mutableStateOf<BoxSummary?>(null) }
    var tagging by remember { mutableStateOf<BoxSummary?>(null) }
    val nfc = LocalContext.current.let { c -> remember { hasNfc(c) } }
    val ctx = LocalContext.current
    remember { LastChecked.load(ctx) }
    val checkedAt = LastChecked.times
    val now = remember(checkedAt) { System.currentTimeMillis() }
    var orderMenu by remember { mutableStateOf(false) }
    val found = rememberCardsFound(query, vm)
    val colors = remember(ui.boxes) { boxStripeColors(ui.boxes) }
    // Boxes whose rules never get a card because an earlier box takes them all.
    val dead = remember(ui.boxes) { deadBoxNotes(ui.boxes) }

    var ringOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val q = query.trim()
        if (layout.sections.isEmpty()) EmptyState("All hidden", action = "Edit page", onAction = onEditPage)
        for (section in layout.sections) {
            when (section) {
                // A thin colour bar by default (in the Boxes row when that shows); tap it for the full ring, and the ring to fold it back.
                CollectionSection.RING -> if (ringOpen) {
                    ValueRing(
                        ui.boxes, ui.stats, colors, ui.totalCards, ui.totalValue, totalEur = ui.totalEur, legend = layout.shows(CollectionPart.LEGEND),
                        modifier = Modifier.clickable { ringOpen = false },
                    )
                } else if (CollectionSection.BOXES !in layout.sections) {
                    ValueBar(ui.boxes, colors, onClick = { ringOpen = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s4))
                }
                // Shown in the Boxes heading as a chip; here only when the Boxes section is hidden.
                CollectionSection.TO_SORT -> {
                    val toMove = toSort.sumOf { it.card.quantity }
                    if (toMove > 0 && CollectionSection.BOXES !in layout.sections) {
                        Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s1)) { ToSortChip(toMove) { sortingOpen = true } }
                    }
                }
                CollectionSection.BOXES -> {
                    // One row with no title: the box tools menu and New on the right. The page search above the tabs
                    // finds boxes by name here, then cards with their box, divider and shelf spot.
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                        // While searching, "Boxes" heads the boxes found, to set them apart from the cards below.
                        if (q.isNotEmpty()) Text("Boxes", style = MaterialTheme.typography.titleMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                        // The folded value bar sits in this row, left of the tools, so it costs no row of its own.
                        else Box(Modifier.weight(1f)) {
                            if (CollectionSection.RING in layout.sections && !ringOpen) {
                                ValueBar(ui.boxes, colors, onClick = { ringOpen = true }, modifier = Modifier.fillMaxWidth().padding(end = Space.s3))
                            }
                        }
                        val toMove = toSort.sumOf { it.card.quantity }
                        if (toMove > 0) ToSortChip(toMove) { sortingOpen = true }
                        // One menu for the box tools and order, then New.
                        if (ui.boxes.size > 1 || (ui.boxes.isNotEmpty() && layout.shows(CollectionPart.BOX_TOOLS))) Box {
                            IconButton(onClick = { orderMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, "Box tools", tint = if (LastChecked.oldestFirst) Palette.Text else Palette.Icon)
                            }
                            DropdownMenu(expanded = orderMenu, onDismissRequest = { orderMenu = false }) {
                                if (layout.shows(CollectionPart.BOX_TOOLS)) {
                                    if (ui.boxes.isNotEmpty()) DropdownMenuItem(text = { Text("Shelf") }, onClick = { orderMenu = false; shelf = true })
                                    if (ui.boxes.size > 1) DropdownMenuItem(text = { Text("Auto-sort order") }, onClick = { orderMenu = false; ordering = true })
                                }
                                if (ui.boxes.size > 1) {
                                    HorizontalDivider(color = Palette.Outline)
                                    Text("Show boxes by", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s3, vertical = Space.s2))
                                    DropdownMenuItem(
                                        text = { Text("Auto-sort order") },
                                        trailingIcon = { if (!LastChecked.oldestFirst) Icon(Icons.Outlined.Check, null) },
                                        onClick = { orderMenu = false; LastChecked.setOldestFirst(ctx, false) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Last checked") },
                                        trailingIcon = { if (LastChecked.oldestFirst) Icon(Icons.Outlined.Check, null) },
                                        onClick = { orderMenu = false; LastChecked.setOldestFirst(ctx, true) },
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { creating = true }) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Space.s1))
                            Text("New")
                        }
                    }
                    if (ui.boxes.isEmpty()) {
                        EmptyState(title = "No boxes yet", action = "New box", onAction = { creating = true })
                    }
                    val matching = if (q.isNotEmpty()) ui.boxes.filter { it.name.contains(q, ignoreCase = true) } else ui.boxes
                    // Never checked first, then the longest since a check.
                    val shown = if (LastChecked.oldestFirst) LastCheckedMath.oldestFirst(matching, { it.id }, checkedAt) else matching
                    val cardsFound = found.second
                    if (q.isNotEmpty() && shown.isEmpty() && cardsFound.isEmpty() && found.first == query && q.length >= 2) {
                        EmptyState("No matches", action = "Clear", onAction = { onQuery("") })
                    }
                    shown.forEach { b ->
                        BoxCard(
                            b, ui.stats[b.id], colors[b.id] ?: boxColor(b.color),
                            onClick = { onBrowse(b.id) }, onCover = { covering = b }, onEdit = { editing = b }, onDelete = { deleting = b }, onSort = { sorting = b },
                            onSplit = { splitting = b }, onTrade = { onTrade(b.id) }, onLayout = { laying = b }, warning = dead[b.id],
                            onNfc = if (nfc) ({ tagging = b }) else null,
                            onShare = { sharing = b },
                            onToDeck = { toDeck = b },
                            layout = layout,
                            checked = LastCheckedMath.label(checkedAt[b.id], now),
                        )
                    }
                    if (q.length >= 2 && cardsFound.isNotEmpty()) {
                        CardsFoundList(q, cardsFound, onBrowse, onOpenCard) {
                            // Where it is on the shelf, when the boxes are laid out there.
                            ShelfSpot(boxRows, cardsFound.map { it.card.boxId }.toSet(), Modifier.padding(horizontal = Space.s4, vertical = Space.s2))
                        }
                    }
                }
                CollectionSection.LISTS -> if (q.isEmpty()) {
                    SmartListsSection(lists, onOpen = { s ->
                        BrowseLaunch.open(s)
                        onBrowse(s.boxId ?: -1L)
                    }, onDelete = vm::deleteList)
                }
            }
        }
        Spacer(Modifier.height(Space.s6))
    }

    if (creating) {
        BoxEditorSheet(
            title = "New box", onSave = { vm.createBox(it); creating = false }, onDismiss = { creating = false }, layout = true,
            onTemplate = { vm.tryTemplate(it); creating = false },
            onTry = { vm.tryPlan(it.name, listOf(it.toChange(null))); creating = false },
        )
    }
    editing?.let { b ->
        BoxEditorSheet(
            title = "Edit box",
            initial = com.mtgscanner.data.BoxPrefs.place(b.id).let { p -> BoxDraft(b.name, b.kind, b.color, BoxRules.fromJson(b.rules), b.capacity, b.divider, p.location, p.rows) },
            editingId = b.id,
            onSave = { vm.updateBox(b.id, it); editing = null },
            onDismiss = { editing = null },
            layout = true,
            onTry = { vm.tryPlan(it.name, listOf(it.toChange(b.id))); editing = null },
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
        val cards by produceState<List<CollectionCard>?>(null, b.id) { value = vm.cardsIn(b.id) }
        SplitBoxDialog(
            b, cards,
            onSplit = { letter -> vm.splitBox(b, letter); splitting = null },
            onDismiss = { splitting = null },
        )
    }
    covering?.let { b ->
        val cards by produceState(emptyList<CollectionCard>(), b.id) { value = vm.cardsIn(b.id) }
        CoverPickerDialog(
            b.name, cards, ui.stats[b.id]?.coverCardId,
            onPick = { vm.setCover(b.id, it); covering = null },
            onDismiss = { covering = null },
        )
    }
    laying?.let { b -> LayoutDialog(b, onSave = { vm.setLayout(b, it); laying = null }, onDismiss = { laying = null }) }
    tagging?.let { b -> NfcWriteDialog(b.id, b.name, onDismiss = { tagging = null }) }
    if (ordering) AutoSortOrderSheet(ui.boxes, vm::reorder, vm::setFallback, onDismiss = { ordering = false })
    if (shelf) ShelfSheet(onDismiss = { shelf = false })
    if (sortingOpen) ToSortSheet(
        toSort, ui.boxes,
        onMove = { vm.sortCards(listOf(it)) },
        onMoveAll = { vm.sortCards(toSort) },
        onOpenCard = { sortingOpen = false; onOpenCard(it) },
        onDismiss = { sortingOpen = false },
    )
    sharing?.let { b ->
        ShareDialog(b.name, scope, onDismiss = { sharing = null }) { vm.cardsIn(b.id).map { it.shareLine() } }
    }
    toDeck?.let { b ->
        BoxToDeckDialog(b, remember { vm.observeDecks() }, onDismiss = { toDeck = null }) { deckId, name -> vm.moveBoxToDeck(b.id, deckId, name); toDeck = null }
    }
    deleting?.let { b -> DeleteBoxDialog(b, ui.boxes.filter { it.id != b.id && it.kind != "trade" && it.kind != "deck" }, onDismiss = { deleting = null }) { moveTo ->
        vm.deleteBox(b.id, moveTo); deleting = null
    } }
}

/** A box row: a strip of its cover card's art under the name, a color stripe, count, value and change since added. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxCard(
    b: BoxSummary, stats: BoxStats?, stripe: Color, onClick: () -> Unit, onCover: () -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit, onSort: () -> Unit,
    onSplit: () -> Unit, onTrade: () -> Unit, onLayout: () -> Unit, warning: String?,
    /** Writes the box's link to an NFC tag; null on phones without NFC. */
    onNfc: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onToDeck: () -> Unit = {},
    /** Which details show, and full or compact. */
    layout: CollectionLayout = CollectionLayout.DEFAULT,
    /** "Checked 3w ago", or null when never checked. */
    checked: String? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Corners.Medium)
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // The ⋮ sits inside the card's top line, so the card uses the full width.
    val menuButton: @Composable (Boolean) -> Unit = { onArt ->
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(Space.s12)) {
                Icon(Icons.Outlined.MoreVert, "Box options", tint = if (onArt) Color.White else Palette.Icon, modifier = Modifier.size(18.dp))
            }
            // The everyday actions first; sharing, decks and NFC wait under More, and Delete sits apart at the end.
            var more by remember { mutableStateOf(false) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false; more = false }) {
                val close = { menu = false; more = false }
                if (!more) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { close(); onEdit() })
                    if (b.cards > 0) DropdownMenuItem(text = { Text("Sort out") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, null) }, onClick = { close(); onSort() })
                    DropdownMenuItem(text = { Text("Card order") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, null) }, onClick = { close(); onLayout() })
                    if (b.kind != "deck") {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val marked = b.id in com.mtgscanner.data.BoxPrefs.toSort
                        DropdownMenuItem(
                            text = { Text("To be sorted") },
                            leadingIcon = { Icon(Icons.Outlined.Inbox, null) },
                            trailingIcon = { if (marked) Icon(Icons.Outlined.Check, null, tint = Palette.Text) },
                            onClick = { close(); com.mtgscanner.data.BoxPrefs.setToSort(ctx, b.id, !marked) },
                        )
                    }
                    if (b.kind == "trade") DropdownMenuItem(text = { Text("Trade") }, leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) }, onClick = { close(); onTrade() })
                    DropdownMenuItem(text = { Text("More") }, leadingIcon = { Icon(Icons.Outlined.MoreHoriz, null) }, onClick = { more = true })
                    HorizontalDivider(color = Palette.Outline)
                    DropdownMenuItem(text = { Text("Delete", color = Palette.Danger) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = Palette.Danger) }, onClick = { close(); onDelete() })
                } else {
                    DropdownMenuItem(text = { Text("Back") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }, onClick = { more = false })
                    if (b.cards > 0) DropdownMenuItem(text = { Text("Cover card") }, onClick = { close(); onCover() })
                    if (b.cards > 1) DropdownMenuItem(text = { Text("Split") }, onClick = { close(); onSplit() })
                    if (b.cards > 0) DropdownMenuItem(text = { Text("Share") }, onClick = { close(); onShare() })
                    if (b.cards > 0) DropdownMenuItem(text = { Text("Move to a deck") }, onClick = { close(); onToDeck() })
                    if (onNfc != null) DropdownMenuItem(text = { Text("Write to NFC tag") }, onClick = { close(); onNfc() })
                }
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s1).pressScale(press, pressed = 0.98f).clip(shape)
            .background(Palette.BoxCard)
            .combinedClickable(
                interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick, onLongClick = { if (b.cards > 0) onCover() },
            ),
    ) {
        if (layout.compact) BoxCardCompact(b, stats, stripe, warning, layout, checked, menuButton) else BoxCardFull(b, stats, stripe, warning, layout, checked, menuButton)
    }
}

/** "12 / 400 · Full", "No cards" or "57 cards". */
private fun boxCountText(b: BoxSummary, fill: BoxFill): String = when {
    b.capacity > 0 -> "${b.cards} / ${b.capacity}"
    b.cards == 0 -> "No cards"
    else -> "${b.cards} cards"
} + (if (fill == BoxFill.FULL) " · Full" else "")

/** The box's cover art filling the whole card, under a flat dark scrim so the text reads; plain card without art. */
@Composable
private fun BoxArtBackground(art: String?, content: @Composable (onArt: Boolean) -> Unit) {
    if (art == null) { content(false); return }
    Box(Modifier.fillMaxWidth().background(Palette.ArtBase)) {
        AsyncImage(
            model = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, -0.35f),
            modifier = Modifier.matchParentSize(),
        )
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.62f)))
        content(true)
    }
}

/** One line: the name, then count and value. */
@Composable
private fun BoxCardCompact(b: BoxSummary, stats: BoxStats?, stripe: Color, warning: String?, layout: CollectionLayout, checked: String? = null, menu: @Composable (Boolean) -> Unit = {}) {
    val fill = BoxFillMath.state(b.cards, b.capacity)
    val art = if (b.cards > 0 && layout.shows(CollectionPart.ART)) stats?.artUrl else null
    BoxArtBackground(art) { onArt ->
        val text = if (onArt) Color.White else Palette.Text
        val muted = if (onArt) Palette.OnDarkMuted else Palette.Muted
        Column {
            Row(Modifier.padding(start = Space.s4, end = Space.s1, top = Space.s2, bottom = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                BoxTitle(b, stripe, onArt = onArt, modifier = Modifier.weight(1f))
                if (layout.shows(CollectionPart.COUNT)) {
                    Spacer(Modifier.width(Space.s2))
                    Text(boxCountText(b, fill), style = MaterialTheme.typography.bodyMedium.merge(TabularNums), color = fillColor(fill, muted), maxLines = 1)
                }
                if (AppSettings.showPrices && layout.shows(CollectionPart.VALUE)) {
                    Spacer(Modifier.width(Space.s3))
                    com.mtgscanner.ui.components.RollingMoney(b.value, b.valueEur, MaterialTheme.typography.titleMedium.merge(TabularNums), text)
                }
                menu(onArt)
            }
            if (warning != null) {
                Text(
                    warning, style = MaterialTheme.typography.labelMedium, color = Palette.Danger, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Space.s6, end = Space.s4, bottom = Space.s3),
                )
            }
            // Only while ordering by it, so the compact row stays one line otherwise.
            if (checked != null && LastChecked.oldestFirst) {
                Text(
                    checked, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1,
                    modifier = Modifier.padding(start = Space.s6, end = Space.s4, bottom = Space.s3),
                )
            }
        }
    }
}

/** The full card: cover art behind everything, rules, count, value with change, and the fill meter, each when turned on. */
@Composable
private fun BoxCardFull(b: BoxSummary, stats: BoxStats?, stripe: Color, warning: String?, layout: CollectionLayout, checked: String? = null, menu: @Composable (Boolean) -> Unit = {}) {
    val rules = remember(b.rules) { BoxRules.fromJson(b.rules) }
    val fill = BoxFillMath.state(b.cards, b.capacity)
    val art = if (b.cards > 0 && layout.shows(CollectionPart.ART)) stats?.artUrl else null
    val showCount = layout.shows(CollectionPart.COUNT)
    val showValue = AppSettings.showPrices && layout.shows(CollectionPart.VALUE)
    val showChange = AppSettings.showPrices && layout.shows(CollectionPart.CHANGE)
    BoxArtBackground(art) { onArt ->
        val text = if (onArt) Color.White else Palette.Text
        val muted = if (onArt) Palette.OnDarkMuted else Palette.Muted
        // Slimmer than before: tighter padding, where-and-status on one line, one line of rules, a title-size
        // value, and the fill meter as a thin line along the bottom edge.
        Column {
        Column(Modifier.padding(start = Space.s4, end = Space.s1, top = Space.s2, bottom = Space.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BoxTitle(b, stripe, onArt = onArt, modifier = Modifier.weight(1f))
                menu(onArt)
            }
            val where = com.mtgscanner.data.BoxPrefs.places[b.id]?.location?.takeIf { it.isNotBlank() }
            val note = listOfNotNull(where, "To be sorted".takeIf { b.id in com.mtgscanner.data.BoxPrefs.toSort }).joinToString(" · ")
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = Space.s3))
            if (layout.shows(CollectionPart.RULES)) {
                Text(rules.describe(), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = Space.s3))
            }
            if (warning != null) Text(warning, style = MaterialTheme.typography.labelMedium, color = Palette.Danger, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (checked != null) Text(checked, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
            if (showCount || showValue || showChange) {
                Spacer(Modifier.height(Space.s1))
                Row(Modifier.padding(end = Space.s3), verticalAlignment = Alignment.Bottom) {
                    // Keeps the value on the right when the count is off.
                    Box(Modifier.weight(1f)) {
                        if (showCount) {
                            Text(
                                boxCountText(b, fill),
                                style = MaterialTheme.typography.bodyMedium.merge(TabularNums),
                                color = fillColor(fill, text),
                                modifier = Modifier.padding(bottom = Space.s1),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (showValue) com.mtgscanner.ui.components.RollingMoney(b.value, b.valueEur, MaterialTheme.typography.titleLarge.merge(TabularNums), text)
                        val pct = stats?.changePercent
                        if (showChange && stats != null && pct != null && boxChangeShown()) {
                            Text(
                                boxChangeLabel(stats.change, pct),
                                style = MaterialTheme.typography.labelMedium.merge(TabularNums),
                                color = boxChangeColor(stats.change),
                            )
                        }
                    }
                }
            }
        }
        if (b.capacity > 0 && layout.shows(CollectionPart.FILL)) {
            BoxFillMeter(b.cards, b.capacity, stripe, Modifier.padding(horizontal = Space.s4).padding(bottom = Space.s2), thickness = 3.dp)
        }
        }
    }
}

/** A box's icon and its name on label tape, as on the real box; the tape reads on the art strip too. */
@Composable
private fun BoxTitle(b: BoxSummary, stripe: Color, onArt: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(boxIcon(b.kind), null, tint = stripe, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Space.s3))
        LabelTape(b.name, Modifier.weight(1f, fill = false))
        if (b.fallback) {
            Spacer(Modifier.width(Space.s2))
            Text("Fallback", style = MaterialTheme.typography.labelSmall, color = if (onArt) Palette.OnDarkMuted else Palette.Muted)
        }
    }
}

/** Tools, plus your trade binders with a share button each. */
@Composable
private fun ToolsTab(
    ui: OverviewUi, vm: CollectionViewModel, onBrowse: (Long) -> Unit, onTrade: (Long) -> Unit,
    onSets: () -> Unit, onExtras: () -> Unit,
    onLoans: () -> Unit = {}, onSell: () -> Unit = {},
    showTrade: Boolean = true,
    onOpenCard: (Long) -> Unit = {},
) {
    val trade = if (showTrade) ui.boxes.filter { it.kind == "trade" } else emptyList()
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf<BoxSummary?>(null) }
    var spreading by remember { mutableStateOf(false) }
    var cheap by remember { mutableStateOf(false) }
    sharing?.let { b ->
        ShareDialog(b.name, scope, onDismiss = { sharing = null }) { vm.cardsIn(b.id).map { it.shareLine() } }
    }
    if (spreading) SpreadCopiesSheet(vm, ui.boxes, onDismiss = { spreading = false })
    if (cheap) CheapInValueSheet(vm, ui.boxes, onOpenCard = { cheap = false; onOpenCard(it) }, onDismiss = { cheap = false })
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ToolRow(Icons.Outlined.ContentCopy, "Extras finder") { onExtras() }
            ToolRow(Icons.Outlined.GridView, "Set tracker") { onSets() }
            ToolRow(Icons.Outlined.Sell, "Sell") { onSell() }
            ToolRow(Icons.Outlined.Person, "Loans") { onLoans() }
            if (ui.boxes.size > 1) {
                ToolRow(Icons.Outlined.Layers, "Spread-out copies") { spreading = true }
                ToolRow(Icons.Outlined.Flag, "Cheap in value boxes") { cheap = true }
            }
            if (showTrade) ToolGroup("Trade binders")
            if (showTrade && trade.isEmpty()) {
                Text(
                    "Make a Trade binder box to trade",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                    modifier = Modifier.padding(horizontal = Space.s4),
                )
            }
        }
        items(trade, key = { "t${it.id}" }) { b ->
            Surface(
                onClick = { onBrowse(b.id) },
                modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s1),
                shape = RoundedCornerShape(Corners.Medium),
                color = Palette.Surface,
            ) {
                Row(Modifier.padding(start = Space.s4, top = Space.s2, bottom = Space.s2, end = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SwapHoriz, null, tint = Palette.Icon)
                    Spacer(Modifier.width(Space.s3))
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.titleMedium)
                        Text("${b.cards} cards${moneySuffix(b.value, b.valueEur)}", style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted)
                    }
                    TextButton(onClick = { onTrade(b.id) }) { Text("Trade") }
                    IconButton(enabled = b.cards > 0, onClick = { sharing = b }) { Icon(Icons.Outlined.Share, "Share ${b.name}", tint = Palette.Icon) }
                }
            }
        }
        item { Spacer(Modifier.height(Space.s6)) }
    }
}

/** Cards you want. Tap one to change how many; delete offers Undo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WishlistTab(vm: CollectionViewModel) {
    val wishlist by vm.wishlist.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WishlistItem?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(WishlistSort.ADDED.name) }
    val sort = WishlistSort.entries.firstOrNull { it.name == sortName && (AppSettings.showPrices || it != WishlistSort.PRICE) } ?: WishlistSort.ADDED
    val shown = remember(wishlist, query, sort) {
        WishlistSorting.apply(wishlist, query, sort, { it.name }, { it.priceUsd }, { it.addedAt })
    }
    // Scryfall names for the typed text, so cards can be added right here.
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(query) {
        suggestions = emptyList()
        if (query.trim().length < 2) return@LaunchedEffect
        delay(300)
        suggestions = vm.suggestCards(query)
    }
    val wishDecks by vm.wishDecks.collectAsStateWithLifecycle()
    // New printings found by the weekly watch (Reprint · SET).
    val watchCtx = androidx.compose.ui.platform.LocalContext.current
    remember { com.mtgscanner.work.WatchStore.load(watchCtx) }
    val watch by com.mtgscanner.work.WatchStore.state.collectAsStateWithLifecycle()
    val gotIt by vm.gotIt.collectAsStateWithLifecycle()
    val received by vm.received.collectAsStateWithLifecycle()
    // Stages: cards marked ordered, and the pill that shows only those.
    remember { com.mtgscanner.data.WishStages.load(watchCtx) }
    val ordered = com.mtgscanner.data.WishStages.ordered
    LaunchedEffect(wishlist) { if (wishlist.isNotEmpty()) com.mtgscanner.data.WishStages.keepOnly(wishlist.mapTo(HashSet()) { it.oracleId }) }
    // Target prices ("tell me when it's under $40"), checked by the daily price job.
    remember { com.mtgscanner.data.WishTargets.load(watchCtx) }
    val targets = com.mtgscanner.data.WishTargets.targets
    LaunchedEffect(wishlist) { if (wishlist.isNotEmpty()) com.mtgscanner.data.WishTargets.keepOnly(wishlist.mapTo(HashSet()) { it.oracleId }) }
    var targeting by remember { mutableStateOf<WishlistItem?>(null) }
    var history by remember { mutableStateOf<WishlistItem?>(null) }
    var onlyOrdered by rememberSaveable { mutableStateOf(false) }
    val staged = if (onlyOrdered && wishlist.any { it.oracleId in ordered }) shown.filter { it.oracleId in ordered } else shown
    // "Deck" sort: one group per deck that wants cards (each card under its first deck A to Z), then the rest.
    val groups: List<Pair<String?, List<WishlistItem>>> = remember(staged, sort, wishDecks) {
        if (sort != WishlistSort.DECK) listOf(null to staged)
        else staged.groupBy { wishDecks[CardNames.key(it.name)]?.firstOrNull() ?: "Loose" }.toList()
            .sortedWith(compareBy<Pair<String, List<WishlistItem>>> { it.first == "Loose" }.thenBy { it.first.lowercase() })
    }
    val onList = remember(wishlist) { wishlist.mapTo(HashSet()) { it.name.lowercase() } }
    val toAdd = suggestions.filter { it.lowercase() !in onList }.take(5)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            if (wishlist.isEmpty()) {
                EmptyState(title = "Wishlist is empty")
                SearchField(query, { query = it }, Modifier.padding(horizontal = Space.s4, vertical = Space.s1), placeholder = "Add a card")
            } else {
                SectionHeader("${wishlist.sumOf { it.wanted }} wanted") {
                    if (AppSettings.showPrices) {
                        Text(
                            formatUsd(wishlist.sumOf { (it.priceUsd ?: 0.0) * it.wanted }),
                            style = MaterialTheme.typography.titleSmall.merge(TabularNums),
                            color = Palette.Gold,
                            modifier = Modifier.padding(end = Space.s1),
                        )
                    }
                    IconButton(onClick = { sharing = true }) { Icon(Icons.Outlined.Share, "Share wishlist", tint = Palette.Icon) }
                }
                SearchField(query, { query = it }, Modifier.padding(horizontal = Space.s4, vertical = Space.s1), placeholder = "Search or add a card")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.s4, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    val nOrdered = wishlist.count { it.oracleId in ordered }
                    if (nOrdered > 0) SelectPill("Ordered · $nOrdered", onlyOrdered, { onlyOrdered = !onlyOrdered })
                    WishlistSort.entries.filter { AppSettings.showPrices || it != WishlistSort.PRICE }.forEach { s ->
                        SelectPill(s.label, sort == s, { sortName = s.name })
                    }
                }
                if (shown.isEmpty() && toAdd.isEmpty()) EmptyState("No matches", Modifier.padding(top = Space.s4))
            }
        }
        if (toAdd.isNotEmpty()) {
            item { SectionHeader("Add to wishlist") }
            items(toAdd, key = { "add:$it" }) { name ->
                // Tap adds one; hold to pick how many.
                var counts by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.s4).panelRow(first = name == toAdd.first(), last = name == toAdd.last())
                            .combinedClickable(onClick = { vm.wishByName(name); query = "" }, onLongClick = { counts = true })
                            .padding(start = Space.s4, end = Space.s1).heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.Add, "Add $name", tint = Palette.Icon, modifier = Modifier.padding(Space.s3))
                    }
                    DropdownMenu(expanded = counts, onDismissRequest = { counts = false }) {
                        (1..4).forEach { n ->
                            DropdownMenuItem(text = { Text("Want $n") }, onClick = { counts = false; vm.wishByName(name, n); query = "" })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Space.s3)) }
        }
        groups.forEach { (group, rows) ->
            if (group != null) item(key = "g:$group") { SectionHeader(group) }
            items(rows, key = { "w${it.id}" }) { w ->
                // Swipe right for Got it, left to remove (with Undo).
                com.mtgscanner.ui.components.SwipeActions(
                    start = com.mtgscanner.ui.components.SwipeAction("Got it", Icons.Outlined.Check, Palette.Accent) { vm.startGotIt(w) },
                    end = com.mtgscanner.ui.components.SwipeAction("Remove", Icons.Outlined.DeleteOutline, Palette.Danger) { vm.removeWish(w) },
                    modifier = Modifier.animateItem().padding(horizontal = Space.s4),
                ) {
                    Row(
                        Modifier.fillMaxWidth().panelRow(first = w === rows.first(), last = w === rows.last())
                            .clickable { editing = w }.padding(start = Space.s3, top = Space.s2, bottom = Space.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardImage(w.imageUrl, Modifier.width(44.dp), zoom = true)
                        Spacer(Modifier.width(Space.s3))
                        Column(Modifier.weight(1f)) {
                            Text(w.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            com.mtgscanner.work.WatchStore.tag(watch, w.oracleId)?.let { tag ->
                                Text(tag, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
                            }
                            val decks = wishDecks[CardNames.key(w.name)].orEmpty()
                            val more = if (group != null) decks.drop(1) else decks
                            val isOrdered = w.oracleId in ordered
                            Text(
                                (if (isOrdered) "Ordered · " else "") +
                                    "Want ${w.wanted}" + if (more.isNotEmpty()) " · ${if (group != null) "also " else ""}${more.joinToString(", ")}" else "",
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            targets[w.oracleId]?.let { t ->
                                val hit = w.priceUsd != null && w.priceUsd <= t
                                Text(
                                    if (hit) "At your ${formatUsd(t)} target" else "Target ${formatUsd(t)}",
                                    style = MaterialTheme.typography.labelMedium, color = if (hit) Palette.AccentText else Palette.Muted, maxLines = 1,
                                )
                            }
                        }
                        if (AppSettings.showPrices) Text(
                            formatUsd(w.priceUsd), style = MaterialTheme.typography.titleMedium.merge(TabularNums), color = Palette.Text,
                            modifier = Modifier.padding(start = Space.s2),
                        )
                        // One menu instead of three buttons: swipes do Got it and Remove too, so the name keeps its room.
                        val isOrdered = w.oracleId in ordered
                        var rowMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { rowMenu = true }) { Icon(Icons.Outlined.MoreVert, "More for ${w.name}", tint = Palette.Icon) }
                            DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (isOrdered) "Not ordered yet" else "Mark ordered") },
                                    leadingIcon = { Icon(Icons.Outlined.LocalShipping, null, tint = Palette.Icon, modifier = Modifier.size(18.dp)) },
                                    onClick = { rowMenu = false; vm.setOrdered(w, !isOrdered) },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isOrdered) "Received" else "Got it") },
                                    leadingIcon = { Icon(Icons.Outlined.Check, null, tint = Palette.Icon, modifier = Modifier.size(18.dp)) },
                                    onClick = { rowMenu = false; vm.startGotIt(w) },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (w.oracleId in targets) "Change target price" else "Target price") },
                                    leadingIcon = { Icon(Icons.Outlined.NotificationsNone, null, tint = Palette.Icon, modifier = Modifier.size(18.dp)) },
                                    onClick = { rowMenu = false; targeting = w },
                                )
                                DropdownMenuItem(
                                    text = { Text("Price history") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ShowChart, null, tint = Palette.Icon, modifier = Modifier.size(18.dp)) },
                                    onClick = { rowMenu = false; history = w },
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove", color = Palette.Danger) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = Palette.Danger, modifier = Modifier.size(18.dp)) },
                                    onClick = { rowMenu = false; vm.removeWish(w) },
                                )
                            }
                        }
                    }
                }
            }
            if (group != null) item(key = "s:$group") { Spacer(Modifier.height(Space.s2)) }
        }
        item { Spacer(Modifier.height(Space.s6)) }
    }
    if (sharing) ShareDialog("Wishlist", scope, carts = true, onDismiss = { sharing = false }) { wishlist.map { it.shareLine() } }
    gotIt?.let { (w, printings) ->
        PrintingsSheet(printings, w.scryfallId, vm::gotItPrinting, vm::cancelGotIt)
    }
    received?.let { (_, card) -> ReceivedDialog(card, onAdd = vm::receive, onDismiss = vm::cancelReceived) }
    editing?.let { w ->
        WantedDialog(w, onSave = { n -> vm.setWanted(w, n); editing = null }, onDismiss = { editing = null })
    }
    targeting?.let { w ->
        WishTargetDialog(w, targets[w.oracleId], onSave = { com.mtgscanner.data.WishTargets.set(w.oracleId, it); targeting = null }, onDismiss = { targeting = null })
    }
    history?.let { w ->
        val flow = remember(w.scryfallId) { vm.priceHistory(w.scryfallId) }
        WishHistorySheet(w, targets[w.oracleId], flow, onDismiss = { history = null })
    }
}

/** Received a wishlist card: its finish (when it comes both ways) and what was paid, then it's added. */
@Composable
private fun ReceivedDialog(card: com.mtgscanner.data.ScryfallCard, onAdd: (Boolean, Double?) -> Unit, onDismiss: () -> Unit) {
    var foil by remember { mutableStateOf(!card.hasNonFoil) }
    var paid by remember { mutableStateOf("") }
    val price = com.mtgscanner.util.PriceInput.parse(paid)
    val bad = paid.isNotBlank() && price == null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(card.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text("${card.set.uppercase()} · #${card.collectorNumber}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                if (card.hasFoil && card.hasNonFoil) {
                    Spacer(Modifier.height(Space.s2))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        SelectPill("Normal", !foil, { foil = false })
                        SelectPill("Foil", foil, { foil = true })
                    }
                }
                Spacer(Modifier.height(Space.s2))
                OutlinedTextField(
                    value = paid, onValueChange = { paid = it }, singleLine = true,
                    label = { Text("Paid (optional)") }, isError = bad,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(foil, price) }, enabled = !bad) { Text("Add to collection") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A short muted heading over a group of tools. */
@Composable
private fun ToolGroup(title: String) {
    Text(
        title, style = MaterialTheme.typography.labelLarge, color = Palette.Muted,
        modifier = Modifier.padding(start = Space.s4, end = Space.s4, top = Space.s4, bottom = Space.s1),
    )
}

/** One tool on one line: a gray icon and its name. */
@Composable
private fun ToolRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s4))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "Never gets cards · Rares first" for each box whose rules an earlier box always beats. */
internal fun deadBoxNotes(boxes: List<BoxSummary>): Map<Long, String> {
    val names = boxes.associate { it.id to it.name }
    return RuleCheck.deadBoxes(boxes.map { Triple(it.id, BoxRules.fromJson(it.rules), it.capacity > 0) })
        .mapValues { (_, by) -> if (by != null) "Never gets cards · ${names[by]} first" else "Never gets cards" }
}

/** Cards matching [query], worked out off the main thread once typing pauses; paired with the query they're for. */
@Composable
internal fun rememberCardsFound(query: String, vm: CollectionViewModel): Pair<String, List<Found>> {
    var found by remember { mutableStateOf("" to emptyList<Found>()) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { found = query to emptyList<Found>(); return@LaunchedEffect }
        delay(150)
        found = query to vm.findCards(q)
    }
    return found
}

/**
 * The cards part of the page search, under a "Cards" heading with space above it so it reads apart from the boxes
 * or decks found. "All cards" opens Browse with the same search.
 */
@Composable
internal fun CardsFoundList(
    query: String, cards: List<Found>, onBrowse: (Long) -> Unit, onOpenCard: (Long) -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Spacer(Modifier.height(Space.s3))
    SectionHeader("Cards") {
        TextButton(onClick = {
            BrowseLaunch.filters = Filters(query = query)
            BrowseLaunch.title = null
            BrowseLaunch.boxId = null
            onBrowse(-1L)
        }) { Text("All cards") }
    }
    extra()
    cards.forEach { f -> FoundRow(f.card, f.where) { onOpenCard(f.card.id) } }
}

/** The cards part of the page search for tabs that don't hold cards themselves, like Decks. */
@Composable
internal fun CardsFound(query: String, vm: CollectionViewModel, onBrowse: (Long) -> Unit, onOpenCard: (Long) -> Unit) {
    val found = rememberCardsFound(query, vm)
    val q = query.trim()
    if (q.length >= 2 && found.second.isNotEmpty()) {
        Column { CardsFoundList(q, found.second, onBrowse, onOpenCard) }
    }
}
