package com.mtgscanner.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.data.PreconDeck
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums

/** Decks › New › Precon decks: preconstructed decks from MTGJSON, newest first; open one to see how much you own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreconsSheet(vm: DecksViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadPrecons() }
    val list by vm.precons.collectAsStateWithLifecycle()
    val open by vm.precon.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var onlyCommander by rememberSaveable { mutableStateOf(true) }
    val shown = remember(list, query, onlyCommander) {
        val q = query.trim()
        list.orEmpty().filter { (!onlyCommander || it.isCommander) && (q.isEmpty() || it.name.contains(q, true) || it.code.equals(q, true)) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Precon decks", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            SearchField(query, { query = it }, placeholder = "Search precons")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectPill("Commander", onlyCommander, { onlyCommander = true })
                SelectPill("All kinds", !onlyCommander, { onlyCommander = false })
            }
            val l = list
            when {
                l == null -> com.mtgscanner.ui.components.PlaceholderRows(4, Modifier.padding(vertical = 12.dp))
                l.isEmpty() -> EmptyState("Couldn't get the precon list", Modifier.padding(vertical = 24.dp), action = "Try again", onAction = { vm.loadPrecons(fresh = true) })
                shown.isEmpty() -> EmptyState("No precons found", Modifier.padding(vertical = 24.dp))
                else -> LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
                    items(shown, key = { it.fileName }) { e ->
                        Column(Modifier.fillMaxWidth().clickable { vm.openPrecon(e) }.padding(vertical = 10.dp)) {
                            Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(e.code, e.releaseDate.take(4), e.type.takeIf { !onlyCommander }).filterNot { it.isNullOrBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                            )
                        }
                        HorizontalDivider(color = Palette.Rule)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    open?.let { (entry, deck, failed) ->
        PreconDeckSheet(entry.name, deck, failed, vm, onDismiss = { vm.openPrecon(null) })
    }
}

/** One precon: how much of it you own, what's missing, and saving it as a deck. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreconDeckSheet(name: String, deck: PreconDeck?, failed: Boolean, vm: DecksViewModel, onDismiss: () -> Unit) {
    val owned by vm.ownedByName.collectAsStateWithLifecycle()
    val clip = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            when {
                failed -> EmptyState("Couldn't get this deck", Modifier.padding(vertical = 24.dp))
                deck == null -> com.mtgscanner.ui.components.PlaceholderRows(4, Modifier.padding(vertical = 12.dp))
                else -> {
                    val have = deck.ownedCopies(owned)
                    val missing = remember(deck, owned) { deck.missing(owned) }
                    Text(
                        "You own $have of ${deck.total}" + if (deck.total > 0) " (${have * 100 / deck.total}%)" else "",
                        style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = { vm.savePrecon(deck); onDismiss() }) { Text("Save as deck") }
                        if (missing.isNotEmpty()) TextButton(onClick = { vm.wishPrecon(deck) }) { Text("Wish for missing") }
                        if (missing.isNotEmpty()) TextButton(onClick = {
                            clip.setText(AnnotatedString(missing.joinToString("\n") { (n, c) -> "$c $n" }))
                            Undo.offer("Missing cards copied")
                        }) { Text("Copy missing") }
                    }
                    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.6f)) {
                        if (missing.isNotEmpty()) item(key = "m") { Label("Missing · ${missing.sumOf { it.second }}") }
                        items(missing, key = { "m" + it.first }) { (n, c) ->
                            Text(if (c > 1) "$c $n" else n, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                        }
                        val all = deck.cards.filter { it.section != "Sideboard" }
                        item(key = "a") { Label("The deck · ${deck.total}") }
                        items(all, key = { "a" + it.section + it.name + it.set + it.number }) { c ->
                            Text(
                                (if (c.count > 1) "${c.count} " else "") + c.name + if (c.section == "Commander") " · commander" else "",
                                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}
