package com.mtgscanner.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgscanner.data.CommanderRules
import com.mtgscanner.data.DeckCheckLine
import com.mtgscanner.data.DeckCommanders
import com.mtgscanner.data.DeckFormats
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums

/**
 * The top of a commander deck: the commanders' art across the page with their names and the deck's totals over it,
 * then rule problems, salt and EDHREC. Other decks (or before the art is known) keep the plain [DeckHeader].
 * [totals] is "100 cards · $123.51".
 */
@Composable
fun DeckArtHeader(
    format: String, commanders: DeckCommanders, art: List<String>, issues: CommanderRules.Issues,
    onEdhrec: (() -> Unit)?, salt: com.mtgscanner.data.DeckSalt?, totals: String?,
    /** The mana curve and colors, drawn over the art under the names. */
    shape: DeckShape? = null,
    /** "Bracket 2: Core", shown under the names on the art; tapping it opens [onBracket]. */
    bracket: String? = null,
    onBracket: (() -> Unit)? = null,
) {
    val f = DeckFormats.of(format)
    if (!hasArtHeader(format, commanders, art)) {
        DeckHeader(format, commanders, art, issues, onEdhrec, salt)
        if (totals != null) Text(
            totals, style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted,
            modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s1),
        )
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s1)) {
        // The content sets the height; the art fills behind it.
        Box(Modifier.fillMaxWidth().heightIn(min = 152.dp).clip(RoundedCornerShape(Corners.Medium)).background(Palette.ArtBase)) {
            Row(Modifier.matchParentSize()) {
                art.take(2).forEach { url ->
                    AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
            // A scrim only, to keep the names readable over the art (design/Design.md).
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.85f)))))
            Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(start = Space.s3, end = Space.s3, top = Space.s12, bottom = Space.s3)) {
                Text(
                    commanders.commanders.joinToString(" & "), style = MaterialTheme.typography.titleLarge, color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                // The deck's bracket where the card count used to be; it opens the bracket sheet.
                if (bracket != null) Text(
                    bracket, style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.OnDarkMuted, maxLines = 1,
                    modifier = if (onBracket != null) Modifier.clickable(onClickLabel = "Bracket", onClick = onBracket) else Modifier,
                )
                val saltTag: (@Composable () -> Unit)? = salt?.let { st ->
                    { Text("Salt %.1f".format(st.total), style = MaterialTheme.typography.labelSmall.merge(TabularNums), color = Palette.OnDarkMuted, maxLines = 1) }
                }
                if (shape != null && !shape.isEmpty) {
                    Spacer(Modifier.height(Space.s3))
                    DeckShapeView(shape, onArt = true, trailing = saltTag)
                } else if (saltTag != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { saltTag() }
            }
            // EDHREC sits on the art's top right, on a dark chip so it reads over any picture.
            if (onEdhrec != null) Row(
                Modifier.align(Alignment.TopEnd).padding(Space.s2).clip(RoundedCornerShape(Corners.Small))
                    .background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onEdhrec)
                    .heightIn(min = 40.dp).padding(horizontal = Space.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("EDHREC", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Spacer(Modifier.width(Space.s1))
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        if (!issues.isEmpty) DeckIssuesLine(issues)
    }
}

/** How much of a card shows above the next one in a pile: about its name bar. */
private const val PEEK = 0.13f
private const val PILES = 3

/**
 * One group of the picture view: a header with the group's card count, then its cards as three piles, each card
 * showing its name bar above the next. Cards not all owned are grey; more than one copy gets a "4×" badge.
 */
@Composable
fun DeckPiles(title: String, lines: List<DeckCheckLine>, onOpen: (DeckCheckLine) -> Unit) {
    SectionHeader("$title (${lines.sumOf { it.needed }})")
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        DeckTypes.piles(lines, PILES).forEach { pile ->
            BoxWithConstraints(Modifier.weight(1f)) {
                val w = maxWidth
                val h = w * (88f / 63f)
                val peek = h * PEEK
                Box(Modifier.fillMaxWidth().height(if (pile.isEmpty()) 0.dp else peek * (pile.size - 1) + h)) {
                    pile.forEachIndexed { i, l ->
                        Box(Modifier.offset(y = peek * i).fillMaxWidth()) {
                            CardImage(l.imageUrl, Modifier.fillMaxWidth(), zoom = true, onTap = { onOpen(l) }, grey = l.owned < l.needed)
                            if (l.needed > 1) Text(
                                "${l.needed}×",
                                style = MaterialTheme.typography.labelSmall.merge(TabularNums), color = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(Space.s1)
                                    .clip(RoundedCornerShape(Corners.Small)).background(Color.Black.copy(alpha = 0.72f))
                                    .padding(horizontal = Space.s1),
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(Space.s2))
}

/** Whether [DeckArtHeader] draws the art band (a commander deck whose commander art is known). */
fun hasArtHeader(format: String, commanders: DeckCommanders, art: List<String>): Boolean =
    DeckFormats.of(format).commander && art.isNotEmpty() && commanders.commanders.isNotEmpty()
