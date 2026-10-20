package com.mtgscanner.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Style
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.data.SortPrefs
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette

/** The modes button over the camera: sort a pile, open a pack, scan a binder page, lock to a set. */
@Composable
fun ModesButton(state: ScanUiState, vm: ScanViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            val active = state.pack != null || state.pageMode || state.sort != null || state.stack || state.target != ScanTarget.COLLECTION
            Icon(Icons.Filled.ViewModule, "Scan modes", tint = if (active) Palette.Gold else Color.White, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { if (state.stack) ModeText("Stop stack mode", "Back to normal") else ModeText("Stack mode", "Hands-free, beeps on each card") },
                leadingIcon = { Icon(Icons.Filled.Layers, null, tint = if (state.stack) Palette.Gold else Palette.Text) },
                onClick = { open = false; vm.setStack(!state.stack) },
            )
            if (state.target == ScanTarget.DECK) DropdownMenuItem(
                text = { ModeText("Stop deck: ${state.deck?.name.orEmpty()}", "Add to the collection again") },
                leadingIcon = { Icon(Icons.Filled.Style, null, tint = Palette.Gold) },
                onClick = { open = false; vm.stopTarget() },
            ) else DropdownMenuItem(
                text = { ModeText("Scan to a deck", "Add to a deck list") },
                leadingIcon = { Icon(Icons.Filled.Style, null) },
                onClick = { open = false; vm.showDeckPick(true) },
            )
            if (state.target == ScanTarget.CHECK) DropdownMenuItem(
                text = { ModeText("Stop price check", "Add cards again") },
                leadingIcon = { Icon(Icons.Filled.Sell, null, tint = Palette.Gold) },
                onClick = { open = false; vm.stopTarget() },
            ) else DropdownMenuItem(
                text = { ModeText("Price check", "Value and owned, no adding") },
                leadingIcon = { Icon(Icons.Filled.Sell, null) },
                onClick = { open = false; vm.startCheck() },
            )
            if (state.sort == null) DropdownMenuItem(
                text = { ModeText("Sort a pile", "Scan all, then file by box") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
                onClick = { open = false; vm.startSort() },
            ) else {
                DropdownMenuItem(
                    text = { ModeText("Sort now", "See stacks by box") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, tint = Palette.Accent) },
                    onClick = { open = false; vm.showSort(true) },
                )
                DropdownMenuItem(
                    text = { ModeText("Stop sorting", "Cards stay in their boxes") },
                    leadingIcon = { Icon(Icons.Filled.Close, null) },
                    onClick = { open = false; vm.endSort() },
                )
            }
            if (state.pack == null) DropdownMenuItem(
                text = { ModeText("Open a pack", "Running total as you scan") },
                leadingIcon = { Icon(Icons.Filled.Inventory2, null) },
                onClick = { open = false; vm.showPackStart(true) },
            ) else {
                DropdownMenuItem(
                    text = { ModeText("Finish pack", "Cards stay added") },
                    leadingIcon = { Icon(Icons.Filled.CheckCircle, null, tint = Palette.Accent) },
                    onClick = { open = false; vm.finishPack() },
                )
                DropdownMenuItem(
                    text = { ModeText("Stop pack mode", "Cards stay added") },
                    leadingIcon = { Icon(Icons.Filled.Close, null) },
                    onClick = { open = false; vm.cancelPack() },
                )
            }
            DropdownMenuItem(
                text = {
                    if (state.pageMode) ModeText("Back to single cards", "Scan one card at a time")
                    else ModeText("Binder page", "Read a 9-pocket page in one go")
                },
                leadingIcon = { Icon(Icons.Filled.GridOn, null) },
                onClick = { open = false; vm.setPageMode(!state.pageMode) },
            )
            DropdownMenuItem(
                text = {
                    val lock = SortPrefs.setLock
                    if (lock != null) ModeText("Set: ${lock.uppercase()}", "Change or unlock")
                    else ModeText("Lock to a set", "Faster reads for one set")
                },
                leadingIcon = { Icon(Icons.Filled.Lock, null, tint = if (SortPrefs.setLock != null) Palette.Gold else Palette.Text) },
                onClick = { open = false; vm.showSetLock(true) },
            )
        }
    }
}

