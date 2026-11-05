package com.mtgscanner.ui.home

import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.Corners
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.outlined.DragHandle
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
import com.mtgscanner.ui.components.PlainGroup
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
        com.mtgscanner.ui.components.EmptyState("No goals yet", action = "Add a goal", onAction = onGoals)
        return
    }
    // On a panel, like Stats. Tapping a goal opens that goal, not just the list.
    PlainGroup(Modifier.padding(horizontal = Space.s4)) {
        Column(Modifier.padding(horizontal = Space.s3, vertical = Space.s1)) {
            val shown = goals.sortedBy { if (it.done) 1 else 0 }.take(3)
            shown.forEachIndexed { i, g -> GoalRow(g, rule = i < shown.lastIndex) { GoalsLaunch.open(g.goal.id); onGoals() } }
        }
    }
}

/** A goal as a plain row: name and count, with a thin progress line under it. */
@Composable
private fun GoalRow(p: GoalProgress, rule: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).then(if (rule) Modifier.ruleBelow() else Modifier).padding(horizontal = Space.s1, vertical = Space.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.goal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            when {
                p.error -> Icon(Icons.Outlined.CloudOff, "Offline", tint = Palette.Icon, modifier = Modifier.size(18.dp))
                !p.loaded -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Palette.Text)
                else -> Text(
                    "${p.have}/${p.total}",
                    style = PriceStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize * 1.15f),
                    color = if (p.done) Palette.Success else Palette.Muted,
                )
            }
        }
        Spacer(Modifier.height(Space.s2))
        LinearProgressIndicator(
            progress = { p.fraction },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
            color = if (p.done) Palette.Success else Palette.Text,
            trackColor = Palette.SurfaceHigh,
            drawStopIndicator = {},
        )
    }
}

/** Cards whose price moved most this week; the label is the change per copy. */
@Composable
internal fun MoversCarousel(movers: List<PriceMover>, onOpenCard: (Long) -> Unit, shared: Set<Long> = emptySet()) {
    LazyRow(contentPadding = PaddingValues(horizontal = Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
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

/** Show, hide and reorder Home sections: drag a row by its handle, or use the arrows. */
@Composable
internal fun EditHomeDialog(onDismiss: () -> Unit, onTiles: () -> Unit) {
    val shown = HomePrefs.sections
    var rows by remember { mutableStateOf(shown.map { it to true } + HomeSection.entries.filter { it !in shown }.map { it to false }) }
    var colorBar by remember { mutableStateOf(HomePrefs.colorBar) }
    /** The row being dragged, and how far it has moved from its slot. */
    var dragging by remember { mutableStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var rowPx by remember { mutableFloatStateOf(1f) }
    fun move(i: Int, by: Int): Boolean {
        val j = i + by
        if (i !in rows.indices || j !in rows.indices) return false
        val l = rows.toMutableList()
        val t = l[i]; l[i] = l[j]; l[j] = t
        rows = l
        return true
    }
    fun save() {
        HomePrefs.updateSections(rows.filter { it.second }.map { it.first })
        HomePrefs.updateColorBar(colorBar)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Home sections") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState(), enabled = dragging < 0)) {
                // Keeps the section changes made so far instead of dropping them.
                TextButton(onClick = { save(); onTiles() }) { Text("Stat tiles…") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(colorBar, { colorBar = it }, colors = CheckboxDefaults.colors(checkedColor = Palette.Text))
                    Text("Color bar", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("Under the value", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.padding(end = Space.s3))
                }
                HorizontalDivider(color = Palette.Rule)
                rows.forEachIndexed { i, (section, on) ->
                    val lifted = i == dragging
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { rowPx = it.height.toFloat().coerceAtLeast(1f) }
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { translationY = if (lifted) dragY else 0f }
                            .background(if (lifted) Palette.SurfaceHigh else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(Corners.Small))
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction("Move up") { move(i, -1) },
                                    CustomAccessibilityAction("Move down") { move(i, 1) },
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = { v -> rows = rows.toMutableList().also { it[i] = section to v } },
                            colors = CheckboxDefaults.colors(checkedColor = Palette.Text),
                        )
                        Text(
                            section.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (on) Palette.Text else Palette.Muted, modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Outlined.DragHandle, "Drag to reorder", tint = Palette.Icon,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(Space.s3)
                                .pointerInput(section) {
                                    detectVerticalDragGestures(
                                        onDragStart = { dragging = rows.indexOfFirst { it.first == section }; dragY = 0f; com.mtgscanner.util.Haptics.hold() },
                                        onDragEnd = { dragging = -1; dragY = 0f },
                                        onDragCancel = { dragging = -1; dragY = 0f },
                                    ) { change, dy ->
                                        change.consume()
                                        dragY += dy
                                        // Past half a row: swap with the neighbour and keep the finger's offset.
                                        while (dragY > rowPx / 2 && move(dragging, 1)) { dragging++; dragY -= rowPx; com.mtgscanner.util.Haptics.step() }
                                        while (dragY < -rowPx / 2 && move(dragging, -1)) { dragging--; dragY += rowPx; com.mtgscanner.util.Haptics.step() }
                                    }
                                },
                        )
                    }
                }
                TextButton(onClick = { rows = HomeOrder.DEFAULT.map { it to true } + HomeSection.entries.filter { it !in HomeOrder.DEFAULT }.map { it to false }; colorBar = true }) {
                    Text("Reset to default", color = Palette.Muted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { save(); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
