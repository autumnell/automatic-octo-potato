package com.mtgscanner.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.DeckCommanders
import com.mtgscanner.data.DeckFormats
import com.mtgscanner.data.GameChangers
import com.mtgscanner.data.edhrecAverageDeck
import com.mtgscanner.data.db.Deck
import com.mtgscanner.ui.components.Spinner
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.UnderlineTabs
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.util.DecklistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Width of the label column in the deck page's small rows (win rate, bracket, mana base, goldfish). */
internal val INSIGHT_LABEL = 96.dp

/** The played cards of the deck being shown, with what's known about each; commanders the list leaves out are added. */
internal fun insightCards(ui: DeckUi): List<InsightCard> {
    val lines = DecklistParser.parse(ui.text).filter { it.section in PLAYED }
    val leads = if (DeckFormats.isCommander(ui.format)) ui.commanders.commanders else emptyList()
    val leadKeys = leads.map { DeckNames.key(it) }.toSet()
    val listed = lines.groupBy { DeckNames.key(it.name) }.map { (k, ls) -> Triple(k, ls.first().name, ls.sumOf { it.quantity }) }
    val missingLeads = leads.filter { DeckNames.key(it) !in listed.map { l -> l.first } }.map { Triple(DeckNames.key(it), it, 1) }
    return (listed + missingLeads).mapNotNull { (k, name, n) ->
        ui.cardInfo[k]?.let { InsightCard(name, n, it.cmc, it.typeLine, it.oracleText, it.manaCost, commander = k in leadKeys) }
    }
}

/** The deck's colors: its commanders' color identity, or every played card's colors when it has none. */
internal fun deckIdentity(ui: DeckUi): Set<Char> {
    val leads = if (DeckFormats.isCommander(ui.format)) ui.commanders.commanders else emptyList()
    val from = if (leads.isNotEmpty()) leads.mapNotNull { ui.cardInfo[DeckNames.key(it)]?.colorIdentity }
    else DecklistParser.parse(ui.text).filter { it.section in PLAYED }.mapNotNull { ui.cardInfo[DeckNames.key(it.name)]?.colorIdentity }
    return from.flatMap { it.toList() }.filter { it in "WUBRG" }.toSet()
}

private enum class InsightSheet { BRACKET, MANA, GOLDFISH }

/** Bracket (Commander decks), mana base and goldfish rows under the win rate; each opens a sheet with the detail. */
@Composable
internal fun DeckInsightRows(ui: DeckUi, vm: DeckViewModel) {
    val cards = remember(ui.text, ui.cardInfo, ui.commanders, ui.format) { insightCards(ui) }
    if (cards.sumOf { it.copies } < 20) return
    val commander = DeckFormats.isCommander(ui.format)
    val repo = (LocalContext.current.applicationContext as com.mtgscanner.MtgApp).repository
    var sheet by remember { mutableStateOf<InsightSheet?>(null) }
    if (commander) LaunchedEffect(ui.text, ui.format) { vm.loadCombos() }
    val gameChangers by produceState(BracketMath.GAME_CHANGERS) { GameChangers.fetch(repo.api)?.let { value = it } }
    val twoCard = ui.combos.takeIf { !it.failed }?.value?.included?.filter { it.cards.size == 2 }?.map { it.cards }
    val bracket = remember(cards, gameChangers, twoCard) { BracketMath.of(cards, gameChangers, twoCard) }
    val identity = remember(ui.text, ui.cardInfo, ui.commanders, ui.format) { deckIdentity(ui) }
    val mana = remember(cards, identity) { ManaBaseMath.of(cards, identity) }
    val goldfish by produceState<Goldfish?>(null, cards) { value = withContext(Dispatchers.Default) { GoldfishMath.of(cards) } }

    if (commander) InsightRow("Bracket", "${bracket.level} · ${bracket.label}", bracket.reasons.firstOrNull()) { sheet = InsightSheet.BRACKET }
    InsightRow("Mana base", "${mana.lands} lands", mana.advice.firstOrNull() ?: "Looks right") { sheet = InsightSheet.MANA }
    goldfish?.let { g ->
        InsightRow("Goldfish", "Keep 7 ${pct(g.keepable)}", "4th land on turn 4 ${pct(g.landDrop[3])}") { sheet = InsightSheet.GOLDFISH }
    }
    when (sheet) {
        InsightSheet.BRACKET -> BracketSheet(bracket, onDismiss = { sheet = null })
        InsightSheet.MANA -> ManaBaseSheet(mana, onDismiss = { sheet = null })
        InsightSheet.GOLDFISH -> GoldfishSheet(cards, onDismiss = { sheet = null })
        null -> Unit
    }
}