/** Picks the deck list that scanned cards go to, or makes a new one. */
@Composable
fun DeckPickDialog(vm: ScanViewModel) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.showDeckPick(false) },
        containerColor = Palette.Surface,
        title = { Text("Scan to a deck") },
        text = {
            Column {
                if (decks.isEmpty()) {
                    Text("No decks yet. Name one:", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                } else {
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        decks.forEach { d ->
                            Text(
                                d.name.ifBlank { "Untitled deck" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { vm.scanToDeck(d) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("New deck") })
            }
        },
        confirmButton = { TextButton(onClick = { vm.newDeck(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = { vm.showDeckPick(false) }) { Text("Cancel") } },
    )
}

@Composable
private fun ModeText(title: String, body: String) = Column {
    Text(title)
    Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
}

/** Shows the pack being opened and its running total, with a Finish button. */
@Composable
fun PackChip(state: ScanUiState, vm: ScanViewModel, modifier: Modifier = Modifier) {
    val p = state.pack ?: return
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = 0.75f)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(p.name, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${state.packEntries.sumOf { it.count }} cards" + priceSuffix(state.packValue) +
                        (p.cost?.takeIf { it > 0 && AppSettings.showPrices }?.let { " / " + com.mtgscanner.ui.components.formatUsd(it) } ?: ""),
                    color = Palette.Gold, style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = vm::finishPack) { Text("Finish", color = Palette.Accent) }
        }
    }
}

@Composable
fun PackStartDialog(vm: ScanViewModel) {
    var name by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.showPackStart(false) },
        containerColor = Palette.Surface,
        title = { Text("Open a pack") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Pack") }, placeholder = { Text("e.g. Bloomburrow play booster") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    cost, { cost = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true,
                    label = { Text("Paid (optional)") }, prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.startPack(name.trim(), cost.toDoubleOrNull()) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = { vm.showPackStart(false) }) { Text("Cancel") } },
    )
}

/** A 3x3 grid to line the binder page up with. */
@Composable
fun PageGuide(modifier: Modifier) {
    Canvas(modifier.fillMaxWidth(0.92f).aspectRatio(3 * 63f / (3 * 88f))) {
        val w = size.width / 3
        val h = size.height / 3
        val gap = 4.dp.toPx()
        for (r in 0 until 3) for (c in 0 until 3) {
            drawRoundRect(
                Color.White.copy(alpha = 0.6f),
                topLeft = Offset(c * w + gap, r * h + gap),
                size = Size(w - gap * 2, h - gap * 2),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

/** The capture button and status shown at the bottom in binder page mode. */
@Composable
fun PageControls(state: ScanUiState, vm: ScanViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            state.status, color = Color.White, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(10.dp))
        if (state.wantPage || state.pageLoading) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Palette.Accent)
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = vm::cancelCapture) { Text("Cancel", color = Color.White) }
        }
        else GradientButton("Capture page", vm::capturePage, Modifier.fillMaxWidth(), icon = Icons.Filled.GridOn)
    }
}

/** What was read from each pocket. Tick the ones to add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageSheet(state: ScanUiState, vm: ScanViewModel) {
    val slots = state.page ?: return
    ModalBottomSheet(onDismissRequest = vm::closePage, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            val picked = slots.filter { it.selected }
            Text("Binder page", style = MaterialTheme.typography.titleLarge)
            Text(
                "Found ${slots.count { it.card != null }} of ${slots.size}",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxHeight(0.75f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(slots, key = { it.index }) { s ->
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.SurfaceHigh)
                            .clickable(enabled = s.card != null) { vm.togglePageSlot(s.index) }
                            .aspectRatio(63f / 88f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val card = s.card
                        if (card != null) {
                            CardImage(card.smallImageUrl ?: card.imageUrl, Modifier.fillMaxWidth().alpha(if (s.selected) 1f else 0.35f), corner = 8.dp)
                            if (s.selected) Icon(Icons.Filled.CheckCircle, null, tint = Palette.Accent, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                        } else Text(
                            s.read?.let { "Couldn't find\n$it" } ?: "Empty or unreadable",
                            style = MaterialTheme.typography.labelSmall, color = Palette.Muted, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::closePage) { Text("Try again") }
                Spacer(Modifier.width(8.dp))
                GradientButton(
                    "Add ${picked.size}" + priceSuffix(picked.sumOf { it.card?.prices?.usd?.toDoubleOrNull() ?: 0.0 }),
                    vm::addPage, Modifier.weight(1f), enabled = picked.isNotEmpty(),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
