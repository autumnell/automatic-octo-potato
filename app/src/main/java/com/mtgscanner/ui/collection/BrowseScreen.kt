package com.mtgscanner.ui.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
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
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.RarityMark
import com.mtgscanner.ui.components.PriceSparkline
import com.mtgscanner.ui.components.foilShimmer
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
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
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalConfiguration
import com.mtgscanner.data.Binder
import com.mtgscanner.ui.components.CardPreviewSheet
import com.mtgscanner.ui.detail.CardDetailScreen

@Composable
fun BrowseScreen(boxId: Long, onBack: () -> Unit, onOpenCard: (Long) -> Unit, onTrade: (binderId: Long) -> Unit = {}) {
    val vm: BrowseViewModel = viewModel(key = "browse-$boxId", factory = AppViewModels.browse(boxId))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val f by vm.filters.collectAsStateWithLifecycle()
    val grid by vm.grid.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val selecting by vm.selecting.collectAsStateWithLifecycle()
    val showTip by vm.showTip.collectAsStateWithLifecycle()
    val dense by vm.dense.collectAsStateWithLifecycle()
    val pagesOn by vm.pages.collectAsStateWithLifecycle()
    val stackOn by vm.stack.collectAsStateWithLifecycle()
    val sparks by vm.sparks.collectAsStateWithLifecycle()
    /** Wide screens show the card beside the list. */
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    /** The card in the side pane on wide screens. */
    var paneId by remember { mutableStateOf<Long?>(null) }
    /** The card in the quick preview sheet. */
    var preview by remember { mutableStateOf<CollectionCard?>(null) }
    var menu by remember { mutableStateOf(false) }
    val kinds = remember(ui.boxes) { ui.boxes.associate { it.id to it.kind } }
    val perCopy = remember(ui.boxes) { ui.boxes.filter { it.pocketPerCopy }.map { it.id }.toSet() }
    fun tap(c: CollectionCard) {
        if (wide) paneId = c.id else preview = c
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
    androidx.activity.compose.BackHandler(enabled = picking) { vm.clearSelection() }

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
    Column(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) {
        if (picking) SelectionBar(selected.size, ui.boxes, vm, onTag = { tagging = true }, onEdit = { editing = true })
        else Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(ui.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    if (f.activeCount > 0 || f.query.isNotBlank()) "${ui.shownCards} of ${ui.totalCards} cards${moneySuffix(ui.shownValue, ui.shownEur)}"
                    else "${ui.totalCards} cards${moneySuffix(ui.shownValue, ui.shownEur)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
            }
            if (ui.boxes.any { it.id == boxId && it.kind == "trade" }) TextButton(onClick = { onTrade(boxId) }) { Text("Trade") }
            if (ui.totalCards > 0) TextButton(onClick = vm::startSelecting) { Text("Select") }
            if (!stackOn || boxId < 0) IconButton(onClick = vm::cycleView) {
                // Shows the view you get next.
                when {
                    grid -> Icon(Icons.Filled.ViewAgenda, "List view")
                    !dense -> Icon(Icons.Filled.ViewHeadline, "Compact list")
                    else -> Icon(Icons.Filled.GridView, "Grid view")
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (boxId >= 0) DropdownMenuItem(
                        text = { Text("Card stack") },
                        trailingIcon = { if (stackOn) Icon(Icons.Filled.Check, null, tint = Palette.Accent) },
                        onClick = { menu = false; vm.toggleStack() },
                    )
                    if (ui.binder) {
                        DropdownMenuItem(
                            text = { Text("Pages") },
                            trailingIcon = { if (pagesOn) Icon(Icons.Filled.Check, null, tint = Palette.Accent) },
                            onClick = { menu = false; vm.togglePages() },
                        )
                        DropdownMenuItem(
                            text = { Text("Pocket per copy") },
                            trailingIcon = { if (ui.pocketPerCopy) Icon(Icons.Filled.Check, null, tint = Palette.Accent) },
                            onClick = { menu = false; vm.setPocketPerCopy(!ui.pocketPerCopy) },
                        )
                        if (ui.unslotted > 0) DropdownMenuItem(
                            text = { Text("Number slots") },
                            onClick = { menu = false; vm.numberSlots() },
                        )
                    }
                }
            }
        }
        MoveListHost()

        SearchField(f.query, { q -> vm.update { it.copy(query = q) } }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        if (showTip && ui.totalCards > 0 && !picking) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Palette.Surface).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Hold to zoom or select · swipe right to move, left to sell",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = vm::dismissTip) { Icon(Icons.Filled.Close, "Hide tip", tint = Palette.Muted, modifier = Modifier.size(18.dp)) }
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
                        Icon(Icons.Filled.History, null, tint = Palette.Muted, modifier = Modifier.size(14.dp))
                    })
                }
                if (recent.isNotEmpty()) TextButton(onClick = vm::clearRecent) { Text("Clear recent", color = Palette.Muted) }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var sortMenu by remember { mutableStateOf(false) }
            var groupMenu by remember { mutableStateOf(false) }
            Box {
                ToolChip(f.sort.label, Icons.AutoMirrored.Filled.Sort, { sortMenu = true })
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
                ToolChip(if (f.group == GroupBy.NONE) "Group" else f.group.label, Icons.AutoMirrored.Filled.FormatListBulleted, { groupMenu = true }, active = f.group != GroupBy.NONE)
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
                ToolChip("Filters", Icons.Filled.FilterAlt, { showFilters = true }, active = f.activeCount > 0)
            }
            if (f.query.isNotBlank() || f.activeCount > 0) {
                ToolChip("Save search", Icons.Filled.StarBorder, { savingSearch = true })
            }
        }

        when {
            !ui.loaded -> Unit
            ui.totalCards == 0 -> EmptyState(if (boxId < 0) "No cards yet" else "Empty box", Modifier.padding(top = 24.dp))
            ui.groups.isEmpty() -> EmptyState(
                "No matches", Modifier.padding(top = 24.dp),
                action = "Clear filters", onAction = { vm.clearFilters(); vm.update { it.copy(query = "") } },
            )
            ui.binder && pagesOn && !picking -> BinderPages(ui.binderCards, ui.unslotted, ui.pocketPerCopy, onNumber = vm::numberSlots, onOpen = { tap(it) })
            boxId >= 0 && stackOn && !picking -> {
                val flat = remember(ui.groups) { ui.groups.flatMap { it.second } }
                CardStack(flat, sparks, onOpen = { tap(it) }, modifier = Modifier.padding(bottom = 16.dp))
            }
            else -> CardGrid(
                ui.groups, grid, selected, picking, dense, sparks,
                slotOf = { c -> slotLabel(c, kinds[c.boxId], c.boxId in perCopy) },
                onOpen = { c -> if (picking) vm.toggleSelect(c.id) else tap(c) },
                onLongPress = { c -> vm.toggleSelect(c.id); if (showTip) vm.dismissTip() },
                onSwipeMove = { c -> moving = c },
                onSwipeSell = { c -> vm.sellOne(c) },
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

    preview?.let { c ->
        val where = remember(c, ui.all) { vm.whereIs(c) }
        CardPreviewSheet(
            card = c,
            box = ui.boxes.firstOrNull { it.id == c.boxId },
            where = where,
            onOpen = { preview = null; onOpenCard(c.id) },
            onDismiss = { preview = null },
        )
    }
    if (showFilters) FilterSheet(f, ui, vm, saved, onDismiss = { showFilters = false })
    if (savingSearch) NameDialog("Save this search", "e.g. Red rares under $5", "Save", onDone = { vm.saveSearch(it); savingSearch = false }, onDismiss = { savingSearch = false })
    if (tagging) TagDialog(ui.userTags, onDone = { vm.tagSelected(it); tagging = false }, onDismiss = { tagging = false })
    if (editing) BulkEditDialog(
        onApply = { cond, lang, foil -> vm.editSelected(cond, lang, foil); editing = false },
        onDismiss = { editing = false },
    )
    moving?.let { c ->
        BoxPickerDialog("Move ${c.name} to", ui.boxes.filter { it.id != c.boxId }, onPick = { vm.moveOne(c.id, it); moving = null }, onDismiss = { moving = null })
    }
}

/** Top bar while picking cards: how many, and what to do with them. */
@Composable
private fun SelectionBar(count: Int, boxes: List<CardBox>, vm: BrowseViewModel, onTag: () -> Unit, onEdit: () -> Unit) {
    var moveMenu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface).padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Stop picking") }
        Text("$count selected", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = vm::selectAll) { Icon(Icons.Filled.SelectAll, "Select all") }
        Box {
            IconButton(onClick = { moveMenu = true }) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, "Move to box") }
            DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                boxes.forEach { b ->
                    DropdownMenuItem(
                        text = { Text(b.name) },
                        leadingIcon = { Icon(boxIcon(b.kind), null, tint = boxColor(b.color)) },
                        onClick = { moveMenu = false; vm.moveSelected(b.id) },
                    )
                }
            }
        }
        IconButton(onClick = vm::deleteSelected) { Icon(Icons.Filled.DeleteOutline, "Remove from collection", tint = Palette.Danger) }
        Box {
            IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(
                    text = { Text("Add a tag") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                    onClick = { more = false; onTag() },
                )
                DropdownMenuItem(
                    text = { Text("For sale") }, leadingIcon = { Icon(Icons.Filled.Sell, null) },
                    onClick = { more = false; vm.sellSelected() },
                )
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
private fun BulkEditDialog(onApply: (String?, String?, Boolean?) -> Unit, onDismiss: () -> Unit) {
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
                    listOf("NM", "LP", "MP", "HP", "DMG").forEach { c -> SelectPill(c, cond == c, { cond = if (cond == c) null else c }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Language", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BULK_LANGS.forEach { (code, label) -> SelectPill(label, lang == code, { lang = if (lang == code) null else code }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Finish", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectPill("Foil", foil == true, { foil = if (foil == true) null else true })
                    SelectPill("Non-foil", foil == false, { foil = if (foil == false) null else false })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(cond, lang, foil) }, enabled = cond != null || lang != null || foil != null) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val BULK_LANGS = listOf(
    "en" to "English", "ja" to "Japanese", "de" to "German", "fr" to "French", "it" to "Italian", "es" to "Spanish",
    "pt" to "Portuguese", "ru" to "Russian", "ko" to "Korean", "zhs" to "Chinese (S)", "zht" to "Chinese (T)",
)

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
fun BoxPickerDialog(title: String, boxes: List<CardBox>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = {
            // Long box lists scroll instead of running off the dialog.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (boxes.isEmpty()) Text("Make another box first.", color = Palette.Muted)
                boxes.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(b.id) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(boxIcon(b.kind), null, tint = boxColor(b.color), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(b.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search name, type, text, tag") {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.SurfaceHigh).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Palette.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Filled.Clear, "Clear", tint = Palette.Muted, modifier = Modifier.size(20.dp).clickable { onChange("") })
        }
    }
}

@Composable
private fun CardGrid(
    groups: List<Pair<String, List<CollectionCard>>>,
    grid: Boolean,
    selected: Set<Long>,
    picking: Boolean,
    dense: Boolean,
    sparks: Map<Long, List<Double>>,
    slotOf: (CollectionCard) -> String?,
    onOpen: (CollectionCard) -> Unit,
    onLongPress: (CollectionCard) -> Unit,
    onSwipeMove: (CollectionCard) -> Unit,
    onSwipeSell: (CollectionCard) -> Unit,
) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val headers = remember(groups) { ListSections.headerIndices(groups) }
    val rail = remember(groups, headers) { ListSections.rail(groups, headers) }
    /** The section pinned to the top, or -1 while its own header is in place. */
    val pinned by remember(groups, headers) {
        derivedStateOf {
            val g = ListSections.sectionAt(headers, state.firstVisibleItemIndex)
            if (g < 0 || (headers[g] == state.firstVisibleItemIndex && state.firstVisibleItemScrollOffset == 0)) -1 else g
        }
    }
    var railLetter by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = state,
            columns = if (grid) GridCells.Adaptive(108.dp) else GridCells.Fixed(1),
            contentPadding = PaddingValues(start = 10.dp, end = if (rail.isNotEmpty()) 28.dp else 10.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            groups.forEachIndexed { gi, (title, cards) ->
                if (title.isNotEmpty()) {
                    item(key = "h-$gi-$title", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        SectionHead(title, cards.sumOf { it.quantity }, Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp))
                    }
                }
                items(cards, key = { it.id }, contentType = { if (grid) "tile" else if (dense) "dense" else "row" }) { c ->
                    if (grid) {
                        CardTile(
                            c.smallImageUrl ?: c.imageUrl, c.name, cardPriceShort(c), quantity = c.quantity, foil = c.foil,
                            onLongClick = { onLongPress(c) }, selected = c.id in selected, onClick = { onOpen(c) },
                        )
                    } else if (picking) {
                        if (dense) DenseRow(c, c.id in selected, slotOf(c), sparks[c.id], onClick = { onOpen(c) }, onLongClick = { onLongPress(c) })
                        else CardRow(c, c.id in selected, slotOf(c), sparks[c.id], onClick = { onOpen(c) }, onLongClick = { onLongPress(c) })
                    } else {
                        SwipeRow(onMove = { onSwipeMove(c) }, onSell = { onSwipeSell(c) }) {
                            if (dense) DenseRow(c, false, slotOf(c), sparks[c.id], onClick = { onOpen(c) }, onLongClick = { onLongPress(c) })
                            else CardRow(c, false, slotOf(c), sparks[c.id], onClick = { onOpen(c) }, onLongClick = { onLongPress(c) })
                        }
                    }
                }
            }
        }
        // The current section stays at the top while its cards scroll under it.
        val p = pinned
        if (p >= 0 && p < groups.size) {
            val (title, cards) = groups[p]
            SectionHead(
                title, cards.sumOf { it.quantity },
                Modifier.fillMaxWidth().background(Palette.Background).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            )
        }
        if (rail.isNotEmpty()) {
            LetterRail(
                rail, Modifier.align(Alignment.CenterEnd),
                onLetter = { railLetter = it },
                onJump = { index -> scope.launch { state.scrollToItem(index) } },
            )
        }
        railLetter?.let { letter ->
            Box(
                Modifier.align(Alignment.Center).size(72.dp).clip(RoundedCornerShape(18.dp)).background(Palette.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) { Text(letter, style = MaterialTheme.typography.headlineMedium, color = Palette.AccentText) }
        }
    }
}

/** A section title with its card count. */
@Composable
private fun SectionHead(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
    }
}

/** A-Z down the right edge: touch or drag to jump to a letter. */
@Composable
private fun LetterRail(entries: List<Pair<String, Int>>, modifier: Modifier, onLetter: (String?) -> Unit, onJump: (Int) -> Unit) {
    var heightPx by remember { mutableIntStateOf(1) }
    Column(
        modifier
            .fillMaxHeight()
            .padding(vertical = 12.dp)
            .width(26.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(entries) {
                var last = -1
                fun pick(y: Float) {
                    val i = ((y / heightPx) * entries.size).toInt().coerceIn(0, entries.lastIndex)
                    if (i != last) {
                        last = i
                        onLetter(entries[i].first)
                        onJump(entries[i].second)
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
                    last = -1
                    onLetter(null)
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEach { (letter, _) ->
            Text(letter, fontSize = 10.sp, lineHeight = 11.sp, color = Palette.Muted)
        }
    }
}

/** Swipe right to move a card to another box, left to list it for sale (with Undo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(onMove: () -> Unit, onSell: () -> Unit, content: @Composable () -> Unit) {
    // One swipe can confirm more than once; act on the first only.
    val last = remember { longArrayOf(0L) }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        val now = android.os.SystemClock.uptimeMillis()
        if (v != SwipeToDismissBoxValue.Settled && now - last[0] > 600) {
            last[0] = now
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> onMove()
                SwipeToDismissBoxValue.EndToStart -> onSell()
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
                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    .background(if (toEnd) Palette.Accent.copy(alpha = 0.25f) else Palette.Gold.copy(alpha = 0.22f))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                if (toEnd) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, tint = Palette.AccentText)
                    Spacer(Modifier.width(8.dp))
                    Text("Move", color = Palette.AccentText)
                } else {
                    Text("Sell", color = Palette.Gold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Sell, null, tint = Palette.Gold)
                }
            }
        },
    ) { Box(Modifier.background(Palette.Background)) { content() } }
}

/** A card's thumbnail; foils get a moving sheen. */
@Composable
private fun Thumb(c: CollectionCard, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    // Holding the picture shows it big; holding the rest of the row still starts picking.
    Box(Modifier.width(width).clip(RoundedCornerShape(4.dp)).foilShimmer(c.foil)) {
        CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.fillMaxWidth(), corner = 4.dp, zoom = true, onTap = onClick)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardRow(c: CollectionCard, selected: Boolean, slot: String?, spark: List<Double>?, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Palette.Accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(c, 46.dp, onClick)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                ManaCost(c.manaCost, size = 14.dp)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RarityMark(c.rarity)
                Spacer(Modifier.width(6.dp))
                Text("${c.setCode.uppercase()} #${c.collectorNumber} · ${c.typeLine}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (slot != null || c.pinned) Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.pinned) Icon(Icons.Filled.PushPin, "Stays in this box", tint = Palette.Muted, modifier = Modifier.size(12.dp).padding(end = 2.dp))
                if (slot != null) Text(slot, style = MaterialTheme.typography.labelSmall, color = Palette.AccentText)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.foil) { FoilTag(); Spacer(Modifier.width(4.dp)) }
                Text("×${c.quantity}", style = MaterialTheme.typography.titleSmall)
            }
            if (AppSettings.showPrices) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (spark != null) { PriceSparkline(spark); Spacer(Modifier.width(6.dp)) }
                    Text(cardPrice(c), style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
                }
            }
        }
    }
}

/** One line per card: rarity, name, set, copies, price. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DenseRow(c: CollectionCard, selected: Boolean, slot: String?, spark: List<Double>?, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) Palette.Accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RarityMark(c.rarity)
        Spacer(Modifier.width(8.dp))
        Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (c.foil) { Spacer(Modifier.width(4.dp)); FoilTag() }
        Spacer(Modifier.width(8.dp))
        Text(
            slot?.replace("Page ", "P")?.replace(" · Slots ", "·")?.replace(" · Slot ", "·") ?: c.setCode.uppercase(),
            style = MaterialTheme.typography.labelSmall, color = Palette.Muted,
        )
        Text(
            "×${c.quantity}", style = MaterialTheme.typography.labelMedium,
            color = if (c.quantity > 1) Palette.Text else Palette.Muted,
            textAlign = TextAlign.End, modifier = Modifier.width(30.dp),
        )
        if (AppSettings.showPrices) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(36.dp)) { if (spark != null) PriceSparkline(spark) }
            Text(
                cardPriceShort(c), style = MaterialTheme.typography.labelMedium, color = Palette.AccentText,
                textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(64.dp),
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
                Text("Page $page", style = MaterialTheme.typography.titleSmall, color = Palette.Accent, modifier = Modifier.padding(bottom = 6.dp))
                for (row in 0 until 3) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0 until 3) {
                            val slot = (page - 1) * Binder.POCKETS + row * 3 + col + 1
                            val c = bySlot[slot]
                            Box(Modifier.weight(1f)) {
                                if (c != null) {
                                    Box(Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpen(c) }) {
                                        CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.fillMaxWidth(), corner = 6.dp, zoom = true)
                                    }
                                } else {
                                    Box(
                                        Modifier.fillMaxWidth().aspectRatio(63f / 88f).clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, Palette.Outline, RoundedCornerShape(6.dp)),
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
                (COLOR_FILTERS + f.colors.filter { com.mtgscanner.ui.home.ColorPairs.isPairKey(it) }).forEach { col ->
                    val label = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colorless", "M" to "Multicolor")[col]
                        ?: com.mtgscanner.ui.home.ColorPairs.name(col)
                    val pip: (@Composable () -> Unit)? = if (col in listOf("W", "U", "B", "R", "G", "C", "M")) {
                        { ColorPip(col[0]) }
                    } else null
                    SelectPill(label, col in f.colors, onClick = { vm.update { it.copy(colors = it.colors.toggle(col)) } }, leading = pip)
                }
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
            FilterGroup("Role") {
                if (ui.roles.isEmpty()) {
                    Text(
                        "Roles appear once cards are looked up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
                ui.roles.forEach { r ->
                    SelectPill(r, r in f.roles, onClick = { vm.update { it.copy(roles = it.roles.toggle(r)) } })
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
            if (saved.isNotEmpty()) {
                Text("Saved searches", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                saved.forEach { sv ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sv.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).clickable { vm.applySaved(sv); onDismiss() }.padding(vertical = 8.dp))
                        IconButton(onClick = { vm.deleteSaved(sv.name) }) { Icon(Icons.Filled.DeleteOutline, "Delete saved search", tint = Palette.Muted) }
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
