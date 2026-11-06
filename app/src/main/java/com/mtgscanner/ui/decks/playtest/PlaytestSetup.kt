package com.mtgscanner.ui.decks.playtest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.data.DeckFormats
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.PlaceholderRows
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.ruleBelow
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space

private enum class Against(val label: String) { NONE("Nobody"), DECKS("My decks"), PRECONS("Precons") }

/** Before the game: who the deck plays against, and whether the opponent plays itself. */
@Composable
internal fun PlaytestSetup(ui: PlaytestUi, vm: PlaytestViewModel, onBack: () -> Unit) {
    var against by rememberSaveable { mutableStateOf(Against.NONE) }
    var picked by remember { mutableStateOf<Opponent?>(null) }
    var plays by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    val decks by vm.otherDecks.collectAsStateWithLifecycle()
    val precons by vm.precons.collectAsStateWithLifecycle()
    if (against == Against.PRECONS) LaunchedEffect(Unit) { vm.loadPrecons() }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(Space.s1), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Playtest", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        val deck = ui.deck
        Column(Modifier.padding(horizontal = Space.s4)) {
            if (deck != null) {
                Text(deck.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(DeckFormats.of(deck.format).label.takeIf { deck.format.isNotEmpty() }, "${deck.cards.size + deck.commanders.size} cards").joinToString(" · ") +
                        if (deck.commanders.isNotEmpty()) " · ${deck.commanders.joinToString(" & ") { it.name }}" else "",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Space.s4))
            Text("Play against", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            Spacer(Modifier.height(Space.s2))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Against.entries.forEach { a -> SelectPill(a.label, against == a, { against = a; picked = null; query = "" }) }
            }
            if (against != Against.NONE) {
                Spacer(Modifier.height(Space.s3))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    SelectPill(SimplePilot.label, plays, { plays = true })
                    SelectPill(HandPilot.label, !plays, { plays = false })
                }
                Text(
                    if (plays) "On its turn it plays a land, casts what it can and attacks you. Blocks are up to you with Undo and life"
                    else "You take its turns too, with its own hand and library",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = Space.s2),
                )
            }
        }
        Spacer(Modifier.height(Space.s2))
        when (against) {
            Against.NONE -> Spacer(Modifier.weight(1f))
            Against.DECKS -> LazyColumn(Modifier.weight(1f)) {
                if (decks.isEmpty()) item { Text(noneOfFormat(deck?.format.orEmpty(), "decks"), color = Palette.Muted, modifier = Modifier.padding(Space.s4)) }
                items(decks, key = { it.id }) { d -> PickRow(d.name, null, picked == d) { picked = d } }
            }
            Against.PRECONS -> Column(Modifier.weight(1f)) {
                SearchField(query, { query = it }, Modifier.padding(horizontal = Space.s4, vertical = Space.s1), placeholder = "Search precons")
                val list = precons
                if (list == null) PlaceholderRows(6, Modifier.padding(Space.s4))
                else {
                    val q = query.trim()
                    // Only precons of this deck's format; a constructed one is checked card by card at Start.
                    val format = deck?.format.orEmpty()
                    val fits = list.filter { PlaytestFormats.preconFits(format, it.isCommander, DeckFormats.isCommander(format)) }
                    val shown = fits.filter { q.isEmpty() || it.name.contains(q, true) || it.code.equals(q, true) }
                    LazyColumn(Modifier.weight(1f)) {
                        if (list.isEmpty()) item { Text("Couldn't load the precon list", color = Palette.Muted, modifier = Modifier.padding(Space.s4)) }
                        else if (fits.isEmpty()) item { Text(noneOfFormat(format, "precons"), color = Palette.Muted, modifier = Modifier.padding(Space.s4)) }
                        items(shown, key = { it.fileName }) { e ->
                            val o = Opponent.Precon(e)
                            PickRow(e.name, "${e.code} · ${e.releaseDate.take(4)} · ${e.type}", picked == o) { picked = o }
                        }
                    }
                }
            }
        }
        GradientButton(
            if (ui.opponentLoading) "Shuffling up" else "Start",
            onClick = { vm.start(if (against == Against.NONE) null else picked, if (plays) SimplePilot else HandPilot) },
            modifier = Modifier.fillMaxWidth().padding(Space.s4),
            enabled = deck != null && !ui.opponentLoading && (against == Against.NONE || picked != null),
        )
    }
}

/** "No other Modern decks", or "No precons for Brawl". */
private fun noneOfFormat(format: String, what: String): String {
    val label = DeckFormats.of(format).label.takeIf { format.isNotEmpty() }
    return when {
        what == "decks" && label != null -> "No other $label decks"
        what == "decks" -> "No other decks without a format"
        label != null -> "No precons for $label"
        else -> "No precons"
    }
}

@Composable
private fun PickRow(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = Space.s4).ruleBelow().padding(vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(Icons.Outlined.Check, "Picked", tint = Palette.Text, modifier = Modifier.size(24.dp))
    }
}
