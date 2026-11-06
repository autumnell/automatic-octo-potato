package com.mtgscanner.ui.decks

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.util.DeckLine

/** A card in the library for the odds: its copies, and what it is. */
data class OddsCard(val name: String, val copies: Int, val typeLine: String, val oracleText: String)

/** Groups of cards whose odds can be looked at together. */
enum class OddsGroup(val label: String) {
    LANDS("Lands"), RAMP("Ramp"), CREATURES("Creatures"), DRAW("Card draw"), REMOVAL("Removal");

    fun has(c: OddsCard): Boolean {
        val front = c.typeLine.substringBefore("//")
        val text = c.oracleText.lowercase()
        val land = front.contains("Land", ignoreCase = true)
        return when (this) {
            LANDS -> land
            // Mana rocks and dorks, and spells that fetch lands.
            RAMP -> !land && (Regex("""add \{|add one mana|adds? (two|three) mana|mana of any""").containsMatchIn(text) ||
                Regex("""search your library for (a|up to \w+) (basic )?(land|forest|plains|island|swamp|mountain)""").containsMatchIn(text))
            CREATURES -> front.contains("Creature", ignoreCase = true)
            DRAW -> !land && Regex("""draws? (a|two|three|x|that many) cards?""").containsMatchIn(text)
            REMOVAL -> !land && Regex("""(destroy|exile) (target|all|each)|deals? \S+ damage to (any target|target creature)|return target \S+ (creature|permanent) to its owner's hand""").containsMatchIn(text)
        }
    }
}

/** Chances of drawing cards by a turn; pure, for tests. */
object DrawOddsMath {
    /** Turns shown. */
    const val TURNS = 10

    /** The chance of at least one of [hits] cards among [draws] drawn from a [library] of cards. */
    fun atLeastOne(library: Int, hits: Int, draws: Int): Double {
        if (hits <= 0 || library <= 0 || draws <= 0) return 0.0
        if (draws > library - hits) return 1.0
        // The chance of none: (L-h)/L × (L-h-1)/(L-1) × … over the draws.
        var none = 1.0
        for (i in 0 until draws) none *= (library - hits - i).toDouble() / (library - i)
        return 1.0 - none
    }

    /** Ways to choose [k] of [n], as a Double (big enough for any deck). */
    private fun choose(n: Int, k: Int): Double {
        if (k < 0 || k > n) return 0.0
        val m = minOf(k, n - k)
        var r = 1.0
        for (i in 1..m) r = r * (n - m + i) / i
        return r
    }

    /** The chance of exactly [k] of [hits] cards among [draws] drawn from a [library] of cards. */
    fun exactly(library: Int, hits: Int, draws: Int, k: Int): Double {
        if (library <= 0) return if (k == 0) 1.0 else 0.0
        val d = draws.coerceIn(0, library)
        val h = hits.coerceIn(0, library)
        return choose(h, k) * choose(library - h, d - k) / choose(library, d)
    }

    /** The chance of at least [k] of [hits] cards among [draws] drawn from a [library] of cards. */
    fun atLeast(library: Int, hits: Int, draws: Int, k: Int): Double {
        if (k <= 0) return 1.0
        if (k == 1) return atLeastOne(library, hits, draws)
        val d = draws.coerceIn(0, library)
        return (k..minOf(hits, d)).sumOf { exactly(library, hits, d, it) }.coerceIn(0.0, 1.0)
    }

    /** Chances of exactly 0 to 7 of [hits] cards in the opening hand. */
    fun openingHand(library: Int, hits: Int): List<Double> = (0..HAND).map { exactly(library, hits, HAND, it) }

    const val HAND = 7

    /** Cards seen by turn [turn]: the opening 7 and a draw each turn, but none on turn 1 when [onPlay]. */
    fun seenBy(turn: Int, onPlay: Boolean): Int = 7 + turn - if (onPlay) 1 else 0

    /** Chances for turns 1 to [TURNS]. */
    fun byTurn(library: Int, hits: Int, onPlay: Boolean, atLeast: Int = 1): List<Double> =
        (1..TURNS).map { atLeast(library, hits, seenBy(it, onPlay), atLeast) }

    /** The library: the main deck's cards (not commanders or the sideboard) with what [info] knows of them. */
    fun library(lines: List<DeckLine>, info: (String) -> Pair<String, String>?): List<OddsCard> =
        lines.filter { it.section == "Main" }.groupBy { com.mtgscanner.util.CardNames.key(it.name) }.map { (_, ls) ->
            val (type, text) = info(ls.first().name) ?: ("" to "")
            OddsCard(com.mtgscanner.util.CardNames.full(ls.first().name), ls.sumOf { it.quantity }, type, text)
        }
}

