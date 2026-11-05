package com.mtgscanner.ui.decks

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.data.AlmostDeck
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums

/** The look at decks you could nearly build: loading, then the decks found (empty and [failed] when none). */
data class AlmostRun(
    val loading: Boolean = true,
    val progress: Float = 0f,
    val decks: List<AlmostDeck> = emptyList(),
    val failed: Boolean = false,
)

/**
 * Decks › New › Decks you can almost build: Commander decks (EDHREC's most played cards for commanders you own and
 * the week's popular ones), most owned first. Tap one for what's missing, to save it as a deck or wish for the rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlmostDecksSheet(vm: DecksViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadAlmost() }
    val run by vm.almost.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<AlmostDeck?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = Space.s4).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Decks you can almost build", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (run?.loading == false) TextButton(onClick = { vm.loadAlmost(fresh = true) }) { Text("Refresh") }
            }
            Text("Commanders you own and this week's popular ones, by EDHREC's most played cards", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Spacer(Modifier.height(Space.s2))
            val r = run
            when {
                r == null || r.loading -> Column(Modifier.fillMaxWidth().padding(vertical = Space.s6), horizontalAlignment = Alignment.CenterHorizontally) {
                    if ((r?.progress ?: 0f) > 0f) LinearProgressIndicator(progress = { r?.progress ?: 0f }, color = Palette.Text, modifier = Modifier.fillMaxWidth(0.6f))
                    else com.mtgscanner.ui.components.Spinner()
                    Spacer(Modifier.height(Space.s2))
                    Text("Reading EDHREC…", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                r.decks.isEmpty() -> EmptyState("Couldn't reach EDHREC", Modifier.padding(vertical = Space.s6), action = "Try again", onAction = { vm.loadAlmost(fresh = true) })
                else -> LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
                    items(r.decks, key = { it.commander }) { d ->
                        AlmostRow(d, onClick = { open = d })
                        HorizontalDivider(color = Palette.Rule)
                    }
                }
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
    open?.let { d -> AlmostDeckSheet(d, onSave = { vm.saveAlmost(d); open = null }, onWish = { vm.wishAlmost(d) }, onDismiss = { open = null }) }
}

@Composable
private fun AlmostRow(d: AlmostDeck, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Space.s3), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(d.commander, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (d.ownCommander) "You own the commander · " else "") + "${d.missing.size} missing",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
        }
        Text("${(d.share * 100).toInt()}%", style = MaterialTheme.typography.titleMedium.merge(TabularNums))
    }
}

/** One deck: what you have and what's missing, to save as a deck, wish for or copy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlmostDeckSheet(d: AlmostDeck, onSave: () -> Unit, onWish: () -> Unit, onDismiss: () -> Unit) {
    val clip = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = Space.s4).navigationBarsPadding()) {
            Text(d.commander, style = MaterialTheme.typography.titleLarge)
            Text(
                "You own ${d.cards.size - d.missing.size} of ${d.cards.size} (${(d.share * 100).toInt()}%)" + if (d.ownCommander) " and the commander" else "",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            Spacer(Modifier.height(Space.s2))
            Row {
                TextButton(onClick = onSave) { Text("Save as deck") }
                if (d.missing.isNotEmpty()) TextButton(onClick = onWish) { Text("Wish for missing") }
                if (d.missing.isNotEmpty()) TextButton(onClick = {
                    clip.setText(AnnotatedString(d.missing.joinToString("\n") { "1 $it" }))
                    Undo.offer("Missing cards copied")
                }) { Text("Copy missing") }
            }
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.6f)) {
                if (d.missing.isNotEmpty()) item(key = "m") { Label("Missing · ${d.missing.size}") }
                items(d.missing, key = { "m$it" }) { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Space.s1)) }
                val have = d.cards.filterNot { it in d.missing }
                if (have.isNotEmpty()) item(key = "h") { Label("You have · ${have.size}") }
                items(have, key = { "h$it" }) { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(vertical = Space.s1)) }
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(top = Space.s3, bottom = Space.s1))
}

