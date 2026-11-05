package com.mtgscanner.ui.collection

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.BoxPrefs
import com.mtgscanner.data.BoxReminders
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.SortPrefs
import com.mtgscanner.ui.components.SelectPill
import androidx.compose.foundation.layout.Arrangement
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.components.ruleBelow
import androidx.compose.ui.graphics.Color

/**
 * Every sorting rule in one place: the order Auto-sort checks boxes in, the fallback box, keep a
 * playset, the bulk floor, the price buffer and the box check reminder. Changes save as you make them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoSortOrderSheet(
    boxes: List<BoxSummary>,
    onReorder: (List<Long>) -> Unit,
    onFallback: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    remember { BoxPrefs.load(ctx) }
    var order by remember { mutableStateOf(boxes.map { it.id }) }
    var fallback by remember { mutableStateOf(boxes.firstOrNull { it.fallback }?.id) }
    val byId = boxes.associateBy { it.id }
    // Re-checked as boxes move up and down.
    val warnings = remember(order, boxes) { deadBoxNotes(order.mapNotNull { byId[it] }) }

    fun move(from: Int, to: Int) {
        if (to !in order.indices) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        onReorder(order)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = Space.s4).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text("Auto-sort order", style = MaterialTheme.typography.titleLarge)
            Text(
                "Cards go to the first box that fits.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(Space.s3))
            order.forEachIndexed { i, id ->
                val b = byId[id] ?: return@forEachIndexed
                val rules = remember(b.rules) { BoxRules.fromJson(b.rules) }
                Surface(Modifier.fillMaxWidth().padding(vertical = Space.s1), shape = RoundedCornerShape(Corners.Small), color = Palette.SurfaceHigh) {
                    Row(Modifier.padding(start = Space.s3), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.width(22.dp))
                        Icon(boxIcon(b.kind), null, tint = boxColor(b.color), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Space.s3))
                        Column(Modifier.weight(1f).padding(vertical = Space.s3)) {
                            Text(b.name, style = MaterialTheme.typography.titleSmall)
                            Text(rules.describe(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
                            warnings[id]?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Danger, maxLines = 1)
                            }
                        }
                        IconButton(onClick = { move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { move(i, i + 1) }, enabled = i < order.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down") }
                    }
                }
            }

            Spacer(Modifier.height(Space.s4))
            HorizontalDivider(color = Palette.Rule)
            Text("When nothing fits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.s4))
            Text(
                "Box for cards that fit nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(Space.s2))
            FallbackChoice("No fallback box", fallback == null) { fallback = null; onFallback(null) }
            order.forEach { id ->
                val b = byId[id] ?: return@forEach
                // Trade binders and deck boxes aren't for leftovers (still shown if one already is).
                if ((b.kind == "trade" || b.kind == "deck") && fallback != id) return@forEach
                FallbackChoice(b.name, fallback == id) { fallback = id; onFallback(id) }
            }

            Spacer(Modifier.height(Space.s4))
            HorizontalDivider(color = Palette.Rule)
            Text("Keep a playset", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.s4))
            Text("Extras go to another box.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Spacer(Modifier.height(Space.s2))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                listOf(0 to "Off", 1 to "1", 4 to "4").forEach { (n, label) ->
                    SelectPill(label, SortPrefs.playsetLimit == n, { SortPrefs.updatePlayset(n, SortPrefs.extrasBoxId) })
                }
            }
            if (SortPrefs.playsetLimit > 0) {
                Spacer(Modifier.height(Space.s2))
                BoxChoice("Extras", boxes, SortPrefs.extrasBoxId) { SortPrefs.updatePlayset(SortPrefs.playsetLimit, it) }
            }

            Spacer(Modifier.height(Space.s4))
            HorizontalDivider(color = Palette.Rule)
            Text("Bulk floor", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.s4))
            Text("Cheaper cards go to bulk.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Spacer(Modifier.height(Space.s2))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                SelectPill("Off", SortPrefs.bulkFloor == null, { SortPrefs.updateBulk(null, SortPrefs.bulkBoxId) })
                BULK_FLOORS.forEach { f ->
                    SelectPill(
                        "$" + String.format(java.util.Locale.US, "%.2f", f),
                        SortPrefs.bulkFloor == f,
                        { SortPrefs.updateBulk(f, SortPrefs.bulkBoxId) },
                    )
                }
            }
            if (SortPrefs.bulkFloor != null) {
                Spacer(Modifier.height(Space.s2))
                BoxChoice("Bulk", boxes, SortPrefs.bulkBoxId) { SortPrefs.updateBulk(SortPrefs.bulkFloor, it) }
            }

            Spacer(Modifier.height(Space.s4))
            HorizontalDivider(color = Palette.Rule)
            Text("Price buffer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.s4))
            Text(
                "Re-sort leaves cards near a price line where they are.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(Space.s2))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                listOf(0, 5, 10, 20).forEach { pct ->
                    SelectPill(if (pct == 0) "Off" else "±$pct%", SortPrefs.priceBuffer == pct, { SortPrefs.updatePriceBuffer(pct) })
                }
            }

            Spacer(Modifier.height(Space.s4))
            HorizontalDivider(color = Palette.Rule)
            ColorOrderSection()

            Spacer(Modifier.height(Space.s4))
            HorizontalDivider(color = Palette.Rule)
            Text("Box check reminder", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.s4))
            Spacer(Modifier.height(Space.s2))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                BoxReminders.CHOICES.forEach { m ->
                    SelectPill(if (m == 0) "Off" else "$m mo", BoxPrefs.reminderMonths == m, { BoxPrefs.setReminder(ctx, m) })
                }
            }
            Spacer(Modifier.height(Space.s6))
        }
    }
}

@Composable
private fun FallbackChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Palette.Accent))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private val BULK_FLOORS = listOf(0.10, 0.25, 0.50, 1.00)

/** "Extras → Trade binder" chip that opens a box menu. No box picked means the rule does nothing yet. */
@Composable
private fun BoxChoice(label: String, boxes: List<BoxSummary>, current: Long?, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val here = boxes.firstOrNull { it.id == current }
    Box {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(Corners.Small))
                .background(Palette.SurfaceHigh)
                .clickable { open = true }
                .padding(horizontal = Space.s3, vertical = Space.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(boxIcon(here?.kind ?: "box"), null, Modifier.size(16.dp), tint = if (here != null) Palette.AccentText else Palette.Icon)
            Spacer(Modifier.width(Space.s2))
            Text(
                "$label → ${here?.name ?: "pick a box"}",
                style = MaterialTheme.typography.labelLarge,
                color = if (here != null) Palette.Text else Palette.Danger,
                maxLines = 1,
            )
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = Palette.Icon)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (boxes.isEmpty()) DropdownMenuItem(text = { Text("No boxes yet", color = Palette.Muted) }, onClick = { open = false })
            boxes.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.name, color = if (b.id == current) Palette.AccentText else Palette.Text) },
                    leadingIcon = { Icon(boxIcon(b.kind), null, tint = if (b.id == current) Palette.AccentText else Palette.Icon) },
                    onClick = { open = false; onPick(b.id) },
                )
            }
        }
    }
}

