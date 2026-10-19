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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Style
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
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.ScryfallSet
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.components.AddCardSheet
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.Palette

@Composable
fun SearchScreen(vm: SearchViewModel = viewModel(factory = AppViewModels.Factory)) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabBar(ui.tab, vm::setTab)
            if (ui.tab == 0) CardsTab(ui, vm) else SetsTab(ui, vm)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    ui.selected?.let { card ->
        if (!ui.showPrintings) {
            AddCardSheet(
                card = card,
                boxes = boxes,
                defaultBoxId = CardBox.BULK_ID,
                onAdd = vm::add,
                onChangePrinting = vm::openPrintings,
                onDismiss = { vm.select(null) },
            )
        }
    }
    if (ui.showPrintings) PrintingsSheet(ui.printings, ui.selected?.id, vm::pickPrinting, vm::closePrintings)
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
            Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.Text else Palette.Muted)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(if (selected) Palette.Accent else Palette.Outline.copy(alpha = 0.4f)))
    }
}

@Composable
private fun CardsTab(ui: SearchUi, vm: SearchViewModel) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Palette.SurfaceHigh).padding(horizontal = 16.dp, vertical = 14.dp),
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
                    Icon(Icons.Filled.Close, "Clear", tint = Palette.Muted, modifier = Modifier.size(20.dp).clickable { vm.setQuery("") })
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.AccentGradient)
                    .clickable { focus.clearFocus(); vm.submit() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) { Text("SEARCH", style = MaterialTheme.typography.labelLarge, color = Palette.OnAccent) }
            IconButton(onClick = vm::toggleGrid) {
                Icon(if (ui.grid) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView, "Change layout", tint = Palette.Text)
            }
        }
        Text(
            "Tip: Scryfall search works too, like  t:dragon c:r  or  e:mh3 r:mythic",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
            modifier = Modifier.padding(top = 8.dp),
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
            ui.ranQuery == null -> EmptyState(
                Icons.Filled.Search,
                "Find any card",
                "Search by name, or open the Sets tab to browse a whole set. Tap a card to add it to your collection.",
                Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
            )
            ui.results.isEmpty() && ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Palette.Accent)
            ui.results.isEmpty() -> EmptyState(Icons.Filled.SearchOff, "No cards found", "Check the spelling, or try fewer words.", Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
            else -> Results(ui, vm)
        }
    }
}

@Composable
private fun Results(ui: SearchUi, vm: SearchViewModel) {
    LazyVerticalGrid(
        columns = if (ui.grid) GridCells.Adaptive(108.dp) else GridCells.Fixed(1),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(start = 6.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ui.ranLabel.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Palette.Accent, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${ui.results.size}${if (ui.hasMore) "+" else ""} cards", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                IconButton(onClick = vm::clearResults) { Icon(Icons.Filled.Close, "Clear results", tint = Palette.Muted) }
            }
        }
        itemsIndexed(ui.results, key = { _, c -> c.id }) { i, c ->
            if (i >= ui.results.size - 8) LaunchedEffect(ui.results.size) { vm.loadMore() }
            if (ui.grid) {
                CardTile(c.imageUrl, c.name, formatUsd(c.prices.usd ?: c.prices.usdFoil), onClick = { vm.select(c) })
            } else {
                ResultRow(c) { vm.select(c) }
            }
        }
        if (ui.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
            }
        }
    }
}

@Composable
private fun ResultRow(c: ScryfallCard, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(c.smallImageUrl, Modifier.width(46.dp), corner = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                ManaCost(c.effectiveManaCost, size = 14.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RarityDot(c.rarity, 8.dp)
                Spacer(Modifier.width(6.dp))
                Text("${c.set.uppercase()} #${c.collectorNumber} · ${c.effectiveTypeLine}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(formatUsd(c.prices.usd), style = MaterialTheme.typography.titleSmall, color = Palette.Accent)
    }
}

@Composable
private fun SetsTab(ui: SearchUi, vm: SearchViewModel) {
    SearchField(ui.setFilter, vm::setSetFilter, Modifier.padding(16.dp), placeholder = "Filter sets")
    val sets = ui.sets
    if (sets == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
        return
    }
    val q = ui.setFilter.trim().lowercase()
    val shown = if (q.isEmpty()) sets else sets.filter { it.name.lowercase().contains(q) || it.code.lowercase() == q }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(shown, key = { it.code }) { s -> SetRow(s) { vm.openSet(s) } }
    }
}

@Composable
private fun SetRow(s: ScryfallSet, onClick: () -> Unit) {
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
        Text("${s.cardCount}", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
    }
}
