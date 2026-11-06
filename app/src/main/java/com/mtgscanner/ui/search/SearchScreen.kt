package com.mtgscanner.ui.search

import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.components.pinchToResize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mtgscanner.data.CardKingdom
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.ScryfallSet
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.collection.LIST_COLUMN
import com.mtgscanner.ui.components.AddCardSheet
import com.mtgscanner.ui.components.BoxEditorSheet
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.OwnedBadge
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.ui.components.SetMark
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.UnderlineTabs
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.ui.components.scryfallPriceShort
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.components.PlaceholderCards
import com.mtgscanner.ui.components.PlaceholderRows
import com.mtgscanner.ui.components.ruleBelow
import com.mtgscanner.ui.components.panelRow

@Composable
fun SearchScreen(
    vm: SearchViewModel = viewModel(factory = AppViewModels.Factory),
    /** Opens the set browser for a set code; without it, a set opens as a card search. */
    onOpenSet: ((String) -> Unit)? = null,
    /** Opens a collection row; "You own" in the add sheet uses it. */
    onOpenCard: ((Long) -> Unit)? = null,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ownedSets by vm.ownedBySet.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    // A box or deck just made from the add sheet, so the sheet selects it.
    var pick by remember { mutableStateOf<com.mtgscanner.ui.components.AddPick?>(null) }
    LaunchedEffect(ui.selected?.id) { pick = null }
    val owned by vm.owned.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var newBox by remember { mutableStateOf(false) }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    com.mtgscanner.util.Haptics.Attach()

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabBar(ui.tab, vm::setTab)
            when (ui.tab) {
                0 -> CardsTab(ui, vm, owned)
                1 -> SetsTab(ui, vm, ownedSets, onOpenSet)
                // Every precon MTGJSON lists, to see how much you own and save one as a deck.
                else -> com.mtgscanner.ui.decks.PreconBrowser(
                    viewModel(factory = AppViewModels.Factory),
                    Modifier.padding(horizontal = Space.s4, vertical = Space.s2), title = false,
                )
            }
        }
        com.mtgscanner.ui.components.SlimSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    ui.selected?.let { card ->
        if (!ui.showPrintings) {
            val mine = owned.printingOf(card)
            AddCardSheet(
                card = card,
                boxes = boxes,
                // Default to where you already keep this card.
                defaultBoxId = mine.boxId?.takeIf { id -> boxes.any { it.id == id } } ?: vm.lastBoxId,
                onAdd = vm::add,
                onChangePrinting = vm::openPrintings,
                onDismiss = { vm.select(null) },
                onCreateBox = { newBox = true },
                onWishlist = vm::wishlist,
                owned = mine.label,
                onOwned = mine.rowId?.let { id -> onOpenCard?.let { open -> { vm.select(null); open(id) } } },
                decks = decks,
                onAddToDeck = vm::addToDeck,
                onCreateDeck = { name -> vm.createDeck(name) { pick = com.mtgscanner.ui.components.AddPick(deckId = it) } },
                pick = pick,
            )
        }
    }
    if (ui.showPrintings) PrintingsSheet(ui.printings, ui.selected?.id, vm::pickPrinting, vm::closePrintings)
    if (newBox) {
        BoxEditorSheet(
            title = "New box",
            onSave = { d -> vm.createBox(d) { pick = com.mtgscanner.ui.components.AddPick(boxId = it) }; newBox = false },
            onDismiss = { newBox = false },
        )
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    UnderlineTabs(listOf("Cards", "Sets", "Precons"), selected, onSelect, Modifier.background(Palette.Background))
}

