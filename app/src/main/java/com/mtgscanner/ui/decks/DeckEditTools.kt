package com.mtgscanner.ui.decks

import androidx.compose.material.icons.outlined.BookmarkBorder

import androidx.compose.material.icons.filled.Bookmark

import androidx.compose.material.icons.outlined.MoreVert

import androidx.compose.material3.DropdownMenuItem

import androidx.compose.material3.DropdownMenu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.Combo
import com.mtgscanner.data.DeckCommanders
import com.mtgscanner.data.DeckFormats
import com.mtgscanner.data.DeckIdeasMath
import com.mtgscanner.data.EdhrecRec
import com.mtgscanner.scan.DeckText
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.panelRow
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.util.DecklistParser

/** What edit mode shows under the deck's name, format and commanders. */
enum class EditTab(val label: String) { CARDS("Cards"), LIST("List"), COMBOS("Combos"), EDHREC("EDHREC") }

/** The tabs this deck has: EDHREC only for a commander deck with its commander picked. */
fun editTabs(format: String, commanders: DeckCommanders): List<EditTab> =
    EditTab.entries.filter { it != EditTab.EDHREC || (DeckFormats.isCommander(format) && commanders.commanders.isNotEmpty()) }

/** Steppers on one row: − count +. */
@Composable
internal fun Stepper(count: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { com.mtgscanner.util.Haptics.tick(); onChange(-1) }, enabled = count > 0) {
            Icon(Icons.Outlined.Remove, "One fewer", tint = if (count > 0) Palette.Icon else Palette.Faint, modifier = Modifier.size(20.dp))
        }
        Text("$count", style = MaterialTheme.typography.bodyLarge.merge(TabularNums), modifier = Modifier.width(Space.s6))
        IconButton(onClick = { com.mtgscanner.util.Haptics.tick(); onChange(1) }) { Icon(Icons.Outlined.Add, "One more", tint = Palette.Icon, modifier = Modifier.size(20.dp)) }
    }
}

/**
 * Edit › Cards: the main deck grouped by card type, each row with a picture, the printing (tap to change it) and
 * − / + for the count. The commanders have their own slots above, so they're left out. A Find box narrows the list.
 * Changes go into the list and are saved with it.
 */
