package com.mtgscanner.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.MtgApp
import com.mtgscanner.data.SortPrefs
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.theme.Palette

private val bulkFloors = listOf(0.10, 0.25, 0.50, 1.00)

/** Keep-a-playset and bulk floor: where extra and cheap copies go. */
@Composable
fun SortingSection() {
    val ctx = LocalContext.current
    val repo = remember { (ctx.applicationContext as MtgApp).repository }
    val flow = remember { repo.observeBoxSummaries() }
    val boxes by flow.collectAsStateWithLifecycle(emptyList())

    Card {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Keep a playset", style = MaterialTheme.typography.titleSmall)
            Text("Extras go to another box", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Spacer(Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Off", 1 to "1", 4 to "4").forEach { (n, label) ->
                    SelectPill(label, SortPrefs.playsetLimit == n, { SortPrefs.updatePlayset(n, SortPrefs.extrasBoxId) })
                }
            }
            if (SortPrefs.playsetLimit > 0) {
                Spacer(Modifier.padding(top = 8.dp))
                BoxChoice("Extras", boxes, SortPrefs.extrasBoxId) { SortPrefs.updatePlayset(SortPrefs.playsetLimit, it) }
            }
        }
        HorizontalDivider(color = Palette.Outline)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Bulk floor", style = MaterialTheme.typography.titleSmall)
            Text("Cheaper cards go to bulk", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Spacer(Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectPill("Off", SortPrefs.bulkFloor == null, { SortPrefs.updateBulk(null, SortPrefs.bulkBoxId) })
                bulkFloors.forEach { f ->
                    SelectPill(
                        "$" + String.format(java.util.Locale.US, "%.2f", f),
                        SortPrefs.bulkFloor == f,
                        { SortPrefs.updateBulk(f, SortPrefs.bulkBoxId) },
                    )
                }
            }
            if (SortPrefs.bulkFloor != null) {
                Spacer(Modifier.padding(top = 8.dp))
                BoxChoice("Bulk", boxes, SortPrefs.bulkBoxId) { SortPrefs.updateBulk(SortPrefs.bulkFloor, it) }
            }
        }
    }
}

/** "Extras → Trade binder" chip that opens a box menu. No box picked means the rule does nothing yet. */
@Composable
private fun BoxChoice(label: String, boxes: List<BoxSummary>, current: Long?, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val here = boxes.firstOrNull { it.id == current }
    Box {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(50))
                .background(Palette.SurfaceHigh)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(boxIcon(here?.kind ?: "box"), null, Modifier.size(16.dp), tint = if (here != null) Palette.AccentText else Palette.Muted)
            Spacer(Modifier.width(6.dp))
            Text(
                "$label → ${here?.name ?: "pick a box"}",
                style = MaterialTheme.typography.labelLarge,
                color = if (here != null) Palette.Text else Palette.Danger,
                maxLines = 1,
            )
            Icon(Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = Palette.Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (boxes.isEmpty()) DropdownMenuItem(text = { Text("No boxes yet", color = Palette.Muted) }, onClick = { open = false })
            boxes.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.name, color = if (b.id == current) Palette.AccentText else Palette.Text) },
                    leadingIcon = { Icon(boxIcon(b.kind), null, tint = if (b.id == current) Palette.AccentText else Palette.Muted) },
                    onClick = { open = false; onPick(b.id) },
                )
            }
        }
    }
}