/** A small row on the deck page: label, value and a muted note, tapping for more. */
@Composable
internal fun InsightRow(label: String, value: String, note: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(start = Space.s4, end = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.width(INSIGHT_LABEL))
        Text(value, style = MaterialTheme.typography.bodyMedium.merge(TabularNums), maxLines = 1)
        if (note != null) {
            Spacer(Modifier.width(Space.s2))
            Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        } else Spacer(Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Palette.Icon, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(start = Space.s4, end = Space.s4, top = Space.s4, bottom = Space.s1))
}

@Composable
private fun SheetLine(text: String, muted: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = if (muted) Palette.Muted else Palette.Text,
        modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s1),
    )
}

/** A label and its figure on one line, the figure on the right. */
@Composable
private fun FigureLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.merge(TabularNums))
    }
}

@Composable
private fun ColumnScope.NamesOrNone(names: List<String>) {
    if (names.isEmpty()) SheetLine("None", muted = true) else names.forEach { SheetLine(it) }
}

@Composable
private fun BracketSheet(b: Bracket, onDismiss: () -> Unit) {
    DeckActionsSheet("Bracket ${b.level} · ${b.label}", onDismiss) {
        Text(
            "Worked out from the list. Brackets 1 and 5 depend on how the deck is meant to be played, so they aren't guessed.",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s4),
        )
        SheetLabel("Game Changers (${b.gameChangers.size})")
        NamesOrNone(b.gameChangers)
        SheetLabel("Two-card combos")
        val combos = b.twoCardCombos
        if (combos == null) SheetLine("Couldn't check Commander Spellbook", muted = true) else NamesOrNone(combos.map { it.joinToString(" + ") })
        SheetLabel("Mass land denial")
        NamesOrNone(b.massLandDenial)
        SheetLabel("Extra turns")
        NamesOrNone(b.extraTurns)
        SheetLabel("Tutors (${b.tutors.size})")
        NamesOrNone(b.tutors)
        SheetLabel("How it's set")
        SheetLine("2 Core: no Game Changers, no two-card combos, few tutors", muted = true)
        SheetLine("3 Upgraded: up to 3 Game Changers, or a two-card combo, or several tutors or extra turns", muted = true)
        SheetLine("4 Optimized: more than 3 Game Changers, or mass land denial", muted = true)
    }
}

@Composable
private fun ManaBaseSheet(m: ManaBase, onDismiss: () -> Unit) {
    DeckActionsSheet("Mana base", onDismiss) {
        Spacer(Modifier.height(Space.s2))
        FigureLine("Lands", "${m.lands}")
        m.suggested?.let { FigureLine("Suggested for this curve", "$it") }
        FigureLine("Average mana value", "%.2f".format(m.avgManaValue))
        SheetLabel("Advice")
        if (m.advice.isEmpty()) SheetLine("Looks right for this curve and these colors") else m.advice.forEach { SheetLine(it) }
        if (m.wanted.isNotEmpty()) {
            SheetLabel("Sources of each color")
            m.wanted.entries.sortedBy { "WUBRG".indexOf(it.key) }.forEach { (c, want) ->
                FigureLine(ManaBaseMath.colorName(c).replaceFirstChar { it.uppercase() }, "${m.sources[c] ?: 0} of $want")
            }
        }
        Spacer(Modifier.height(Space.s2))
        Text(
            "Frank Karsten's numbers: lands from the average mana value and cheap ramp, sources from each color's share of the mana symbols",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s4),
        )
    }
}

@Composable
private fun GoldfishSheet(cards: List<InsightCard>, onDismiss: () -> Unit) {
    var onPlay by rememberSaveable { mutableStateOf(true) }
    val g by produceState<Goldfish?>(null, cards, onPlay) { value = withContext(Dispatchers.Default) { GoldfishMath.of(cards, onPlay = onPlay) } }
    DeckActionsSheet("Goldfish", onDismiss) {
        Text(
            "1,000 games played alone, one land a turn when you have one",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s4),
        )
        Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            SelectPill("On the play", onPlay, { onPlay = true })
            SelectPill("On the draw", !onPlay, { onPlay = false })
        }
        val r = g ?: return@DeckActionsSheet
        FigureLine("Opening 7 you'd keep (2 to 5 lands)", pct(r.keepable))
        r.landDrop.forEachIndexed { i, x -> FigureLine("Land drop on turn ${i + 1}", pct(x)) }
        if (r.commanderCost != null && r.commanderOnCurve != null) FigureLine("Commander on turn ${r.commanderCost}", pct(r.commanderOnCurve))
        FigureLine("Lands in play on turn 5", "%.1f".format(r.landsTurn5))
    }
}

