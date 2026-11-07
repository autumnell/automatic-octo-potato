package com.mtgscanner.ui.decks

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.DeckCheckLine
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.ui.components.primaryValue
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums

/** How the deck page shows its cards: overlapping piles by type, a grid of whole cards with + and −, or rows. */
enum class DeckLayout(val label: String) { STACKS("Stacks"), GRID("Grid"), LIST("List") }

/** The layout picked last on any deck page; null until one is picked. */
object DeckLayoutPref {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("deck_view", Context.MODE_PRIVATE)
    fun get(ctx: Context): DeckLayout? = runCatching { prefs(ctx).getString("layout", null)?.let { DeckLayout.valueOf(it) } }.getOrNull()
    fun set(ctx: Context, layout: DeckLayout) = prefs(ctx).edit().putString("layout", layout.name).apply()
}

/**
 * A card type's heading as a small panel: the name, then how many cards and what they're worth (when prices
 * are shown).
 */
@Composable
fun CategoryHeader(title: String, lines: List<DeckCheckLine>, modifier: Modifier = Modifier) {
    val qty = lines.sumOf { it.needed }
    val usd = lines.sumOf { (it.price ?: 0.0) * it.needed }
    val eur = lines.sumOf { (it.priceEur ?: 0.0) * it.needed }
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2)
            .clip(RoundedCornerShape(Corners.Small)).background(Palette.Surface).padding(horizontal = Space.s3, vertical = Space.s2),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth()) {
            Text("Qty $qty", style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted, modifier = Modifier.weight(1f))
            if (AppSettings.showPrices && primaryValue(usd, eur) > 0) {
                Text(formatMoney(usd, eur), style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted)
            }
        }
    }
}

/**
 * Whole cards two to a row under their [CategoryHeader]. Each card shows its count in the corner and, when
 * [onChange] is given, + and − down its right edge to change the deck straight away. Tapping a card opens it.
 */
@Composable
fun DeckGrid(title: String, lines: List<DeckCheckLine>, onOpen: (DeckCheckLine) -> Unit, onChange: ((DeckCheckLine, Int) -> Unit)?) {
    CategoryHeader(title, lines)
    Column(Modifier.padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        lines.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                pair.forEach { l -> GridCard(l, Modifier.weight(1f), onOpen, onChange) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.size(Space.s2))
}

@Composable
private fun GridCard(l: DeckCheckLine, modifier: Modifier, onOpen: (DeckCheckLine) -> Unit, onChange: ((DeckCheckLine, Int) -> Unit)?) {
    Box(modifier) {
        CardImage(l.imageUrl, Modifier.fillMaxWidth(), zoom = true, onTap = { onOpen(l) }, grey = l.owned < l.needed)
        // The count, top left, on a dark tab so it reads over any frame.
        Text(
            "${l.needed}",
            style = MaterialTheme.typography.labelMedium.merge(TabularNums), color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(topStart = Corners.Small, bottomEnd = Corners.Small))
                .background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = Space.s2, vertical = Space.s1),
        )
        if (onChange != null) Column(
            Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(topStart = Corners.Small, bottomStart = Corners.Small))
                .background(Color.Black.copy(alpha = 0.72f)),
        ) {
            IconButton(onClick = { com.mtgscanner.util.Haptics.tick(); onChange(l, 1) }) {
                Icon(Icons.Outlined.Add, "One more ${l.name}", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            HorizontalDivider(Modifier.padding(horizontal = Space.s2), color = Color.White.copy(alpha = 0.2f))
            IconButton(onClick = { com.mtgscanner.util.Haptics.tick(); onChange(l, -1) }) {
                Icon(Icons.Outlined.Remove, "One fewer ${l.name}", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * The deck page's tool bar along the bottom: Search, Quick add, Layout, Filter and More, each an icon over its
 * name. Layout opens a small menu of the three layouts.
 */
@Composable
fun DeckToolBar(
    layout: DeckLayout,
    filtering: Boolean,
    onSearch: () -> Unit,
    onQuickAdd: () -> Unit,
    onLayout: (DeckLayout) -> Unit,
    onFilter: () -> Unit,
    onMore: () -> Unit,
) {
    var layoutMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Palette.Surface).navigationBarsPadding()) {
        HorizontalDivider(color = Palette.Rule)
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s1)) {
            ToolItem(Icons.Outlined.Search, "Search", Modifier.weight(1f), onClick = onSearch)
            ToolItem(Icons.Outlined.Add, "Quick add", Modifier.weight(1f), onClick = onQuickAdd)
            Box(Modifier.weight(1f)) {
                ToolItem(layoutIcon(layout), "Layout", Modifier.fillMaxWidth()) { layoutMenu = true }
                DropdownMenu(expanded = layoutMenu, onDismissRequest = { layoutMenu = false }) {
                    DeckLayout.entries.forEach { l ->
                        DropdownMenuItem(
                            text = { Text(l.label, color = if (l == layout) Palette.Text else Palette.Muted) },
                            leadingIcon = { Icon(layoutIcon(l), null, tint = Palette.Icon, modifier = Modifier.size(18.dp)) },
                            onClick = { layoutMenu = false; onLayout(l) },
                        )
                    }
                }
            }
            ToolItem(Icons.Outlined.FilterList, "Filter", Modifier.weight(1f), selected = filtering, onClick = onFilter)
            ToolItem(Icons.Outlined.MoreHoriz, "More", Modifier.weight(1f), onClick = onMore)
        }
    }
}

private fun layoutIcon(l: DeckLayout): ImageVector = when (l) {
    DeckLayout.STACKS -> Icons.Outlined.Dashboard
    DeckLayout.GRID -> Icons.Outlined.ViewModule
    DeckLayout.LIST -> Icons.Outlined.ViewAgenda
}

@Composable
private fun ToolItem(icon: ImageVector, label: String, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(Corners.Small))
            .selectable(selected = selected, role = Role.Button, onClick = onClick).padding(vertical = Space.s1),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) Palette.Text else Palette.Icon, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Palette.Text else Palette.Muted, maxLines = 1)
    }
}

/** Quick add from the deck page: type a name, tap a suggestion, and the card is in the deck. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(ui: DeckUi, vm: DeckViewModel, onDismiss: () -> Unit) {
    var searching by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(start = Space.s4, end = Space.s4, bottom = Space.s6)) {
            Text("Quick add", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = Space.s3))
            DeckQuickAdd(ui, vm, "Main", searching) { searching = it }
        }
    }
}
