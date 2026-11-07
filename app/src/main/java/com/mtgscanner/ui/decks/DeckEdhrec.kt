package com.mtgscanner.ui.decks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.EdhrecRec
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.UnderlineTabs
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.util.DecklistParser
import kotlin.math.abs
import kotlin.math.roundToInt

/** Cards shown per EDHREC list before "Show more". */
private const val PER_PAGE = 20

/**
 * Edit › EDHREC: the commander page's lists as tabs, each a two-column grid of card pictures. Under every card:
 * the share of decks that play it, how many decks that is, and its synergy, then + to add it (or − count + once
 * it's in) and a bookmark for the maybeboard.
 */
@Composable
fun EdhrecTab(ui: DeckUi, vm: DeckViewModel) {
    LaunchedEffect(ui.commanders) { vm.loadEdhrec() }
    val e = ui.edhrecLists
    LoadState(e.loading && e.value == null, e.failed, "Couldn't reach EDHREC") { vm.loadEdhrec(force = true) }
    val groups = e.value ?: return
    EdhrecLists(groups, ui.text) { name, delta, board -> vm.adjustCard(name, delta, board) }
}

/** The lists as tabs over the card grid; [text] is the list being edited, [adjust] adds or takes away copies. */
@Composable
fun EdhrecLists(groups: List<com.mtgscanner.data.EdhrecGroup>, text: String, adjust: (name: String, delta: Int, board: String) -> Unit) {
    if (groups.isEmpty()) return
    var picked by rememberSaveable { mutableIntStateOf(0) }
    val index = picked.coerceIn(0, groups.lastIndex)
    var missingOnly by rememberSaveable { mutableStateOf(false) }
    var bySynergy by rememberSaveable { mutableStateOf(false) }
    var shownCount by rememberSaveable(index) { mutableIntStateOf(PER_PAGE) }
    UnderlineTabs(groups.map { it.header }, index, { picked = it }, horizontalPadding = 0.dp)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Space.s3),
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        SelectPill("Not in the deck", missingOnly, { missingOnly = !missingOnly })
        SelectPill("Highest synergy", bySynergy, { bySynergy = !bySynergy })
    }
    // Counts by card, from one read of the list: a page has hundreds of cards, and each count re-read the list.
    val counts = remember(text) {
        DecklistParser.parse(text).groupBy { it.section to DeckNames.key(it.name) }.mapValues { (_, ls) -> ls.sumOf { it.quantity } }
    }
    fun count(name: String, section: String = "Main") = counts[section to DeckNames.key(name)] ?: 0
    val all = groups[index].cards
        .let { cs -> if (missingOnly) cs.filter { count(it.name) == 0 } else cs }
        .let { cs -> if (bySynergy) cs.sortedByDescending { it.synergy ?: -1.0 } else cs }
    if (all.isEmpty()) {
        Text("Every card in this list is in the deck", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(vertical = Space.s4))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        all.take(shownCount).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                pair.forEach { rec ->
                    val maybe = count(rec.name, "Maybeboard")
                    EdhrecCard(
                        rec, count(rec.name), maybe > 0, Modifier.weight(1f),
                        onMaybe = { on -> adjust(rec.name, if (on) 1 else -maybe, "Maybeboard") },
                    ) { d ->
                        adjust(rec.name, d, "Main")
                        // Added to the deck: it's no longer just being considered, so it leaves the maybeboard.
                        if (d > 0 && maybe > 0) adjust(rec.name, -maybe, "Maybeboard")
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (all.size > shownCount) TextButton(onClick = { shownCount += PER_PAGE }, modifier = Modifier.padding(top = Space.s2)) {
        Text("Show ${minOf(PER_PAGE, all.size - shownCount)} more")
    }
}

/** One recommendation: the card's picture, its numbers in three cells, and the add controls, in one panel. */
@Composable
private fun EdhrecCard(
    rec: EdhrecRec, inDeck: Int, considering: Boolean, modifier: Modifier,
    onMaybe: (Boolean) -> Unit, onChange: (Int) -> Unit,
) {
    Column(modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.Surface)) {
        Box(Modifier.padding(start = Space.s2, end = Space.s2, top = Space.s2)) {
            val url = rec.normalImageUrl ?: rec.imageUrl
            CardImage(url, Modifier.fillMaxWidth(), zoom = url != null, grey = false)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
            StatCell("Played", Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rec.share?.let { ShareRing(it.toFloat()) }
                    Spacer(Modifier.width(Space.s1))
                    Text(rec.share?.let { sharePct(it) } ?: "–", style = MaterialTheme.typography.labelLarge.merge(TabularNums))
                }
            }
            CellRule()
            StatCell("Decks", Modifier.weight(1f)) {
                Text(rec.decks?.let { shortCount(it) } ?: "–", style = MaterialTheme.typography.labelLarge.merge(TabularNums))
            }
            CellRule()
            StatCell("Synergy", Modifier.weight(1f)) {
                val s = rec.synergy
                Text(
                    if (s == null || abs(s) < 0.005) "0%" else "${if (s > 0) "+" else "−"}${(abs(s) * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge.merge(TabularNums),
                    color = when { s == null || abs(s) < 0.005 -> Palette.Text; s > 0 -> Palette.Success; else -> Palette.Danger },
                )
            }
        }
        Row(Modifier.fillMaxWidth().background(Palette.SurfaceHigh), verticalAlignment = Alignment.CenterVertically) {
            if (inDeck > 0) {
                Spacer(Modifier.weight(1f))
                Stepper(inDeck, onChange)
                Spacer(Modifier.weight(1f))
            } else {
                TextButton(onClick = { com.mtgscanner.util.Haptics.tick(); onChange(1) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s1))
                    Text("Add")
                }
                // The maybeboard: cards you're considering, not counted and not played in playtest.
                IconButton(onClick = { com.mtgscanner.util.Haptics.tick(); onMaybe(!considering) }) {
                    if (considering) Icon(Icons.Filled.Bookmark, "Take out of the maybeboard", tint = Palette.Text, modifier = Modifier.size(20.dp))
                    else Icon(Icons.Outlined.BookmarkBorder, "Add to the maybeboard", tint = Palette.Icon, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** A number over its small label, centered in its third of the card. */
@Composable
private fun StatCell(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier.heightIn(min = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        value()
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun CellRule() {
    Box(Modifier.width(1.dp).height(Space.s8).background(Palette.Rule))
}

/** A small ring filled to [share] of the way round, for the share of decks playing a card. */
@Composable
private fun ShareRing(share: Float) {
    val track = Palette.Rule
    val fill = Palette.Text
    Canvas(Modifier.size(14.dp)) {
        val w = 2.dp.toPx()
        val box = Size(size.width - w, size.height - w)
        val at = Offset(w / 2, w / 2)
        drawArc(track, 0f, 360f, false, at, box, style = Stroke(w))
        drawArc(fill, -90f, 360f * share.coerceIn(0f, 1f), false, at, box, style = Stroke(w))
    }
}

private fun sharePct(share: Double): String {
    val p = share * 100
    return if (p < 10) "${(p * 10).roundToInt() / 10.0}%" else "${p.roundToInt()}%"
}

/** 1234 as "1.2k", smaller numbers as they are. */
private fun shortCount(n: Int): String = if (n >= 1000) "${(n / 100) / 10.0}k" else "$n"
