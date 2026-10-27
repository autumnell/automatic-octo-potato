package com.mtgscanner.ui.collection

import androidx.compose.runtime.LaunchedEffect
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

    // A box's page picks up a soft tint from its cover card's art.
    val coverArt = remember(ui.all, boxId) {
        if (boxId < 0) null
        else {
            val cover = com.mtgscanner.data.BoxCovers(ctx).covers.value[boxId]
            com.mtgscanner.data.BoxArt.coverCard(ui.all.filter { it.boxId == boxId }, cover)?.imageUrl
        }
    }
    val tint = com.mtgscanner.ui.components.rememberArtTint(com.mtgscanner.data.db.artCropUrl(coverArt))
    val top = com.mtgscanner.ui.components.tintedBackground(tint)
    Row(
        Modifier.fillMaxSize().background(Palette.Background)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, Palette.Background), endY = 900f)),
    ) {
    Column(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) {
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
        else Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            // Tap a box's name to jump to another box.
            val others = remember(ui.boxes, boxId) { if (boxId < 0 || onOpenBox == null) emptyList() else ui.boxes.filter { it.id != boxId } }
            var switching by remember { mutableStateOf(false) }
            Column(Modifier.weight(1f).then(if (others.isNotEmpty()) Modifier.clickable { switching = true } else Modifier)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ui.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (others.isNotEmpty()) Icon(Icons.Outlined.ArrowDropDown, "Other boxes", tint = Palette.Icon)
                }
                DropdownMenu(expanded = switching, onDismissRequest = { switching = false }) {
                    others.forEach { b ->
                        DropdownMenuItem(text = { Text(b.placeName()) }, onClick = { switching = false; onOpenBox?.invoke(b.id) })
                    }
                }
                Text(
                    if (f.activeCount > 0 || f.query.isNotBlank()) "${ui.shownCards} of ${ui.totalCards} cards${moneySuffix(ui.shownValue, ui.shownEur)}"
                    else "${ui.totalCards} cards${moneySuffix(ui.shownValue, ui.shownEur)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
            }
            if (ui.boxes.any { it.id == boxId && it.kind == "trade" }) TextButton(onClick = { onTrade(boxId) }) { Text("Trade") }
            if (ui.totalCards > 0) TextButton(onClick = vm::startSelecting) { Text("Select") }
            IconButton(onClick = vm::cycleView) {
                // Shows the view you get next.
                when {
                    grid -> Icon(Icons.Outlined.ViewAgenda, "List view")
                    !dense -> Icon(Icons.Outlined.ViewHeadline, "Compact list")
                    else -> Icon(Icons.Outlined.GridView, "Grid view")
                }
            }
            if (ui.pagesView) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Pages") },
                        trailingIcon = { if (pagesOn) Icon(Icons.Outlined.Check, null, tint = Palette.Accent) },
                        onClick = { menu = false; vm.togglePages() },
                    )
                    if (ui.binder) DropdownMenuItem(
                        text = { Text("Pocket per copy") },
                        trailingIcon = { if (ui.pocketPerCopy) Icon(Icons.Outlined.Check, null, tint = Palette.Accent) },
                        onClick = { menu = false; vm.setPocketPerCopy(!ui.pocketPerCopy) },
                    )
                    if (ui.unslotted > 0) DropdownMenuItem(
                        text = { Text("Number slots") },
                        onClick = { menu = false; vm.numberSlots() },
                    )
                }
            }
        }
        MoveListHost()

        var syntaxHelp by remember { mutableStateOf(false) }
        SearchField(
            f.query, { q -> vm.update { it.copy(query = q) } }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = "r:rare t:creature",
            mono = true,
            trailing = {
                Text(
                    "?",
                    color = if (syntaxHelp) Palette.Text else Palette.Muted,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { syntaxHelp = !syntaxHelp }.padding(horizontal = 8.dp),
                )
            },
        )
        ui.searchNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 24.dp))
        }
        if (syntaxHelp) SyntaxHelp(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (showTip && ui.totalCards > 0 && !picking) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(Corners.Medium)).background(Palette.Surface).padding(start = 14.dp),
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
        if (f.query.isEmpty() && f.activeCount == 0 && (saved.isNotEmpty() || recent.isNotEmpty())) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                saved.forEach { sv ->
                    SelectPill(sv.name, false, { vm.applySaved(sv) }, leading = {
                        Icon(Icons.Filled.Star, null, tint = Palette.Gold, modifier = Modifier.size(14.dp))
                    })
                }
                recent.forEach { q ->
                    SelectPill(q, false, { vm.update { it.copy(query = q) } }, leading = {
                        Icon(Icons.Outlined.History, null, tint = Palette.Icon, modifier = Modifier.size(14.dp))
                    })
                }
                if (recent.isNotEmpty()) TextButton(onClick = vm::clearRecent) { Text("Clear recent", color = Palette.Muted) }
            }
        }
        // Card pages opened last, hidden while searching or filtering.
        if (f.query.isEmpty() && f.activeCount == 0 && !picking) RecentCardsStrip(ui.totalCards, onOpen = { tap(it) })

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var sortMenu by remember { mutableStateOf(false) }
            var groupMenu by remember { mutableStateOf(false) }
            Box {
                ToolChip(f.sort.label, Icons.AutoMirrored.Outlined.Sort, { sortMenu = true })
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortBy.entries.filter { AppSettings.showPrices || !it.isPrice }.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label, color = if (f.sort == s) Palette.Accent else Palette.Text) },
                            onClick = { vm.update { it.copy(sort = s) }; sortMenu = false },
                        )
                    }
                }
            }
            Box {
                ToolChip(if (f.group == GroupBy.NONE) "Group" else f.group.label, Icons.AutoMirrored.Outlined.FormatListBulleted, { groupMenu = true }, active = f.group != GroupBy.NONE)
                DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                    GroupBy.entries.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g.label, color = if (f.group == g) Palette.Accent else Palette.Text) },
                            onClick = { vm.update { it.copy(group = g) }; groupMenu = false },
                        )
                    }
                }
            }
            BadgedBox(badge = { if (f.activeCount > 0) Badge(containerColor = Palette.Accent) { Text("${f.activeCount}") } }) {
                ToolChip("Filters", Icons.Outlined.FilterAlt, { showFilters = true }, active = f.activeCount > 0)
            }
            if (f.query.isNotBlank() || f.activeCount > 0) {
                ToolChip("Save search", Icons.Outlined.StarBorder, { savingSearch = true })
            }
        }

        when {
            !ui.loaded -> Unit
            ui.totalCards == 0 -> EmptyState(if (boxId < 0) "No cards yet" else "Empty box", Modifier.padding(top = 24.dp), fullScreen = boxId < 0)
            ui.groups.isEmpty() -> EmptyState(
                "No matches", Modifier.padding(top = 24.dp),
                action = "Clear filters", onAction = { vm.clearFilters(); vm.update { it.copy(query = "") } },
            )
            ui.pagesView && pagesOn && !picking -> BinderPages(ui.binderCards, ui.unslotted, ui.binder && ui.pocketPerCopy, onNumber = vm::numberSlots, onOpen = { tap(it) })
            else -> CardGrid(
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
            )
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

/** Top bar while picking cards: how many, and what to do with them. */
@Composable
private fun SelectionBar(count: Int, boxes: List<CardBox>, decks: List<Deck>, vm: BrowseViewModel, onTag: () -> Unit, onEdit: () -> Unit, value: String?, onCopy: () -> Unit) {
    var moveMenu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface).padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::clearSelection) { Icon(Icons.Outlined.Close, "Stop picking") }
        Text(
            if (value != null) "$count selected · $value" else "$count selected",
            style = MaterialTheme.typography.titleMedium.merge(TabularNums), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = vm::selectAll) { Icon(Icons.Outlined.SelectAll, "Select all") }
        Box {
            IconButton(onClick = { moveMenu = true }) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, "Move to box") }
            DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                boxes.forEach { b ->
                    DropdownMenuItem(
                        text = { Text(b.name) },
                        leadingIcon = { Icon(boxIcon(b.kind), null, tint = boxColor(b.color)) },
                        onClick = { moveMenu = false; vm.moveSelected(b.id) },
                    )
                }
                if (decks.isNotEmpty()) {
                    DecksHeader(Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    decks.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d.name) },
                            leadingIcon = { Icon(Icons.Outlined.Style, null, tint = Palette.Icon) },
                            onClick = { moveMenu = false; vm.moveSelectedToDeck(d.id) },
                        )
                    }
                }
            }
        }
        IconButton(onClick = vm::deleteSelected) { Icon(Icons.Outlined.DeleteOutline, "Remove from collection", tint = Palette.Danger) }
        Box {
            IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, "More") }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(
                    text = { Text("Add a tag") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                    onClick = { more = false; onTag() },
                )
                DropdownMenuItem(
                    text = { Text("For sale") }, leadingIcon = { Icon(Icons.Outlined.Sell, null) },
                    onClick = { more = false; vm.sellSelected() },
                )
                DropdownMenuItem(text = { Text(if (count == 1) "Copy name" else "Copy names") }, onClick = { more = false; onCopy() })
                DropdownMenuItem(text = { Text("Condition, language, foil") }, onClick = { more = false; onEdit() })
                DropdownMenuItem(text = { Text("Stay in this box") }, onClick = { more = false; vm.pinSelected(true) })
                DropdownMenuItem(text = { Text("Let re-sort move") }, onClick = { more = false; vm.pinSelected(false) })
            }
        }
    }
}

