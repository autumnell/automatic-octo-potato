package com.mtgscanner.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.mtgscanner.ui.components.ColorPip
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.MtgColors
import com.mtgscanner.ui.theme.PriceStyle
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

private enum class InsightSheet { MANA, GOLDFISH }

/**
 * A bracket the player set by hand for a deck (1 to 5), by deck id in the "deck_brackets" prefs (saved in backups);
 * none means use the worked-out one. The file is read once, so composition never writes the state it reads.
 */
internal object BracketChoice {
    const val FILE = "deck_brackets"
    private val levels = androidx.compose.runtime.mutableStateMapOf<Long, Int>()
    private var loaded = false

    @Synchronized
    private fun load(ctx: android.content.Context) {
        if (loaded) return
        loaded = true
        ctx.applicationContext.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).all.forEach { (k, v) ->
            val id = k.toLongOrNull() ?: return@forEach
            (v as? Int)?.takeIf { it in 1..5 }?.let { levels[id] = it }
        }
    }

    /** Reads the file again, after a backup restore replaced or cleared it. */
    @Synchronized
    fun reload(ctx: android.content.Context) {
        levels.clear()
        loaded = false
        load(ctx)
    }

    fun get(ctx: android.content.Context, deckId: Long): Int? {
        load(ctx)
        return levels[deckId]
    }

    fun set(ctx: android.content.Context, deckId: Long, level: Int?) {
        load(ctx)
        val e = ctx.applicationContext.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).edit()
        if (level == null || level !in 1..5) {
            levels.remove(deckId); e.remove(deckId.toString())
        } else {
            levels[deckId] = level; e.putInt(deckId.toString(), level)
        }
        e.apply()
    }
}

/** "Core", "Upgraded"… for a bracket from 1 to 5. */
internal fun bracketName(level: Int): String = BRACKET_NAMES.getOrElse(level - 1) { "" }

/**
 * A Commander deck's bracket, worked out from its list (shown under the commander's name on the deck page); null
 * for other formats and for lists too short to judge.
 */
@Composable
internal fun rememberBracket(ui: DeckUi, vm: DeckViewModel): Bracket? {
    val commander = DeckFormats.isCommander(ui.format)
    val cards = remember(ui.text, ui.cardInfo, ui.commanders, ui.format) { insightCards(ui) }
    val repo = (LocalContext.current.applicationContext as com.mtgscanner.MtgApp).repository
    if (commander) LaunchedEffect(ui.text, ui.format) { vm.loadCombos() }
    val gameChangers by produceState(BracketMath.GAME_CHANGERS) { GameChangers.fetch(repo.api)?.let { value = it } }
    // Only two-card combos that win or go infinite count toward Bracket 3; Spellbook also lists value combos.
    val twoCard = ui.combos.takeIf { !it.failed }?.value?.included
        ?.filter { it.cards.size == 2 && BracketMath.endsGames(it.produces) }?.map { it.cards }
    val ctx = LocalContext.current
    // Seeded from the cache, so a deck opened after the first doesn't show a text-check bracket for a frame.
    val tags by produceState(BracketTags.cachedOrNull()) { if (value == null) value = BracketTags.load(ctx) }
    val bracket = remember(cards, gameChangers, twoCard, tags) { BracketMath.of(cards, gameChangers, twoCard, tags) }
    return bracket.takeIf { commander && cards.sumOf { it.copies } >= 20 }
}

/** Bracket (Commander decks), mana base and goldfish rows under the win rate; each opens a sheet with the detail. */
@Composable
internal fun DeckInsightRows(ui: DeckUi, vm: DeckViewModel, leads: List<InsightStat> = emptyList()) {
    val cards = remember(ui.text, ui.cardInfo, ui.commanders, ui.format) { insightCards(ui) }
    if (cards.sumOf { it.copies } < 20) {
        if (leads.isNotEmpty()) InsightStrip(leads)
        return
    }
    var sheet by remember { mutableStateOf<InsightSheet?>(null) }
    val identity = remember(ui.text, ui.cardInfo, ui.commanders, ui.format) { deckIdentity(ui) }
    val mana = remember(cards, identity) { ManaBaseMath.of(cards, identity) }
    val goldfish by produceState<Goldfish?>(null, cards) { value = withContext(Dispatchers.Default) { GoldfishMath.of(cards) } }

    // One compact strip of figures instead of a row each (unslop: compact stats, not equal cards). Tap one for its sheet.
    val items = buildList {
        addAll(leads)
        add(InsightStat("Mana base", "${mana.lands} lands", mana.advice.firstOrNull() ?: "Looks right") { sheet = InsightSheet.MANA })
        add(InsightStat("Goldfish", goldfish?.let { "Keep ${pct(it.keepable)}" } ?: "…", goldfish?.let { "Turn 4 land ${pct(it.landDrop[3])}" }) { if (goldfish != null) sheet = InsightSheet.GOLDFISH })
    }
    InsightStrip(items)
    when (sheet) {
        InsightSheet.MANA -> ManaBaseSheet(mana, onDismiss = { sheet = null })
        InsightSheet.GOLDFISH -> GoldfishSheet(cards, onDismiss = { sheet = null })
        null -> Unit
    }
}


