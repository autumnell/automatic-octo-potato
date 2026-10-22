package com.mtgscanner.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.ImportPlan
import com.mtgscanner.data.db.Box
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums

/** Cards listed under an opened box before "and N more". */
private const val SHOWN_PER_BOX = 40

/**
 * What an import will add, before anything is saved: copies per box (tap a box to see its cards)
 * and the cards not found, each with Retry, Edit and Remove.
 */
@Composable
internal fun ImportPreviewDialog(
    plan: ImportPlan,
    boxes: Map<Long, Box>,
    offline: Boolean,
    busy: String?,
    onRetry: (index: Int, name: String?) -> Unit,
    onRetryAll: () -> Unit,
    onRemove: (Int) -> Unit,
    onChangeBox: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var open by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val added = plan.added
    val missing = plan.notFound
    val idle = busy == null

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Palette.Surface,
        title = { Text(if (added == 1) "Add 1 card?" else "Add $added cards?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(destination(plan.choice, boxes), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                    TextButton(onClick = onChangeBox, enabled = idle) { Text("Change") }
                }
                busy?.let {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Palette.Accent)
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (offline) {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudOff, null, tint = Palette.Danger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Offline. Retry when back online.", style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
                    }
                }

                plan.perBox().forEach { (id, n) ->
                    val box = boxes[id]
                    val isOpen = open == id
                    Row(
                        Modifier.fillMaxWidth().clickable { open = if (isOpen) null else id }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (box == null) Icons.Filled.AddBox else boxIcon(box.kind), null,
                            tint = Palette.Icon, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            box?.name ?: "New box \"Imported\"",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text("$n", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Palette.AccentText)
                        Icon(if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (isOpen) "Hide cards" else "Show cards", tint = Palette.Icon)
                    }
                    if (isOpen) {
                        val inBox = plan.rows.indices.filter { plan.targets[it] == id }
                        inBox.take(SHOWN_PER_BOX).forEach { i ->
                            val name = plan.found[i]?.name ?: plan.rows[i].name
                            Text(
                                "${plan.rows[i].quantity}  $name" + if (plan.rows[i].foil) " ✦" else "",
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                                modifier = Modifier.padding(start = 28.dp, bottom = 2.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (inBox.size > SHOWN_PER_BOX) {
                            Text("and ${inBox.size - SHOWN_PER_BOX} more", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(start = 28.dp, bottom = 4.dp))
                        }
                    }
                }

                val other = plan.otherPrinting
                if (other.isNotEmpty()) {
                    HorizontalDivider(color = Palette.Outline, modifier = Modifier.padding(vertical = 6.dp))
                    Text("Other printing (${other.sumOf { plan.rows[it].quantity }})", style = MaterialTheme.typography.titleSmall, color = Palette.GoldText)
                    other.forEach { i ->
                        val r = plan.rows[i]
                        val c = plan.found[i] ?: return@forEach
                        val asked = (r.setCode?.uppercase() ?: "") + (r.collectorNumber?.let { " #$it" } ?: "")
                        Text("${r.quantity}  ${r.name}", style = MaterialTheme.typography.bodyMedium.merge(TabularNums), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "No $asked · got ${c.set.uppercase()} #${c.collectorNumber}",
                            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (missing.isNotEmpty()) {
                    HorizontalDivider(color = Palette.Outline, modifier = Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Not found (${missing.sumOf { plan.rows[it].quantity }})",
                            style = MaterialTheme.typography.titleSmall, color = Palette.Danger, modifier = Modifier.weight(1f),
                        )
                        if (missing.size > 1) TextButton(onClick = onRetryAll, enabled = idle) { Text("Retry all") }
                    }
                    missing.forEach { i ->
                        val r = plan.rows[i]
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${r.quantity}  ${r.name}", style = MaterialTheme.typography.bodyMedium.merge(TabularNums), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                r.setCode?.let { Text(it.uppercase() + (r.collectorNumber?.let { n -> " #$n" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
                            }
                            IconButton(onClick = { onRetry(i, null) }, enabled = idle) { Icon(Icons.Filled.Refresh, "Retry", tint = Palette.Accent) }
                            IconButton(onClick = { editing = i }, enabled = idle) { Icon(Icons.Filled.Edit, "Edit name", tint = Palette.Icon) }
                            IconButton(onClick = { onRemove(i) }, enabled = idle) { Icon(Icons.Filled.Close, "Remove", tint = Palette.Icon) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = idle && added > 0) { Text(if (added > 0) "Add $added" else "Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )

    val editIndex = editing
    val editRow = editIndex?.let { plan.rows.getOrNull(it) }
    if (editIndex != null && editRow != null) {
        val i: Int = editIndex
        var name by remember(i) { mutableStateOf(editRow.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = Palette.Surface,
            title = { Text("Card name") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Accent),
                )
            },
            confirmButton = {
                TextButton(onClick = { editing = null; onRetry(i, name) }, enabled = name.isNotBlank()) { Text("Look up") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

/** Where the cards were sent: "Auto-sort", a box, or a new box. */
private fun destination(choice: Long, boxes: Map<Long, Box>): String = when (choice) {
    Box.AUTO_SORT -> "Auto-sort by box rules"
    ImportPlan.NEW_BOX -> "Into a new box"
    else -> "Into ${boxes[choice]?.name ?: "a new box"}"
}
