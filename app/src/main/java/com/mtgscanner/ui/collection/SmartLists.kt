package com.mtgscanner.ui.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.BoxLayout
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.facts
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

/** A saved Browse search shown like a box: it holds no cards itself, it just counts what matches. */
data class SmartList(val search: SavedSearch, val cards: Int, val value: Double) {
    companion object {
        /** Uses Browse's own filter so the count matches what Browse shows; a list saved in a box counts that box only. */
        fun of(s: SavedSearch, all: List<CollectionCard>, env: com.mtgscanner.data.search.SearchEnv = com.mtgscanner.data.search.SearchEnv()): SmartList {
            val box = s.boxId
            val hits = all.filter { (box == null || it.boxId == box) && BrowseViewModel.matches(it, s.filters, null, env) }
            return SmartList(s, hits.sumOf { it.quantity }, hits.sumOf { it.totalPrice() })
        }
    }
}

/** The "Lists" section on the Boxes page. */
@Composable
fun SmartListsSection(lists: List<SmartList>, onOpen: (SavedSearch) -> Unit, onDelete: (SavedSearch) -> Unit) {
    SectionHeader("Lists")
    if (lists.isEmpty()) {
        Text(
            "Save a search in Browse.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        return
    }
    lists.forEach { l ->
        Surface(
            onClick = { onOpen(l.search) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(Corners.Medium),
            color = Palette.Surface,
        ) {
            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = Palette.Gold)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.search.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${l.cards} cards${priceSuffix(l.value)}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                IconButton(onClick = { onDelete(l.search) }) { Icon(Icons.Filled.DeleteOutline, "Delete list", tint = Palette.Muted) }
            }
        }
    }
}

/** Walks through cards that now belong in another box: move each, or all at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResortSheet(
    moves: List<Sorting.Move<CollectionCard>>,
    boxes: List<BoxSummary>,
    onMove: (Sorting.Move<CollectionCard>) -> Unit,
    onMoveAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val byId = boxes.associateBy { it.id }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Re-sort · ${moves.sumOf { it.item.quantity }} cards", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Later") }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(moves, key = { it.item.id }) { m ->
                    val to = byId[m.to]
                    val spot = to?.let { Sorting.spot(BoxLayout.of(it.divider), m.item.name, m.item.facts()) }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        CardImage(m.item.smallImageUrl, Modifier.width(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (m.item.quantity > 1) "${m.item.quantity}× " else "") + m.item.name,
                                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${byId[m.from]?.name ?: "?"} → ${to?.name ?: "?"}" + (spot?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onMove(m) }) { Text("Move") }
                    }
                }
            }
            GradientButton(
                "Move all",
                onMoveAll,
                Modifier.fillMaxWidth().padding(16.dp),
                enabled = moves.isNotEmpty(),
            )
        }
    }
}

