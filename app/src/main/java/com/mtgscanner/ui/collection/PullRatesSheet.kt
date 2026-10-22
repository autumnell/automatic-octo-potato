package com.mtgscanner.ui.collection

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.PackLog
import com.mtgscanner.data.PullGroup
import com.mtgscanner.data.PullRates
import com.mtgscanner.data.db.PackOpening
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.RarityMark
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.launch
import java.util.Locale

/** Prices typed in later for logged packs that were opened without one, by pack id. Saved on the phone. */
object PackCosts {
    private const val KEY = "packCosts"

    fun all(context: Context): Map<Long, Double> =
        context.getSharedPreferences("app", Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            .split(',').mapNotNull { e ->
                val (id, cost) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                (id.toLongOrNull() ?: return@mapNotNull null) to (cost.toDoubleOrNull() ?: return@mapNotNull null)
            }.toMap()

    /** Sets or (with null) clears the price of pack [id]; returns the new map. */
    fun set(context: Context, id: Long, cost: Double?): Map<Long, Double> {
        val map = all(context).toMutableMap()
        if (cost == null) map.remove(id) else map[id] = cost
        context.getSharedPreferences("app", Context.MODE_PRIVATE).edit()
            .putString(KEY, map.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
        return map
    }
}

private fun pct(v: Double) = String.format(Locale.US, "%.0f%%", v * 100)
private fun oneDp(v: Double) = String.format(Locale.US, "%.1f", v)

/** Pull rates from logged packs: rarities per pack, mythic rate, and pulled vs paid. Per set with the pills. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRatesSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val repo = (ctx.applicationContext as MtgApp).repository
    val scope = rememberCoroutineScope()
    val packs by remember { repo.dao.observePacks() }.collectAsState(initial = null)
    val cards by remember { repo.observeAll() }.collectAsState(initial = emptyList())
    var costs by remember { mutableStateOf(PackCosts.all(ctx)) }
    var pricing by remember { mutableStateOf<PackOpening?>(null) }
    var picked by remember { mutableStateOf("") }

    val rarities = remember(cards) { cards.associate { (it.setCode.lowercase() + "#" + it.collectorNumber) to it.rarity } }
    val setNames = remember(cards) { cards.associate { it.setCode.lowercase() to it.setName } }
    val logs = remember(packs, rarities, costs) {
        packs.orEmpty().map { p ->
            PackLog(
                p.id, p.name, costs[p.id] ?: p.cost, p.value,
                PullRates.fillRarity(PullRates.parse(p.pulls)) { s, n -> rarities["$s#$n"] },
            )
        }
    }
    val groups = remember(logs, setNames) { listOf(PullRates.all(logs)) + PullRates.bySet(logs) { setNames[it] ?: it.uppercase() } }
    val group = groups.firstOrNull { it.key == picked } ?: groups.first()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text("Pull rates", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            val list = packs
            if (list != null && list.isEmpty()) {
                EmptyState("No packs yet", body = "Scan › Open a pack")
                Spacer(Modifier.height(16.dp))
                return@Column
            }
            Text(
                "${group.packs} packs · ${group.cards} cards",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (groups.size > 2) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    groups.forEach { g -> SelectPill(if (g.key.isEmpty()) "All" else g.label, g.key == group.key, { picked = g.key }) }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
                item { GroupStats(group) }
                item {
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Palette.Hairline)
                    Text("Packs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
                val shown = list.orEmpty().filter { p -> group.key.isEmpty() || logs.firstOrNull { it.id == p.id }?.let(PullRates::mainSet) == group.key }
                items(shown, key = { "p${it.id}" }) { p ->
                    PackRow(
                        p, costs[p.id] ?: p.cost,
                        onPrice = { pricing = p },
                        onDelete = {
                            scope.launch {
                                repo.dao.deletePack(p.id)
                                Undo.offer("Pack deleted") { repo.dao.addPack(p) }
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    pricing?.let { p ->
        PackCostDialog(
            current = costs[p.id] ?: p.cost,
            onSave = { v -> costs = PackCosts.set(ctx, p.id, v); pricing = null },
            onDismiss = { pricing = null },
        )
    }
}

@Composable
private fun GroupStats(g: PullGroup) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        g.mythicRate?.let { rate ->
            StatLine("Mythic", if (g.mythicPacks == 0) "None yet" else "1 in ${oneDp(g.known.toDouble() / g.mythicPacks)} · ${pct(rate)}")
        }
        g.rarities.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                RarityMark(r.rarity)
                Spacer(Modifier.width(8.dp))
                Text(r.rarity.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${oneDp(r.perPack)} / pack", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                Spacer(Modifier.width(12.dp))
                Text("${r.cards}", style = MaterialTheme.typography.titleSmall)
            }
        }
        if (AppSettings.showPrices) {
            Spacer(Modifier.height(6.dp))
            StatLine("Pulled", formatUsd(g.pulled))
            if (g.paidPacks > 0) {
                StatLine("Paid", formatUsd(g.paid) + if (g.paidPacks < g.packs) " · ${g.paidPacks} packs" else "")
                val net = g.net
                StatLine(
                    if (net >= 0) "Up" else "Down",
                    (if (net >= 0) "+" else "−") + formatUsd(kotlin.math.abs(net)),
                    if (net >= 0) Palette.Success else Palette.Danger,
                )
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, color: androidx.compose.ui.graphics.Color = Palette.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
private fun PackRow(p: PackOpening, cost: Double?, onPrice: () -> Unit, onDelete: () -> Unit) {
    val day = remember(p.openedAt) {
        java.time.Instant.ofEpochMilli(p.openedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPrice).padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$day · ${p.cards} cards" + if (p.best.isNotEmpty()) " · ${p.best}" else "",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (AppSettings.showPrices) {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatUsd(p.value), style = MaterialTheme.typography.titleSmall, color = Palette.AccentText)
                Text(if (cost != null) "Paid ${formatUsd(cost)}" else "Add price", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, "Delete pack", tint = Palette.Icon) }
    }
}

@Composable
private fun PackCostDialog(current: Double?, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()) }
    val parsed = text.replace(',', '.').trim().removePrefix("$").toDoubleOrNull()?.takeIf { it >= 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Pack price") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = parsed != null || text.isBlank(), onClick = { onSave(parsed) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