/** Condition, language and finish for every picked card; anything left unpicked stays as it is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkEditDialog(onApply: (String?, String?, Boolean?, Double?, String?) -> Unit, onDismiss: () -> Unit) {
    var paidText by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    val paid = com.mtgscanner.util.PriceInput.parse(paidText)
    var cond by remember { mutableStateOf<String?>(null) }
    var lang by remember { mutableStateOf<String?>(null) }
    var foil by remember { mutableStateOf<Boolean?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Edit cards") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Condition", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CARD_CONDITIONS.forEach { (c, _) -> SelectPill(c, cond == c, { cond = if (cond == c) null else c }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Language", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CARD_LANGUAGES.forEach { (code, label) -> SelectPill(label, lang == code, { lang = if (lang == code) null else code }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Finish", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectPill("Foil", foil == true, { foil = if (foil == true) null else true })
                    SelectPill("Non-foil", foil == false, { foil = if (foil == false) null else false })
                }
                Spacer(Modifier.height(12.dp))
                // After buying a lot: what each card cost and who it came from.
                OutlinedTextField(
                    paidText, { paidText = it }, singleLine = true,
                    label = { Text("Paid each") }, placeholder = { Text("Leave as is") },
                    isError = paidText.isNotBlank() && paid == null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    from, { from = it }, singleLine = true,
                    label = { Text("From") }, placeholder = { Text("e.g. Local game store") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val fromOrNull = from.trim().ifEmpty { null }
            val ok = (paidText.isBlank() || paid != null) && (cond != null || lang != null || foil != null || paid != null || fromOrNull != null)
            TextButton(onClick = { onApply(cond, lang, foil, paid, fromOrNull) }, enabled = ok) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


@Composable
fun NameDialog(title: String, hint: String, confirm: String, onDone: (String) -> Unit, onDismiss: () -> Unit, initial: String = "") {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text(hint) }) },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Type a new tag, or tap one you've used before. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagDialog(existing: List<String>, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var tag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Add a tag") },
        text = {
            Column {
                OutlinedTextField(tag, { tag = it }, singleLine = true, placeholder = { Text("e.g. For Kenji's deck, Lent out") })
                if (existing.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        existing.forEach { t -> SelectPill(t, false, { onDone(t) }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(tag) }, enabled = tag.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BoxPickerDialog(
    title: String,
    boxes: List<CardBox>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    decks: List<Deck> = emptyList(),
    onPickDeck: (Long) -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = {
            // Long box lists scroll instead of running off the dialog.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (boxes.isEmpty() && decks.isEmpty()) Text("Make another box first.", color = Palette.Muted)
                boxes.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable { onPick(b.id) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(boxIcon(b.kind), null, tint = boxColor(b.color), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(b.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (decks.isNotEmpty()) {
                    DecksHeader(Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp))
                    decks.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable { onPickDeck(d.id) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Style, null, tint = Palette.Icon, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Decks", above the decks in a Move picker. */
