package com.mtgscanner.ui.collection

import com.mtgscanner.ui.theme.Space
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import com.mtgscanner.data.db.placeName
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.mtgscanner.ui.components.pinchToResize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.components.boxIcon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import com.mtgscanner.data.BoxRules
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.mtgscanner.ui.components.CollapsingHeader
import com.mtgscanner.ui.components.flashOnce
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Deck
import androidx.compose.material.icons.outlined.Style
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.RarityMark
import com.mtgscanner.ui.components.SetMark
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.data.db.unitEur
import com.mtgscanner.util.PriceCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mtgscanner.ui.components.foilShimmer
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.ToolChip
import com.mtgscanner.ui.components.cardPrice
import com.mtgscanner.ui.components.cardPriceShort
import com.mtgscanner.ui.components.moneySuffix
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.mtgscanner.data.Binder
import com.mtgscanner.ui.detail.CardDetailScreen
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.CardShape
import com.mtgscanner.ui.theme.TabularNums

@Composable
fun BrowseScreen(
    boxId: Long,
    onBack: () -> Unit,
    onOpenCard: (Long) -> Unit,
    onTrade: (binderId: Long) -> Unit = {},
    /** Another box picked from the title's menu. */
    onOpenBox: ((Long) -> Unit)? = null,
    /**
     * Shown as Collection's All tab: no back arrow or title (the page has them) and no search of its own, since
     * [pageQuery], the page search above the tabs, drives it.
     */
    embedded: Boolean = false,
    pageQuery: String = "",
) {
    val vm: BrowseViewModel = viewModel(key = "browse-$boxId", factory = AppViewModels.browse(boxId))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val f by vm.filters.collectAsStateWithLifecycle()
    val grid by vm.grid.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val selecting by vm.selecting.collectAsStateWithLifecycle()
    val showTip by vm.showTip.collectAsStateWithLifecycle()
    val dense by vm.dense.collectAsStateWithLifecycle()
    val pagesOn by vm.pages.collectAsStateWithLifecycle()
    val deckChoices by vm.deckChoices.collectAsStateWithLifecycle()
    /** Wide screens show the card beside the list. */
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    /** The card in the side pane on wide screens. */
    var paneId by remember { mutableStateOf<Long?>(null) }
    var menu by remember { mutableStateOf(false) }
    val kinds = remember(ui.boxes) { ui.boxes.associate { it.id to it.kind } }
    val perCopy = remember(ui.boxes) { ui.boxes.filter { it.pocketPerCopy }.map { it.id }.toSet() }
    fun tap(c: CollectionCard) {
        if (wide) paneId = c.id else onOpenCard(c.id)
    }
    val saved by vm.saved.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var savingSearch by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    /** A card swiped right, waiting for a box to move to. */
    var moving by remember { mutableStateOf<CollectionCard?>(null) }
    val picking = selecting || selected.isNotEmpty()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Each card's change over the last week, for the 7d column of the list. History is in dollars.
    val showWeek = AppSettings.showPrices && !grid && AppSettings.currency != PriceCurrency.EUR
    // Every shown card in list order, flattened once per list change rather than on each tap.
    val shownCards = remember(ui.groups) { ui.groups.flatMap { it.second } }
    val week by produceState(emptyMap<Long, Double>(), shownCards, showWeek) {
        value = if (!showWeek) emptyMap() else {
            val repo = (ctx.applicationContext as com.mtgscanner.MtgApp).repository
            val cards = shownCards
            withContext(Dispatchers.Default) { runCatching { weekChanges(repo, cards) }.getOrDefault(emptyMap()) }
        }
    }
    androidx.activity.compose.BackHandler(enabled = picking) { vm.clearSelection() }

    // Dots on cards whose price moved a lot, or that came in from elsewhere, since this list was last looked at.
    LaunchedEffect(Unit) { com.mtgscanner.data.SeenCards.load(ctx) }
    val fresh = remember(shownCards, com.mtgscanner.data.SeenCards.seenAt) { com.mtgscanner.data.SeenCards.fresh(shownCards) }
    val shownNow by rememberUpdatedState(shownCards)
    val allNow by rememberUpdatedState(ui.all)
    DisposableEffect(boxId) {
        onDispose {
            val repo = (ctx.applicationContext as com.mtgscanner.MtgApp).repository
            val seen = shownNow
            val all = allNow
            if (all.isNotEmpty()) repo.appScope.launch { runCatching { com.mtgscanner.data.SeenCards.markSeen(ctx.applicationContext, seen, all) } }
        }
    }

    Row(
        Modifier.fillMaxSize().background(Palette.Background),
    ) {
    val collapse = com.mtgscanner.ui.components.rememberCollapseState()
    // A new search or sort starts from the top, with the header showing.
    LaunchedEffect(f.query, f.sort, f.group, f.activeCount) { collapse.show() }
    val closeKeyboard = com.mtgscanner.ui.components.rememberKeyboardCloser()
    if (embedded) LaunchedEffect(pageQuery) { vm.update { it.copy(query = pageQuery) } }
    Column(Modifier.weight(1f).fillMaxHeight().then(if (embedded) Modifier else Modifier.statusBarsPadding()).nestedScroll(closeKeyboard).nestedScroll(collapse.connection)) {
        val pickedRows = remember(shownCards) { shownCards.map { PickedRow(it.id, it.unitPrice(), it.quantity, it.proxy) } }
        val pickedEur = remember(shownCards) { shownCards.map { PickedRow(it.id, it.unitEur(), it.quantity, it.proxy) } }
        val currency = AppSettings.currency
        val pickedValue = remember(selected, pickedRows, pickedEur, currency) {
            if (selected.isEmpty()) null
            else formatMoney(SelectionValue.total(pickedRows, selected), SelectionValue.total(pickedEur, selected)).takeIf { it != "--" }
        }
        if (picking) SelectionBar(
            selected.size, ui.boxes, deckChoices, vm, onTag = { tagging = true }, onEdit = { editing = true },
            value = if (AppSettings.showPrices) pickedValue else null,
            onCopy = {
                val names = shownCards.filter { it.id in selected }.map { it.name }.distinct()
                com.mtgscanner.util.CopyText.copy(ctx, names.joinToString("\n"), if (names.size == 1) "Card name" else "Card names")
            },
        )
        // In Collection's All tab the totals line above the tabs says it all, so this row only shows once filtered.
        else if (!embedded || f.activeCount > 0 || f.query.isNotBlank()) Row(Modifier.fillMaxWidth().padding(end = Space.s2, top = Space.s1), verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            else Spacer(Modifier.width(Space.s4))
            // Tap a box's name to jump to another box.
            val others = remember(ui.boxes, boxId) { if (boxId < 0 || onOpenBox == null) emptyList() else ui.boxes.filter { it.id != boxId } }
            var switching by remember { mutableStateOf(false) }
            Column(Modifier.weight(1f).then(if (others.isNotEmpty()) Modifier.clickable { switching = true } else Modifier)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!embedded) Text(ui.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (others.isNotEmpty()) Icon(Icons.Outlined.ArrowDropDown, "Other boxes", tint = Palette.Icon)
                }
                DropdownMenu(expanded = switching, onDismissRequest = { switching = false }) {
                    others.forEach { b ->
                        DropdownMenuItem(text = { Text(b.placeName()) }, onClick = { switching = false; onOpenBox?.invoke(b.id) })
                    }
                }
                // In Collection's All tab the totals line above the tabs already has the count; show it only once filtered.
                val filtered = f.activeCount > 0 || f.query.isNotBlank()
                if (!embedded || filtered) Text(
                    if (filtered) "${ui.shownCards} of ${ui.totalCards} cards${moneySuffix(ui.shownValue, ui.shownEur)}"
                    else "${ui.totalCards} cards${moneySuffix(ui.shownValue, ui.shownEur)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
            }
            // A saved or recent search picked on the All tab isn't in the page search box, so it gets its own way out.
            if (embedded && f.query.isNotBlank() && f.query != pageQuery) TextButton(onClick = { vm.update { it.copy(query = pageQuery) } }) {
                Text("Clear ${f.query.take(24)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (ui.boxes.any { it.id == boxId && it.kind == "trade" }) TextButton(onClick = { onTrade(boxId) }) { Text("Trade") }
            if (ui.pagesView) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Pages") },
                        trailingIcon = { if (pagesOn) Icon(Icons.Outlined.Check, null, tint = Palette.Text) },
                        onClick = { menu = false; vm.togglePages() },
                    )
                    if (ui.binder) DropdownMenuItem(
                        text = { Text("Pocket per copy") },
                        trailingIcon = { if (ui.pocketPerCopy) Icon(Icons.Outlined.Check, null, tint = Palette.Text) },
                        onClick = { menu = false; vm.setPocketPerCopy(!ui.pocketPerCopy) },
                    )
                    if (ui.unslotted > 0) DropdownMenuItem(
                        text = { Text("Number slots") },
                        onClick = { menu = false; vm.numberSlots() },
                    )
                }
            }
        }
        // Everything above the cards slides away while scrolling down, and comes back on scrolling up.
        CollapsingHeader(collapse) {
            MoveListHost()

            var syntaxHelp by remember { mutableStateOf(false) }
            if (!embedded) SearchField(
                f.query, { q -> vm.update { it.copy(query = q) } }, Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
                placeholder = "r:rare t:creature",
                mono = true,
                focusOnOpen = remember { BrowseLaunch.takeFocus() },
                trailing = {
                    Text(
                        "?",
                        color = if (syntaxHelp) Palette.Text else Palette.Muted,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { syntaxHelp = !syntaxHelp }.padding(horizontal = Space.s2),
                    )
                },
            )
            ui.searchNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s6))
            }
            if (syntaxHelp) SyntaxHelp(Modifier.padding(horizontal = Space.s4, vertical = Space.s1))
            // The tip shows on one visit only: it goes by itself after a few seconds, or when closed.
            if (showTip && ui.totalCards > 0 && !picking) LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(8_000)
                vm.dismissTip()
            }
            if (showTip && ui.totalCards > 0 && !picking) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s1)
                        .clip(RoundedCornerShape(Corners.Medium)).background(Palette.Surface).padding(start = Space.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Hold to zoom or select · swipe right to move, left to sell",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::dismissTip) { Icon(Icons.Outlined.Close, "Hide tip", tint = Palette.Icon, modifier = Modifier.size(18.dp)) }
                }
            }
            // Sort, group and filters scroll on the left; Select and the card size stay put on the right.
            Row(Modifier.fillMaxWidth().padding(end = Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = Space.s4, end = Space.s2, top = Space.s2, bottom = Space.s2),
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var sortMenu by remember { mutableStateOf(false) }
                var groupMenu by remember { mutableStateOf(false) }
                Box {
                    // The short name keeps Group and Filters in view next to Select; the menu has the full names.
                    ToolChip(f.sort.short, Icons.AutoMirrored.Outlined.Sort, { sortMenu = true }, compact = true)
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortBy.entries.filter { AppSettings.showPrices || !it.isPrice }.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label, color = if (f.sort == s) Palette.Text else Palette.Text) },
                                onClick = { vm.update { it.copy(sort = s) }; sortMenu = false },
                            )
                        }
                    }
                }
                Box {
                    ToolChip(if (f.group == GroupBy.NONE) "Group" else f.group.label, Icons.AutoMirrored.Outlined.FormatListBulleted, { groupMenu = true }, active = f.group != GroupBy.NONE, compact = true)
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        GroupBy.entries.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g.label, color = if (f.group == g) Palette.Text else Palette.Text) },
                                onClick = { vm.update { it.copy(group = g) }; groupMenu = false },
                            )
                        }
                    }
                }
                BadgedBox(badge = { if (f.activeCount > 0) Badge(containerColor = Palette.Accent) { Text("${f.activeCount}") } }) {
                    ToolChip("Filters", Icons.Outlined.FilterAlt, { showFilters = true }, active = f.activeCount > 0, compact = true)
                }
                if (f.query.isNotBlank() || f.activeCount > 0) {
                    ToolChip("Save search", Icons.Outlined.StarBorder, { savingSearch = true }, compact = true)
                }
                // Saved and recent searches share this row, after the tools.
                if (f.query.isEmpty() && f.activeCount == 0) {
                    saved.forEach { sv ->
                        SelectPill(sv.name, false, { vm.applySaved(sv) }, leading = {
                            Icon(Icons.Filled.Star, null, tint = Palette.Gold, modifier = Modifier.size(18.dp))
                        })
                    }
                    recent.forEach { q ->
                        SelectPill(q, false, { vm.update { it.copy(query = q) } }, leading = {
                            Icon(Icons.Outlined.History, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
                        })
                    }
                    if (recent.isNotEmpty()) TextButton(onClick = vm::clearRecent) { Text("Clear recent", color = Palette.Muted) }
                }
            }
            if (ui.totalCards > 0 && !picking) TextButton(onClick = vm::startSelecting, contentPadding = PaddingValues(horizontal = Space.s2)) { Text("Select") }
            IconButton(onClick = vm::cycleView) {
                // Shows the view you get next.
                when {
                    grid -> Icon(Icons.Outlined.ViewAgenda, "List view")
                    !dense -> Icon(Icons.Outlined.ViewHeadline, "Compact list")
                    else -> Icon(Icons.Outlined.GridView, "Grid view")
                }
            }
            }
        }

        when {
            !ui.loaded -> Unit
            ui.totalCards == 0 -> EmptyState(if (boxId < 0) "No cards yet" else "Empty box", Modifier.padding(top = Space.s6), fullScreen = boxId < 0)
            ui.groups.isEmpty() -> EmptyState(
                "No matches", Modifier.padding(top = Space.s6),
                action = "Clear filters", onAction = { vm.clearFilters(); vm.update { it.copy(query = "") } },
            )
            ui.pagesView && pagesOn && !picking -> BinderPages(ui.binderCards, ui.unslotted, ui.binder && ui.pocketPerCopy, onNumber = vm::numberSlots, onOpen = { tap(it) })
            // Pull down to refresh prices: in a box, just its cards; in All cards, the whole collection.
            else -> com.mtgscanner.ui.components.PriceRefreshBox(
                Modifier.fillMaxSize(),
                cards = if (boxId < 0) null else ({ allNow.filter { it.boxId == boxId }.map { it.scryfallId } }),
            ) { CardGrid(
                ui.groups, grid, selected, picking, dense, week,
                fresh = fresh,
                slotOf = { c -> slotLabel(c, kinds[c.boxId], c.boxId in perCopy) },
                onOpen = { c -> if (picking) vm.toggleSelect(c.id) else tap(c) },
                onLongPress = { c -> vm.toggleSelect(c.id); if (showTip) vm.dismissTip() },
                onSwipeMove = { c -> moving = c },
                onSwipeSell = { c -> vm.sellOne(c) },
                // A box with dividers lists its cards by divider: draw the headers as divider tabs.
                tabs = f.group == GroupBy.NONE && ui.boxes.any { it.id == boxId && com.mtgscanner.data.BoxLayout.of(it.divider).hasDividers },
                byName = f.sort == SortBy.NAME && f.group == GroupBy.NONE,
                // Pinch out for bigger pictures, in for more cards on screen.
                modifier = Modifier.pinchToResize(vm::bigger, vm::smaller),
                onDragSelect = vm::setSelection,
                scrollKey = "browse-$boxId",
            ) }
        }
    }

    if (wide) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val id = paneId
            if (id != null) key(id) { CardDetailScreen(id = id, onBack = { paneId = null }) }
            else EmptyState("Pick a card", Modifier.align(Alignment.Center))
        }
    }
    }

    if (showFilters) FilterSheet(f, ui, vm, saved, onDismiss = { showFilters = false })
    if (savingSearch) NameDialog("Save this search", "e.g. Red rares under $5", "Save", onDone = { vm.saveSearch(it); savingSearch = false }, onDismiss = { savingSearch = false })
    if (tagging) TagDialog(ui.userTags, onDone = { vm.tagSelected(it); tagging = false }, onDismiss = { tagging = false })
    if (editing) BulkEditDialog(
        onApply = { cond, lang, foil, paid, from -> vm.editSelected(cond, lang, foil, paid, from); editing = false },
        onDismiss = { editing = false },
    )
    moving?.let { c ->
        BoxPickerDialog(
            "Move ${c.name} to", ui.boxes.filter { it.id != c.boxId }, onPick = { vm.moveOne(c.id, it); moving = null }, onDismiss = { moving = null },
            decks = deckChoices, onPickDeck = { vm.moveOneToDeck(c.id, it); moving = null },
        )
    }
}
