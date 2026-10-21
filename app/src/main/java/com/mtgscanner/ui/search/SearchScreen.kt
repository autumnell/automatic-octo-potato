package com.mtgscanner.ui.search

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mtgscanner.ui.components.AddCardSheet
import com.mtgscanner.ui.components.BoxEditorSheet
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.OwnedBadge
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.ui.components.RarityMark
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette

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
    val owned by vm.owned.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var newBox by remember { mutableStateOf(false) }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    com.mtgscanner.util.Haptics.Attach()

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabBar(ui.tab, vm::setTab)
            if (ui.tab == 0) CardsTab(ui, vm, owned) else SetsTab(ui, vm, ownedSets, onOpenSet)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    ui.selected?.let { card ->
        if (!ui.showPrintings) {
            val mine = owned.of(card)
            AddCardSheet(
                card = card,
                boxes = boxes,
                // Default to where you already keep this card.
                defaultBoxId = mine?.boxId ?: vm.lastBoxId,
                onAdd = vm::add,
                onChangePrinting = vm::openPrintings,
                onDismiss = { vm.select(null) },
                onCreateBox = { newBox = true },
                onWishlist = vm::wishlist,
                owned = mine?.label,
                onOwned = mine?.rowId?.let { id -> onOpenCard?.let { open -> { vm.select(null); open(id) } } },
            )
        }
    }
    if (ui.showPrintings) PrintingsSheet(ui.printings, ui.selected?.id, vm::pickPrinting, vm::closePrintings)
    if (newBox) {
        BoxEditorSheet(
            title = "New box",
            onSave = { vm.createBox(it); newBox = false },
            onDismiss = { newBox = false },
        )
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Palette.Background)) {
        listOf("Cards" to Icons.Filled.Style, "Sets" to Icons.AutoMirrored.Filled.ViewList).forEachIndexed { i, (label, icon) ->
            TabItem(label, icon, selected == i, Modifier.weight(1f)) { onSelect(i) }
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) Palette.Text else Palette.Muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.Text else Palette.Muted)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(if (selected) Palette.Accent else Palette.Outline.copy(alpha = 0.4f)))
    }
}