/**
 * Deck ⋮ › Draw odds: pick a group of cards (lands, ramp…) or one card, and see the chance of having drawn at least
 * one by each turn, on the play and on the draw.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawOddsSheet(cards: List<OddsCard>, onDismiss: () -> Unit) {
    var group by rememberSaveable { mutableStateOf<String?>(OddsGroup.LANDS.name) }
    var card by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // How many of them: at least 1, 2, 3…
    var need by rememberSaveable { mutableStateOf(1) }
    val library = cards.sumOf { it.copies }
    val hits = when {
        card != null -> cards.firstOrNull { it.name == card }?.copies ?: 0
        group != null -> cards.filter { OddsGroup.valueOf(group!!).has(it) }.sumOf { it.copies }
        else -> 0
    }
    val most = hits.coerceIn(1, MOST)
    val k = need.coerceAtMost(most)
    val play = remember(library, hits, k) { DrawOddsMath.byTurn(library, hits, onPlay = true, atLeast = k) }
    val draw = remember(library, hits, k) { DrawOddsMath.byTurn(library, hits, onPlay = false, atLeast = k) }
    val opening = remember(library, hits) { DrawOddsMath.openingHand(library, hits) }
    val what = card ?: group?.let { OddsGroup.valueOf(it).label.lowercase() } ?: "them"
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4).navigationBarsPadding()) {
            Text("Draw odds", style = MaterialTheme.typography.titleLarge)
            Text(
                "Chance of at least ${if (k == 1) "one" else "$k"} of $what ($hits in $library cards)",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                OddsGroup.entries.forEach { g ->
                    SelectPill(g.label, card == null && group == g.name, { group = g.name; card = null; query = "" })
                }
            }
            if (most > 1) Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(bottom = Space.s2),
                horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("At least", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                (1..most).forEach { n -> SelectPill("$n", k == n, { need = n }) }
            }
            SearchField(query, { query = it }, placeholder = "Or one card")
            val matches = remember(query, cards) {
                val q = query.trim()
                if (q.length < 2) emptyList() else cards.filter { it.name.contains(q, ignoreCase = true) }.take(5)
            }
            if (card == null || query.isNotBlank()) LazyColumn(Modifier.heightIn(max = 220.dp)) {
                items(matches, key = { it.name }) { c ->
                    Text(
                        c.name + if (c.copies > 1) " ×${c.copies}" else "", style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { card = c.name; group = null; query = "" }.padding(vertical = Space.s2),
                    )
                }
            }
            Spacer(Modifier.height(Space.s3))
            OpeningHandOdds(opening, k)
            Spacer(Modifier.height(Space.s3))
            Row(Modifier.fillMaxWidth().padding(vertical = Space.s1)) {
                Text("Turn", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.width(56.dp))
                Text("On the play", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("On the draw", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            HorizontalDivider(color = Palette.Rule)
            (0 until DrawOddsMath.TURNS).forEach { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.bodyMedium.merge(TabularNums), modifier = Modifier.width(56.dp))
                    Text(pct(play[i]), style = MaterialTheme.typography.bodyMedium.merge(TabularNums), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(pct(draw[i]), style = MaterialTheme.typography.bodyMedium.merge(TabularNums), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
}

/** The most the "At least" picker offers. */
private const val MOST = 7

/**
 * The opening 7 on its own: the chance of exactly 0 to 7 of the cards, with the "at least [k]" ones in ink and the
 * rest muted, and the total of those under it.
 */
@Composable
private fun OpeningHandOdds(exactly: List<Double>, k: Int) {
    Text("In the opening 7", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
    HorizontalDivider(color = Palette.Rule, modifier = Modifier.padding(vertical = Space.s1))
    Row(Modifier.fillMaxWidth()) {
        exactly.forEachIndexed { n, p ->
            val hit = n >= k
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$n", style = MaterialTheme.typography.labelMedium.merge(TabularNums), color = Palette.Muted)
                Text(
                    pct(p), style = MaterialTheme.typography.bodySmall.merge(TabularNums),
                    color = if (hit) Palette.Text else Palette.Faint, maxLines = 1,
                )
            }
        }
    }
    Text(
        "$k or more: ${pct(exactly.drop(k).sum())}",
        style = MaterialTheme.typography.bodyMedium.merge(TabularNums), modifier = Modifier.padding(top = Space.s1),
    )
}

internal fun pct(p: Double): String = when {
    p >= 0.995 && p < 1.0 -> ">99%"
    p > 0.0 && p < 0.005 -> "<1%"
    else -> "${(p * 100).toInt()}%"
}