@Composable
private fun DecksHeader(modifier: Modifier = Modifier) {
    Text("Decks", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = modifier)
}

/** A few examples of Scryfall search syntax, which the collection search reads too. */
@Composable
private fun SyntaxHelp(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Medium)).background(Palette.Surface).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        listOf(
            "t:elf  c:rg  id<=esper  mv>=4",
            "o:\"draw a card\"  r:mythic  s:mh2",
            "pow>tou  usd>5  is:foil  a:rush",
            "-t:land  (bolt or shock)  !\"Sol Ring\"",
            "box:binder  tag:trade  cond:lp  qty>1",
            "added>=7d  is:lent  is:forsale",
            "f:modern  otag:ramp  (asks Scryfall)",
        ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Text) }
    }
}

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search name, type, text, tag",
    /** Scryfall-syntax search: placeholder and text in Plex Mono, no search icon. */
    mono: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val textStyle = if (mono) monoStyle(14.sp) else MaterialTheme.typography.bodyLarge
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).clip(LookShape).background(Palette.SurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!mono) {
            Icon(Icons.Outlined.Search, null, tint = Palette.Icon, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Palette.Muted, style = textStyle, maxLines = 1)
            // Results update as you type: the keyboard's Search key just puts the keyboard away.
            val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            val focus = androidx.compose.ui.platform.LocalFocusManager.current
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
                textStyle = textStyle.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Outlined.Clear, "Clear", tint = Palette.Icon, modifier = Modifier.size(20.dp).clickable { onChange("") })
        }
        trailing?.invoke()
    }
}

