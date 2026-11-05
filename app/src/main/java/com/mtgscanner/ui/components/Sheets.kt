package com.mtgscanner.ui.components

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

/** Bottom sheet listing every printing of a card so the right set can be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintingsSheet(
    printings: List<ScryfallCard>?,
    selectedId: String?,
    onPick: (ScryfallCard) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Text(
            "Choose the printing",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
        )
        if (printings == null) {
            PlaceholderRows(5, Modifier.padding(horizontal = Space.s4, vertical = Space.s2), rowHeight = 72.dp, color = Palette.SurfaceHigh)
        } else {
            LazyColumn(Modifier.fillMaxWidth().height(520.dp)) {
                items(printings, key = { it.id }) { p ->
                    val selected = p.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (selected) Palette.SurfaceHigh else Color.Transparent)
                            .clickable { onPick(p) }
                            .padding(horizontal = Space.s4)
                            .ruleBelow()
                            .padding(vertical = Space.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardImage(p.smallImageUrl, Modifier.width(52.dp))
                        Spacer(Modifier.width(Space.s3))
                        Column(Modifier.weight(1f)) {
                            Text(p.setName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SetMark(p.set, p.rarity)
                                Spacer(Modifier.width(Space.s2))
                                Text(
                                    "${p.set.uppercase()} · #${p.collectorNumber} · ${p.releasedAt.orEmpty().take(4)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.Muted,
                                )
                            }
                        }
                        if (AppSettings.showPrices) Column(horizontalAlignment = Alignment.End) {
                            Text(formatMoneyShort(p.prices.usd?.toDoubleOrNull(), p.prices.eur?.toDoubleOrNull()), style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                            if (p.prices.usdFoil != null) {
                                Text("Foil ${formatMoneyShort(p.prices.usdFoil?.toDoubleOrNull(), p.prices.eurFoil?.toDoubleOrNull())}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

/**
 * Sheet for adding a card found by search: shows the card, then lets you choose
 * foil, how many, and which box before adding.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCardSheet(
    card: ScryfallCard,
    boxes: List<CardBox>,
    defaultBoxId: Long,
    onAdd: (foil: Boolean, qty: Int, boxId: Long) -> Unit,
    onChangePrinting: () -> Unit,
    onDismiss: () -> Unit,
    onCreateBox: () -> Unit = {},
    onWishlist: (() -> Unit)? = null,
    /** A short line such as "You own 3 · Rares", or null when the card isn't in the collection. */
    owned: String? = null,
    /** Opens your copy when the "You own" line is tapped; null leaves the line plain. */
    onOwned: (() -> Unit)? = null,
    /** Decks to put the card straight into; shown when [onAddToDeck] is set. */
    decks: List<com.mtgscanner.data.db.Deck> = emptyList(),
    onAddToDeck: ((foil: Boolean, qty: Int, deckId: Long) -> Unit)? = null,
    /** Makes a deck with this name; the caller then passes it back as [pick]. */
    onCreateDeck: ((String) -> Unit)? = null,
    /** A box or deck just made from this sheet, to select. */
    pick: AddPick? = null,
) {
    var foil by remember(card.id) { mutableStateOf(!card.hasNonFoil) }
    var qty by remember(card.id) { mutableIntStateOf(1) }
    // The last box used may have been deleted since; fall back to Auto-sort rather than a box that's gone.
    var boxId by remember { mutableLongStateOf(defaultBoxId) }
    /** A deck picked instead of a box; 0 when a box is picked. */
    var deckId by remember { mutableLongStateOf(0L) }
    var namingDeck by remember { mutableStateOf(false) }
    LaunchedEffect(pick) {
        when {
            pick == null -> Unit
            pick.deckId != 0L -> deckId = pick.deckId
            else -> { boxId = pick.boxId; deckId = 0L }
        }
    }
    // A box just made may not be in [boxes] yet; it isn't "gone".
    if (boxId != CardBox.AUTO_SORT && boxId != pick?.boxId && boxes.isNotEmpty() && boxes.none { it.id == boxId }) boxId = CardBox.AUTO_SORT
    if (deckId != 0L && deckId != pick?.deckId && decks.none { it.id == deckId }) deckId = 0L
    val showDecks = onAddToDeck != null
    if (namingDeck && onCreateDeck != null) {
        NewDeckDialog(onCreate = { onCreateDeck(it); namingDeck = false }, onDismiss = { namingDeck = false })
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).navigationBarsPadding()) {
            Row {
                CardImage(card.imageUrl, Modifier.width(130.dp))
                Spacer(Modifier.width(Space.s4))
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Space.s1))
                    ManaCost(card.effectiveManaCost)
                    Spacer(Modifier.height(Space.s1))
                    Text(card.effectiveTypeLine, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Spacer(Modifier.height(Space.s2))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RarityDot(card.rarity, 8.dp)
                        Spacer(Modifier.width(Space.s2))
                        Text("${card.setName} · #${card.collectorNumber}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(Space.s2))
                    if (AppSettings.showPrices) Text(
                        scryfallPrice(card, foil),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Palette.Text,
                    )
                    TextButton(onClick = onChangePrinting, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Icon(Icons.Outlined.SwapHoriz, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s1))
                        Text("Other printings")
                    }
                }
            }
            if (owned != null) {
                Spacer(Modifier.height(Space.s3))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Medium)).background(Palette.SurfaceHigh)
                        .then(if (onOwned != null) Modifier.clickable(onClickLabel = "Open your copy", onClick = onOwned) else Modifier)
                        .padding(horizontal = Space.s3, vertical = Space.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Check, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s2))
                    Text(owned, style = MaterialTheme.typography.labelLarge, color = Palette.Text, modifier = Modifier.weight(1f))
                    if (onOwned != null) Text("Open", style = MaterialTheme.typography.labelLarge, color = Palette.AccentText)
                }
            }
            Spacer(Modifier.height(Space.s4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Foil", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = foil,
                    onCheckedChange = { foil = it },
                    enabled = card.hasFoil && card.hasNonFoil,
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.Text),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                QuantityStepper(qty, onChange = { qty = it.coerceIn(1, 99) })
            }
            if (boxes.isEmpty() && (!showDecks || decks.isEmpty())) {
                Spacer(Modifier.height(Space.s3))
                Text("Cards go into boxes. Create one to add this card.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Spacer(Modifier.height(Space.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    QuietButton(onClick = onCreateBox, shape = RoundedCornerShape(Corners.Medium)) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s2))
                        Text("Create a box")
                    }
                    if (showDecks && onCreateDeck != null) QuietButton(onClick = { namingDeck = true }, shape = RoundedCornerShape(Corners.Medium)) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s2))
                        Text("New deck")
                    }
                }
            } else {
                Spacer(Modifier.height(Space.s2))
                Text("Add to", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Space.s2))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    if (boxes.isNotEmpty()) {
                        val auto = deckId == 0L && boxId == CardBox.AUTO_SORT
                        SelectPill("Auto-sort", auto, onClick = { boxId = CardBox.AUTO_SORT; deckId = 0L }, leading = {
                            Icon(Icons.AutoMirrored.Outlined.Sort, null, modifier = Modifier.size(18.dp), tint = if (auto) Palette.AccentText else Palette.Icon)
                        })
                    }
                    boxes.forEach { b ->
                        val on = deckId == 0L && b.id == boxId
                        SelectPill(b.name, on, onClick = { boxId = b.id; deckId = 0L }, leading = {
                            Icon(boxIcon(b.kind), null, modifier = Modifier.size(18.dp), tint = if (on) Palette.AccentText else Palette.Icon)
                        })
                    }
                    NewPill("New box", onCreateBox)
                }
                if (showDecks) {
                    Spacer(Modifier.height(Space.s3))
                    Text("Or into a deck", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Space.s2))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        decks.forEach { d ->
                            val on = d.id == deckId
                            SelectPill(d.name, on, onClick = { deckId = if (on) 0L else d.id }, leading = {
                                Icon(boxIcon("deck"), null, modifier = Modifier.size(18.dp), tint = if (on) Palette.AccentText else Palette.Icon)
                            })
                        }
                        if (onCreateDeck != null) NewPill("New deck") { namingDeck = true }
                    }
                }
            }
            Spacer(Modifier.height(Space.s6))
            val toDeck = deckId != 0L && onAddToDeck != null
            GradientButton(
                text = if (toDeck) "Add to ${decks.firstOrNull { it.id == deckId }?.name ?: "deck"}" else "Add to collection",
                icon = Icons.Outlined.Add,
                onClick = { if (toDeck) onAddToDeck?.invoke(foil, qty, deckId) else onAdd(foil, qty, boxId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = toDeck || boxes.isNotEmpty(),
            )
            if (onWishlist != null) {
                Spacer(Modifier.height(Space.s2))
                QuietButton(onClick = onWishlist, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium)) {
                    Icon(Icons.Outlined.FavoriteBorder, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s2))
                    Text("Add to wishlist")
                }
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
}

/** A box or deck just made from [AddCardSheet], for it to select. */
data class AddPick(val boxId: Long = 0L, val deckId: Long = 0L)

/** A dashed "+ New box" pill at the end of a row of choices. */
@Composable
private fun NewPill(label: String, onClick: () -> Unit) {
    SelectPill(label, false, onClick = onClick, leading = {
        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp), tint = Palette.Icon)
    })
}

/** Asks for a new deck's name. */
@Composable
private fun NewDeckDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("New deck") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) },
                placeholder = { Text("Deck name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun QuantityStepper(value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Corners.Medium)).background(Palette.SurfaceHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hold either button to keep counting; holding − stops at 1.
        RepeatIconButton(onClick = { onChange(value - 1) }, repeat = value > 1) { Icon(Icons.Outlined.Remove, "One fewer") }
        // Grows for 100 or more instead of cutting the number off.
        Text(
            "$value", style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false,
            modifier = Modifier.widthIn(min = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RepeatIconButton(onClick = { onChange(value + 1) }) { Icon(Icons.Outlined.Add, "One more", tint = Palette.Text) }
    }
}
