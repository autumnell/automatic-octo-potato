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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Style
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
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.ToolChip
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette

@Composable
fun BrowseScreen(boxId: Long, onBack: () -> Unit, onOpenCard: (Long) -> Unit) {
    val vm: BrowseViewModel = viewModel(key = "browse-$boxId", factory = AppViewModels.browse(boxId))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val f by vm.filters.collectAsStateWithLifecycle()
    val grid by vm.grid.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var savingSearch by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }
    /** A card swiped right, waiting for a box to move to. */
    var moving by remember { mutableStateOf<CollectionCard?>(null) }
    val picking = selected.isNotEmpty()
    androidx.activity.compose.BackHandler(enabled = picking) { vm.clearSelection() }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        if (picking) SelectionBar(selected.size, ui.boxes, vm, onTag = { tagging = true })
        else Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(ui.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    if (f.activeCount > 0 || f.query.isNotBlank()) "${ui.shownCards} of ${ui.totalCards} cards${priceSuffix(ui.shownValue)}"
                    else "${ui.totalCards} cards${priceSuffix(ui.shownValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
            }
            IconButton(onClick = vm::toggleGrid) {
                Icon(if (grid) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView, if (grid) "List view" else "Grid view")
            }
        }

        SearchField(f.query, { q -> vm.update { it.copy(query = q) } }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
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
            ui.totalCards == 0 -> EmptyState(Icons.Filled.Style, "Nothing here yet", "Cards you scan or add into this box will show up here.")
            ui.groups.isEmpty() -> EmptyState(
                Icons.Filled.SearchOff, "No cards match", "Try a different search or clear the filters.",
                action = { TextButton(onClick = { vm.clearFilters(); vm.update { it.copy(query = "") } }) { Text("Clear search and filters") } },
            )
            else -> CardGrid(
                ui.groups, grid, selected,
                onOpen = { c -> if (picking) vm.toggleSelect(c.id) else onOpenCard(c.id) },
                onLongPress = { c -> vm.toggleSelect(c.id) },
                onSwipeMove = { c -> moving = c },
                onSwipeDelete = { c -> vm.deleteOne(c.id, c.name) },
            )
        }
    }

    if (showFilters) FilterSheet(f, ui, vm, saved, onDismiss = { showFilters = false })
    if (savingSearch) NameDialog("Save this search", "e.g. Red rares under $5", "Save", onDone = { vm.saveSearch(it); savingSearch = false }, onDismiss = { savingSearch = false })
    if (tagging) TagDialog(ui.userTags, onDone = { vm.tagSelected(it); tagging = false }, onDismiss = { tagging = false })
    moving?.let { c ->
        BoxPickerDialog("Move ${c.name} to", ui.boxes.filter { it.id != c.boxId }, onPick = { vm.moveOne(c.id, it); moving = null }, onDismiss = { moving = null })
    }
}

/** Top bar while picking cards: how many, and what to do with them. */
@Composable
private fun SelectionBar(count: Int, boxes: List<CardBox>, vm: BrowseViewModel, onTag: () -> Unit) {
    var moveMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface).padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Stop picking") }
        Text("$count selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
        IconButton(onClick = onTag) { Icon(Icons.AutoMirrored.Filled.Label, "Add a tag") }
        IconButton(onClick = vm::deleteSelected) { Icon(Icons.Filled.DeleteOutline, "Remove from collection", tint = Palette.Danger) }
    }
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
fun BoxPickerDialog(title: String, boxes: List<CardBox>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = {
            Column {
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
    onOpen: (CollectionCard) -> Unit,
    onLongPress: (CollectionCard) -> Unit,
    onSwipeMove: (CollectionCard) -> Unit,
    onSwipeDelete: (CollectionCard) -> Unit,
) {
    LazyVerticalGrid(
        columns = if (grid) GridCells.Adaptive(108.dp) else GridCells.Fixed(1),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        groups.forEach { (title, cards) ->
            if (title.isNotEmpty()) {
                item(key = "h-$title", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Accent)
                        Spacer(Modifier.width(8.dp))
                        Text("${cards.sumOf { it.quantity }}", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                    }
                }
            }
            items(cards, key = { it.id }) { c ->
                if (grid) {
                    CardTile(
                        c.imageUrl, c.name, formatUsd(c.unitPrice()), quantity = c.quantity, foil = c.foil,
                        onLongClick = { onLongPress(c) }, selected = c.id in selected, onClick = { onOpen(c) },
                    )
                } else if (selected.isNotEmpty()) {
                    CardRow(c, c.id in selected, onClick = { onOpen(c) }, onLongClick = { onLongPress(c) })
                } else {
                    SwipeRow(onMove = { onSwipeMove(c) }, onDelete = { onSwipeDelete(c) }) {
                        CardRow(c, false, onClick = { onOpen(c) }, onLongClick = { onLongPress(c) })
                    }
                }
            }
        }
    }
}

/** Swipe right to move a card to another box, left to remove it (with Undo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(onMove: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> onMove()
            SwipeToDismissBoxValue.EndToStart -> onDelete()
            else -> Unit
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
                    .background(if (toEnd) Palette.Accent.copy(alpha = 0.25f) else Palette.Danger.copy(alpha = 0.25f))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                if (toEnd) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, tint = Palette.Accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Move", color = Palette.Accent)
                } else {
                    Text("Remove", color = Palette.Danger)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.DeleteOutline, null, tint = Palette.Danger)
                }
            }
        },
    ) { Box(Modifier.background(Palette.Background)) { content() } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardRow(c: CollectionCard, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Palette.Accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.width(46.dp), corner = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                ManaCost(c.manaCost, size = 14.dp)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RarityDot(c.rarity, 8.dp)
                Spacer(Modifier.width(6.dp))
                Text("${c.setCode} #${c.collectorNumber} · ${c.typeLine}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.foil) { FoilTag(); Spacer(Modifier.width(4.dp)) }
                Text("×${c.quantity}", style = MaterialTheme.typography.titleSmall)
            }
            if (AppSettings.showPrices) Text(formatUsd(c.unitPrice()), style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
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
                COLOR_FILTERS.forEach { col ->
                    val label = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colorless", "M" to "Multicolor")[col] ?: col
                    val pip: (@Composable () -> Unit)? = if (col in listOf("W", "U", "B", "R", "G")) {
                        { ColorPip(col[0]) }
                    } else null
                    SelectPill(label, col in f.colors, onClick = { vm.update { it.copy(colors = it.colors.toggle(col)) } }, leading = pip)
                }
            }
            FilterGroup("Rarity") {
                RARITIES.forEach { r ->
                    SelectPill(r.replaceFirstChar(Char::uppercase), r in f.rarities, onClick = { vm.update { it.copy(rarities = it.rarities.toggle(r)) } },
                        leading = { RarityDot(r) })
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
