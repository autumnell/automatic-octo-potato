package com.mtgscanner.ui.collection

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.DeleteOutline
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
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.home.SortItem
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums

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
                    Text("${l.cards} cards${priceSuffix(l.value)}", style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted)
                }
                IconButton(onClick = { onDelete(l.search) }) { Icon(Icons.Outlined.DeleteOutline, "Delete list", tint = Palette.Icon) }
            }
        }
    }
}

/** "To be sorted" from the Boxes page: cards that belong in another box. Move each, or all at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToSortSheet(
    list: List<SortItem>,
    boxes: List<BoxSummary>,
    onMove: (SortItem) -> Unit,
    onMoveAll: () -> Unit,
    onOpenCard: (Long) -> Unit,
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
                Text("To sort · ${list.sumOf { it.card.quantity }}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Later") }
            }
            if (list.isEmpty()) {
                EmptyState("All sorted")
                return@Column
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(list, key = { it.card.id }) { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenCard(m.card.id) }.padding(start = 16.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardImage(m.card.smallImageUrl, Modifier.width(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (m.card.quantity > 1) "${m.card.quantity}× " else "") + m.card.name,
                                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${byId[m.card.boxId]?.name ?: "?"} → ${byId[m.to]?.name ?: "?"}" + (m.spot?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onMove(m) }) { Text("Move") }
                    }
                }
            }
            GradientButton("Move all", onMoveAll, Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}
