package com.mtgscanner.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette

/** Show, hide and reorder what the Collection tab shows, like Edit on Home. */
@Composable
internal fun EditCollectionDialog(onDismiss: () -> Unit) {
    val start = CollectionPrefs.layout
    fun rowsOf(l: CollectionLayout) = l.sections.map { it to true } + CollectionSection.entries.filter { it !in l.sections }.map { it to false }
    var rows by remember { mutableStateOf(rowsOf(start)) }
    var hidden by remember { mutableStateOf(start.hidden) }
    var compact by remember { mutableStateOf(start.compact) }
    fun move(i: Int, by: Int) {
        val j = i + by
        if (j !in rows.indices) return
        val l = rows.toMutableList()
        val t = l[i]; l[i] = l[j]; l[j] = t
        rows = l
    }

    fun toggle(p: CollectionPart, on: Boolean) { hidden = if (on) hidden - p else hidden + p }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Collection") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Boxes page", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
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
                Parts(CollectionPart.Group.PAGE, hidden, onToggle = { p, v -> toggle(p, v) })
                Heading("Box cards")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectPill("Full", !compact, { compact = false })
                    SelectPill("Compact", compact, { compact = true })
                }
                // Compact cards are one line: no art, rules, change or meter.
                Parts(
                    CollectionPart.Group.BOX, hidden, onToggle = { p, v -> toggle(p, v) },
                    off = if (compact) setOf(CollectionPart.ART, CollectionPart.RULES, CollectionPart.CHANGE, CollectionPart.FILL) else emptySet(),
                )
                Heading("Tabs")
                Parts(CollectionPart.Group.TABS, hidden, onToggle = { p, v -> toggle(p, v) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                CollectionPrefs.update(CollectionLayout(rows.filter { it.second }.map { it.first }, hidden, compact))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val d = CollectionLayout.DEFAULT
                    rows = rowsOf(d); hidden = d.hidden; compact = d.compact
                }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
}

/** A checkbox for each part in [group]; parts in [off] can't be changed in the current card style. */
@Composable
private fun Parts(
    group: CollectionPart.Group,
    hidden: Set<CollectionPart>,
    onToggle: (CollectionPart, Boolean) -> Unit,
    off: Set<CollectionPart> = emptySet(),
) {
    CollectionPart.entries.filter { it.group == group }.forEach { p ->
        val enabled = p !in off
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = p !in hidden,
                onCheckedChange = { v -> onToggle(p, v) },
                enabled = enabled,
                colors = CheckboxDefaults.colors(checkedColor = Palette.Accent),
            )
            Text(
                p.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (enabled) Palette.Text else Palette.Muted,
            )
        }
    }
}
