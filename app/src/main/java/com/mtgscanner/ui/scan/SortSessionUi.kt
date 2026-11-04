package com.mtgscanner.ui.scan

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.Divider
import com.mtgscanner.data.SortPrefs
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

/** One box's stack in a sorting session. */
private data class SortStack(val boxId: Long, val name: String, val color: Int, val entries: List<ScanEntry>) {
    val count: Int get() = entries.sumOf { it.count }
    val full: Boolean get() = entries.any { it.full }
}

/** Groups the session's cards by box, in Auto-sort order, each stack front to back by divider. */
private fun stacks(session: SortSession, boxes: List<CardBox>): List<SortStack> {
    val order = boxes.withIndex().associate { (i, b) -> b.id to i }
    return session.entries.groupBy { it.boxId }.map { (id, list) ->
        val box = boxes.firstOrNull { it.id == id }
        val byName = list.sortedBy { it.card.name }
        val cmp = Sorting.spotOrder(Divider.of(box?.divider.orEmpty()))
        val sorted = byName.sortedWith { a, b ->
            val x = a.spot
            val y = b.spot
            when {
                x == null && y == null -> 0
                x == null -> 1
                y == null -> -1
                else -> cmp.compare(x, y)
            }
        }
        SortStack(id, box?.name ?: list.first().boxName.ifEmpty { "Unsorted" }, box?.color ?: 0, sorted)
    }.sortedBy { order[it.boxId] ?: Int.MAX_VALUE }
}

/** Shows the sorting session's card count, with a Sort button. */
@Composable
fun SortChip(state: ScanUiState, vm: ScanViewModel, modifier: Modifier = Modifier) {
    val s = state.sort ?: return
    Surface(modifier, shape = RoundedCornerShape(Corners.Medium), color = Color.Black.copy(alpha = 0.75f)) {
        Row(Modifier.padding(start = Space.s3, end = Space.s1, top = Space.s1, bottom = Space.s1), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Sorting", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Text("${s.entries.sumOf { it.count }} cards", color = Palette.Gold, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { vm.showSort(true) }) { Text("Sort", color = Palette.Accent) }
        }
    }
}

/** The pile grouped by box, to file each stack and check it off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(state: ScanUiState, boxes: List<CardBox>, vm: ScanViewModel) {
    val session = state.sort ?: return
    // Regrouped only when the pile or the boxes change, not on every redraw.
    val all = remember(session.entries, boxes) { stacks(session, boxes) }
    val (done, todo) = remember(all, session.done) { all.partition { it.boxId in session.done } }
    ModalBottomSheet(onDismissRequest = { vm.showSort(false) }, containerColor = Palette.Surface) {
        Row(Modifier.padding(horizontal = Space.s4), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sort the pile", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (all.isEmpty()) "Nothing scanned yet" else all.joinToString(" · ") { "${it.name}: ${it.count}" },
                    style = MaterialTheme.typography.bodySmall, color = Palette.Gold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = vm::endSort) { Text("End") }
        }
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            (todo + done).forEach { st ->
                val isDone = st.boxId in session.done
                item(key = "h${st.boxId}") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s2, top = Space.s3, bottom = Space.s1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${st.name} · ${st.count}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isDone) Palette.Muted else boxColor(st.color),
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (st.full && !isDone) {
                            Spacer(Modifier.width(Space.s2))
                            FullTag()
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.clip(RoundedCornerShape(Corners.Small)).clickable { vm.toggleSortDone(st.boxId) }.padding(horizontal = Space.s3, vertical = Space.s2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isDone) Icons.Filled.CheckCircle else Icons.Outlined.CheckBoxOutlineBlank, null,
                                tint = if (isDone) Palette.Success else Palette.Icon, modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Space.s1))
                            Text("Done", style = MaterialTheme.typography.labelLarge, color = if (isDone) Palette.Success else Palette.Text)
                        }
                    }
                }
                if (!isDone) items(st.entries, key = { it.key }) { e ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            e.spot ?: "",
                            style = MaterialTheme.typography.labelMedium, color = Palette.Text,
                            modifier = Modifier.width(72.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            e.card.name + if (e.count > 1) " ×${e.count}" else "",
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (e.foil) {
                            Spacer(Modifier.width(Space.s2))
                            FoilTag()
                        }
                        e.note?.let {
                            Spacer(Modifier.width(Space.s2))
                            Text(it, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (all.isNotEmpty() && todo.isEmpty()) {
            GradientButton("All filed · End", vm::endSort, Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2))
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

/** Small "Full" warning for a box at capacity. */
@Composable
fun FullTag(modifier: Modifier = Modifier) {
    Text(
        "Full",
        color = Color.Black,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.Gold).padding(horizontal = Space.s2, vertical = 1.dp),
    )
}
