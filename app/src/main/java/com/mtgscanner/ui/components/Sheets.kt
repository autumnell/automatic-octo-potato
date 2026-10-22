package com.mtgscanner.ui.components

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
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
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (printings == null) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Accent)
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().height(520.dp)) {
                items(printings, key = { it.id }) { p ->
                    val selected = p.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(Corners.Medium))
                            .background(if (selected) Palette.Accent.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onPick(p) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardImage(p.smallImageUrl, Modifier.width(52.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.setName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RarityDot(p.rarity, 8.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${p.set.uppercase()} · #${p.collectorNumber} · ${p.releasedAt.orEmpty().take(4)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.Muted,
                                )
                            }
                        }
                        if (AppSettings.showPrices) Column(horizontalAlignment = Alignment.End) {
                            Text(formatMoneyShort(p.prices.usd?.toDoubleOrNull(), p.prices.eur?.toDoubleOrNull()), style = MaterialTheme.typography.titleSmall, color = Palette.AccentText)
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
) {
    var foil by remember(card.id) { mutableStateOf(!card.hasNonFoil) }
    var qty by remember(card.id) { mutableIntStateOf(1) }
    // The last box used may have been deleted since; fall back to Auto-sort rather than a box that's gone.
    var boxId by remember { mutableLongStateOf(defaultBoxId) }
    if (boxId != CardBox.AUTO_SORT && boxes.isNotEmpty() && boxes.none { it.id == boxId }) boxId = CardBox.AUTO_SORT
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row {
                CardImage(card.imageUrl, Modifier.width(130.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    ManaCost(card.effectiveManaCost)
                    Spacer(Modifier.height(4.dp))
                    Text(card.effectiveTypeLine, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RarityDot(card.rarity, 8.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("${card.setName} · #${card.collectorNumber}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (AppSettings.showPrices) Text(
                        scryfallPrice(card, foil),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Palette.AccentText,
                    )
                    TextButton(onClick = onChangePrinting, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Icon(Icons.Filled.SwapHoriz, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Other printings")
                    }
                }
            }
            if (owned != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Medium)).background(Palette.Accent.copy(alpha = 0.14f))
                        .then(if (onOwned != null) Modifier.clickable(onClickLabel = "Open your copy", onClick = onOwned) else Modifier)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Check, null, tint = Palette.AccentText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(owned, style = MaterialTheme.typography.labelLarge, color = Palette.AccentText, modifier = Modifier.weight(1f))
                    if (onOwned != null) Text("Open", style = MaterialTheme.typography.labelLarge, color = Palette.AccentText)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Foil", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = foil,
                    onCheckedChange = { foil = it },
                    enabled = card.hasFoil && card.hasNonFoil,
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                QuantityStepper(qty, onChange = { qty = it.coerceIn(1, 99) })
            }
            if (boxes.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Cards go into boxes. Create one to add this card.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCreateBox, shape = RoundedCornerShape(Corners.Medium)) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create a box")
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Add to", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectPill("Auto-sort", boxId == CardBox.AUTO_SORT, onClick = { boxId = CardBox.AUTO_SORT }, leading = {
                        Icon(Icons.AutoMirrored.Filled.Sort, null, modifier = Modifier.size(14.dp), tint = if (boxId == CardBox.AUTO_SORT) Palette.AccentText else Palette.Icon)
                    })
                    boxes.forEach { b ->
                        SelectPill(b.name, b.id == boxId, onClick = { boxId = b.id }, leading = {
                            Icon(boxIcon(b.kind), null, modifier = Modifier.size(14.dp), tint = if (b.id == boxId) Palette.AccentText else Palette.Icon)
                        })
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            GradientButton(
                text = "Add to collection",
                icon = Icons.Filled.Add,
                onClick = { onAdd(foil, qty, boxId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = boxes.isNotEmpty(),
            )
            if (onWishlist != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onWishlist, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium)) {
                    Icon(Icons.Filled.FavoriteBorder, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add to wishlist")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun QuantityStepper(value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Corners.Medium)).background(Palette.SurfaceHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hold either button to keep counting; holding − stops at 1.
        RepeatIconButton(onClick = { onChange(value - 1) }, repeat = value > 1) { Icon(Icons.Filled.Remove, "One fewer") }
        // Grows for 100 or more instead of cutting the number off.
        Text(
            "$value", style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false,
            modifier = Modifier.widthIn(min = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RepeatIconButton(onClick = { onChange(value + 1) }) { Icon(Icons.Filled.Add, "One more", tint = Palette.AccentText) }
    }
}
