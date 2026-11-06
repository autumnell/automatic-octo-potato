package com.mtgscanner.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.mtgscanner.data.DeckIdeasMath
import com.mtgscanner.data.OwnedCombo
import com.mtgscanner.data.ownedCombos
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.Spinner
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space

/** The combos found so far: null while reading, and [failed] when the combo list couldn't be had. */
private data class CombosRun(val combos: List<OwnedCombo>? = null, val failed: Boolean = false)

/**
 * Decks › New › Combos you could build: Commander Spellbook combos whose every card you own, with the box (or deck)
 * each card is in. Tap one for its page on Commander Spellbook.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionCombosSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val repo = (ctx.applicationContext as com.mtgscanner.MtgApp).repository
    val run by produceState(CombosRun()) { value = repo.ownedCombos()?.let { CombosRun(it) } ?: CombosRun(emptyList(), failed = true) }
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text("Combos you could build", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.s4))
            Text(
                "From Commander Spellbook, every card already in your collection",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s4),
            )
            Spacer(Modifier.height(Space.s2))
            val combos = run.combos
            when {
                combos == null -> Column(Modifier.fillMaxWidth().padding(vertical = Space.s6), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spinner()
                    Spacer(Modifier.height(Space.s2))
                    Text("Checking your cards…", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                run.failed -> EmptyState("Couldn't get the combo list", Modifier.padding(vertical = Space.s6))
                combos.isEmpty() -> EmptyState("No complete combos in your collection yet", Modifier.padding(vertical = Space.s6))
                else -> {
                    SearchField(query, { query = it }, Modifier.padding(horizontal = Space.s4), placeholder = "Find a card or result")
                    val shown = remember(combos, query) {
                        val q = query.trim()
                        if (q.isEmpty()) combos else combos.filter { c ->
                            c.pieces.any { it.first.contains(q, ignoreCase = true) } || c.produces.any { it.contains(q, ignoreCase = true) }
                        }
                    }
                    Text(
                        if (shown.size == 1) "1 combo" else "${shown.size} combos",
                        style = MaterialTheme.typography.labelLarge, color = Palette.Muted,
                        modifier = Modifier.padding(start = Space.s4, end = Space.s4, top = Space.s3, bottom = Space.s1),
                    )
                    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
                        items(shown, key = { it.id }) { c ->
                            ComboRow(c) { openLink(ctx, DeckIdeasMath.comboUrl(c.id)) }
                            HorizontalDivider(color = Palette.Rule)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
}

@Composable
private fun ComboRow(c: OwnedCombo, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.s4, vertical = Space.s3)) {
        Text(
            c.produces.joinToString(" · ").ifEmpty { "Combo" }, style = MaterialTheme.typography.bodyLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        c.pieces.forEach { (name, where) ->
            Text(
                if (where.isEmpty()) name else "$name · $where", style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