@Composable
fun DeckCardsTab(ui: DeckUi, vm: DeckViewModel, onPaste: () -> Unit) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var board by rememberSaveable { mutableStateOf("Main") }
    val leads = remember(ui.commanders) { ui.commanders.all.map { DeckNames.key(it) }.toSet() }
    val parsed = remember(ui.text) { DecklistParser.parse(ui.text) }
    val counts = Board.entries.associateWith { b -> parsed.filter { it.section == b.section && (b != Board.MAIN || DeckNames.key(it.name) !in leads) }.sumOf { it.quantity } }
    // The deck, the sideboard and the maybeboard (cards you're considering: not counted, not played in playtest).
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        Board.entries.forEach { b ->
            val n = counts[b] ?: 0
            SelectPill(if (n > 0) "${b.label} $n" else b.label, board == b.section, { board = b.section })
        }
    }
    val lines = parsed.filter { it.section == board && (board != "Main" || DeckNames.key(it.name) !in leads) }
    DeckQuickAdd(ui, vm, board, searching) { searching = it }
    if (lines.isEmpty() && board == "Main") {
        // A blank deck: the ways to fill it, so the page starts with something to do.
        Text("Start the deck", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(top = Space.s3, bottom = Space.s2))
        StartRow(Icons.Outlined.Search, "Search all cards", "By name, type, format and commander colors", first = true, last = false) { searching = true }
        StartRow(Icons.Outlined.ContentPaste, "Paste a list", "From Moxfield, Archidekt or any text list", first = false, last = true, onClick = onPaste)
        Text(
            "Or scan cards into it: pick this deck in the Scan screen's add-to menu",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = Space.s3),
        )
        return
    }
    if (lines.isEmpty()) {
        Text(
            when (board) {
                "Main" -> "No cards yet: add them by name above, search for them, or scan them into this deck"
                "Sideboard" -> "No sideboard cards yet: add them above, or move one here from the deck with its menu"
                else -> "Nothing you're considering yet: add cards above, or move one here from the deck"
            },
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(vertical = Space.s4),
        )
        return
    }
    // A long list gets its own filter; a short one is easy to scan by eye, and one search box is enough.
    if (lines.size > 15 || query.isNotEmpty()) SearchField(query, { query = it }, Modifier.padding(bottom = Space.s2), placeholder = "Find in deck")
    val q = query.trim()
    val shown = if (q.isEmpty()) lines else lines.filter { it.name.contains(q, ignoreCase = true) }
    // The total is in the header; a filter says how many it kept.
    if (q.isNotEmpty()) Text(
        "${shown.size} of ${lines.size} cards",
        style = MaterialTheme.typography.labelLarge.merge(TabularNums), color = Palette.Muted, modifier = Modifier.padding(bottom = Space.s1),
    )
    // Each card type is one panel under a small heading with its count on the right.
    DeckTypes.group(shown) { ui.cardInfo[DeckNames.key(it.name)]?.typeLine }.forEach { (type, group) ->
        val n = group.sumOf { it.quantity }
        Row(Modifier.fillMaxWidth().padding(top = Space.s3, bottom = Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Text(if (n == 1) type.one else type.many, style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
            Text("$n", style = MaterialTheme.typography.labelLarge.merge(TabularNums), color = Palette.Muted)
        }
        group.forEachIndexed { i, l -> EditCardRow(ui, vm, l, board, first = i == 0, last = i == group.lastIndex) }
    }
}

/** One card in Edit › Cards: its picture, name, mana cost and printing (tap to change it), − count +, and ⋮ to move it. */
@Composable
private fun EditCardRow(ui: DeckUi, vm: DeckViewModel, l: com.mtgscanner.util.DeckLine, board: String, first: Boolean, last: Boolean) {
    val info = ui.cardInfo[DeckNames.key(l.name)]
    Row(
        Modifier.fillMaxWidth().panelRow(first, last).heightIn(min = 56.dp).padding(start = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(info?.imageUrl, Modifier.width(32.dp), zoom = info?.imageUrl != null)
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f).clickable { vm.openPrintingsFor(l) }.padding(vertical = Space.s2)) {
            Text(l.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                info?.manaCost?.takeIf { it.isNotBlank() }?.let { com.mtgscanner.ui.components.ManaCost(it, 14.dp) }
                Text(
                    l.setCode?.let { s -> s.uppercase() + (l.collectorNumber?.let { " #$it" } ?: "") } ?: "Any printing",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Stepper(l.quantity) { d -> vm.adjustCard(l.name, d, board, l.setCode, l.collectorNumber) }
        MoveMenu(board) { to -> com.mtgscanner.util.Haptics.tick(); vm.moveCard(l.name, board, to) }
    }
}

/** One way to start a blank deck: an icon, what it does and a line on how, in one panel with the others. */
@Composable
private fun StartRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, first: Boolean, last: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().panelRow(first, last).clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = Space.s3, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.Icon, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The parts of a list a card can be in while editing. */
enum class Board(val section: String, val label: String) {
    MAIN("Main", "Deck"), SIDE("Sideboard", "Sideboard"), MAYBE("Maybeboard", "Maybeboard"),
}

/** A card row's ⋮: move it to one of the other boards. */
@Composable
private fun MoveMenu(from: String, onMove: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "Move card", tint = Palette.Icon, modifier = Modifier.size(20.dp)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Board.entries.filter { it.section != from }.forEach { b ->
                DropdownMenuItem(
                    text = { Text(if (b == Board.MAIN) "Move to the deck" else "Move to the ${b.label.lowercase()}") },
                    onClick = { open = false; onMove(b.section) },
                )
            }
        }
    }
}

/** A load in progress, or why it failed with a way to try again. */
@Composable
internal fun LoadState(loading: Boolean, failed: Boolean, failText: String, onRetry: () -> Unit) {
    if (loading) Box(Modifier.fillMaxWidth().padding(vertical = Space.s6), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Palette.Text, modifier = Modifier.size(Space.s6))
    } else if (failed) Row(Modifier.fillMaxWidth().padding(vertical = Space.s4), verticalAlignment = Alignment.CenterVertically) {
        Text(failText, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

/**
 * Edit › Combos, from Commander Spellbook: the combos the list already has, then the ones a single card away with
 * a button to add that card.
 */
@Composable
fun CombosTab(ui: DeckUi, vm: DeckViewModel) {
    LaunchedEffect(ui.text, ui.commanders, ui.format) { vm.loadCombos() }
    val c = ui.combos
    val ctx = LocalContext.current
    LoadState(c.loading && c.value == null, c.failed, "Couldn't reach Commander Spellbook") { vm.loadCombos(force = true) }
    val found = c.value ?: return
    Text(
        "From Commander Spellbook", style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
        modifier = Modifier.padding(top = Space.s2),
    )
    ComboGroup("In the deck", found.included, emptyText = "No combos in the list yet", onAdd = null) { openLink(ctx, DeckIdeasMath.comboUrl(it.id)) }
    ComboGroup("One card away", found.almost.take(30), emptyText = "Nothing a single card away", onAdd = { vm.adjustCard(it, 1) }) {
        openLink(ctx, DeckIdeasMath.comboUrl(it.id))
    }
}

@Composable
private fun ComboGroup(title: String, combos: List<Combo>, emptyText: String, onAdd: ((String) -> Unit)?, onOpen: (Combo) -> Unit) {
    Text(
        "$title (${combos.size})", style = MaterialTheme.typography.titleMedium, color = Palette.Muted,
        modifier = Modifier.padding(top = Space.s4, bottom = Space.s1),
    )
    if (combos.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
    combos.forEachIndexed { i, combo ->
        if (i > 0) HorizontalDivider(color = Palette.Rule)
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // The combo's cards side by side; hold one to see it big.
                if (combo.images.isNotEmpty()) Row(Modifier.padding(bottom = Space.s1), horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
                    combo.cards.take(5).forEach { n ->
                        CardImage(combo.images[n], Modifier.width(44.dp), zoom = combo.images[n] != null, grey = n in combo.missing)
                    }
                }
                Text(combo.cards.joinToString(" + "), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (combo.produces.isNotEmpty()) Text(
                    combo.produces.take(2).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (onAdd != null) combo.missing.forEach { name ->
                    TextButton(onClick = { com.mtgscanner.util.Haptics.tick(); onAdd(name) }) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s1))
                        Text("Add $name", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            IconButton(onClick = { onOpen(combo) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open on Commander Spellbook", tint = Palette.Icon, modifier = Modifier.size(18.dp))
            }
        }
    }
}
