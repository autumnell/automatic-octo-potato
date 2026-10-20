package com.mtgscanner.ui.collection

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.ui.components.boxColor
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.theme.Palette

/**
 * The order Auto-sort checks boxes in, and the fallback box for cards nothing fits.
 * Changes save as you make them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoSortOrderSheet(
    boxes: List<BoxSummary>,
    onReorder: (List<Long>) -> Unit,
    onFallback: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var order by remember { mutableStateOf(boxes.map { it.id }) }
    var fallback by remember { mutableStateOf(boxes.firstOrNull { it.fallback }?.id) }
    val byId = boxes.associateBy { it.id }

    fun move(from: Int, to: Int) {
        if (to !in order.indices) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        onReorder(order)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text("Auto-sort order", style = MaterialTheme.typography.titleLarge)
            Text(
                "Each scanned card goes into the first box below whose rules fit. Boxes that take any card are only used when no other box fits.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(10.dp))
            order.forEachIndexed { i, id ->
                val b = byId[id] ?: return@forEachIndexed
                val rules = remember(b.rules) { BoxRules.fromJson(b.rules) }
                Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(14.dp), color = Palette.SurfaceHigh) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.width(22.dp))
                        Icon(boxIcon(b.kind), null, tint = boxColor(b.color), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(b.name, style = MaterialTheme.typography.titleSmall)
                            Text(rules.describe(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
                        }
                        IconButton(onClick = { move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { move(i, i + 1) }, enabled = i < order.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Palette.Outline)
            Text("When nothing fits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
            Text(
                "Pick a box for cards no rules fit. Without one, they go to the first box that takes any card, or you're asked to pick.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(6.dp))
            FallbackChoice("No fallback box", fallback == null) { fallback = null; onFallback(null) }
            order.forEach { id ->
                val b = byId[id] ?: return@forEach
                FallbackChoice(b.name, fallback == id) { fallback = id; onFallback(id) }
            }
            Spacer(Modifier.height(24.dp))
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
