package com.mtgscanner.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.Deck
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.Flow

/** Box › Move to a deck: a new deck made from the box, or one of your decks. [onPick] gets 0 for a new deck. */
@Composable
internal fun BoxToDeckDialog(box: BoxSummary, decksFlow: Flow<List<Deck>>, onDismiss: () -> Unit, onPick: (deckId: Long, name: String) -> Unit) {
    val decks by decksFlow.collectAsStateWithLifecycle(emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Move ${box.cards} cards to", maxLines = 1) },
        text = {
            Column {
                Text(
                    "New deck: ${box.name}", style = MaterialTheme.typography.bodyLarge, color = Palette.AccentText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(0L, box.name) }.padding(vertical = 12.dp),
                )
                if (decks.isNotEmpty()) {
                    HorizontalDivider(color = Palette.Rule)
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(decks.sortedBy { it.name.lowercase() }, key = { it.id }) { d ->
                            Text(
                                d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clickable { onPick(d.id, d.name) }.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
                Text("Cards the list doesn't have are added to it", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