@Composable
private fun CardsTab(ui: SearchUi, vm: SearchViewModel, owned: Map<String, Owned>) {
    val focus = LocalFocusManager.current
    var showFilters by remember { mutableStateOf(false) }
    var pricing by remember { mutableStateOf(false) }
    if (pricing) PriceListSheet(owned, onDismiss = { pricing = false })
    if (showFilters) FilterSheet(
        filters = ui.filters,
        onChange = vm::setFilters,
        onApply = { showFilters = false; focus.clearFocus(); vm.submit() },
        onDismiss = { showFilters = false },
    )

    // The search field and filters slide away while scrolling the results, and come back on the way up.
    val collapse = com.mtgscanner.ui.components.rememberCollapseState()
    LaunchedEffect(ui.ranQuery) { collapse.show() }
    val closeKeyboard = com.mtgscanner.ui.components.rememberKeyboardCloser()
    Column(Modifier.fillMaxSize().nestedScroll(closeKeyboard).nestedScroll(collapse.connection)) {
        com.mtgscanner.ui.components.CollapsingHeader(collapse) {
            Column(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = Space.s4, vertical = Space.s3)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(Corners.Medium)).background(Palette.SurfaceHigh).padding(start = Space.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (ui.query.isEmpty()) Text("Search", color = Palette.Muted, style = MaterialTheme.typography.bodyLarge)
                            BasicTextField(
                                value = ui.query,
                                onValueChange = vm::setQuery,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                                cursorBrush = SolidColor(Palette.Text),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.submit() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (ui.query.isNotEmpty()) {
                            IconButton(onClick = vm::clearResults) { Icon(Icons.Outlined.Close, "Clear search", tint = Palette.Icon, modifier = Modifier.size(18.dp)) }
                        } else {
                            Spacer(Modifier.width(Space.s4))
                        }
                    }
                    Spacer(Modifier.width(Space.s3))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Corners.Medium))
                            .background(Palette.Accent)
                            .clickable { focus.clearFocus(); vm.submit() }
                            .padding(horizontal = Space.s4, vertical = Space.s4),
                    ) { Text("Search", style = MaterialTheme.typography.labelLarge, color = Palette.OnAccent) }
                    IconButton(onClick = vm::toggleGrid) {
                        Icon(if (ui.grid) Icons.AutoMirrored.Outlined.List else Icons.Outlined.GridView, "Change layout", tint = Palette.Icon)
                    }
                }
                Row(Modifier.padding(top = Space.s3), verticalAlignment = Alignment.CenterVertically) {
                    val n = ui.filters.activeCount
                    SelectPill(
                        if (n > 0) "Filters · $n" else "Filters", n > 0, { showFilters = true },
                        leading = { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp), tint = if (n > 0) Palette.AccentText else Palette.Icon) },
                    )
                    Spacer(Modifier.width(Space.s2))
                    SelectPill("Price a list", false, { pricing = true })
                    Spacer(Modifier.width(Space.s3))
                    Text("e.g. t:dragon c:r", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                ui.suggestions.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.suggestions) { name ->
                        Row(
                            Modifier.fillMaxWidth().clickable { focus.clearFocus(); vm.pickSuggestion(name) }.padding(horizontal = Space.s4, vertical = Space.s4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Search, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Space.s4))
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                ui.ranQuery == null && ui.query.isEmpty() && (ui.recent.isNotEmpty() || ui.viewed.isNotEmpty()) -> History(ui, vm)
                ui.ranQuery == null -> EmptyState("Find any card", Modifier.align(Alignment.TopCenter).padding(top = Space.s6))
                ui.results.isEmpty() && ui.loading ->
                    if (ui.grid) PlaceholderCards(9, Modifier.align(Alignment.TopCenter).padding(Space.s4))
                    else PlaceholderRows(8, Modifier.align(Alignment.TopCenter).padding(Space.s4))
                ui.results.isEmpty() && ui.failed && ui.error != null ->
                    EmptyState("Can't run this search", Modifier.align(Alignment.TopCenter).padding(top = Space.s6), body = ui.error.orEmpty())
                ui.results.isEmpty() && ui.failed -> OfflineState(vm::retry, Modifier.align(Alignment.TopCenter).padding(top = Space.s6))
                ui.results.isEmpty() -> EmptyState("No cards found", Modifier.align(Alignment.TopCenter).padding(top = Space.s6))
                else -> Results(
                    results = ui.results, hasMore = ui.hasMore, loading = ui.loading, moreFailed = ui.moreFailed,
                    grid = ui.grid, ranLabel = ui.ranLabel, owned = owned,
                    note = if (ui.fromPhone && !ui.loading) "Offline · from the card list on your phone" else null,
                    onSelect = vm::select, onLoadMore = vm::loadMore, onClear = vm::clearResults,
                    onBigger = { vm.setGrid(true) }, onSmaller = { vm.setGrid(false) },
                )
            }
        }
    }
}

/**
 * The result list. Takes only what it shows (not the whole [SearchUi]), so typing in the search field
 * doesn't redraw every tile.
 */
