package com.mtgscanner.ui.search

import com.mtgscanner.ui.theme.Space
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
                // Owned copies are shared out across lines of the same card, so two printings don't both count them.
                val used = HashMap<String, Int>()
                rows = lines.zip(cards).map { (l, c) ->
                    val have = c?.let { owned.of(it)?.count } ?: 0
                    val key = c?.let { it.oracleId ?: it.name }.orEmpty()
                    val left = (have - (used[key] ?: 0)).coerceAtLeast(0)
                    val mine = minOf(left, l.quantity)
                    if (c != null) used[key] = (used[key] ?: 0) + mine
                    PriceRow(
                        PricedLine(
                            name = c?.name ?: l.name,
                            quantity = l.quantity,
                            unitPrice = c?.price(l.foil)?.toDoubleOrNull(),
                            owned = mine,
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
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4).navigationBarsPadding()) {
            val result = rows
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Price a list", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (result != null) TextButton(onClick = { rows = null; note = null }) { Text("Edit list") }
            }
            Spacer(Modifier.height(Space.s2))
            if (result == null) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("4 Lightning Bolt\n1 Sol Ring (CMR) 263") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp),
                    shape = RoundedCornerShape(Corners.Medium),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Text, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Text),
                )
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.padding(top = Space.s2)) }
                Spacer(Modifier.height(Space.s3))
                GradientButton(if (busy) "Pricing…" else "Price it", { price() }, Modifier.fillMaxWidth(), enabled = !busy && text.isNotBlank())
                Spacer(Modifier.height(Space.s4))
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
        EmptyState("No cards in the list", Modifier.padding(vertical = Space.s6))
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
    Spacer(Modifier.height(Space.s2))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        items(rows) { r -> PriceLineRow(r, prices) }
    }
    Spacer(Modifier.height(Space.s3))
    val shortCards = t.shortCards
    if (shortCards > 0) {
        GradientButton(
            if (wished) "Added to wishlist" else "Want the $shortCards you don't own",
            onWish, Modifier.fillMaxWidth(), enabled = !wished,
        )
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = Space.s2)) }
    Spacer(Modifier.height(Space.s4))
}

@Composable
private fun PriceLineRow(r: PriceRow, prices: Boolean) {
    val l = r.line
    Row(Modifier.fillMaxWidth().ruleBelow().padding(vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        CardImage(r.card?.smallImageUrl, Modifier.width(32.dp), zoom = r.card != null)
        Spacer(Modifier.width(Space.s3))
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