@Composable
private fun CardGrid(
    groups: List<Pair<String, List<CollectionCard>>>,
    grid: Boolean,
    selected: Set<Long>,
    picking: Boolean,
    dense: Boolean,
    /** Change per copy over the last week, by card id; cards without a week of history are missing. */
    week: Map<Long, Double>,
    slotOf: (CollectionCard) -> String?,
    onOpen: (CollectionCard) -> Unit,
    onLongPress: (CollectionCard) -> Unit,
    onSwipeMove: (CollectionCard) -> Unit,
    onSwipeSell: (CollectionCard) -> Unit,
    tabs: Boolean = false,
    byName: Boolean = false,
    modifier: Modifier = Modifier,
    /** Hold a card, then drag over more to select them all. */
    onDragSelect: ((Set<Long>) -> Unit)? = null,
    /** Where the list was scrolled is kept under this key while the app runs, so coming back lands in the same spot. */
    scrollKey: String? = null,
    /** Cards new since the list was last looked at; each gets a small dot. */
    fresh: Set<Long> = emptySet(),
) {
    val saved = remember(scrollKey) { scrollKey?.let { ScrollMemory.get(it) } }
    val state = rememberLazyGridState(saved?.first ?: 0, saved?.second ?: 0)
    if (scrollKey != null) DisposableEffect(scrollKey, state) {
        onDispose { ScrollMemory.put(scrollKey, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
    }
    val scope = rememberCoroutineScope()
    val headers = remember(groups) { ListSections.headerIndices(groups) }
    /** Copies per section, counted once per list change instead of in every header. */
    val counts = remember(groups) { groups.map { g -> g.second.sumOf { it.quantity } } }
    val rail = remember(groups, headers) { ListSections.rail(groups, headers) }
    val jumpBar = remember(groups, rail, byName) { JumpBar.shows(byName, groups.sumOf { it.second.size }, rail) }
    /** The section pinned to the top, or -1 while its own header is in place. */
    val pinned by remember(groups, headers) {
        derivedStateOf {
            val g = ListSections.sectionAt(headers, state.firstVisibleItemIndex)
            if (g < 0 || (headers[g] == state.firstVisibleItemIndex && state.firstVisibleItemScrollOffset == 0)) -1 else g
        }
    }
    var railLetter by remember { mutableStateOf<String?>(null) }
    // Card ids in the order shown, for selecting a run by dragging.
    val order = remember(groups) { groups.flatMap { g -> g.second.map { it.id } } }
    val selectedNow by rememberUpdatedState(selected)
    Column(modifier.fillMaxSize()) {
    if (!grid) ListHeader(end = (if (jumpBar) 28.dp else 10.dp) + 6.dp)
    Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyVerticalGrid(
            state = state,
            columns = if (grid) GridCells.Adaptive(108.dp) else GridCells.Fixed(1),
            contentPadding = PaddingValues(start = 10.dp, end = if (jumpBar) 28.dp else 10.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize().then(
                if (onDragSelect != null) Modifier.dragToSelect(state, order, { selectedNow }, onDragSelect, scope) else Modifier,
            ),
        ) {
            groups.forEachIndexed { gi, (title, cards) ->
                if (title.isNotEmpty()) {
                    item(key = "h-$gi-$title", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        if (tabs) DividerTabHead(title, counts[gi], gi, Modifier.padding(top = 14.dp, bottom = 6.dp))
                        else SectionHead(title, counts[gi], Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp))
                    }
                }
                items(cards, key = { it.id }, contentType = { if (grid) "tile" else if (dense) "dense" else "row" }) { c ->
                    Box {
                    if (grid) {
                        CardTile(
                            c.smallImageUrl ?: c.imageUrl, c.name, cardPriceShort(c), quantity = c.quantity, foil = c.foil,
                            onLongClick = { onLongPress(c) }, selected = c.id in selected, onClick = { onOpen(c) },
                        )
                    } else if (picking) {
                        PriceRow(c, c.id in selected, slotOf(c), week[c.id], dense, onClick = { onOpen(c) }, onLongClick = { onLongPress(c) })
                    } else {
                        SwipeRow(onMove = { onSwipeMove(c) }, onSell = { onSwipeSell(c) }) {
                            PriceRow(c, false, slotOf(c), week[c.id], dense, onClick = { onOpen(c) }, onLongClick = { onLongPress(c) })
                        }
                    }
                    if (c.id in fresh) FreshDot(Modifier.align(if (grid) Alignment.TopStart else Alignment.CenterStart).padding(if (grid) 6.dp else 0.dp))
                    }
                }
            }
        }
        // The current section stays at the top while its cards scroll under it.
        val p = pinned
        if (p >= 0 && p < groups.size) {
            SectionHead(
                groups[p].first, counts[p],
                Modifier.fillMaxWidth().background(Palette.Background).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            )
        }
        if (jumpBar) {
            LetterRail(
                rail, Modifier.align(Alignment.CenterEnd),
                onLetter = { railLetter = it },
                onJump = { index -> scope.launch { state.scrollToItem(index) } },
            )
        }
        railLetter?.let { letter ->
            Box(
                Modifier.align(Alignment.Center).size(72.dp).clip(RoundedCornerShape(Corners.Medium)).background(Palette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) { Text(letter, style = MaterialTheme.typography.headlineMedium, color = Palette.Text) }
        }
    }
    }
}

/** The "new since last time" mark: a small accent dot. */
@Composable
private fun FreshDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Palette.Accent)
            .semantics { contentDescription = "New since last time" },
    )
}