/** One figure in the strip; [onClick] null for a figure with nothing behind it. */
internal class InsightStat(val label: String, val value: String, val note: String?, val onClick: (() -> Unit)?)

/**
 * The deck page's figures in one compact table split by hairlines: one row for up to three, two by two for four,
 * rows of three for more. Each cell is a label, a figure and a short note, and taps through to its detail.
 */
@Composable
internal fun InsightStrip(items: List<InsightStat>) {
    val width = if (items.size == 4) 2 else 3
    val rows = items.chunked(width)
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s2)) {
        rows.forEachIndexed { r, row ->
            if (r > 0) HorizontalDivider(Modifier.padding(horizontal = Space.s2), color = Palette.Rule)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                row.forEachIndexed { i, it ->
                    if (i > 0) VerticalDivider(Modifier.fillMaxHeight().padding(vertical = Space.s1), color = Palette.Rule)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(Corners.Small))
                            .then(it.onClick?.let { go -> Modifier.clickable(onClickLabel = it.label, onClick = go) } ?: Modifier)
                            .padding(horizontal = Space.s2, vertical = Space.s1),
                    ) {
                        Text(it.label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
                        Text(it.value, style = MaterialTheme.typography.titleMedium.merge(TabularNums), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(it.note ?: "", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // A short last row keeps its cells the same width as the row above.
                if (rows.size > 1 && row.size < width) repeat(width - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SheetLine(text: String, muted: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = if (muted) Palette.Muted else Palette.Text,
        modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s1),
    )
}

/**
 * One group in an insight sheet: a panel in the page color, so it stands a clear step apart from the sheet, with a
 * small muted heading and an optional figure on the right of it.
 */
@Composable
private fun InsightPanel(title: String? = null, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(start = Space.s4, end = Space.s4, top = Space.s2).fillMaxWidth()
            .clip(RoundedCornerShape(Corners.Medium)).background(Palette.Background).padding(Space.s4),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.weight(1f))
                if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            }
            Spacer(Modifier.height(Space.s3))
        }
        content()
    }
}

/** The sheet's subtitle under its title. */
@Composable
private fun SheetNote(text: String, top: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
        modifier = Modifier.padding(start = Space.s4, end = Space.s4, top = if (top) Space.s1 else Space.s4),
    )
}

/** Two or three headline figures side by side, each a large number over its label. */
@Composable
private fun Figures(vararg items: Pair<String, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
        items.forEach { (value, label) ->
            Column(Modifier.weight(1f)) {
                Text(value, style = PriceStyle.copy(fontSize = PriceStyle.fontSize * 1.6f), color = Palette.Text, maxLines = 1)
                Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2)
            }
        }
    }
}

