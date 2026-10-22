package com.mtgscanner.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.data.db.PendingScan
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.UnderlineTabs
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.home.UnknownScanDialog
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

/** Cards the skip rules skipped this session, each with "Add anyway". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkippedSheet(state: ScanUiState, vm: ScanViewModel) {
    ModalBottomSheet(onDismissRequest = { vm.showSkipped(false) }, containerColor = Palette.Surface) {
        Text("Skipped", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
            if (state.skippedCards.isEmpty()) item(key = "empty") { EmptyState("Nothing skipped", Modifier.padding(vertical = 24.dp)) }
            items(state.skippedCards, key = { it.key }) { s ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardImage(s.card.smallImageUrl, Modifier.width(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${s.card.set.uppercase()} · ${s.reason}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { vm.addSkipped(s) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add anyway") }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

/** Scans saved because no card matched. Tap one to search for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownSheet(vm: ScanViewModel) {
    val scans by vm.unknownScans.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<PendingScan?>(null) }
    ModalBottomSheet(onDismissRequest = { vm.showUnknown(false) }, containerColor = Palette.Surface) {
        Text("Unknown scans", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
            if (scans.isEmpty()) item(key = "empty") { EmptyState("None left", Modifier.padding(vertical = 24.dp)) }
            items(scans, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = p }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val extra = listOfNotNull(
                        p.quantity.takeIf { it > 1 }?.let { "×$it" }, p.lang?.uppercase(), "Foil".takeIf { p.foil },
                    ).joinToString(" · ")
                    if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
    open?.let { scan ->
        UnknownScanDialog(
            scan = scan,
            search = { vm.searchCards(it) },
            onPick = { card -> vm.resolveUnknown(scan, card); open = null },
            onDelete = { vm.deleteUnknown(scan); open = null },
            onDismiss = { open = null },
        )
    }
}

/** Picks the box to check. */
@Composable
fun BoxPickDialog(boxes: List<CardBox>, vm: ScanViewModel) {
    AlertDialog(
        onDismissRequest = { vm.showBoxPick(false) },
        containerColor = Palette.Surface,
        title = { Text("Check a box") },
        text = {
            if (boxes.isEmpty()) Text("No boxes yet.", color = Palette.Muted)
            else Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                boxes.forEach { b ->
                    Text(
                        b.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = boxColor(b.color),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable { vm.startBoxCheck(b) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.showBoxPick(false) }) { Text("Cancel") } },
    )
}

/** The box being checked, how many scanned, and a button to see the result. */
@Composable
fun BoxCheckChip(state: ScanUiState, vm: ScanViewModel, modifier: Modifier = Modifier) {
    val run = state.boxCheck ?: return
    Surface(modifier, shape = RoundedCornerShape(Corners.Medium), color = Color.Black.copy(alpha = 0.75f)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(run.boxName, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${run.scans.sumOf { it.count }} scanned", color = Palette.Gold, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { vm.showBoxCheck(true) }) { Text("Result", color = Palette.Accent) }
        }
    }
}

/** Scan to remove: how many came out, and a way back to adding. */
@Composable
fun RemoveChip(state: ScanUiState, vm: ScanViewModel, modifier: Modifier = Modifier) {
    if (state.target != ScanTarget.REMOVE) return
    Surface(modifier, shape = RoundedCornerShape(Corners.Medium), color = Color.Black.copy(alpha = 0.75f)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Removing", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Text("${state.removedCount} out", color = Palette.Gold, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = vm::stopTarget) { Text("Done", color = Palette.Accent) }
        }
    }
}

private enum class CheckTab { MISSING, NOT_RECORDED, FOUND }

