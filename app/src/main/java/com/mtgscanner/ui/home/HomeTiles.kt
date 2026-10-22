package com.mtgscanner.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import com.mtgscanner.ui.components.RollingText
import com.mtgscanner.ui.theme.PriceStyle
import com.mtgscanner.ui.theme.Corners

/** Numbers for the stat tiles: every metric, and each saved search's card count by name. */
data class TileData(val values: Map<TileMetric, Double> = emptyMap(), val lists: Map<String, Int> = emptyMap())

/** The tiles that can show now: money tiles need prices on, the list tile needs its saved search. */
internal fun shownTiles(tiles: List<TileMetric>, data: TileData, listName: String?, prices: Boolean): List<TileMetric> =
    tiles.filter { m ->
        when {
            m.money -> prices
            m == TileMetric.CUSTOM -> listName != null && data.lists.containsKey(listName)
            else -> true
        }
    }

/** The row of stat tiles, a Home section of its own. Tap opens what the number counts; hold to change the tiles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StatTileRow(data: TileData, onTap: (TileMetric) -> Unit, onEdit: () -> Unit) {
    val listName = HomePrefs.tileList
    val shown = shownTiles(HomePrefs.tiles, data, listName, AppSettings.showPrices)
    if (shown.isEmpty()) return
    val compact = shown.size >= 4
    // Plain figures split by hairlines, no boxes: the value card above is the only panel.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { i, m ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(Palette.Hairline))
            val label = if (m == TileMetric.CUSTOM) listName.orEmpty() else m.short
            val value = if (m == TileMetric.CUSTOM) (listName?.let { data.lists[it] } ?: 0).toDouble() else data.values[m] ?: 0.0
            val format: (Double) -> String = { v -> if (m == TileMetric.CUSTOM) TileMath.shortCount(v) else TileMath.text(m, v) }
            FlatTile(
                label, value, format, compact,
                Modifier.weight(1f).clip(RoundedCornerShape(Corners.Medium)).combinedClickable(onClick = { onTap(m) }, onLongClick = onEdit),
            )
        }
    }
}

/** One stat: the number counting up in the serif, its label under it. */
@Composable
private fun FlatTile(label: String, value: Double, format: (Double) -> String, compact: Boolean, modifier: Modifier) {
    Column(modifier.padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RollingText(
            value, format,
            PriceStyle.copy(fontSize = PriceStyle.fontSize * (if (compact) 1.2f else 1.4f), lineHeight = PriceStyle.lineHeight * 1.3f),
            Palette.AccentText,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** Pick 2 to 4 tiles and their order. The "Saved list" tile also needs a saved search. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditTilesDialog(lists: List<String>, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(HomePrefs.tiles) }
    var listName by remember { mutableStateOf(HomePrefs.tileList?.takeIf { it in lists }) }
    val full = chosen.size >= TileMetric.MAX
    fun move(i: Int, by: Int) {
        val j = i + by
        if (j !in chosen.indices) return
        val l = chosen.toMutableList()
        val t = l[i]; l[i] = l[j]; l[j] = t
        chosen = l
    }
    val needsList = TileMetric.CUSTOM in chosen && listName == null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Stat tiles") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Pick 2 to 4", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Spacer(Modifier.height(4.dp))
                chosen.forEachIndexed { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = true,
                            onCheckedChange = { chosen = chosen - m },
                            colors = CheckboxDefaults.colors(checkedColor = Palette.Accent),
                        )
                        Text(
                            if (m == TileMetric.CUSTOM && listName != null) "List: $listName" else m.label,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { move(i, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { move(i, 1) }, enabled = i < chosen.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                    }
                }
                if (TileMetric.CUSTOM in chosen) {
                    if (lists.isEmpty()) {
                        Text("Save a search in Browse first.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(start = 12.dp))
                    } else {
                        FlowRow(
                            Modifier.padding(start = 12.dp, top = 2.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            lists.forEach { n -> SelectPill(n, n == listName, { listName = n }) }
                        }
                    }
                }
                TileMetric.entries.filter { it !in chosen }.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = false,
                            enabled = !full,
                            onCheckedChange = { chosen = chosen + m },
                            colors = CheckboxDefaults.colors(checkedColor = Palette.Accent),
                        )
                        Text(
                            m.label, style = MaterialTheme.typography.bodyMedium,
                            color = if (full) Palette.Muted else Palette.Text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen.size >= TileMetric.MIN && !needsList,
                onClick = { HomePrefs.updateTiles(chosen, listName); onDismiss() },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { chosen = TileMetric.DEFAULT; listName = null }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