/** What a deck is compared with: another saved deck, or EDHREC's average deck for its commanders. */
private sealed interface CompareWith {
    data class Saved(val deck: Deck) : CompareWith
    data object Average : CompareWith
}

/** The played cards of a list with their copies, commanders included. */
private fun playedCards(text: String, commanders: List<String>): List<Pair<String, Int>> {
    val lines = DecklistParser.parse(text).filter { it.section in PLAYED }.map { it.name to it.quantity }
    val keys = lines.map { DeckNames.key(it.first) }.toSet()
    return lines + commanders.filter { DeckNames.key(it) !in keys }.map { it to 1 }
}

/** Deck › Compare: pick another deck (or EDHREC's average deck), then see the cards only in one, the other or both. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompareDecksSheet(ui: DeckUi, onDismiss: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as com.mtgscanner.MtgApp).repository
    val decks by produceState<List<Deck>?>(null) { repo.observeDecks().collect { value = it } }
    val leads = if (DeckFormats.isCommander(ui.format)) ui.commanders.commanders else emptyList()
    var target by remember { mutableStateOf<CompareWith?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            val w = target
            if (w == null) {
                Text("Compare with", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.s4))
                Spacer(Modifier.height(Space.s2))
                LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
                    if (leads.isNotEmpty()) item(key = "avg") {
                        DeckActionRow(Icons.Outlined.Groups, "EDHREC average deck", "The cards most decks for ${leads.joinToString(" and ")} play") { target = CompareWith.Average }
                        HorizontalDivider(color = Palette.Rule)
                    }
                    items(decks.orEmpty().filter { it.id != ui.id }.sortedBy { it.name.lowercase() }, key = { it.id }) { d ->
                        val lead = DeckCommanders.decode(d.commanders).commanders.joinToString(" and ").ifEmpty { null }
                        DeckActionRow(Icons.Outlined.Style, d.name, lead) { target = CompareWith.Saved(d) }
                    }
                }
            } else CompareView(ui, leads, w, onBack = { target = null })
            Spacer(Modifier.height(Space.s4))
        }
    }
}

@Composable
private fun CompareView(ui: DeckUi, leads: List<String>, w: CompareWith, onBack: () -> Unit) {
    val mine = remember(ui.text, ui.commanders) { playedCards(ui.text, ui.commanders.all) }
    val other by produceState<Pair<String, List<Pair<String, Int>>>?>(null, w) {
        value = when (w) {
            is CompareWith.Saved -> w.deck.name to playedCards(w.deck.text, DeckCommanders.decode(w.deck.commanders).all)
            CompareWith.Average -> edhrecAverageDeck(leads)?.let { "EDHREC average" to (it.cards + it.commanders.map { c -> c to 1 }) } ?: ("EDHREC average" to emptyList())
        }
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    Row(Modifier.padding(horizontal = Space.s4), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${ui.name.ifBlank { "This deck" }} and ${(w as? CompareWith.Saved)?.deck?.name ?: "EDHREC average"}",
            style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onBack) { Text("Change") }
    }
    val o = other
    if (o == null) {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.s6), horizontalAlignment = Alignment.CenterHorizontally) { Spinner() }
        return
    }
    if (o.second.isEmpty()) {
        SheetLine("Couldn't reach EDHREC", muted = true)
        return
    }
    val diff = remember(mine, o) { DeckCompare.diff(mine, o.second) }
    val labels = listOf("Only here (${diff.onlyA.sumOf { it.second }})", "Only there (${diff.onlyB.sumOf { it.second }})", "Both (${diff.both.size})")
    UnderlineTabs(labels, tab, { tab = it }, Modifier.padding(vertical = Space.s1), horizontalPadding = 16.dp)
    val rows: List<String> = when (tab) {
        0 -> diff.onlyA.map { (n, q) -> if (q > 1) "$q $n" else n }
        1 -> diff.onlyB.map { (n, q) -> if (q > 1) "$q $n" else n }
        else -> diff.both
    }
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
        if (rows.isEmpty()) item { SheetLine("None", muted = true) }
        items(rows) { SheetLine(it) }
    }
}
