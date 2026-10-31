package com.mtgscanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.BoxLayout
import com.mtgscanner.data.CardOrder
import com.mtgscanner.data.Divider
import com.mtgscanner.data.SortPreset
import com.mtgscanner.ui.theme.Palette

/** "Color → Type → A–Z" for a preset, the levels for a custom order, or null when the box keeps no order. */
fun sortSummary(key: String): String? {
    val layout = BoxLayout.of(key)
    if (!layout.needsFacts) return null
    return SortPreset.of(key)?.label ?: layout.label()
}

/**
 * How cards are ordered in a box: one tap on a ready-made order, or Custom for up to three levels of dividers
 * and the order of cards behind them. [key] is a [BoxLayout] key.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortPicker(key: String, onKey: (String) -> Unit) {
    val layout = BoxLayout.of(key)
    val preset = SortPreset.of(key)
    var custom by remember { mutableStateOf(preset == null && layout.needsFacts) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectPill("None", !custom && !layout.needsFacts, { custom = false; onKey("") })
        SortPreset.ALL.forEach { p -> SelectPill(p.label, !custom && preset == p, { custom = false; onKey(p.key) }) }
        SelectPill("Custom", custom, { custom = true })
    }
    if (!custom) return
    val levels = layout.levels
    Column(Modifier.padding(top = 12.dp)) {
        // One more level than is picked, up to the most a box can have.
        for (i in 0 until minOf(levels.size + 1, BoxLayout.MAX_LEVELS)) {
            Heading(if (i == 0) "Dividers" else "Then")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectPill("None", levels.size <= i, { onKey(BoxLayout(levels.take(i), layout.order).key) })
                Divider.entries.filter { it != Divider.NONE && it !in levels.take(i) }.forEach { d ->
                    SelectPill(d.label, levels.getOrNull(i) == d, {
                        onKey(BoxLayout(BoxLayout.clean(levels.take(i) + d + levels.drop(i + 1)), layout.order).key)
                    })
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Heading("Cards behind each divider")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardOrder.entries.forEach { o -> SelectPill(o.label, layout.order == o, { onKey(layout.copy(order = o).key) }) }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(bottom = 6.dp))
}
