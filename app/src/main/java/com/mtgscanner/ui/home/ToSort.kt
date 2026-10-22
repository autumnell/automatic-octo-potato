package com.mtgscanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.Divider
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.SortPrefs
import com.mtgscanner.data.SortReason
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PendingScan
import com.mtgscanner.data.db.boxRules
import com.mtgscanner.data.db.facts
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.detail.sharedCard
import com.mtgscanner.ui.components.PlaceholderRows

/** A card that should go to another box, and the divider inside it when the box has dividers. */
data class SortItem(val card: CollectionCard, val to: Long, val spot: String?)

/** What's waiting to be sorted: cards to move, and scans the app couldn't read. */
data class ToSortUi(
    val items: List<SortItem> = emptyList(),
    val unknown: List<PendingScan> = emptyList(),
    val boxNames: Map<Long, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = items.isEmpty() && unknown.isEmpty()
}

/**
 * Cards waiting to be sorted: ones the box rules say belong elsewhere ([CollectionRepository.misplaced]),
 * plus cards sitting in a catch-all box (not a trade binder or deck) that the sorting rules would
 * send to a real box. Uses the same choice as Auto-sort ([Sorting.decide], as pickDecision does);
 * the card's own copies aren't counted toward the keep-a-playset limit.
 */
internal suspend fun CollectionRepository.cardsToSort(cards: List<CollectionCard>, boxes: List<Box>): List<SortItem> {
    if (boxes.size < 2 || cards.isEmpty()) return emptyList()
    val byId = boxes.associateBy { it.id }
    val moves = LinkedHashMap<Long, Long>()
    misplaced().forEach { moves[it.item.id] = it.to }
    val infos = boxInfos()
    val policy = SortPrefs.policy
    val catchAll = boxes.filter {
        it.kind != "trade" && it.kind != "deck" && it.boxRules().isCatchAll && it.id != policy.extrasBoxId && it.id != policy.bulkBoxId
    }.map { it.id }.toSet()
    val notMain = infos.filterNot { Sorting.isMainBox(it, policy) }.map { it.id }.toSet()
    val inMain = HashMap<String, Int>()
    for (c in cards) {
        val o = c.oracleId ?: continue
        // Copies kept in a deck aren't in any box (see DeckSlots.kt), so they don't count.
        if (c.boxId !in notMain && c.boxId in byId) inMain[o] = (inMain[o] ?: 0) + c.quantity
    }
    for (c in cards) {
        if (c.boxId !in catchAll || c.id in moves) continue
        val main = c.oracleId?.let { inMain[it] } ?: 0
        val others = if (c.boxId in notMain) main else main - c.quantity
        val d = Sorting.decide(infos, c.facts(), policy, others.coerceAtLeast(0)) ?: continue
        if (d.boxId == c.boxId || d.reason == SortReason.FALLBACK) continue
        moves[c.id] = d.boxId
    }
    val cardById = cards.associateBy { it.id }
    return moves.mapNotNull { (id, to) ->
        val c = cardById[id] ?: return@mapNotNull null
        SortItem(c, to, byId[to]?.let { Sorting.spot(Divider.of(it.divider), c.name, c.facts()) })
    }.sortedBy { it.card.name }
}

/** Home section: counts, the first few cards with where they go, a Sort button, and unknown scans. */
@Composable
internal fun ToSortSection(
    ui: ToSortUi,
    onSort: () -> Unit,
    onOpenCard: (Long) -> Unit,
    onUnknown: (PendingScan) -> Unit,
    /** Cards whose picture here grows into the card page. */
    shared: Set<Long> = emptySet(),
) {
    Column(Modifier.fillMaxWidth()) {
        if (ui.items.isNotEmpty()) {
            val copies = ui.items.sumOf { it.card.quantity }
            Text(
                if (copies == 1) "1 card to move" else "$copies cards to move",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ui.items.take(4).forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenCard(item.card.id) }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CardImage(
                        item.card.smallImageUrl ?: item.card.imageUrl,
                        Modifier.width(28.dp).then(if (item.card.id in shared) Modifier.sharedCard(item.card.id) else Modifier),
                        zoom = true,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        (if (item.card.quantity > 1) "${item.card.quantity}× " else "") + item.card.name,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Palette.Icon, modifier = Modifier.size(14.dp).padding(horizontal = 2.dp))
                    Text(
                        listOfNotNull(ui.boxNames[item.to], item.spot).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = Palette.Text, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.8f).padding(start = 4.dp),
                    )
                }
            }
            if (ui.items.size > 4) {
                Text("+ ${ui.items.size - 4} more", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 16.dp))
            }
            GradientButton("Sort", onSort, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (ui.items.isNotEmpty() && ui.unknown.isNotEmpty()) HorizontalDivider(color = Palette.Outline)
        if (ui.unknown.isNotEmpty()) {
            Text(
                if (ui.unknown.size == 1) "1 unknown scan" else "${ui.unknown.size} unknown scans",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ui.unknown.take(5).forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onUnknown(p) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.QuestionMark, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("\"${p.name}\"", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.Search, "Find", tint = Palette.Icon, modifier = Modifier.size(18.dp))
                }
            }
            if (ui.unknown.size > 5) {
                Text("+ ${ui.unknown.size - 5} more", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }
}

/** Search Scryfall for an unknown scan by name; picking a result adds it and clears the scan. */
@Composable
internal fun UnknownScanDialog(
    scan: PendingScan,
    /** Null when Scryfall couldn't be reached. */
    search: suspend (String) -> List<ScryfallCard>?,
    onPick: (ScryfallCard) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(scan.name) }
    var results by remember { mutableStateOf<List<ScryfallCard>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var tries by remember { mutableStateOf(0) }
    LaunchedEffect(query, tries) {
        loading = false
        if (query.isBlank()) { results = emptyList(); offline = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        loading = true
        // A newer query cancels this one; don't leave the spinner on.
        try {
            val got = search(query)
            offline = got == null
            results = got.orEmpty()
        } finally { loading = false }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Find card") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    val r = results
                    when {
                        loading && r == null -> PlaceholderRows(3, rowHeight = 40.dp, color = Palette.SurfaceHigh)
                        offline && !loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Offline", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { tries++ }) { Text("Retry") }
                        }
                        r != null && r.isEmpty() && !loading -> Text("No match", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
                        r != null -> r.forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CardImage(c.smallImageUrl, Modifier.width(32.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${c.set.uppercase()} #${c.collectorNumber}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete scan", tint = Palette.Danger) }
        },
    )
}
