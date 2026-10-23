package com.mtgscanner.ui.collection

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

/** Pick where a full box splits in two, with how many cards each half gets, then confirm. */
@Composable
internal fun SplitBoxDialog(box: BoxSummary, cards: List<CollectionCard>?, onSplit: (Char) -> Unit, onDismiss: () -> Unit) {
    val rules = remember(box.rules) { BoxRules.fromJson(box.rules) }
    val letters = remember(rules) { Sorting.splitLetters(rules.nameFrom, rules.nameTo) }
    // Cards outside the box's own name range stay in the first half.
    val inRange = remember(cards, rules) { cards.orEmpty().filter { rules.inNameRange(it.name) }.map { it.name to it.quantity } }
    val outside = remember(cards, rules) { cards.orEmpty().filterNot { rules.inNameRange(it.name) }.sumOf { it.quantity } }
    val suggested = remember(inRange, letters) { Sorting.splitLetter(inRange)?.takeIf { it in letters } }
    var picked by remember(box.id) { mutableStateOf<Char?>(null) }
    val at: Char? = picked ?: suggested ?: letters.getOrNull(letters.size / 2)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Split ${box.name}") },
        text = {
            Column {
                if (at == null) {
                    Text("This box can't split further.")
                } else {
                    Text("Second box starts at", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        letters.forEach { l -> SelectPill(l.toString(), l == at, { picked = l }) }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (cards == null) {
                        Text("Counting…", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    } else {
                        val (first, second) = Sorting.splitCounts(inRange, at)
                        val from = rules.nameFrom ?: "A"
                        val to = rules.nameTo ?: "Z"
                        HalfRow("$from–${at - 1}", first + outside)
                        Spacer(Modifier.height(6.dp))
                        HalfRow("$at–$to", second)
                    }
                }
            }
        },
        confirmButton = {
            if (at != null && cards != null) TextButton(onClick = { onSplit(at) }) { Text("Split") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HalfRow(range: String, copies: Int) {
    Surface(shape = RoundedCornerShape(Corners.Medium), color = Palette.SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(range, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (copies == 1) "1 card" else "$copies cards", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
        }
    }
}