/** A section title with its card count. */
@Composable
private fun SectionHead(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
    }
}

/** A-Z down the right edge: touch or drag to jump to a letter. Letters with no cards are dimmed and jump to the next one. */
@Composable
private fun LetterRail(entries: List<Pair<String, Int>>, modifier: Modifier, onLetter: (String?) -> Unit, onJump: (Int) -> Unit) {
    var heightPx by remember { mutableIntStateOf(1) }
    val present = remember(entries) { entries.map { it.first }.toSet() }
    Column(
        modifier
            .fillMaxHeight()
            .padding(vertical = 12.dp)
            .width(26.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(entries) {
                var last: String? = null
                fun pick(y: Float) {
                    val letter = JumpBar.letterAt(y, heightPx.toFloat())
                    if (letter != last) {
                        last = letter
                        onLetter(letter)
                        JumpBar.firstIndex(entries, letter)?.let(onJump)
                        com.mtgscanner.util.Haptics.tick()
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.y)
                    }
                    last = null
                    onLetter(null)
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JumpBar.LETTERS.forEach { letter ->
            Text(letter, fontSize = 10.sp, lineHeight = 11.sp, color = if (letter in present) Palette.Muted else Palette.Muted.copy(alpha = 0.35f))
        }
    }
}

/** Swipe right to move a card to another box, left to list it for sale (with Undo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(onMove: () -> Unit, onSell: () -> Unit, content: @Composable () -> Unit) {
    // One swipe can confirm more than once; act on the first only.
    val last = remember { longArrayOf(0L) }
    // The swipe state keeps its first callback, so read the row's latest actions (and card) through these.
    val move by rememberUpdatedState(onMove)
    val sell by rememberUpdatedState(onSell)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        val now = android.os.SystemClock.uptimeMillis()
        if (v != SwipeToDismissBoxValue.Settled && now - last[0] > 600) {
            last[0] = now
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> move()
                SwipeToDismissBoxValue.EndToStart -> sell()
                else -> Unit
            }
        }
        // Always spring back: the list updates itself, and a move asks for the box first.
        false
    })
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(Corners.Medium))
                    .background(if (toEnd) Palette.Accent.copy(alpha = 0.25f) else Palette.Gold.copy(alpha = 0.22f))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                if (toEnd) {
                    Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null, tint = Palette.AccentText)
                    Spacer(Modifier.width(8.dp))
                    Text("Move", color = Palette.AccentText)
                } else {
                    Text("Sell", color = Palette.Gold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Sell, null, tint = Palette.Gold)
                }
            }
        },
    ) { Box(Modifier.background(Palette.Background)) { content() } }
}

/** A card's thumbnail; foils get a moving sheen. */
@Composable
private fun Thumb(c: CollectionCard, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    // Holding the picture shows it big; holding the rest of the row still starts picking.
    Box(Modifier.width(width).clip(CardShape).foilShimmer(c.foil)) {
        CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.fillMaxWidth(), zoom = true, onTap = onClick)
    }
}

private val SET_COL = 48.dp
private val USD_COL = 64.dp

/** The price column, wider with a larger system font so prices don't clip. */
@Composable
private fun usdCol(): Dp = USD_COL * LocalDensity.current.fontScale.coerceAtLeast(1f)
private val WEEK_COL = 52.dp
private val COL_GAP = 8.dp

// Row text styles, made once rather than for every row.
private val Mono11 = monoStyle(11.sp)
private val Mono12 = monoStyle(12.sp)
private val Mono14 = monoStyle(14.sp)

/** Each card's change per copy since a week ago, for the cards that have that much history. */
private suspend fun weekChanges(repo: CollectionRepository, cards: List<CollectionCard>): Map<Long, Double> {
    if (cards.isEmpty()) return emptyMap()
    val weekAgo = java.time.LocalDate.now().minusDays(WeekChange.DAYS)
    val oldest = weekAgo.minusDays(WeekChange.SLACK_DAYS)
    val history = PriceTrends.history(repo, cards.mapTo(HashSet()) { it.scryfallId })
    val out = HashMap<Long, Double>()
    for (c in cards) {
        val change = WeekChange.change(c.nearMintPrice(), historyPairs(history[c.scryfallId].orEmpty(), c.foil), weekAgo.toString(), oldest.toString())
        if (change != null) out[c.id] = change
    }
    return out
}

/** Price history is in dollars, so the 7d column only shows with dollar prices. */
private fun weekColumn() = AppSettings.currency != PriceCurrency.EUR

/** One copy's price as a plain number; the header says which currency. */
private fun rowPrice(c: CollectionCard): String =
    WeekChange.amount(if (AppSettings.currency == PriceCurrency.EUR) c.unitEur() else c.unitPrice())

/** Set / Card / USD / 7d over the list, with a rule under it. [end] lines the numbers up with the rows'. */
@Composable
private fun ListHeader(end: Dp) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = end, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(COL_GAP),
        ) {
            val style = MaterialTheme.typography.labelSmall
            Text("Set", style = style, color = Palette.Muted, maxLines = 1, modifier = Modifier.width(SET_COL))
            Text("Card", style = style, color = Palette.Muted, maxLines = 1, modifier = Modifier.weight(1f))
            if (AppSettings.showPrices) {
                Text(
                    if (AppSettings.currency == PriceCurrency.EUR) "EUR" else "USD",
                    style = style, color = Palette.Muted, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(usdCol()),
                )
                if (weekColumn()) Text("7d", style = style, color = Palette.Muted, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(WEEK_COL))
            }
        }
        RuleLine()
    }
}