/** A thin bar filled to [fraction]: ink by default, or a mana color where the color means something. */
@Composable
private fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = Palette.Text) {
    Box(modifier.height(6.dp).clip(RoundedCornerShape(Corners.Small)).background(Palette.SurfaceHigh)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

/** A line of advice with a mark: a green check when all is well, a gold mark when something wants changing. */
@Composable
private fun AdviceLine(text: String, ok: Boolean) {
    Row(Modifier.padding(vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
            tint = if (ok) Palette.Success else Palette.GoldText, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Space.s2))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One thing the bracket looks for: its name and count, then the cards found under it. */
@Composable
private fun CheckRow(label: String, names: List<String>?, first: Boolean) {
    if (!first) HorizontalDivider(color = Palette.Rule)
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            when { names == null -> "Couldn't check"; names.isEmpty() -> "None"; else -> "${names.size}" },
            style = MaterialTheme.typography.labelLarge, color = if (names.isNullOrEmpty()) Palette.Muted else Palette.Text,
        )
    }
    names?.forEach { n ->
        Text(n, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(bottom = Space.s1))
    }
    if (!names.isNullOrEmpty()) Spacer(Modifier.height(Space.s1))
}

private val BRACKET_NAMES = listOf("Exhibition", "Core", "Upgraded", "Optimized", "cEDH")
private val BRACKET_RULES = mapOf(
    1 to "Exhibition: a deck built to show off a theme or idea, not to win",
    2 to "No Game Changers, no two-card combos, few tutors",
    3 to "Up to 3 Game Changers, or a two-card combo, or several tutors or extra turns",
    4 to "More than 3 Game Changers, or mass land denial",
    5 to "cEDH: a competitive deck built to win as fast as it can",
)

/** The five brackets as steps, the deck's one filled in ink; tap one to set the bracket by hand. */
@Composable
private fun BracketScale(level: Int, onPick: (Int) -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
        (1..5).forEach { n ->
            val on = n == level
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(Corners.Small))
                        .background(if (on) Palette.Text else Palette.SurfaceHigh)
                        .clickable(onClickLabel = "Set bracket $n") { onPick(n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$n", style = MaterialTheme.typography.titleMedium.merge(TabularNums),
                        color = if (on) Palette.Background else Palette.Muted,
                    )
                }
                Spacer(Modifier.height(Space.s1))
                Text(
                    BRACKET_NAMES[n - 1], style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (on) Palette.Text else Palette.Muted,
                )
            }
        }
    }
}

@Composable
internal fun BracketSheet(b: Bracket, chosen: Int?, onChoose: (Int?) -> Unit, onDismiss: () -> Unit) {
    val shown = chosen ?: b.level
    DeckActionsSheet("Bracket $shown: ${bracketName(shown)}", onDismiss) { BracketBody(b, chosen, onChoose) }
}

/** The bracket sheet under its title; internal so the screen pictures can draw it. */
@Composable
internal fun ColumnScope.BracketBody(b: Bracket, chosen: Int? = null, onChoose: (Int?) -> Unit = {}) {
    val shown = chosen ?: b.level
    InsightPanel {
        BracketScale(shown, onPick = { n -> onChoose(if (n == b.level) null else n) })
        Spacer(Modifier.height(Space.s3))
        Text(BRACKET_RULES[shown].orEmpty(), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.s2))
        // Set by hand or worked out: say which, and give the way back.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (chosen != null) "Set by you · the list says ${b.level}" else "Worked out from the list · tap a number to set your own",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f),
            )
            if (chosen != null) TextButton(onClick = { onChoose(null) }) { Text("Use ${b.level}") }
        }
    }
    InsightPanel("Deck stats:") {
        CheckRow("Game Changers", b.gameChangers, first = true)
        CheckRow("Two-card combos", b.twoCardCombos?.map { it.joinToString(" + ") }, first = false)
        CheckRow("Mass land denial", b.massLandDenial, first = false)
        CheckRow("Extra turns", b.extraTurns, first = false)
        CheckRow("Tutors", b.tutors, first = false)
    }
    InsightPanel("Bracket system:") {
        (1..5).forEach { n ->
            Row(Modifier.padding(vertical = Space.s1)) {
                Text(
                    "$n", style = MaterialTheme.typography.labelLarge, color = if (n == shown) Palette.Text else Palette.Muted,
                    modifier = Modifier.width(Space.s6),
                )
                Text(
                    BRACKET_RULES.getValue(n), style = MaterialTheme.typography.bodySmall,
                    color = if (n == shown) Palette.Text else Palette.Muted,
                )
            }
        }
    }
}

@Composable
private fun ManaBaseSheet(m: ManaBase, onDismiss: () -> Unit) {
    DeckActionsSheet("Mana base", onDismiss) { ManaBaseBody(m) }
}

