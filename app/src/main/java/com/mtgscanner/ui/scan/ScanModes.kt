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
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.home.ChangeText
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette

/** The modes button over the camera: open a pack, or scan a binder page. */
@Composable
fun ModesButton(state: ScanUiState, vm: ScanViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ViewModule, "Scan modes", tint = if (state.pack != null || state.pageMode) Palette.Gold else Color.White, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.pack == null) DropdownMenuItem(
                text = { ModeText("Open a pack", "Scan what you pulled and see what it was worth") },
                leadingIcon = { Icon(Icons.Filled.Inventory2, null) },
                onClick = { open = false; vm.showPackStart(true) },
            ) else {
                DropdownMenuItem(
                    text = { ModeText("Finish pack", "Save it to your pack history") },
                    leadingIcon = { Icon(Icons.Filled.CheckCircle, null, tint = Palette.Accent) },
                    onClick = { open = false; vm.finishPack() },
                )
                DropdownMenuItem(
                    text = { ModeText("Stop pack mode", "Cards you added stay in your collection") },
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
        }
    }
}

@Composable
private fun ModeText(title: String, body: String) = Column {
    Text(title)
    Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
}

/** Shows the pack being opened and its running value, with a Finish button. */
@Composable
fun PackChip(state: ScanUiState, vm: ScanViewModel, modifier: Modifier = Modifier) {
    val p = state.pack ?: return
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = 0.75f)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(p.name, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${state.packEntries.sumOf { it.count }} cards" + priceSuffix(state.packValue),
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
                Text("Scan every card in the pack, then tap Finish to see if it paid off.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Pack") }, placeholder = { Text("e.g. Bloomburrow play booster") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    cost, { cost = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true,
                    label = { Text("What you paid (optional)") }, prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.startPack(name.trim(), cost.toDoubleOrNull()) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = { vm.showPackStart(false) }) { Text("Cancel") } },
    )
}

@Composable
fun PackSummaryDialog(state: ScanUiState, vm: ScanViewModel) {
    val p = state.packSummary ?: return
    AlertDialog(
        onDismissRequest = vm::closePackSummary,
        containerColor = Palette.Surface,
        title = { Text(p.name) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("${p.cards} cards", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                if (AppSettings.showPrices) {
                    Text(formatUsd(p.value), style = MaterialTheme.typography.displaySmall, color = Palette.Accent)
                    p.cost?.takeIf { it > 0 }?.let { cost ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("vs ${formatUsd(cost)} paid  ", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                            ChangeText(p.value - cost, cost)
                        }
                        Text(
                            if (p.value >= cost) "Worth it!" else "Better luck next pack.",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (p.best.isNotBlank()) Text("Best pull: ${p.best}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = vm::closePackSummary) { Text("Done") } },
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
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(460.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
