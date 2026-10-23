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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.CheapInValue
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.SortPrefs
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.SpreadCopies
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.facts
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.PlaceholderRows
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.home.SortItem
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A card spread over boxes, and its rows as moves to the box with the most copies. */
data class SpreadRow(val group: SpreadCopies.Group, val items: List<SortItem>)

/**
 * Copies of a card in two or more main boxes (not trade binders, decks, or the extras and bulk boxes). Pinned
 * rows stay. A row only moves when it fits the target's rules, and never from a rule box into a catch-all box,
 * so it doesn't come straight back as "to sort".
 */
internal suspend fun CollectionRepository.spreadCopies(byPrinting: Boolean): List<SpreadRow> {
    val infos = boxInfos()
    val all = allCards()
    return withContext(Dispatchers.Default) {
        val policy = SortPrefs.policy
        val slack = SortPrefs.priceBuffer / 100.0
        val main = infos.filter { Sorting.isMainBox(it, policy) }
        val rules = main.associate { it.id to it.rules }
        val cards = all.filter { !it.pinned }
        val byId = cards.associateBy { it.id }
        val groups = SpreadCopies.find(
            cards.map { SpreadCopies.Row(it.id, it.name, it.setCode, it.collectorNumber, it.boxId, it.quantity) },
            main.map { it.id },
            byPrinting,
            fits = { r, to ->
                val target = rules[to]
                val card = byId[r.id]
                target != null && card != null && target.matches(card.facts(), slack) && (!target.isOpen || rules[r.boxId]?.isOpen == true)
            },
        )
        groups.map { g -> SpreadRow(g, g.moves.mapNotNull { (id, to) -> byId[id]?.let { SortItem(it, to, null) } }) }
    }
}

/** Cards under the bulk floor still in a value box, each with the box Auto-sort picks now. */
internal suspend fun CollectionRepository.cheapInValueBoxes(): List<SortItem> {
    val infos = boxInfos()
    val all = allCards()
    return withContext(Dispatchers.Default) {
        CheapInValue.find(all, { it.boxId }, { it.facts() }, infos, SortPrefs.policy, { it.pinned })
            .map { SortItem(it.item, it.to, null) }
            .sortedBy { Sorting.nameKey(it.card.name) }
    }
}

/** "Rares 3 · Blue 1". */
private fun perBoxText(g: SpreadCopies.Group, names: Map<Long, String>): String =
    g.perBox.joinToString(" · ") { (id, n) -> "${names[id] ?: "?"} $n" }

/** Spread-out copies: pick which to gather, then one move list for all of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpreadCopiesSheet(vm: CollectionViewModel, boxes: List<BoxSummary>, onDismiss: () -> Unit) {
    var byPrinting by rememberSaveable { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(setOf<String>()) }
    val names = remember(boxes) { boxes.associate { it.id to it.name } }
    val state = produceState<List<SpreadRow>?>(null, byPrinting, boxes) { value = vm.spread(byPrinting) }
    val list = state.value
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text("Spread-out copies", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SelectPill("Same printing", byPrinting, { byPrinting = !byPrinting })
            }
            when {
                list == null -> PlaceholderRows(5, Modifier.padding(16.dp), color = Palette.SurfaceHigh)
                list.isEmpty() -> EmptyState("Nothing spread out", Modifier.padding(vertical = 24.dp))
                else -> {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                        items(list, key = { it.group.key }) { r ->
                            val g = r.group
                            val on = g.key !in skipped
                            val toggle = { skipped = if (on) skipped + g.key else skipped - g.key }
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = toggle).padding(start = 8.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(on, { toggle() }, colors = CheckboxDefaults.colors(checkedColor = Palette.Accent))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        g.name + (g.setCode?.let { " · ${it.uppercase()}" } ?: ""),
                                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(perBoxText(g, names), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("→ ${names[g.target] ?: "?"}", style = MaterialTheme.typography.labelLarge, color = Palette.Text, maxLines = 1)
                            }
                        }
                    }
                    val chosen = list.filter { it.group.key !in skipped }
                    val n = chosen.sumOf { it.group.toMove }
                    GradientButton(
                        if (n == 0) "Make move list" else "Make move list · $n",
                        { vm.sortCards(chosen.flatMap { it.items }); onDismiss() },
                        Modifier.fillMaxWidth().padding(16.dp),
                        enabled = n > 0,
                    )
                }
            }
        }
    }
}

/** Cards under the bulk floor still in a value box, with one move list to where Auto-sort puts them now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheapInValueSheet(vm: CollectionViewModel, boxes: List<BoxSummary>, onOpenCard: (Long) -> Unit, onDismiss: () -> Unit) {
    val names = remember(boxes) { boxes.associate { it.id to it.name } }
    val floor = SortPrefs.bulkFloor
    val state = produceState<List<SortItem>?>(null, boxes, floor) { value = vm.cheapInValue() }
    val list = state.value
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text("Cheap in value boxes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            if (floor != null) Text(
                "Under ${formatUsd(floor)}",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 20.dp),
            )
            when {
                floor == null || SortPrefs.bulkBoxId == null -> EmptyState("No bulk floor set", Modifier.padding(vertical = 24.dp), body = "Set one in Auto-sort order")
                list == null -> PlaceholderRows(5, Modifier.padding(16.dp), color = Palette.SurfaceHigh)
                list.isEmpty() -> EmptyState("None found", Modifier.padding(vertical = 24.dp))
                else -> {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                        items(list, key = { it.card.id }) { s ->
                            val c = s.card
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenCard(c.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (if (c.quantity > 1) "${c.quantity}× " else "") + c.name,
                                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${names[c.boxId] ?: "?"} → ${names[s.to] ?: "?"}",
                                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (AppSettings.showPrices) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(formatUsd(c.unitPrice()), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.Text)
                                }
                            }
                        }
                    }
                    val n = list.sumOf { it.card.quantity }
                    GradientButton(
                        "Make move list · $n",
                        { vm.sortCards(list); onDismiss() },
                        Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}
