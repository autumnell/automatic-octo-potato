package com.mtgscanner.ui.collection

import com.mtgscanner.util.plural
import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.BoxChange
import com.mtgscanner.data.BoxPlanResult
import com.mtgscanner.data.BoxPlans
import com.mtgscanner.data.PlanRow
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.ui.components.BoxDraft
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.components.PlaceholderRows

/** A box plan being tried: the boxes to make or change, and where the cards would go (null while working it out). */
data class PlanUi(val title: String, val changes: List<BoxChange>, val result: BoxPlanResult<CollectionCard>?)

/** A box editor draft as a plan change; [id] null makes a new box. */
fun BoxDraft.toChange(id: Long?): BoxChange = BoxChange(id, name, kind, rules, color, capacity, divider)

/** Common box sizes, for boxes with no capacity set. */
private val BOX_SIZES = listOf(400, 800, 1000, 5000)

/**
 * "Try a box plan first": per-box counts if the plan were saved, and how many real boxes each would need.
 * Apply saves the boxes and moves the cards in one go (one Undo); Discard leaves everything as it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxPlanSheet(plan: PlanUi, onApply: () -> Unit, onDiscard: () -> Unit) {
    var boxSize by rememberSaveable { mutableIntStateOf(800) }
    ModalBottomSheet(
        onDismissRequest = onDiscard,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4).navigationBarsPadding()) {
            Text("Try: ${plan.title}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val r = plan.result
            if (r == null) {
                PlaceholderRows(3, Modifier.padding(vertical = Space.s3), rowHeight = 44.dp, color = Palette.SurfaceHigh)
                return@Column
            }
            Text(
                when (r.cardsMoving) {
                    0 -> "No cards would move"
                    1 -> "1 card would move"
                    else -> "${plural(r.cardsMoving, "card")} would move"
                },
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
            )
            Spacer(Modifier.height(Space.s3))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Box holds", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                BOX_SIZES.forEach { n -> SelectPill("$n", boxSize == n, { boxSize = n }) }
            }
            Spacer(Modifier.height(Space.s2))
            val (touched, same) = r.rows.partition { it.isNew || it.changed || it.change != 0 }
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                touched.forEach { PlanLine(it, boxSize) }
                if (same.isNotEmpty()) {
                    Text(
                        if (same.size == 1) "1 other box stays the same" else "${same.size} other boxes stay the same",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                        modifier = Modifier.padding(vertical = Space.s3),
                    )
                }
            }
            Spacer(Modifier.height(Space.s4))
            GradientButton("Apply", onApply, Modifier.fillMaxWidth())
            TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text("Discard plan", color = Palette.Muted) }
            Spacer(Modifier.height(Space.s2))
        }
    }
}

@Composable
private fun PlanLine(row: PlanRow, boxSize: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (row.isNew || row.changed) {
                    Spacer(Modifier.size(8.dp))
                    Text(if (row.isNew) "New" else "Changed", style = MaterialTheme.typography.labelSmall, color = Palette.Text)
                }
            }
            val need = row.boxesNeeded(boxSize)
            Text(
                BoxPlans.label(row, boxSize) + if (row.over > 0) " · ${row.over} over" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (row.over > 0 || (need ?: 1) > 1) Palette.GoldText else Palette.Muted,
            )
        }
        val c = row.change
        if (c != 0) {
            Text(
                if (c > 0) "+$c" else "−${-c}",
                style = MaterialTheme.typography.titleSmall,
                color = if (c > 0) Palette.Success else Palette.Muted,
            )
        }
    }
}
