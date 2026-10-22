package com.mtgscanner.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.Found
import com.mtgscanner.data.Placement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.components.cardPrice
import com.mtgscanner.ui.components.formatUsd

/** Lost card finder: type a name, see every box it's in and exactly where. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindCardSheet(onOpenCard: (Long) -> Unit, onDismiss: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as MtgApp).repository
    val cards by remember { repo.observeAll() }.collectAsState(initial = emptyList())
    val boxes by remember { repo.observeBoxes() }.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    // Results for which query: worked out off the main thread once typing pauses.
    var result by remember { mutableStateOf("" to emptyList<Found>()) }
    LaunchedEffect(query, cards, boxes) {
        val q = query
        if (q.trim().length < 2) { result = q to emptyList<Found>(); return@LaunchedEffect }
        delay(150)
        result = q to withContext(Dispatchers.Default) { Placement.find(q, cards, boxes) }
    }
    val found = result.second
    val settled = result.first == query

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Text("Find a card", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), placeholder = "Card name")
            when {
                query.trim().length < 2 -> Text(
                    "Type at least 2 letters.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                found.isEmpty() && !settled -> Spacer(Modifier.height(48.dp))
                found.isEmpty() -> EmptyState("Not in your collection", action = "Clear", onAction = { query = "" })
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                    // Where it is on the shelf, when the boxes are laid out there.
                    item(key = "shelf") {
                        ShelfSpot(boxes, found.map { it.card.boxId }.toSet(), Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                    items(found, key = { it.card.id }) { f ->
                        FoundRow(f.card, f.where) { onOpenCard(f.card.id) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FoundRow(c: CollectionCard, where: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.width(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (c.quantity > 1) "${c.quantity}× " else "") + c.name,
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (c.foil) { Spacer(Modifier.width(6.dp)); FoilTag() }
                Spacer(Modifier.width(6.dp))
                Text(c.setCode, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
            Text(where, style = MaterialTheme.typography.bodySmall, color = Palette.AccentText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Every card you own in more than one printing, to consolidate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesSheet(onOpenCard: (Long) -> Unit, onDismiss: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as MtgApp).repository
    val cards by remember { repo.observeAll() }.collectAsState(initial = null)
    val boxes by remember { repo.observeBoxes() }.collectAsState(initial = emptyList())
    val groups = remember(cards) { Placement.duplicates(cards.orEmpty()) }
    val boxNames = remember(boxes) { boxes.associate { it.id to it.name } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                if (groups.isEmpty()) "Duplicates" else "Duplicates · ${groups.size}",
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text("Cards in 2+ printings", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 20.dp))
            if (cards != null && groups.isEmpty()) {
                EmptyState("No duplicates", action = "Close", onAction = onDismiss)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                groups.forEach { g ->
                    item(key = "h-${g.name}-${g.cards.first().id}") {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium, color = Palette.AccentText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${g.printings} printings · ${g.copies}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        }
                    }
                    items(g.cards, key = { "d${it.id}" }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenCard(c.id) }.padding(horizontal = 20.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.width(32.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${c.setCode} #${c.collectorNumber}" + if (c.foil) " · Foil" else "", style = MaterialTheme.typography.bodyMedium)
                                Text(boxNames[c.boxId] ?: "No box", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
                            }
                            Text("×${c.quantity}", style = MaterialTheme.typography.titleSmall)
                            if (AppSettings.showPrices) {
                                Spacer(Modifier.width(10.dp))
                                Text(cardPrice(c), style = MaterialTheme.typography.bodySmall, color = Palette.AccentText)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