/** The mana base sheet under its title. */
@Composable
internal fun ColumnScope.ManaBaseBody(m: ManaBase) {
    SheetNote("Average mana value ${"%.2f".format(m.avgManaValue)}", top = true)
    InsightPanel {
        if (m.suggested != null) Figures("${m.lands}" to "Lands", "${m.suggested}" to "Suggested for this curve")
        else Figures("${m.lands}" to "Lands")
        m.suggested?.let { s ->
            Spacer(Modifier.height(Space.s3))
            Meter(m.lands.toFloat() / s, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Space.s3))
        if (m.advice.isEmpty()) AdviceLine("Looks right for this curve and these colors", ok = true)
        else m.advice.forEach { AdviceLine(it, ok = false) }
    }
    if (m.wanted.isNotEmpty()) InsightPanel("Sources of each color", trailing = "Have / need") {
        m.wanted.entries.sortedBy { "WUBRG".indexOf(it.key) }.forEachIndexed { i, (c, want) ->
            val have = m.sources[c] ?: 0
            if (i > 0) Spacer(Modifier.height(Space.s3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorPip(c, 14.dp)
                Spacer(Modifier.width(Space.s2))
                Text(ManaBaseMath.colorName(c).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (have < want) {
                    Text("${want - have} short", style = MaterialTheme.typography.labelMedium, color = Palette.GoldText)
                    Spacer(Modifier.width(Space.s2))
                }
                Text("$have / $want", style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            }
            Spacer(Modifier.height(Space.s2))
            Meter(have.toFloat() / want.coerceAtLeast(1), Modifier.fillMaxWidth(), color = if (c == 'W') MtgColors.WOnSurface else MtgColors.forLetter(c))
        }
    }
    SheetNote("Frank Karsten's numbers: lands from the average mana value and cheap ramp, sources from each color's share of the mana symbols")
}

@Composable
private fun GoldfishSheet(cards: List<InsightCard>, onDismiss: () -> Unit) {
    var onPlay by rememberSaveable { mutableStateOf(true) }
    val g by produceState<Goldfish?>(null, cards, onPlay) { value = withContext(Dispatchers.Default) { GoldfishMath.of(cards, onPlay = onPlay) } }
    DeckActionsSheet("Goldfish", onDismiss) { GoldfishBody(g, onPlay) { onPlay = it } }
}

/** The goldfish sheet under its title: play or draw, then the results once worked out. */
@Composable
internal fun ColumnScope.GoldfishBody(g: Goldfish?, onPlay: Boolean, onToggle: (Boolean) -> Unit) {
    SheetNote("1,000 games played alone, one land a turn when you have one", top = true)
    Row(Modifier.padding(start = Space.s4, end = Space.s4, top = Space.s3, bottom = Space.s1), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        SelectPill("On the play", onPlay, { onToggle(true) })
        SelectPill("On the draw", !onPlay, { onToggle(false) })
    }
    val r = g ?: return
    InsightPanel {
        val cmd = if (r.commanderCost != null && r.commanderOnCurve != null) pct(r.commanderOnCurve) to "Commander on turn ${r.commanderCost}" else null
        if (cmd != null) Figures(pct(r.keepable) to "Opening 7s to keep", cmd, "%.1f".format(r.landsTurn5) to "Lands by turn 5")
        else Figures(pct(r.keepable) to "Opening 7s to keep", "%.1f".format(r.landsTurn5) to "Lands by turn 5")
        Spacer(Modifier.height(Space.s3))
        Text("A keepable hand has 2 to 5 lands", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
    InsightPanel("Land drop each turn") {
        r.landDrop.forEachIndexed { i, x ->
            if (i > 0) Spacer(Modifier.height(Space.s3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Turn ${i + 1}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(INSIGHT_LABEL * 0.75f))
                Meter(x.toFloat(), Modifier.weight(1f))
                Text(
                    pct(x), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End,
                    modifier = Modifier.width(Space.s12),
                )
            }
        }
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
        SheetLine(if (w is CompareWith.Saved) "That deck has no cards to compare" else "Couldn't reach EDHREC", muted = true)
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
    // Against another saved deck, Both also says whether you own enough to have both built at once (this was
    // "Build with another deck", now folded in here).
    val saved = (w as? CompareWith.Saved)?.deck
    val repo = (LocalContext.current.applicationContext as com.mtgscanner.MtgApp).repository
    val owned by produceState<Map<String, Int>>(emptyMap(), saved != null) {
        if (saved != null) repo.observeOwnedByName().collect { value = it }
    }
    val shared = remember(ui.text, saved, owned) {
        saved?.let { SharedCards.both(DecklistParser.parse(ui.text), com.mtgscanner.data.ParsedDecks.of(it), owned) }.orEmpty()
    }
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
        if (tab == 2 && saved != null) {
            val short = shared.filter { it.short > 0 }
            item {
                SheetLine(
                    when {
                        shared.isEmpty() -> "No cards in common"
                        short.isEmpty() -> "You own enough of each to build both"
                        else -> "Short ${short.sumOf { it.short }} to build both at once"
                    },
                    muted = true,
                )
            }
            items(shared, key = { it.name }) { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        if (r.short > 0) "Short ${r.short}" else "${r.owned} owned",
                        style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = if (r.short > 0) Palette.Text else Palette.Muted,
                    )
                }
            }
            return@LazyColumn
        }
        if (rows.isEmpty()) item { SheetLine("None", muted = true) }
        items(rows) { SheetLine(it) }
    }
}
