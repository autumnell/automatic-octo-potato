package com.mtgscanner.ui.collection

import androidx.compose.foundation.background
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
import com.mtgscanner.ui.theme.Palette

@Composable
fun BrowseScreen(boxId: Long, onBack: () -> Unit, onOpenCard: (Long) -> Unit) {
    val vm: BrowseViewModel = viewModel(key = "browse-$boxId", factory = AppViewModels.browse(boxId))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val f by vm.filters.collectAsStateWithLifecycle()
    val grid by vm.grid.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(ui.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    if (f.activeCount > 0 || f.query.isNotBlank()) "${ui.shownCards} of ${ui.totalCards} cards · ${formatUsd(ui.shownValue)}"
                    else "${ui.totalCards} cards · ${formatUsd(ui.shownValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
            }
            IconButton(onClick = vm::toggleGrid) {
                Icon(if (grid) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView, if (grid) "List view" else "Grid view")
            }
        }

        SearchField(f.query, { q -> vm.update { it.copy(query = q) } }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var sortMenu by remember { mutableStateOf(false) }
            var groupMenu by remember { mutableStateOf(false) }
            Box {
                ToolChip(f.sort.label, Icons.AutoMirrored.Filled.Sort, { sortMenu = true })
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortBy.entries.forEach { s ->
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
        }

        when {
            !ui.loaded -> Unit
            ui.totalCards == 0 -> EmptyState(Icons.Filled.Style, "Nothing here yet", "Cards you scan or add into this box will show up here.")
            ui.groups.isEmpty() -> EmptyState(
                Icons.Filled.SearchOff, "No cards match", "Try a different search or clear the filters.",
                action = { TextButton(onClick = { vm.clearFilters(); vm.update { it.copy(query = "") } }) { Text("Clear search and filters") } },
            )
            else -> CardGrid(ui.groups, grid, onOpenCard)
        }
    }

    if (showFilters) FilterSheet(f, ui, vm, onDismiss = { showFilters = false })
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
private fun CardGrid(groups: List<Pair<String, List<CollectionCard>>>, grid: Boolean, onOpenCard: (Long) -> Unit) {
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
                    CardTile(c.imageUrl, c.name, formatUsd(c.unitPrice()), quantity = c.quantity, foil = c.foil, onClick = { onOpenCard(c.id) })
                } else {
                    CardRow(c) { onOpenCard(c.id) }
                }
            }
        }
    }
}

@Composable
private fun CardRow(c: CollectionCard, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
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
            Text(formatUsd(c.unitPrice()), style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(f: Filters, ui: BrowseUi, vm: BrowseViewModel, onDismiss: () -> Unit) {
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
                        "Roles like Removal, Ramp and Card draw come from Scryfall's community tags. They show up here once your cards have been looked up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
                ui.roles.forEach { r ->
                    SelectPill(r, r in f.roles, onClick = { vm.update { it.copy(roles = it.roles.toggle(r)) } })
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
