package com.mtgscanner.ui.search

import com.mtgscanner.data.undoWishes
import com.mtgscanner.data.wishSnapshot
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.PrintingLookup
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.util.DecklistParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.mtgscanner.ui.components.ruleBelow

/** A priced line and the card it was found as (null when not found). */
private data class PriceRow(val line: PricedLine, val card: ScryfallCard?)

/** Paste a trade offer or store list: total, each card's price, and which ones you already own. Read-only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceListSheet(owned: Map<String, Owned>, onDismiss: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as MtgApp).repository
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<PriceRow>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var wished by remember { mutableStateOf(false) }

    fun price() {
        val lines = DecklistParser.parse(text)
        if (lines.isEmpty() || busy) return
        busy = true
        note = null
        scope.launch {
            try {
                val wants = lines.map { PrintingLookup.Wanted(it.name, it.setCode, it.collectorNumber) }
                val cards = PrintingLookup.resolve(wants) { ids -> repo.api.identify(ids.map(PrintingLookup::json)) }
                rows = lines.zip(cards).map { (l, c) ->
                    PriceRow(
                        PricedLine(
                            name = c?.name ?: l.name,
                            quantity = l.quantity,
                            unitPrice = c?.price(l.foil)?.toDoubleOrNull(),
                            owned = c?.let { owned.of(it)?.count } ?: 0,
                            found = c != null,
                        ),
                        c,
                    )
                }
                wished = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note = "Couldn't reach Scryfall"
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            val result = rows
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Price a list", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (result != null) TextButton(onClick = { rows = null; note = null }) { Text("Edit list") }
            }
            Spacer(Modifier.height(8.dp))
            if (result == null) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("4 Lightning Bolt\n1 Sol Ring (CMR) 263") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp),
                    shape = RoundedCornerShape(Corners.Medium),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Accent),
                )
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.padding(top = 6.dp)) }
                Spacer(Modifier.height(12.dp))
                GradientButton(if (busy) "Pricing…" else "Price it", { price() }, Modifier.fillMaxWidth(), enabled = !busy && text.isNotBlank())
                Spacer(Modifier.height(16.dp))
            } else {
                PriceResult(result, wished, note, onWish = {
                    if (!wished && !busy) {
                        busy = true
                        scope.launch {
                            try {
                                val wants = result.filter { it.card != null && it.line.short > 0 }
                                val ids = wants.mapNotNull { it.card!!.oracleId }
                                val before = repo.wishSnapshot(ids)
                                wants.forEach { r -> repo.wantAtLeast(r.card!!, r.line.short) }
                                wished = true
                                com.mtgscanner.ui.components.Undo.offer("Added ${wants.size} to wishlist") {
                                    repo.undoWishes(ids, before)
                                    wished = false
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                note = "Couldn't add to wishlist"
                            } finally {
                                busy = false
                            }
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun PriceResult(rows: List<PriceRow>, wished: Boolean, note: String?, onWish: () -> Unit) {
    if (rows.isEmpty()) {
        EmptyState("No cards in the list", Modifier.padding(vertical = 24.dp))
        return
    }
    val t = remember(rows) { PriceListMath.totals(rows.map { it.line }) }
    val prices = AppSettings.showPrices
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (t.cards == 1) "1 card" else "${t.cards} cards", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (prices) Text(formatUsd(t.total), style = MaterialTheme.typography.titleMedium.merge(TabularNums), color = Palette.Text)
    }
    val extras = buildList {
        if (t.notFound > 0) add("${t.notFound} not found")
        if (prices && t.unpriced > 0) add("${t.unpriced} no price")
    }
    if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    Spacer(Modifier.height(8.dp))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(rows) { r -> PriceLineRow(r, prices) }
    }
    Spacer(Modifier.height(12.dp))
    val shortCards = t.shortCards
    if (shortCards > 0) {
        GradientButton(
            if (wished) "Added to wishlist" else "Want the $shortCards you don't own",
            onWish, Modifier.fillMaxWidth(), enabled = !wished,
        )
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 6.dp)) }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PriceLineRow(r: PriceRow, prices: Boolean) {
    val l = r.line
    Row(Modifier.fillMaxWidth().ruleBelow().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CardImage(r.card?.smallImageUrl, Modifier.width(32.dp), zoom = r.card != null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (l.quantity > 1) "${l.quantity} ${l.name}" else l.name,
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (l.found) Palette.Text else Palette.Muted,
            )
            when {
                !l.found -> Text("Not found", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                l.owned > 0 -> Text("Own ${l.owned}", style = MaterialTheme.typography.labelSmall, color = Palette.Text)
            }
        }
        if (prices && l.found) Text(
            l.lineTotal?.let { formatUsd(it) } ?: "--",
            style = MaterialTheme.typography.bodyMedium.merge(TabularNums),
            color = if (l.lineTotal != null) Palette.Text else Palette.Muted,
        )
    }
}
