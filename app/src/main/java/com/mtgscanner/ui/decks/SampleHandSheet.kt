package com.mtgscanner.ui.decks

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.DeckCheckLine
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import kotlin.random.Random

/** One card in a sample hand. */
data class HandCard(val name: String, val imageUrl: String?)

/**
 * Seven random cards from the main deck, with Mulligan (a new 7) and Draw (the next card).
 * Commanders ([leads]) aren't shuffled in; they're shown on their own above the hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleHandSheet(lines: List<DeckCheckLine>, leads: List<String>, onDismiss: () -> Unit) {
    val leadKeys = remember(leads) { leads.map { DeckNames.key(it) }.toSet() }
    val deck = remember(lines, leadKeys) {
        SampleHandMath.copies(
            lines.filter { it.section == "Main" && DeckNames.key(it.name) !in leadKeys }.map { HandCard(it.name, it.imageUrl) to it.needed },
        )
    }
    val leadCards = remember(lines, leads) {
        leads.map { n -> HandCard(n, lines.firstOrNull { DeckNames.key(it.name) == DeckNames.key(n) }?.imageUrl) }
    }
    val random = remember { Random(System.nanoTime()) }
    var state by remember(deck) { mutableStateOf(SampleHandMath.deal(deck, random)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sample hand", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (state.mulligans > 0) Text(
                    if (state.mulligans == 1) "1 mulligan" else "${state.mulligans} mulligans",
                    style = MaterialTheme.typography.labelLarge, color = Palette.Muted,
                )
            }
            if (deck.isEmpty()) {
                EmptyState("No main deck cards", Modifier.padding(vertical = Space.s6))
                return@Column
            }
            if (leadCards.isNotEmpty()) {
                Spacer(Modifier.height(Space.s2))
                Text(if (leadCards.size > 1) "Commanders" else "Commander", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                Spacer(Modifier.height(Space.s1))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    leadCards.forEach { c -> HandTile(c, Modifier.width(72.dp)) }
                }
            }
            Spacer(Modifier.height(Space.s3))
            Text(
                "${state.hand.size} in hand · ${state.library.size} left",
                style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
            )
            Spacer(Modifier.height(Space.s1))
            state.hand.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    row.forEach { c -> HandTile(c, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(Space.s2))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                com.mtgscanner.ui.components.QuietButton(
                    onClick = { state = SampleHandMath.mulligan(state, random) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(Corners.Medium),
                ) { Text("Mulligan") }
                GradientButton("Draw", { state = SampleHandMath.draw(state) }, Modifier.weight(1f), enabled = state.canDraw)
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
}

@Composable
private fun HandTile(c: HandCard, modifier: Modifier) {
    Box(modifier) {
        CardImage(c.imageUrl, Modifier.fillMaxWidth(), zoom = c.imageUrl != null)
        if (c.imageUrl == null) Text(
            c.name, style = MaterialTheme.typography.labelSmall, color = Palette.Text, textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(Space.s1),
        )
    }
}
