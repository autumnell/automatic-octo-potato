package com.mtgscanner.ui.scan

import com.mtgscanner.ui.theme.Space
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ExpandMore
import com.mtgscanner.data.SortPrefs
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Style
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
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
import com.mtgscanner.data.ScanBarPrefs
import com.mtgscanner.ui.components.moneySuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.CardShape

/**
 * The line over the camera saying what happens to the next card. Tapping it opens "What are you doing?", where one
 * activity is picked at a time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModesButton(state: ScanUiState, vm: ScanViewModel, boxes: List<com.mtgscanner.data.db.Box>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val activity = state.activity()
    val lock = SortPrefs.setLock
    Row(
        modifier.heightIn(min = 44.dp).glass(RoundedCornerShape(Corners.Medium))
            .clickable(onClickLabel = "Change what you're doing") { open = true }
            .padding(horizontal = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(activity.icon(), null, tint = if (activity != ScanActivity.ADD) Palette.Gold else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s2))
        Text(
            state.nextCard({ id -> boxes.firstOrNull { it.id == id }?.name }, lock),
            color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(Space.s1))
        Icon(Icons.Outlined.ExpandMore, null, tint = Palette.OnDarkMuted, modifier = Modifier.size(18.dp))
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = Palette.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Space.s4)) {
            Text("What are you doing?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2))
            ScanActivity.entries.forEach { a ->
                val on = a == activity
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable { open = false; vm.chooseActivity(a) }
                        .padding(horizontal = Space.s4, vertical = Space.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(a.icon(), null, tint = if (on) Palette.AccentText else Palette.Icon, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(Space.s4))
                    Column(Modifier.weight(1f)) {
                        val title = if (on && a == ScanActivity.DECK) "Adding to ${state.deck?.name?.ifBlank { null } ?: "a deck"}" else a.title
                        Text(title, style = MaterialTheme.typography.titleMedium, color = if (on) Palette.AccentText else Palette.Text)
                        Text(a.body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    if (on) Icon(Icons.Filled.CheckCircle, "Now", tint = Palette.AccentText, modifier = Modifier.size(24.dp))
                }
            }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = Space.s2), color = Palette.Hairline)
            Text("While scanning", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s1))
            if (activity.oneByOne) Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { vm.setStack(!state.stack) }.padding(horizontal = Space.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Layers, null, tint = Palette.Icon, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(Space.s4))
                Column(Modifier.weight(1f)) {
                    Text("Hands-free", style = MaterialTheme.typography.titleMedium)
                    Text("Flip cards one by one, a beep on each", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                androidx.compose.material3.Switch(
                    checked = state.stack, onCheckedChange = { vm.setStack(it) },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Palette.Text),
                )
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = false; vm.showSetLock(true) }.padding(horizontal = Space.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, null, tint = Palette.Icon, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(Space.s4))
                Column(Modifier.weight(1f)) {
                    Text("Only one set", style = MaterialTheme.typography.titleMedium)
                    Text(lock?.let { "Locked to ${it.uppercase()}" } ?: "Any set", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
        }
    }
}

private fun ScanActivity.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    ScanActivity.ADD -> Icons.Outlined.CollectionsBookmark
    ScanActivity.DECK -> Icons.Outlined.Style
    ScanActivity.WISHLIST -> Icons.Outlined.FavoriteBorder
    ScanActivity.CHECK -> Icons.Outlined.Sell
    ScanActivity.REMOVE -> Icons.Outlined.RemoveCircleOutline
    ScanActivity.BOX_CHECK -> Icons.Outlined.Checklist
    ScanActivity.SORT -> Icons.AutoMirrored.Outlined.Sort
    ScanActivity.PACK -> Icons.Outlined.Inventory2
    ScanActivity.BATCH -> Icons.Outlined.Inbox
    ScanActivity.PAGE -> Icons.Outlined.GridOn
}


/** Picks the deck list that scanned cards go to, or makes a new one. */
@Composable
fun DeckPickDialog(vm: ScanViewModel) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.showDeckPick(false) },
        containerColor = Palette.Surface,
        title = { Text("Scan into a deck") },
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
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable { vm.scanToDeck(d) }
                                    .padding(vertical = Space.s3, horizontal = Space.s1),
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.s2))
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
    Surface(modifier, shape = RoundedCornerShape(Corners.Medium), color = Color.Black.copy(alpha = 0.75f)) {
        Row(Modifier.padding(start = Space.s3, end = Space.s1, top = Space.s1, bottom = Space.s1), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(p.name, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${state.packEntries.sumOf { it.count }} cards" + moneySuffix(state.packValue, state.packEur) +
                        (p.cost?.takeIf { it > 0 && AppSettings.showPrices }?.let { " / " + com.mtgscanner.ui.components.formatUsd(it) } ?: ""),
                    color = Palette.Gold, style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = vm::finishPack) { Text("Finish", color = Color.White) }
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
                Spacer(Modifier.height(Space.s2))
                OutlinedTextField(
                    cost, { cost = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, singleLine = true,
                    label = { Text("Paid (optional)") }, prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.startPack(name.trim(), com.mtgscanner.ui.sell.Sales.parsePrice(cost)) }) { Text("Start") } },
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

/** Many cards at once: capture a binder page with the camera, or read every card in a photo from the gallery. */
@Composable
fun PageControls(state: ScanUiState, vm: ScanViewModel, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // A video (a pack opened, a binder flipped through) is read frame by frame.
        val video = runCatching { ctx.contentResolver.getType(uri) }.getOrNull()?.startsWith("video/") == true
        if (video) vm.readVideo(uri) else vm.readPhoto(uri)
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            state.status, color = Color.White, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(Corners.Medium)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = Space.s3, vertical = Space.s2),
        )
        Spacer(Modifier.height(Space.s3))
        if (state.wantPage || state.wantSpread || state.pageLoading) Row(verticalAlignment = Alignment.CenterVertically) {
            com.mtgscanner.ui.components.Spinner()
            Spacer(Modifier.width(Space.s3))
            TextButton(onClick = vm::cancelCapture) { Text("Cancel", color = Color.White) }
        }
        else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GradientButton("Capture page", vm::capturePage, Modifier.weight(1f), icon = Icons.Outlined.GridOn)
            Spacer(Modifier.width(Space.s2))
            // Two to four loose cards side by side: each one is found and read.
            Row(
                Modifier.clip(RoundedCornerShape(Corners.Medium)).background(Color.Black.copy(alpha = 0.7f))
                    .clickable { vm.captureSpread() }
                    .padding(horizontal = Space.s4, vertical = Space.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.ViewModule, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.s2))
                Text("Loose", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(Space.s2))
            Row(
                Modifier.clip(RoundedCornerShape(Corners.Medium)).background(Color.Black.copy(alpha = 0.7f))
                    .clickable { runCatching { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) } }
                    .padding(horizontal = Space.s4, vertical = Space.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.s2))
                Text("Photo · video", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** What was read from each pocket. Tick the ones to add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageSheet(state: ScanUiState, vm: ScanViewModel) {
    val slots = state.page ?: return
    ModalBottomSheet(onDismissRequest = vm::closePage, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = Space.s4).navigationBarsPadding()) {
            val picked = slots.filter { it.selected }
            Text("Binder page", style = MaterialTheme.typography.titleLarge)
            Text(
                "Found ${slots.count { it.card != null }} of ${slots.size}",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            Spacer(Modifier.height(Space.s3))
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxHeight(0.75f), horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                items(slots, key = { it.index }) { s ->
                    Box(
                        Modifier.clip(CardShape).background(Palette.SurfaceHigh)
                            .clickable(enabled = s.card != null) { vm.togglePageSlot(s.index) }
                            .aspectRatio(63f / 88f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val card = s.card
                        if (card != null) {
                            CardImage(card.smallImageUrl ?: card.imageUrl, Modifier.fillMaxWidth().alpha(if (s.selected) 1f else 0.35f))
                            if (s.selected) Icon(Icons.Filled.CheckCircle, null, tint = Palette.Accent, modifier = Modifier.align(Alignment.TopEnd).padding(Space.s1))
                        } else Text(
                            s.read?.let { "Couldn't find\n$it" } ?: "Empty or unreadable",
                            style = MaterialTheme.typography.labelSmall, color = Palette.Muted, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(Space.s2),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.s3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::closePage) { Text("Try again") }
                Spacer(Modifier.width(Space.s2))
                val worth = addWorth(state, picked.mapNotNull { it.card }, ScanBarPrefs.quantity)
                // Price check adds nothing: the button just closes the page.
                if (state.target == ScanTarget.CHECK) GradientButton("Done$worth", vm::addPage, Modifier.weight(1f))
                else GradientButton(
                    (if (state.target == ScanTarget.REMOVE) "Remove " else if (state.target == ScanTarget.BOX) "Count " else "Add ") +
                        picked.size + worth,
                    vm::addPage, Modifier.weight(1f), enabled = picked.isNotEmpty(),
                )
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
}
