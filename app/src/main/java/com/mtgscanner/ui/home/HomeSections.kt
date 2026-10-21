package com.mtgscanner.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.GoalProgress
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.LineChart
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.goals.GoalCard
import com.mtgscanner.ui.theme.Palette

/** Up to three goals, or a button to add the first one. */
@Composable
internal fun GoalsPreview(goals: List<GoalProgress>, onGoals: () -> Unit) {
    if (goals.isEmpty()) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp), color = Palette.Surface) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("No goals yet", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                TextButton(onClick = onGoals) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add a goal")
                }
            }
        }
        return
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        goals.sortedBy { if (it.done) 1 else 0 }.take(3).forEach { GoalCard(it, onClick = onGoals) }
    }
}

/** Cards whose price moved most this week; the label is the change per copy. */
@Composable
internal fun MoversCarousel(movers: List<PriceMover>, onOpenCard: (Long) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(movers, key = { it.card.id }) { m ->
            val diff = m.now - m.then
            CardTile(
                imageUrl = m.card.imageUrl,
                name = m.card.name,
                price = (if (diff >= 0) "+" else "−") + formatUsd(kotlin.math.abs(diff)),
                quantity = m.card.quantity,
                foil = m.card.foil,
                modifier = Modifier.width(118.dp),
                onClick = { onOpenCard(m.card.id) },
            )
        }
    }
}

/** One box's value over time; pick the box with the pills. Boxes with the most value come first. */
@Composable
internal fun BoxValueChart(data: BoxValues, boxes: List<CardBox>) {
    val ranked = boxes.filter { (data.values[it.id]?.lastOrNull() ?: 0.0) > 0.0 }
        .sortedByDescending { data.values[it.id]?.lastOrNull() ?: 0.0 }
    if (ranked.isEmpty()) return
    var selected by remember { mutableLongStateOf(ranked.first().id) }
    val box = ranked.firstOrNull { it.id == selected } ?: ranked.first()
    val values = data.values[box.id].orEmpty()
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ranked.forEach { b -> SelectPill(b.name, b.id == box.id, { selected = b.id }) }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatUsd(values.lastOrNull()), style = MaterialTheme.typography.titleLarge, color = Palette.AccentText)
                Spacer(Modifier.width(10.dp))
                val first = values.firstOrNull() ?: 0.0
                ChangeText((values.lastOrNull() ?: 0.0) - first, first, MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            LineChart(values, Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(shortDay(data.days.first()), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                Text(shortDay(data.days.last()), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
    }
}

/** Bulk commons and uncommons at a per-1000 rate the user can change. */
@Composable
internal fun BulkCard(cards: Int) {
    var editing by remember { mutableStateOf(false) }
    val rate = HomePrefs.bulkRate
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("≈ ${formatUsd(cards / 1000.0 * rate)}", style = MaterialTheme.typography.titleLarge, color = Palette.AccentText)
                Text(
                    "$cards commons and uncommons under 50¢",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text("Estimate at ${formatUsd(rate)} per 1000", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            TextButton(onClick = { editing = true }) { Text("Rate") }
        }
    }
    if (editing) {
        var text by remember { mutableStateOf(if (rate % 1.0 == 0.0) rate.toInt().toString() else rate.toString()) }
        val parsed = text.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = Palette.Surface,
            title = { Text("Dollars per 1000") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = parsed != null, onClick = { parsed?.let { HomePrefs.updateBulkRate(it) }; editing = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

/** Show, hide and reorder Home sections. */
@Composable
internal fun EditHomeDialog(onDismiss: () -> Unit, onTiles: () -> Unit) {
    val shown = HomePrefs.sections
    var rows by remember { mutableStateOf(shown.map { it to true } + HomeSection.entries.filter { it !in shown }.map { it to false }) }
    fun move(i: Int, by: Int) {
        val j = i + by
        if (j !in rows.indices) return
        val l = rows.toMutableList()
        val t = l[i]; l[i] = l[j]; l[j] = t
        rows = l
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Home sections") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = onTiles) { Text("Stat tiles…") }
                rows.forEachIndexed { i, (section, on) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = { v -> rows = rows.toMutableList().also { it[i] = section to v } },
                            colors = CheckboxDefaults.colors(checkedColor = Palette.Accent),
                        )
                        Text(section.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { move(i, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { move(i, 1) }, enabled = i < rows.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { HomePrefs.updateSections(rows.filter { it.second }.map { it.first }); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