/** Box check result: found, missing (in the app, not scanned) and not recorded (scanned, not in that box). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxCheckSheet(state: ScanUiState, vm: ScanViewModel, onOpenCard: (Long) -> Unit) {
    val run = state.boxCheck ?: return
    val result by vm.boxResult.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(CheckTab.MISSING) }
    var ending by remember { mutableStateOf(false) }
    val cards = remember(run.scans) { run.scans.associateBy { it.card.id } }
    ModalBottomSheet(onDismissRequest = { vm.showBoxCheck(false) }, containerColor = Palette.Surface) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(run.boxName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${run.scans.sumOf { it.count }} scanned", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            TextButton(onClick = { ending = true }) { Text("End") }
        }
        val r = result
        UnderlineTabs(
            listOf("Missing ${r?.missingCount ?: 0}", "Not recorded ${r?.notRecordedCount ?: 0}", "Found ${r?.foundCount ?: 0}"),
            tab.ordinal, { tab = CheckTab.entries[it] }, Modifier.padding(bottom = 4.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
            if (r == null) {
                item(key = "loading") { Text("…", color = Palette.Muted, modifier = Modifier.padding(20.dp)) }
            } else when (tab) {
                CheckTab.MISSING -> {
                    if (r.missing.isEmpty()) item(key = "e1") { EmptyState("Nothing missing", Modifier.padding(vertical = 24.dp)) }
                    items(r.missing, key = { "m${it.first.rowId}" }) { (row, n) ->
                        CheckRow(row.name + if (n > 1) " ×$n" else "", if (row.foil) "Foil" else null, null, onClick = { onOpenCard(row.rowId) }) {
                            TextButton(onClick = { vm.tickBoxRow(row.rowId, n) }) { Text("Found") }
                            TextButton(onClick = { vm.removeMissing(row, n) }) { Text("Remove", color = Palette.Danger) }
                        }
                    }
                }
                CheckTab.NOT_RECORDED -> {
                    if (r.notRecorded.isEmpty()) item(key = "e2") { EmptyState("All recorded", Modifier.padding(vertical = 24.dp)) }
                    items(r.notRecorded, key = { "n${it.first.scryfallId}${it.first.foil}" }) { (scan, n) ->
                        val card = cards[scan.scryfallId]?.card
                        CheckRow(
                            scan.name + if (n > 1) " ×$n" else "", if (scan.foil) "Foil" else null, card?.smallImageUrl,
                            sub = card?.let { "${it.set.uppercase()} · #${it.collectorNumber}" },
                        ) {
                            TextButton(onClick = { vm.fixNotRecorded(scan, n) }) { Text("Put here") }
                        }
                    }
                }
                CheckTab.FOUND -> {
                    if (r.found.isEmpty()) item(key = "e3") { EmptyState("Nothing found yet", Modifier.padding(vertical = 24.dp)) }
                    items(r.found, key = { "f${it.first.rowId}" }) { (row, n) ->
                        val ticked = run.ticked[row.rowId] ?: 0
                        CheckRow(row.name + if (n > 1) " ×$n" else "", if (row.foil) "Foil" else null, null, onClick = { onOpenCard(row.rowId) }) {
                            if (ticked > 0) TextButton(onClick = { vm.tickBoxRow(row.rowId, -ticked) }) { Text("Untick", color = Palette.Muted) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
    if (ending) AlertDialog(
        onDismissRequest = { ending = false },
        containerColor = Palette.Surface,
        title = { Text("End the box check?") },
        text = { Text("The scanned list is cleared.", color = Palette.Muted) },
        confirmButton = { TextButton(onClick = { ending = false; vm.endBoxCheck() }) { Text("End") } },
        dismissButton = { TextButton(onClick = { ending = false }) { Text("Keep") } },
    )
}

@Composable
private fun CheckRow(
    title: String, tag: String?, imageUrl: String?, sub: String? = null, onClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (imageUrl != null) {
            CardImage(imageUrl, Modifier.width(36.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (tag != null) {
                    Spacer(Modifier.width(6.dp))
                    FoilTag()
                }
            }
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        actions()
    }
}
