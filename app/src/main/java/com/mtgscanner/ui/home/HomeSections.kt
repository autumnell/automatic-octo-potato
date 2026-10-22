package com.mtgscanner.ui.home

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.GoalProgress
import com.mtgscanner.ui.components.CardTile
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.Palette
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.outlined.CloudOff
import com.mtgscanner.ui.theme.PriceStyle
import androidx.compose.foundation.shape.CircleShape
import com.mtgscanner.ui.detail.sharedCard
import com.mtgscanner.ui.components.ruleBelow

/** Up to three goals, or a button to add the first one. */
@Composable
internal fun GoalsPreview(goals: List<GoalProgress>, onGoals: () -> Unit) {
    if (goals.isEmpty()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("No goals yet", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
            TextButton(onClick = onGoals) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Add a goal")
            }
        }
        return
    }
    Column {
        // Tapping a goal opens that goal, not just the list.
        goals.sortedBy { if (it.done) 1 else 0 }.take(3).forEach { g -> GoalRow(g) { GoalsLaunch.open(g.goal.id); onGoals() } }
    }
}

/** A goal as a plain row: name and count, with a thin progress line under it. */
@Composable
private fun GoalRow(p: GoalProgress, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp).ruleBelow().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.goal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            when {
                p.error -> Icon(Icons.Outlined.CloudOff, "Offline", tint = Palette.Icon, modifier = Modifier.size(16.dp))
                !p.loaded -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Palette.Accent)
                else -> Text(
                    "${p.have}/${p.total}",
                    style = PriceStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize * 1.15f),
                    color = if (p.done) Palette.Success else Palette.Muted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { p.fraction },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
            color = if (p.done) Palette.Success else Palette.Accent,
            trackColor = Palette.SurfaceHigh,
            drawStopIndicator = {},
        )
    }
}

/** Cards whose price moved most this week; the label is the change per copy. */
@Composable
internal fun MoversCarousel(movers: List<PriceMover>, onOpenCard: (Long) -> Unit, shared: Set<Long> = emptySet()) {
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
                imageModifier = if (m.card.id in shared) Modifier.sharedCard(m.card.id) else Modifier,
                onClick = { onOpenCard(m.card.id) },
            )
        }
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
                // Keeps the section changes made so far instead of dropping them.
                TextButton(onClick = { HomePrefs.updateSections(rows.filter { it.second }.map { it.first }); onTiles() }) { Text("Stat tiles…") }
                rows.forEachIndexed { i, (section, on) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = { v -> rows = rows.toMutableList().also { it[i] = section to v } },
                            colors = CheckboxDefaults.colors(checkedColor = Palette.Accent),
                        )
                        Text(section.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { move(i, -1) }, enabled = i > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { move(i, 1) }, enabled = i < rows.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down") }
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
