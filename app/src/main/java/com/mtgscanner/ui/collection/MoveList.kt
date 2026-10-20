package com.mtgscanner.ui.collection

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.Binder
import com.mtgscanner.data.Divider
import com.mtgscanner.data.MoveLine
import com.mtgscanner.data.MoveLists
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.facts
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.theme.Palette

/**
 * The physical move list: after a re-sort, bulk move or split, "take X from box A to box B"
 * to tick off while moving the real cards. App-wide so it survives leaving a screen; cleared by hand.
 */
object MoveList {
    var lines by mutableStateOf<List<MoveLine>>(emptyList())
        private set
    /** Indexes of ticked lines. */
    var done by mutableStateOf<Set<Int>>(emptySet())
    var open by mutableStateOf(false)

    fun add(new: List<MoveLine>, show: Boolean = false) {
        if (new.isEmpty()) return
        lines = lines + new
        if (show) open = true
    }

    fun toggle(i: Int) { done = if (i in done) done - i else done + i }

    fun clear() {
        lines = emptyList()
        done = emptySet()
        open = false
    }

    /** A line for each card leaving its box. [box] gives a box's name, kind and divider by id. */
    fun linesFor(
        moves: List<Pair<CollectionCard, Long>>,
        box: (Long) -> Triple<String, String, String>?,
    ): List<MoveLine> = moves.mapNotNull { (c, to) ->
        if (c.boxId == to) return@mapNotNull null
        val target = box(to)
        val spot = target?.let { (_, _, divider) -> Sorting.spot(Divider.of(divider), c.name, c.facts()) }
        MoveLine(c.quantity, c.name, c.setCode, box(c.boxId)?.first ?: "?", target?.first ?: "?", spot)
    }
}

private fun share(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Move list").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, "Share move list"))
}

/** A strip saying a move list is waiting, plus the list itself when opened. */
@Composable
fun MoveListHost(modifier: Modifier = Modifier) {
    val lines = MoveList.lines
    if (lines.isEmpty()) return
    val left = lines.filterIndexed { i, _ -> i !in MoveList.done }.sumOf { it.qty }
    Surface(
        onClick = { MoveList.open = true },
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = Palette.BoxCard,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (left == 0) "Move list done" else "Move list · $left to move", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { MoveList.open = true }) { Text("Open") }
        }
    }
    if (MoveList.open) MoveListSheet(onDismiss = { MoveList.open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveListSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val lines = MoveList.lines
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Move list", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { share(ctx, MoveLists.text(lines, MoveList.done)) }) { Icon(Icons.Filled.Share, "Share or print") }
                TextButton(onClick = { MoveList.clear() }) { Text("Clear") }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                itemsIndexed(lines) { i, l ->
                    val ticked = i in MoveList.done
                    Row(
                        Modifier.fillMaxWidth().clickable { MoveList.toggle(i) }.padding(start = 8.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(ticked, { MoveList.toggle(i) }, colors = CheckboxDefaults.colors(checkedColor = Palette.Accent))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (l.qty > 1) "${l.qty}× " else "") + l.name,
                                style = MaterialTheme.typography.titleSmall,
                                textDecoration = if (ticked) TextDecoration.LineThrough else null,
                                color = if (ticked) Palette.Muted else Palette.Text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${l.from} → ${l.to}" + (l.spot?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            GradientButton("Done", { MoveList.clear() }, Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}

/** Binder slot label for a card in a binder, else null. */
fun slotLabel(c: CollectionCard, boxKind: String?): String? =
    if (boxKind != null && Binder.isBinder(boxKind)) Binder.label(c.binderSlot) else null
