package com.mtgscanner.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mtgscanner.game.GameState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space

/** One page of the table tour: what it's about and how to do it. */
internal data class TourPage(val title: String, val body: String)

/** The table tour's pages; commander damage only comes up in a format that counts it. Pure, for tests. */
internal fun tourPages(commanderDamage: Boolean, players: Int): List<TourPage> = buildList {
    add(TourPage("Change life", "Tap the left half of a player to take 1 life and the right half to give 1. Hold for 10, or swipe up and down to count one at a time."))
    if (commanderDamage && players > 1) {
        add(TourPage("Commander damage", "Tap ⋯ on your panel to flip it over. Tap a player's color when their commander hits you, and hold it to take 1 back. Tap ✓ to flip back."))
        add(TourPage("Counters and more", "The back of your panel also has poison, commander tax and rad. The person button there has your name, color, art, monarch and conceding."))
    } else {
        add(TourPage("Counters and more", "Tap ⋯ on your panel to flip it over for poison and rad. The person button there has your name, color, art and conceding."))
    }
    add(TourPage("The middle button", "Opens the game menu: undo, turns and who goes first, monarch, day and night, dice and coins, and ending the game."))
}

/** The short tour over the table: a few pages with Skip and Next. Taps outside the card do nothing. */
@Composable
fun GameTutorial(g: GameState, onDone: () -> Unit) {
    val pages = remember(g.format, g.players.size) { tourPages(g.format.commanderDamage, g.players.size) }
    var page by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onDone)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val p = pages[page]
        Column(
            Modifier.padding(Space.s6).widthIn(max = 420.dp).fillMaxWidth()
                .clip(RoundedCornerShape(Corners.Medium)).background(Palette.StagePanel).padding(Space.s6),
        ) {
            Text("How it works", style = MaterialTheme.typography.labelLarge, color = Palette.OnDarkMuted)
            Spacer(Modifier.height(Space.s2))
            Text(p.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(Space.s2))
            Text(p.body, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Spacer(Modifier.height(Space.s6))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
                pages.indices.forEach { i ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == page) Color.White else Palette.OnDarkMuted.copy(alpha = 0.4f)))
                }
            }
            Spacer(Modifier.height(Space.s4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (page < pages.lastIndex) TextButton(onClick = onDone) { Text("Skip", color = Palette.OnDarkMuted) }
                Spacer(Modifier.weight(1f))
                GradientButton(if (page < pages.lastIndex) "Next" else "Got it", { if (page < pages.lastIndex) page++ else onDone() })
            }
        }
    }
}
