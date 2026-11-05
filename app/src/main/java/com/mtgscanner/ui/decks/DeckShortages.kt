package com.mtgscanner.ui.decks

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums

/**
 * Decks › New › Shortages across decks: cards several decks play where you don't own enough copies to keep them
 * all built at once. Decks tagged Want or Maybe aren't built, so they're left out unless picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckShortagesSheet(vm: DecksViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val decks by vm.decks.collectAsStateWithLifecycle()
    val owned by vm.ownedByName.collectAsStateWithLifecycle()
    var withUnbuilt by rememberSaveable { mutableStateOf(false) }
    val unbuilt = setOf(DeckStatus.WANT.label, DeckStatus.MAYBE.label)
    val counted = decks.orEmpty().filter { withUnbuilt || deckTag(ctx, it) !in unbuilt }
    val rows = remember(counted, owned) {
        SharedCards.shortages(counted.map { it.deck.name to com.mtgscanner.data.ParsedDecks.of(it.deck) }, owned)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4).navigationBarsPadding()) {
            Text("Shortages across decks", style = MaterialTheme.typography.titleLarge)
            Text(
                if (rows.isEmpty()) "Enough copies to keep all ${counted.size} decks built at once"
                else "Short ${rows.sumOf { it.short }} copies to keep all ${counted.size} decks built at once",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                SelectPill("Include Want and Maybe", withUnbuilt, { withUnbuilt = !withUnbuilt })
                if (rows.isNotEmpty()) TextButton(onClick = { vm.wishShort(rows) }) { Text("Wish for them") }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                items(rows, key = { it.name }) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                r.decks.joinToString(" · ") { (d, n) -> if (n > 1) "$d ×$n" else d },
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Short ${r.short}", style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.AccentText)
                            Text("own ${r.owned} of ${r.needed}", style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted)
                        }
                    }
                    HorizontalDivider(color = Palette.Rule)
                }
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
}
