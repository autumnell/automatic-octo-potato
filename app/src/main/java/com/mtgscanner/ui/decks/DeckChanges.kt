package com.mtgscanner.ui.decks

import com.mtgscanner.ui.theme.Space
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.DeckDiff
import com.mtgscanner.data.DeckDiffs
import com.mtgscanner.data.DeckLinks
import com.mtgscanner.data.DiffLine
import com.mtgscanner.data.SlotRow
import com.mtgscanner.data.deckSlot
import com.mtgscanner.data.db.Box as Place
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.moveToAutoBox
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Moxfield or Archidekt link each deck came from, by deck id. */
object DeckLinkStore {
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("deck_links", Context.MODE_PRIVATE)

    fun get(ctx: Context, deckId: Long): String? = prefs(ctx).getString(deckId.toString(), null)

    fun set(ctx: Context, deckId: Long, url: String?) {
        prefs(ctx).edit().apply { if (url == null) remove(deckId.toString()) else putString(deckId.toString(), url) }.apply()
    }
}

/** A removed card with copies kept in the deck: which rows leave, and where they go ([DeckDiffs.AUTO], [DeckDiffs.REMOVE] or a box id). */
data class LeavingCard(val line: DiffLine, val moves: List<Pair<Long, Int>>, val dest: Long = DeckDiffs.AUTO) {
    val copies: Int get() = moves.sumOf { it.second }
}

/** What the Changes sheet shows for one edit of a deck's list. */
data class DeckChangesUi(
    val deckId: Long,
    val newText: String,
    val diff: DeckDiff,
    val leaving: List<LeavingCard>,
    val boxes: List<Place>,
    val busy: Boolean = false,
)

/**
 * The Changes sheet behind editing a saved deck's list or updating it from its link. Nothing is saved until
 * Save: then the list is applied and the deck's own copies of removed cards go where they were sent, with Undo.
 */
