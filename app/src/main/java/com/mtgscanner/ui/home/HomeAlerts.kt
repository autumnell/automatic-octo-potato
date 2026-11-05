package com.mtgscanner.ui.home

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.PlainGroup
import com.mtgscanner.ui.components.ruleBelow
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.work.CardAlertStore
import com.mtgscanner.work.CardPriceAlert

/** A price alert with the card it's for (null when no copy is left) and today's price. */
data class AlertRow(val alert: CardPriceAlert, val cardId: Long?, val price: Double?)

/** Pairs each alert with an owned copy of that printing and finish. */
internal fun alertRows(alerts: List<CardPriceAlert>, all: List<CollectionCard>): List<AlertRow> {
    val byKey = HashMap<String, CollectionCard>()
    all.forEach { c -> byKey.putIfAbsent(CardPriceAlert.key(c.scryfallId, c.foil), c) }
    return alerts.map { a -> byKey[a.key].let { AlertRow(a, it?.id, it?.nearMintPrice()) } }
}

/** "Over $50 · under $30". */
fun alertLines(a: CardPriceAlert): String = listOfNotNull(
    a.above?.let { "Over ${formatUsd(it)}" },
    a.below?.let { "under ${formatUsd(it)}" },
).joinToString(" · ").replaceFirstChar(Char::uppercase)

/** Active price alerts on Home, each with delete (and undo). */
@androidx.compose.runtime.Composable
internal fun AlertsList(rows: List<AlertRow>, onOpenCard: (Long) -> Unit) {
    val ctx = LocalContext.current
    PlainGroup(Modifier.padding(horizontal = Space.s4)) {
        rows.forEachIndexed { i, r ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = r.cardId != null) { r.cardId?.let(onOpenCard) }
                    .then(if (i < rows.lastIndex) Modifier.ruleBelow() else Modifier)
                    .padding(start = Space.s3, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = Space.s2)) {
                    Text(
                        r.alert.name + if (r.alert.foil) " · foil" else "",
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // An alert on a card no longer owned never fires: say so, and it can be deleted.
                        if (r.cardId == null) "No copies left" else alertLines(r.alert) + (r.price?.let { " · now ${formatUsd(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    val gone = r.alert
                    CardAlertStore.remove(ctx, gone.key)
                    Undo.offer("Alert removed") { CardAlertStore.put(ctx, gone) }
                }) { Icon(Icons.Outlined.DeleteOutline, "Delete alert", tint = Palette.Icon) }
            }
        }
    }
}
