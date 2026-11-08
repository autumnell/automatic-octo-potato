package com.mtgscanner.ui.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.ui.components.LineChart
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.util.PriceInput
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Tell me when it's under $40": sets or clears a wishlist card's target price. */
@Composable
internal fun WishTargetDialog(item: WishlistItem, target: Double?, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by remember(item.id) { mutableStateOf(target?.let { "%.2f".format(Locale.US, it) }.orEmpty()) }
    val price = PriceInput.parse(text)
    val bad = text.isNotBlank() && price == null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(item.name, maxLines = 1) },
        text = {
            Column {
                Text(
                    "You get a notification once its price is at or under this" + (item.priceUsd?.let { ". Now ${formatUsd(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                )
                Spacer(Modifier.height(Space.s3))
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true, isError = bad,
                    label = { Text("Target price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(price) }, enabled = !bad) { Text("Save") } },
        dismissButton = {
            if (target != null) TextButton(onClick = { onSave(null) }) { Text("Clear", color = Palette.Danger) }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Where a wished printing's price has been, from the daily refresh, with its target. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WishHistorySheet(item: WishlistItem, target: Double?, history: Flow<List<PricePoint>>, onDismiss: () -> Unit) {
    val all by history.collectAsStateWithLifecycle(emptyList())
    val points = all.mapNotNull { p -> p.usd?.let { p.day to it } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s4, bottom = Space.s6)) {
            Text(item.name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Spacer(Modifier.height(Space.s1))
            Row {
                Text(
                    "Now " + formatUsd(item.priceUsd), style = MaterialTheme.typography.titleMedium.merge(TabularNums),
                    modifier = Modifier.weight(1f),
                )
                if (target != null) Text("Target " + formatUsd(target), style = MaterialTheme.typography.titleMedium.merge(TabularNums), color = Palette.Muted)
            }
            Spacer(Modifier.height(Space.s3))
            if (points.size < 2) {
                Text("The chart fills in as prices are checked each day", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            } else {
                var scrub by remember(points) { mutableStateOf<Int?>(null) }
                val s = scrub
                Text(
                    if (s != null) "${day(points[s].first)} · ${formatUsd(points[s].second)}"
                    else "low ${formatUsd(points.minOf { it.second })} · high ${formatUsd(points.maxOf { it.second })}",
                    style = MaterialTheme.typography.labelMedium.merge(TabularNums), color = Palette.Muted,
                )
                Spacer(Modifier.height(Space.s2))
                LineChart(points.map { it.second }, Modifier.fillMaxWidth().height(96.dp), onScrub = { scrub = it })
                Spacer(Modifier.height(Space.s2))
                Row {
                    Text(day(points.first().first), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                    Text(day(points.last().first), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private fun day(d: String) = runCatching { LocalDate.parse(d).format(dayFormat) }.getOrDefault(d)