class DeckChangesHost(
    private val repo: CollectionRepository,
    private val scope: CoroutineScope,
    /** Saves [text] as the deck's list, then runs the block with the deck id. */
    private val saveList: (text: String, after: suspend (Long) -> Unit) -> Unit,
    /** Puts the deck back as it was, for Undo. */
    private val restore: suspend (Deck) -> Unit,
) {
    var ui by mutableStateOf<DeckChangesUi?>(null)
        private set
    /** Asking for a link, for a deck that has none saved. */
    var askLink by mutableStateOf(false)
    /** Asking "Discard list?". */
    var askDiscard by mutableStateOf(false)
    var fetching by mutableStateOf(false)
        private set

    /** Shows what [newText] changes; when nothing does, it's saved right away. */
    fun review(deckId: Long, oldText: String, newText: String) {
        scope.launch {
            val u = prepare(deckId, oldText, newText)
            if (u == null) saveList(newText) { } else ui = u
        }
    }

    private suspend fun prepare(deckId: Long, oldText: String, newText: String): DeckChangesUi? {
        val diff = withContext(Dispatchers.Default) { DeckDiffs.of(oldText, newText) }
        if (diff.isEmpty) return null
        val slot = repo.deckSlot(deckId)?.let { s -> repo.cardsInBox(s.id) }.orEmpty()
            .map { SlotRow(it.id, it.name, it.setCode, it.collectorNumber, it.quantity) }
        val totals = DeckDiffs.totals(newText)
        val leaving = diff.removed.mapNotNull { r ->
            val moves = DeckDiffs.leaving(slot, r.key, r.qty, totals[r.key] ?: 0, DeckDiffs.keptPrintings(newText, r.key))
            if (moves.isEmpty()) null else LeavingCard(r, moves)
        }
        val boxes = if (leaving.isEmpty()) emptyList() else repo.observeBoxes().first()
        return DeckChangesUi(deckId, newText, diff, leaving, boxes)
    }

    fun setDest(key: String, dest: Long) {
        ui = ui?.let { u -> u.copy(leaving = u.leaving.map { if (it.line.key == key) it.copy(dest = dest) else it }) }
    }

    fun setAll(dest: Long) {
        ui = ui?.let { u -> u.copy(leaving = u.leaving.map { it.copy(dest = dest) }) }
    }

    /** Runs when the sheet closes without saving: a quick change from the deck page puts the list back. */
    private var onCancel: (() -> Unit)? = null

    fun cancel() { ui = null; onCancel?.invoke(); onCancel = null }

    /**
     * A change made on the deck page itself (+, −, quick add): saved at once, unless the deck holds copies of a
     * removed card, which then need a place to go (the sheet). Closing the sheet runs [revert].
     */
    fun quick(deckId: Long, oldText: String, newText: String, revert: () -> Unit) {
        scope.launch {
            val u = prepare(deckId, oldText, newText)
            if (u == null || u.leaving.isEmpty()) saveList(newText) { } else { onCancel = revert; ui = u }
        }
    }

    fun confirm() {
        val u = ui ?: return
        if (u.busy) return
        onCancel = null
        // An empty list isn't saved (the save step skips it), which would leave this sheet waiting for good.
        if (u.newText.isBlank()) { ui = null; Undo.offer("The new list is empty, so nothing was saved"); return }
        ui = u.copy(busy = true)
        scope.launch {
            val before = repo.observeDeck(u.deckId).first()
            saveList(u.newText) after@{ _ ->
                val rowIds = u.leaving.flatMap { l -> l.moves.map { it.first } }
                val snap = repo.snapshot(rowIds)
                try {
                    for (l in u.leaving) for ((rowId, n) in l.moves) when (l.dest) {
                        DeckDiffs.AUTO -> repo.moveToAutoBox(rowId, n)
                        DeckDiffs.REMOVE -> repo.remove(rowId, n)
                        else -> repo.moveCopies(rowId, n, l.dest)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The list is saved; put the cards back as they were rather than leave some moved.
                    repo.restore(snap)
                    Undo.offer("List saved, but the cards couldn't be moved")
                    ui = null
                    return@after
                } finally {
                    ui = null
                }
                val moved = u.leaving.sumOf { it.copies }
                Undo.offer(if (moved == 0) "List saved" else "List saved · $moved moved") {
                    repo.restore(snap)
                    if (before != null) restore(before)
                }
            }
        }
    }

    /** Re-reads the deck's Moxfield or Archidekt link ([url], or the one saved for it) and shows what changed. */
    fun updateFromLink(deckId: Long, savedText: String, url: String? = null) {
        if (fetching || deckId == 0L) return
        val link = url ?: DeckLinkStore.get(repo.appContext, deckId)
        if (link == null) { askLink = true; return }
        askLink = false
        fetching = true
        Undo.offer("Getting deck…")
        scope.launch {
            val deck = try {
                DeckLinks.fetch(link)
            } catch (e: java.io.IOException) {
                Undo.offer(e.message ?: "Couldn't get that deck")
                null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Undo.offer("Couldn't read that deck")
                null
            } finally {
                fetching = false
            }
            if (deck == null) return@launch
            DeckLinkStore.set(repo.appContext, deckId, link)
            val u = prepare(deckId, savedText, deck.text)
            if (u == null) Undo.offer("No changes") else ui = u
        }
    }
}

/**
 * The deck screen's Changes sheet, link dialog and "Discard list?" check. [dirty]: the paste box has text
 * that isn't saved; [onDiscard] drops it.
 */
@Composable
fun DeckChangesLayer(host: DeckChangesHost, dirty: Boolean, onDiscard: () -> Unit, onLink: (String) -> Unit) {
    BackHandler(enabled = dirty) { host.askDiscard = true }
    if (host.askDiscard) {
        AlertDialog(
            onDismissRequest = { host.askDiscard = false },
            containerColor = Palette.Surface,
            title = { Text("Discard list?") },
            confirmButton = {
                TextButton(onClick = { host.askDiscard = false; onDiscard() }) { Text("Discard", color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { host.askDiscard = false }) { Text("Keep") } },
        )
    }
    if (host.askLink) {
        DeckLinkDialog(onDismiss = { host.askLink = false }, title = "Deck link", action = "Update", onGet = onLink)
    }
    host.ui?.let { DeckChangesSheet(it, host) }
}

private fun destLabel(dest: Long, boxes: List<Place>): String = when (dest) {
    DeckDiffs.AUTO -> "Auto-sort"
    DeckDiffs.REMOVE -> "Remove"
    else -> boxes.firstOrNull { it.id == dest }?.name ?: "Auto-sort"
}

/** A pill that opens a menu of places: Auto-sort, each box, or out of the collection. */
@Composable
private fun DestPicker(label: String, boxes: List<Place>, selected: Boolean, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SelectPill(label, selected, { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Auto-sort") }, onClick = { open = false; onPick(DeckDiffs.AUTO) })
            boxes.forEach { b -> DropdownMenuItem(text = { Text(b.name) }, onClick = { open = false; onPick(b.id) }) }
            DropdownMenuItem(
                text = { Text("Remove from collection", color = Palette.Danger) },
                onClick = { open = false; onPick(DeckDiffs.REMOVE) },
            )
        }
    }
}

@Composable
private fun ChangeRow(text: String, detail: String?, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge.merge(TabularNums), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.s2))
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckChangesSheet(u: DeckChangesUi, host: DeckChangesHost) {
    var showSame by remember { mutableStateOf(false) }
    val d = u.diff
    val leaving = u.leaving.associateBy { it.line.key }
    ModalBottomSheet(
        onDismissRequest = { if (!u.busy) host.cancel() },
        containerColor = Palette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = Space.s4).navigationBarsPadding()) {
            Text("Changes", style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(
                    "+${d.addedCount}".takeIf { d.addedCount > 0 },
                    "−${d.removedCount}".takeIf { d.removedCount > 0 },
                    "${d.changedCount} changed".takeIf { d.changedCount > 0 },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted,
            )
            if (u.leaving.size > 1) {
                Spacer(Modifier.height(Space.s3))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Send all to", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.weight(1f))
                    val same = u.leaving.map { it.dest }.distinct().singleOrNull()
                    DestPicker(same?.let { destLabel(it, u.boxes) } ?: "Mixed", u.boxes, selected = false, onPick = host::setAll)
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                if (d.added.isNotEmpty()) {
                    item { SectionHeader("Added (${d.addedCount})") }
                    items(d.added, key = { "a" + it.key }) { ChangeRow("+${it.qty} ${it.name}", it.printing) }
                }
                if (d.removed.isNotEmpty()) {
                    item { SectionHeader("Removed (${d.removedCount})") }
                    items(d.removed, key = { "r" + it.key }) { r ->
                        val l = leaving[r.key]
                        ChangeRow(
                            "−${r.qty} ${r.name}",
                            if (l != null) "${l.copies} in deck" else r.printing,
                            trailing = if (l == null) null else ({
                                DestPicker(destLabel(l.dest, u.boxes), u.boxes, selected = true) { host.setDest(l.line.key, it) }
                            }),
                        )
                    }
                }
                if (d.changed.isNotEmpty()) {
                    item { SectionHeader("Changed (${d.changedCount})") }
                    items(d.changed, key = { "c" + it.key }) { c ->
                        ChangeRow("${c.qty} ${c.name}", "${c.from ?: "Any"} → ${c.to ?: "Any"}")
                    }
                }
                if (d.unchanged.isNotEmpty()) {
                    item {
                        Text(
                            (if (showSame) "Hide unchanged" else "Unchanged (${d.unchangedCount})"),
                            style = MaterialTheme.typography.labelLarge, color = Palette.AccentText,
                            modifier = Modifier.fillMaxWidth().clickable { showSame = !showSame }.padding(vertical = Space.s3),
                        )
                    }
                    if (showSame) itemsIndexed(d.unchanged, key = { i, _ -> "u$i" }) { _, s -> ChangeRow("${s.qty} ${s.name}", s.printing) }
                }
            }
            Spacer(Modifier.height(Space.s3))
            if (u.busy) {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { com.mtgscanner.ui.components.Spinner() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    TextButton(onClick = host::cancel) { Text("Cancel") }
                    GradientButton("Save", host::confirm, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
}