@Composable
private fun CardsTab(ui: SearchUi, vm: SearchViewModel, owned: Map<String, Owned>) {
    val focus = LocalFocusManager.current
    var showFilters by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(Palette.SurfaceHigh).padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (ui.query.isEmpty()) Text("Search for card…", color = Palette.Muted, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = ui.query,
                        onValueChange = vm::setQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                        cursorBrush = SolidColor(Palette.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (ui.query.isNotEmpty()) {
                    IconButton(onClick = vm::clearResults) { Icon(Icons.Filled.Close, "Clear search", tint = Palette.Muted, modifier = Modifier.size(20.dp)) }
                } else {
                    Spacer(Modifier.width(16.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.AccentGradient)
                    .clickable { focus.clearFocus(); vm.submit() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) { Text("Search", style = MaterialTheme.typography.labelLarge, color = Palette.OnAccent) }
            IconButton(onClick = vm::toggleGrid) {
                Icon(if (ui.grid) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView, "Change layout", tint = Palette.Text)
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val n = ui.filters.activeCount
            SelectPill(
                if (n > 0) "Filters · $n" else "Filters", n > 0, { showFilters = true },
                leading = { Icon(Icons.Filled.Tune, null, Modifier.size(16.dp), tint = if (n > 0) Palette.AccentText else Palette.Text) },
            )
            Spacer(Modifier.width(8.dp))
            SavedMenu(ui.saved, onSave = { saveName = ui.query.trim() }, onLoad = { focus.clearFocus(); vm.loadSaved(it) }, onDelete = vm::deleteSaved)
            Spacer(Modifier.width(10.dp))
            Text("e.g. t:dragon c:r", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    if (showFilters) FilterSheet(
        filters = ui.filters,
        onChange = vm::setFilters,
        onApply = { showFilters = false; focus.clearFocus(); vm.submit() },
        onDismiss = { showFilters = false },
    )
    saveName?.let { name ->
        AlertDialog(
            onDismissRequest = { saveName = null },
            containerColor = Palette.Surface,
            title = { Text("Save search") },
            text = { OutlinedTextField(name, { saveName = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { vm.saveSearch(name); saveName = null }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { saveName = null }) { Text("Cancel") } },
        )
    }

    Box(Modifier.fillMaxSize()) {
        when {
            ui.suggestions.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                items(ui.suggestions) { name ->
                    Row(
                        Modifier.fillMaxWidth().clickable { focus.clearFocus(); vm.pickSuggestion(name) }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, null, tint = Palette.Muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            ui.ranQuery == null && ui.query.isEmpty() && (ui.recent.isNotEmpty() || ui.viewed.isNotEmpty()) -> History(ui, vm)
            ui.ranQuery == null -> EmptyState("Find any card", Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
            ui.results.isEmpty() && ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Palette.Accent)
            ui.results.isEmpty() && ui.failed && ui.error != null ->
                Column(Modifier.align(Alignment.TopCenter).padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("Can't run this search")
                    Text(ui.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 32.dp))
                }
            ui.results.isEmpty() && ui.failed -> OfflineState(vm::retry, Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
            ui.results.isEmpty() -> EmptyState("No cards found", Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
            else -> Results(ui, vm, owned)
        }
    }
}

@Composable
private fun Results(ui: SearchUi, vm: SearchViewModel, owned: Map<String, Owned>) {
    LazyVerticalGrid(
        columns = if (ui.grid) GridCells.Adaptive(108.dp) else GridCells.Fixed(1),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(start = 6.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ui.ranLabel.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Palette.AccentText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${ui.results.size}${if (ui.hasMore) "+" else ""} cards", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                IconButton(onClick = vm::clearResults) { Icon(Icons.Filled.Close, "Clear results", tint = Palette.Muted) }
            }
        }
        itemsIndexed(ui.results, key = { _, c -> c.id }) { i, c ->
            if (i >= ui.results.size - 8) LaunchedEffect(ui.results.size) { vm.loadMore() }
            val have = owned.of(c)?.count ?: 0
            if (ui.grid) {
                CardTile(c.smallImageUrl, c.name, formatUsd(c.prices.usd ?: c.prices.usdFoil), owned = have, onClick = { vm.select(c) })
            } else {
                ResultRow(c, have) { vm.select(c) }
            }
        }
        if (ui.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
            }
        } else if (ui.moreFailed) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.TextButton(onClick = vm::loadMore) { Text("Couldn't load more · Retry", color = Palette.AccentText) }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(c: ScryfallCard, owned: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(c.smallImageUrl, Modifier.width(46.dp), corner = 4.dp, zoom = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                ManaCost(c.effectiveManaCost, size = 14.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RarityMark(c.rarity)
                Spacer(Modifier.width(6.dp))
                Text("${c.set.uppercase()} #${c.collectorNumber} · ${c.effectiveTypeLine}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (owned > 0) {
            Spacer(Modifier.width(8.dp))
            OwnedBadge(owned)
        }
        if (AppSettings.showPrices) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatUsd(c.prices.usd), style = MaterialTheme.typography.titleSmall, color = Palette.AccentText)
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (ui.viewed.isNotEmpty()) {
            item {
                Text("Recently viewed", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                    items(ui.viewed, key = { it.id }) { v ->
                        Column(Modifier.width(84.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.openViewed(v) }.padding(4.dp)) {
                            CardImage(v.image, Modifier.fillMaxWidth(), corner = 6.dp, zoom = true)
                            Text(v.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        if (ui.recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent searches", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                }
            }
            items(ui.recent) { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { focus.clearFocus(); vm.rerun(q) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.History, null, tint = Palette.Muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(q.text.ifEmpty { "Filtered" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val f = q.filterLabel
                        if (f.isNotEmpty()) Text(f, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            androidx.compose.material3.TextButton(onClick = vm::clearHistory, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
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
    SearchField(ui.setFilter, vm::setSetFilter, Modifier.padding(16.dp), placeholder = "Filter sets")
    val sets = ui.sets
    if (ui.setsFailed) {
        OfflineState(vm::retrySets, Modifier.padding(top = 24.dp))
        return
    }
    if (sets == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
        return
    }
    val q = ui.setFilter.trim().lowercase()
    val shown = if (q.isEmpty()) sets else sets.filter { it.name.lowercase().contains(q) || it.code.lowercase() == q }
    if (shown.isEmpty()) {
        EmptyState("No sets found", Modifier.padding(top = 24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(shown, key = { it.code }) { s ->
            SetRow(s, ownedSets[s.code.lowercase()] ?: 0) { if (onOpenSet != null) onOpenSet(s.code) else vm.openSet(s) }
        }
    }
}

@Composable
private fun SetRow(s: ScryfallSet, owned: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Surface), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = s.iconSvgUri,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Palette.Accent),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
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
            color = if (owned > 0) Palette.AccentText else Palette.Muted,
        )
    }
}