/** Front-to-back order of color dividers in every box, moved with up and down arrows. */
@Composable
private fun ColorOrderSection() {
    val order = SortPrefs.colorOrder
    Row(Modifier.fillMaxWidth().padding(top = Space.s4), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Color order", style = MaterialTheme.typography.titleMedium)
            Text("Front to back, for color dividers.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        if (order != com.mtgscanner.data.Sorting.DEFAULT_COLOR_ORDER) {
            androidx.compose.material3.TextButton(onClick = { SortPrefs.updateColorOrder(com.mtgscanner.data.Sorting.DEFAULT_COLOR_ORDER) }) { Text("Reset") }
        }
    }
    Spacer(Modifier.height(Space.s1))
    order.forEachIndexed { i, name ->
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.width(28.dp))
            Text(if (name == "Multicolor") "Multicolor (and color pairs)" else name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { SortPrefs.updateColorOrder(order.swap(i, i - 1)) }, enabled = i > 0) {
                Icon(Icons.Outlined.KeyboardArrowUp, "Move $name up", tint = if (i > 0) Palette.Icon else Palette.Muted.copy(alpha = 0.4f))
            }
            IconButton(onClick = { SortPrefs.updateColorOrder(order.swap(i, i + 1)) }, enabled = i < order.lastIndex) {
                Icon(Icons.Outlined.KeyboardArrowDown, "Move $name down", tint = if (i < order.lastIndex) Palette.Icon else Palette.Muted.copy(alpha = 0.4f))
            }
        }
    }
}

private fun List<String>.swap(a: Int, b: Int): List<String> =
    if (b !in indices) this else toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }
