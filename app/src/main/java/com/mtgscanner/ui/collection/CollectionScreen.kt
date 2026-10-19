package com.mtgscanner.ui.collection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.MtgColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionScreen(
    onOpenCard: (Long) -> Unit,
    vm: CollectionViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val f by vm.filters.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(ctx.contentResolver, uri)
    }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Collection")
                        Text(
                            "${ui.totalCards} cards · ${formatUsd(ui.totalValue)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = vm::refreshPrices) { Icon(Icons.Filled.Refresh, "Refresh prices") }
                    IconButton(onClick = {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        exportLauncher.launch("mtg-collection-$date.csv")
                    }) { Icon(Icons.Filled.FileDownload, "Export CSV") }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, "Sort and group") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            Text("Sort by", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
                            SortBy.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text((if (f.sort == s) "✓ " else "") + s.label) },
                                    onClick = { vm.update { it.copy(sort = s) }; sortMenu = false },
                                )
                            }
                            HorizontalDivider()
                            Text("Group by", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
                            GroupBy.entries.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text((if (f.group == g) "✓ " else "") + g.label) },
                                    onClick = { vm.update { it.copy(group = g) }; sortMenu = false },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = f.query,
                    onValueChange = { q -> vm.update { it.copy(query = q) } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search name, type, text, tag") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (f.query.isNotEmpty()) IconButton(onClick = { vm.update { it.copy(query = "") } }) { Icon(Icons.Filled.Clear, "Clear") }
                    },
                )
                IconButton(onClick = { showFilters = !showFilters }) {
                    BadgedBox(badge = { if (f.activeCount > 0) Badge { Text("${f.activeCount}") } }) {
                        Icon(Icons.Filled.FilterList, "Filters")
                    }
                }
            }
            if (showFilters) FilterPanel(f, ui, vm)
            if (f.activeCount > 0 || f.query.isNotBlank()) {
                Text(
                    "Showing ${ui.shownCards} cards · ${formatUsd(ui.shownValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (ui.loaded && ui.totalCards == 0) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No cards yet. Scan some from the Scan tab.", textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    ui.groups.forEach { (title, cards) ->
                        if (title.isNotEmpty()) {
                            stickyHeader(key = "h-$title") {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "$title · ${cards.sumOf { it.quantity }}",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                        items(cards, key = { it.id }) { c -> CardRow(c, onClick = { onOpenCard(c.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(f: Filters, ui: CollectionUi, vm: CollectionViewModel) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        ChipRow {
            COLOR_FILTERS.forEach { col ->
                val label = mapOf("C" to "Colorless", "M" to "Multi")[col] ?: col
                FilterChip(
                    selected = col in f.colors,
                    onClick = { vm.update { it.copy(colors = it.colors.toggle(col)) } },
                    label = { Text(label) },
                    leadingIcon = if (col.length == 1 && col in "WUBRG") {
                        { Box(Modifier.size(12.dp).background(MtgColors.forLetter(col[0]), CircleShape)) }
                    } else null,
                )
            }
        }
        ChipRow {
            RARITIES.forEach { r ->
                FilterChip(
                    selected = r in f.rarities,
                    onClick = { vm.update { it.copy(rarities = it.rarities.toggle(r)) } },
                    label = { Text(r.replaceFirstChar(Char::uppercase)) },
                    leadingIcon = { RarityDot(r) },
                )
            }
            FilterChip(selected = f.foilOnly, onClick = { vm.update { it.copy(foilOnly = !it.foilOnly) } }, label = { Text("Foil only") })
        }
        ChipRow {
            CARD_TYPES.forEach { t ->
                FilterChip(
                    selected = t in f.types,
                    onClick = { vm.update { it.copy(types = it.types.toggle(t)) } },
                    label = { Text(t) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            var open by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = f.set != null,
                    onClick = { open = true },
                    label = { Text(f.set?.let { code -> ui.sets.firstOrNull { it.first == code }?.second ?: code } ?: "Any set") },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text("Any set") }, onClick = { vm.update { it.copy(set = null) }; open = false })
                    ui.sets.forEach { (code, name) ->
                        DropdownMenuItem(text = { Text("$name ($code)") }, onClick = { vm.update { it.copy(set = code) }; open = false })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (f.activeCount > 0) TextButton(onClick = vm::clearFilters) { Text("Clear filters") }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

@Composable
private fun CardRow(c: CollectionCard, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.width(44.dp).height(61.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (c.foil) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Star, "Foil", tint = Color(0xFFD4AF37), modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(6.dp))
                ManaCost(c.manaCost, size = 14.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RarityDot(c.rarity, 8.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${c.setCode} #${c.collectorNumber} · ${c.typeLine}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("×${c.quantity}", style = MaterialTheme.typography.titleSmall)
            Text(formatUsd(c.unitPrice()), style = MaterialTheme.typography.bodySmall)
        }
    }
}