/**
 * A card in the list's columns: set symbol and code, then the name with a color-identity dot (and the
 * thumbnail unless [dense]), its price and its change over the week. A hairline runs under each row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PriceRow(
    c: CollectionCard, selected: Boolean, slot: String?, week: Double?, dense: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Palette.SurfaceHigh else Palette.Background)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(RuleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = if (dense) 44.dp else 52.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(COL_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(SET_COL), verticalAlignment = Alignment.CenterVertically) {
            SetMark(c.setCode, c.rarity)
            Spacer(Modifier.width(4.dp))
            Text(c.setCode.uppercase(), style = Mono11, color = Palette.Muted, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
        if (!dense) Thumb(c, 30.dp, onClick)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(c.colorIdentity)
                Spacer(Modifier.width(8.dp))
                Text(
                    c.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (c.quantity > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text("×${c.quantity}", style = Mono12, color = Palette.Muted, maxLines = 1)
                }
                if (c.foil) { Spacer(Modifier.width(6.dp)); FoilTag() }
            }
            if (!dense) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (c.pinned) Icon(Icons.Filled.PushPin, "Stays in this box", tint = Palette.Icon, modifier = Modifier.size(12.dp).padding(end = 2.dp))
                    Text(
                        listOfNotNull("#${c.collectorNumber}", slot).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (slot != null) {
                Text(
                    slot, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        if (AppSettings.showPrices) {
            val price = rowPrice(c)
            Text(
                price, style = if (price.length > 7) Mono12 else Mono14, color = Palette.Text,
                textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(usdCol()),
            )
            val change = week?.let { WeekChange.label(it) }.orEmpty()
            if (weekColumn()) Text(
                change, style = if (change.length > 6) Mono11 else Mono12, color = Palette.Muted,
                textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(WEEK_COL),
            )
        }
    }
}

/** A binder as its pages: 9 pockets each, in slot order. Filters don't apply, so it matches the real binder. */
@Composable
private fun BinderPages(cards: List<CollectionCard>, unslotted: Int, perCopy: Boolean, onNumber: () -> Unit, onOpen: (CollectionCard) -> Unit) {
    // With a pocket per copy, a card fills one pocket for each copy.
    val bySlot = remember(cards, perCopy) { Binder.pockets(cards, { it.binderSlot }, { Binder.span(it.quantity, perCopy) }) }
    val pages = bySlot.keys.maxOrNull()?.let { Binder.page(it) } ?: 0
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        if (unslotted > 0 && pages > 0) item(key = "number") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$unslotted to number", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                TextButton(onClick = onNumber) { Text("Number slots") }
            }
        }
        if (pages == 0) item(key = "empty") {
            EmptyState("No pages yet", action = "Number slots", onAction = onNumber)
        }
        items(pages, key = { "p$it" }) { i ->
            val page = i + 1
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Page $page", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(bottom = 6.dp))
                for (row in 0 until 3) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0 until 3) {
                            val slot = (page - 1) * Binder.POCKETS + row * 3 + col + 1
                            val c = bySlot[slot]
                            Box(Modifier.weight(1f)) {
                                if (c != null) {
                                    Box(Modifier.clip(CardShape).clickable { onOpen(c) }) {
                                        CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.fillMaxWidth(), zoom = true)
                                    }
                                } else {
                                    Box(
                                        Modifier.fillMaxWidth().aspectRatio(63f / 88f).clip(CardShape)
                                            .border(1.dp, Palette.Outline, CardShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("${Binder.pocket(slot)}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(f: Filters, ui: BrowseUi, vm: BrowseViewModel, saved: List<SavedSearch>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (f.activeCount > 0) TextButton(onClick = vm::clearFilters) { Text("Clear all") }
            }
            FilterGroup("Color") {
                // Pair keys from Home and an old "Multicolor" pick show only while picked, so they can be cleared.
                val legacy = f.colors.filter { it == CardColors.LEGACY_MULTI || com.mtgscanner.ui.home.ColorPairs.isPairKey(it) }
                (COLOR_FILTERS + legacy).forEach { col ->
                    val label = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colorless", "M" to "Multicolor")[col]
                        ?: com.mtgscanner.ui.home.ColorPairs.name(col)
                    val pip: (@Composable () -> Unit)? = if (col in listOf("W", "U", "B", "R", "G", "C", "M")) {
                        { ColorPip(col[0]) }
                    } else null
                    SelectPill(label, col in f.colors, onClick = { vm.update { it.copy(colors = it.colors.toggle(col)) } }, leading = pip)
                }
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorMatch.entries.forEach { m ->
                    SelectPill(m.label, f.colorMatch == m, onClick = { vm.update { it.copy(colorMatch = m) } })
                }
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectPill("Colors", !f.byIdentity, onClick = { vm.update { it.copy(byIdentity = false) } })
                SelectPill("Identity", f.byIdentity, onClick = { vm.update { it.copy(byIdentity = true) } })
            }
            FilterGroup("Rarity") {
                RARITIES.forEach { r ->
                    SelectPill(r.replaceFirstChar(Char::uppercase), r in f.rarities, onClick = { vm.update { it.copy(rarities = it.rarities.toggle(r)) } },
                        leading = { RarityMark(r) })
                }
                SelectPill("Foil only", f.foilOnly, onClick = { vm.update { it.copy(foilOnly = !it.foilOnly) } })
            }
            FilterGroup("Type") {
                CARD_TYPES.forEach { t ->
                    SelectPill(t, t in f.types, onClick = { vm.update { it.copy(types = it.types.toggle(t)) } })
                }
            }
            if (ui.userTags.isNotEmpty()) {
                FilterGroup("Your tags") {
                    ui.userTags.forEach { t ->
                        SelectPill(t, t in f.userTags, onClick = { vm.update { it.copy(userTags = it.userTags.toggle(t)) } })
                    }
                }
            }
            if (ui.treatments.isNotEmpty()) {
                FilterGroup("Printing") {
                    ui.treatments.forEach { t ->
                        SelectPill(BoxRules.TREATMENT_NAMES[t] ?: t, t in f.treatments, onClick = { vm.update { it.copy(treatments = it.treatments.toggle(t)) } })
                    }
                }
            }
            if (ui.sets.isNotEmpty()) {
                FilterGroup("Set") {
                    SelectPill("Any", f.set == null, onClick = { vm.update { it.copy(set = null) } })
                    ui.sets.forEach { (code, name) ->
                        SelectPill(name, f.set == code, onClick = { vm.update { it.copy(set = if (it.set == code) null else code) } })
                    }
                }
            }
            if (ui.decks.isNotEmpty() || f.deckBox != null) {
                FilterGroup("Deck") {
                    SelectPill("Any", f.deckBox == null, onClick = { vm.update { it.copy(deckBox = null) } })
                    ui.decks.forEach { (id, name) ->
                        SelectPill(name, f.deckBox == id, onClick = { vm.update { it.copy(deckBox = if (it.deckBox == id) null else id) } })
                    }
                }
            }
            if (saved.isNotEmpty()) {
                Text("Saved searches", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                saved.forEach { sv ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sv.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).clickable { vm.applySaved(sv); onDismiss() }.padding(vertical = 8.dp))
                        IconButton(onClick = { vm.deleteSaved(sv.name) }) { Icon(Icons.Outlined.DeleteOutline, "Delete saved search", tint = Palette.Icon) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

/** The card id under [pos] in the grid, or null over a header or a gap. */
private fun keyAt(state: LazyGridState, pos: Offset): Long? =
    state.layoutInfo.visibleItemsInfo.firstOrNull { i ->
        pos.x >= i.offset.x && pos.x < i.offset.x + i.size.width && pos.y >= i.offset.y && pos.y < i.offset.y + i.size.height
    }?.key as? Long

/**
 * Hold a card (its own long press selects it), then drag without lifting: every card between it and the finger
 * is selected, like a phone's photo gallery. Near the top or bottom edge the list scrolls along.
 */
private fun Modifier.dragToSelect(
    state: LazyGridState,
    order: List<Long>,
    selected: () -> Set<Long>,
    onSelect: (Set<Long>) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
): Modifier = pointerInput(order) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        // Lifted or moved before the long press: an ordinary tap or scroll.
        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis + 60) {
            var ended = false
            while (!ended) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val ch = e.changes.firstOrNull { it.id == down.id }
                ended = ch == null || !ch.pressed || (ch.position - down.position).getDistance() > slop
            }
            true
        }
        if (early != null) return@awaitEachGesture
        val startKey = keyAt(state, down.position) ?: return@awaitEachGesture
        val start = order.indexOf(startKey)
        if (start < 0) return@awaitEachGesture
        var base: Set<Long>? = null
        var lastAt = start
        val edge = 56.dp.toPx()
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            ch.consume()
            val key = keyAt(state, ch.position)
            val at = key?.let { order.indexOf(it) } ?: -1
            if (at >= 0 && at != lastAt && (key != startKey || base != null)) {
                // The selection as it was when the drag began (the held card already in it).
                val b = base ?: selected().also { base = it }
                onSelect(b + order.subList(minOf(start, at), maxOf(start, at) + 1))
                com.mtgscanner.util.Haptics.step()
                lastAt = at
            }
            when {
                ch.position.y < edge -> scope.launch { state.scrollBy(-28f) }
                ch.position.y > size.height - edge -> scope.launch { state.scrollBy(28f) }
            }
        }
    }
}

/** Scroll positions of lists left and come back to, while the app runs. */
internal object ScrollMemory {
    private val spots = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int>>()
    fun get(key: String): Pair<Int, Int>? = spots[key]
    fun put(key: String, index: Int, offset: Int) { spots[key] = index to offset }
}