@Composable
private fun Results(
    results: List<ScryfallCard>,
    hasMore: Boolean,
    loading: Boolean,
    moreFailed: Boolean,
    grid: Boolean,
    ranLabel: String?,
    owned: Map<String, Owned>,
    note: String?,
    onSelect: (ScryfallCard) -> Unit,
    onLoadMore: () -> Unit,
    onClear: () -> Unit,
    onBigger: () -> Unit,
    onSmaller: () -> Unit,
) {
    val state = rememberLazyGridState()
    // The next page loads once the last few results are on screen.
    val nearEnd by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd, results.size) { if (nearEnd) onLoadMore() }
    LazyVerticalGrid(
        columns = if (grid) GridCells.Adaptive(108.dp) else GridCells.Adaptive(LIST_COLUMN),
        state = state,
        contentPadding = PaddingValues(start = Space.s3, end = Space.s3, bottom = Space.s6),
        modifier = Modifier.fillMaxSize().pinchToResize(onBigger, onSmaller),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(start = Space.s2, top = Space.s3, bottom = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                Text(ranLabel.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${results.size}${if (hasMore) "+" else ""} cards", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Clear results", tint = Palette.Icon) }
            }
        }
        if (note != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(start = Space.s2, bottom = Space.s2))
        }
        itemsIndexed(results, key = { _, c -> c.id }) { _, c ->
            // This printing's copies: a card owned only in another printing doesn't count as this one.
            val mine = owned.printingOf(c)
            if (grid) {
                CardTile(c.smallImageUrl, c.name, scryfallPriceShort(c, foil = c.prices.usd == null && c.prices.usdFoil != null), owned = mine.count, onClick = { onSelect(c) })
            } else {
                ResultRow(c, mine) { onSelect(c) }
            }
        }
        if (loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PlaceholderRows(2, Modifier.padding(Space.s2))
            }
        } else if (moreFailed) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(Space.s4), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.TextButton(onClick = onLoadMore) { Text("Couldn't load more · Retry", color = Palette.Text) }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(c: ScryfallCard, mine: OwnedPrinting, onClick: () -> Unit) {
    val owned = mine.count
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).ruleBelow().padding(horizontal = Space.s1, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(c.smallImageUrl, Modifier.width(46.dp), zoom = true)
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(Space.s2))
                ManaCost(c.effectiveManaCost, size = 14.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SetMark(c.set, c.rarity)
                Spacer(Modifier.width(Space.s2))
                Text("${c.set.uppercase()} #${c.collectorNumber} · ${c.effectiveTypeLine}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Copies in other printings are said apart, not counted as this one.
            if (mine.others > 0) {
                Text("${mine.others} in other printings", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (owned > 0) {
            Spacer(Modifier.width(Space.s2))
            OwnedBadge(owned, foil = mine.printing?.foil ?: 0)
        }
        if (AppSettings.showPrices) {
            Spacer(Modifier.width(Space.s2))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(c.prices.usd?.toDoubleOrNull(), c.prices.eur?.toDoubleOrNull()), style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                CardKingdom.buyPrice(c.id, foil = c.prices.usd == null && c.prices.usdFoil != null)?.let {
                    Text("CK buys ${formatUsd(it)}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                }
            }
        }
    }
}

/** Recent searches and recently viewed cards, shown while the search box is empty. */
@Composable
private fun History(ui: SearchUi, vm: SearchViewModel) {
    val focus = LocalFocusManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.s6)) {
        if (ui.viewed.isNotEmpty()) {
            item {
                Text("Recently viewed", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(start = Space.s4, top = Space.s4, bottom = Space.s2))
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.s4)) {
                    items(ui.viewed, key = { it.id }) { v ->
                        Column(Modifier.width(84.dp).clip(RoundedCornerShape(Corners.Small)).clickable { vm.openViewed(v) }.padding(Space.s1)) {
                            CardImage(v.image, Modifier.fillMaxWidth(), zoom = true)
                            Text(v.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Space.s1))
                        }
                    }
                }
            }
        }
        if (ui.recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s2, top = Space.s3), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent searches", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                }
            }
            items(ui.recent) { q ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.s4).panelRow(first = q === ui.recent.first(), last = q === ui.recent.last())
                        .clickable { focus.clearFocus(); vm.rerun(q) }.padding(horizontal = Space.s3, vertical = Space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.History, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s4))
                    Column {
                        Text(q.text.ifEmpty { "Filtered" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val f = q.filterLabel
                        if (f.isNotEmpty()) Text(f, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            androidx.compose.material3.TextButton(onClick = vm::clearHistory, modifier = Modifier.padding(start = Space.s2, top = Space.s1)) {
                Text("Clear history", color = Palette.Muted)
            }
        }
    }
}

/** Shown when Scryfall can't be reached. */
@Composable
private fun OfflineState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState("Can't reach Scryfall", modifier, action = "Retry", onAction = onRetry)
}

@Composable
private fun SetsTab(ui: SearchUi, vm: SearchViewModel, ownedSets: Map<String, Int>, onOpenSet: ((String) -> Unit)?) {
    SearchField(ui.setFilter, vm::setSetFilter, Modifier.padding(Space.s4), placeholder = "Filter sets")
    val sets = ui.sets
    if (ui.setsFailed) {
        OfflineState(vm::retrySets, Modifier.padding(top = Space.s6))
        return
    }
    if (sets == null) {
        PlaceholderRows(8, Modifier.padding(horizontal = Space.s4))
        return
    }
    val q = ui.setFilter.trim().lowercase()
    val shown = remember(sets, q) { if (q.isEmpty()) sets else sets.filter { it.name.lowercase().contains(q) || it.code.lowercase() == q } }
    if (shown.isEmpty()) {
        EmptyState("No sets found", Modifier.padding(top = Space.s6))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.s6)) {
        items(shown, key = { it.code }) { s ->
            SetRow(s, ownedSets[s.code.lowercase()] ?: 0) { if (onOpenSet != null) onOpenSet(s.code) else vm.openSet(s) }
        }
    }
}

@Composable
private fun SetRow(s: ScryfallSet, owned: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.s4).ruleBelow().padding(vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(Corners.Medium)).background(Palette.Surface), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = s.iconSvgUri,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Palette.Icon),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(Space.s4))
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${s.code.uppercase()} · ${s.releasedAt.orEmpty().take(4)} · ${s.setType.replace('_', ' ')}",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
        }
        Text(
            if (owned > 0) "$owned / ${s.cardCount}" else "${s.cardCount}",
            style = MaterialTheme.typography.labelLarge,
            color = if (owned > 0) Palette.Text else Palette.Muted,
        )
    }
}
