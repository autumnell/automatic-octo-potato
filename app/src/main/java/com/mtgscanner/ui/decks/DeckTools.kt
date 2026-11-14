package com.mtgscanner.ui.decks

import androidx.compose.material.icons.outlined.PlaylistAdd

import androidx.compose.material.icons.outlined.Style

import androidx.compose.material.icons.outlined.Inventory2

import com.mtgscanner.data.DeckUpgradeMath

import com.mtgscanner.data.UpgradeSource

import com.mtgscanner.data.deckSlotId

import com.mtgscanner.ui.theme.AppSettings

import com.mtgscanner.ui.theme.Space
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Upgrade
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.DeckFormats
import com.mtgscanner.data.DeckLoanMath
import com.mtgscanner.data.LoanMath
import com.mtgscanner.data.UpgradeIdea
import com.mtgscanner.data.addToDeckList
import com.mtgscanner.data.cancelLoan
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.deckById
import com.mtgscanner.data.lendDeck
import com.mtgscanner.data.observeLoans
import com.mtgscanner.data.observePlaces
import com.mtgscanner.data.putLoanBack
import com.mtgscanner.data.returnDeck
import com.mtgscanner.data.upgradeIdeas
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.util.DecklistParser
import com.mtgscanner.util.ProxySheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the What to cut sheet shows; [cut] holds the names taken out from it. */
data class CutUi(val loading: Boolean = true, val ideas: List<com.mtgscanner.data.CutIdea> = emptyList(), val failed: Boolean = false, val cut: Set<String> = emptySet())

/** What the Upgrade sheet shows. */
data class UpgradeUi(val loading: Boolean = true, val ideas: List<UpgradeIdea> = emptyList(), val failed: Boolean = false, val added: Set<String> = emptySet())

/**
 * The deck page's extra tools: lend the whole deck, upgrade it from your own cards, print proxies of what's
 * missing, and see what you're short on to build it alongside another deck.
 */
class DeckTools(
    private val repo: CollectionRepository,
    private val scope: CoroutineScope,
    private val deckId: () -> Long,
    /** Shows the saved deck again after its list changed here. */
    private val reload: suspend (Deck) -> Unit,
) {
    /** Who has this deck, when every copy kept in it is lent; else null. */
    val lentTo: StateFlow<String?> = combine(repo.observeAll(), repo.observePlaces(), repo.observeLoans()) { cards, places, loans ->
        DeckLoanMath.lentTo(cards, places, loans)[deckId()]
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Names lent to before, for quick picks. */
    val recentNames: StateFlow<List<String>> = repo.observeLoans().map { LoanMath.recentNames(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var lending by mutableStateOf(false)
    var upgrade by mutableStateOf<UpgradeUi?>(null)
        private set
    var cuts by mutableStateOf<CutUi?>(null)
        private set
    var printing by mutableStateOf(false)
        private set

    fun lend(who: String) {
        val id = deckId()
        lending = false
        scope.launch {
            val made = repo.lendDeck(id, who)
            if (made.isEmpty()) { Undo.offer("Nothing to lend"); return@launch }
            Undo.offer("Lent to ${LoanMath.cleanName(who)}") { made.forEach { repo.cancelLoan(it) } }
        }
    }

    fun returned() {
        val id = deckId()
        scope.launch {
            val gone = repo.returnDeck(id)
            if (gone.isEmpty()) return@launch
            Undo.offer("Deck returned") { gone.forEach { repo.putLoanBack(it) } }
        }
    }

    /**
     * Opens the Upgrade sheet for a commander deck: [identity] is the commanders' colors, or null while they're
     * still loading, when they're looked up here (else the deck would count as colorless).
     */
    fun openUpgrade(commanders: List<String>, identity: String?, text: String) {
        retryUpgrade = { openUpgrade(commanders, identity, text) }
        upgrade = UpgradeUi()
        scope.launch {
            val names = DecklistParser.parse(text).map { it.name }.toSet()
            val colors = identity ?: com.mtgscanner.util.suspendRunCatching {
                val found = commanders.map { n -> repo.api.named(n) ?: error("no commander") }
                "WUBRG".filter { c -> found.any { card -> card.colorIdentity.any { it.equals(c.toString(), ignoreCase = true) } } }
            }.getOrNull()
            val ideas = colors?.let { com.mtgscanner.util.suspendRunCatching { repo.upgradeIdeas(commanders, it, names, AppSettings.upgradeSkipDeckOnly) }.getOrNull() }
            upgrade = upgrade?.copy(loading = false, ideas = ideas.orEmpty(), failed = ideas == null)
        }
    }

    /** Runs the last Upgrade lookup again, after it failed. */
    var retryUpgrade: () -> Unit = {}
        private set

    fun closeUpgrade() { upgrade = null }

    /** Opens What to cut: the deck's played cards (name to type line) that EDHREC's decks for [commanders] play least. */
    fun openCuts(commanders: List<String>, deck: List<Pair<String, String>>) {
        retryCuts = { openCuts(commanders, deck) }
        cuts = CutUi()
        scope.launch {
            val ideas = com.mtgscanner.util.suspendRunCatching { com.mtgscanner.data.cutIdeas(commanders, deck) }.getOrNull()
            cuts = cuts?.copy(loading = false, ideas = ideas.orEmpty(), failed = ideas == null)
        }
    }

    /** Runs the last What to cut lookup again, after it failed. */
    var retryCuts: () -> Unit = {}
        private set

    fun closeCuts() { cuts = null }

    /** Notes [name] as cut in the open sheet (the deck page takes the copy out of the list). */
    fun markCut(name: String) { cuts = cuts?.let { it.copy(cut = it.cut + name) } }

    /**
     * Adds one copy of an idea to the deck's main list, with Undo. With [from], one copy also moves out of that box
     * (or other deck) into this deck's cards.
     */
    fun add(idea: UpgradeIdea, from: UpgradeSource? = null) {
        val id = deckId()
        scope.launch {
            repo.addToDeckList(id, idea.name, 1) ?: return@launch
            val before = from?.let { repo.snapshot(listOf(it.rowId)) }
            if (from != null) repo.moveCopies(from.rowId, 1, repo.deckSlotId(id))
            upgrade = upgrade?.let { it.copy(added = it.added + idea.name) }
            repo.deckById(id)?.let { reload(it) }
            Undo.offer(if (from != null) "Added ${idea.name} from ${from.place}" else "Added ${idea.name}") {
                repo.addToDeckList(id, idea.name, -1)
                before?.let { repo.restore(it) }
                upgrade = upgrade?.let { it.copy(added = it.added - idea.name) }
                repo.deckById(id)?.let { reload(it) }
            }
        }
    }

    /** Writes proxies of [missing] (name, picture, copies) to [uri]. */
    fun printProxies(ctx: Context, uri: Uri, missing: List<Triple<String, String?, Int>>) {
        if (printing) return
        printing = true
        Undo.offer("Making proxies…")
        // App-wide, so leaving the deck page doesn't stop the PDF halfway and leave a broken file.
        val app = ctx.applicationContext
        repo.appScope.launch {
            try {
                val copies = missing.flatMap { (n, url, q) -> List(q.coerceAtLeast(0)) { n to url } }
                val n = withContext(Dispatchers.IO) {
                    runCatching {
                        (runCatching { app.contentResolver.openOutputStream(uri, "wt") }.getOrNull() ?: app.contentResolver.openOutputStream(uri))
                            ?.use { ProxySheet.write(copies, it) }
                    }.getOrNull()
                }
                Undo.offer(if (n == null) "Couldn't save the proxies" else if (n == 1) "1 proxy saved" else "$n proxies saved")
            } finally {
                printing = false
            }
        }
    }
}

/** The tools' rows in the deck's actions sheet. */
@Composable
fun DeckToolsMenuItems(tools: DeckTools, ui: DeckUi, inDeck: Int, close: () -> Unit, onPrint: () -> Unit) {
    val lent by tools.lentTo.collectAsStateWithLifecycle()
    if (lent != null) {
        DeckActionRow(Icons.Outlined.Replay, "Mark returned", "Lent to $lent") { close(); tools.returned() }
    } else if (inDeck > 0) {
        DeckActionRow(Icons.Outlined.PersonAdd, "Lend deck", "Note who has it until it comes back") { close(); tools.lending = true }
    }
    val leads = ui.commanders.commanders
    if (DeckFormats.isCommander(ui.format) && leads.isNotEmpty()) {
        DeckActionRow(Icons.Outlined.Upgrade, "Upgrade from your cards", "Cards you own in the commander's colors") {
            close()
            // Each commander's colors, once they've loaded; null has the tools look them up first.
            val known = leads.all { ui.leadInfo[it.lowercase()] != null }
            val identity = if (!known) null else "WUBRG".filter { c -> leads.any { c in ui.leadInfo[it.lowercase()]?.identity.orEmpty().uppercase() } }
            tools.openUpgrade(leads, identity, ui.text)
        }
        DeckActionRow(Icons.Outlined.ContentCut, "What to cut", "Cards decks with this commander rarely play") {
            close()
            val played = DecklistParser.parse(ui.text).filter { it.section == "Main" }
            tools.openCuts(leads, played.map { it.name to ui.cardTexts[DeckNames.key(it.name)]?.first.orEmpty() })
        }
    }
    if (ui.check?.missingLines?.isNotEmpty() == true) {
        DeckActionRow(Icons.Outlined.Print, "Print proxies", "A PDF of the missing cards") { close(); onPrint() }
    }
}

/** The tools' dialogs and sheets. Call [print] to ask where to save the proxies. */
@Composable
fun DeckToolsLayer(tools: DeckTools, ui: DeckUi, repo: CollectionRepository, onCut: (String) -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val missing = ui.check?.missingLines.orEmpty()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) tools.printProxies(ctx, uri, missing.map { Triple(it.name, it.imageUrl, it.missing) })
    }
    if (tools.lending) LendDeckDialog(tools, ui.name)
    tools.upgrade?.let { UpgradeSheet(it, tools) }
    tools.cuts?.let { CutSheet(it, tools) { name -> onCut(name); tools.markCut(name) } }
    return { launcher.launch("${ui.name.ifBlank { "Deck" }} proxies.pdf") }
}

@Composable
private fun LendDeckDialog(tools: DeckTools, deckName: String) {
    var who by remember { mutableStateOf("") }
    val recent by tools.recentNames.collectAsStateWithLifecycle()
    val name = LoanMath.cleanName(who)
    AlertDialog(
        onDismissRequest = { tools.lending = false },
        containerColor = Palette.Surface,
        title = { Text("Lend deck", maxLines = 1) },
        text = {
            Column {
                Text(deckName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Space.s3))
                OutlinedTextField(
                    value = who,
                    onValueChange = { who = it.take(30) },
                    label = { Text("To") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (recent.isNotEmpty()) {
                    Spacer(Modifier.height(Space.s2))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        recent.forEach { n -> SelectPill(n, n.equals(name, ignoreCase = true), { who = n }) }
                    }
                }
                Spacer(Modifier.height(Space.s2))
                Text("Every card kept in the deck", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        },
        confirmButton = { TextButton(onClick = { tools.lend(name) }, enabled = name.isNotEmpty()) { Text("Lend") } },
        dismissButton = { TextButton(onClick = { tools.lending = false }) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpgradeSheet(state: UpgradeUi, tools: DeckTools) {
    // The card whose copy is being picked: which box (or deck) it comes from.
    var picking by remember { mutableStateOf<UpgradeIdea?>(null) }
    ModalBottomSheet(
        onDismissRequest = tools::closeUpgrade,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4).navigationBarsPadding()) {
            Text("From your cards", style = MaterialTheme.typography.titleLarge)
            Text(
                if (AppSettings.upgradeSkipDeckOnly) "Cards you own that decks with this commander play, not counting cards only in decks"
                else "Cards you own that decks with this commander play",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            Spacer(Modifier.height(Space.s2))
            when {
                state.loading -> com.mtgscanner.ui.components.PlaceholderRows(4, Modifier.padding(vertical = Space.s3))
                state.failed -> EmptyState(
                    "Couldn't reach EDHREC or Scryfall", Modifier.padding(vertical = Space.s6),
                    action = "Try again", onAction = tools.retryUpgrade, body = "Check your connection",
                )
                state.ideas.isEmpty() -> EmptyState("Nothing new in your boxes", Modifier.padding(vertical = Space.s6))
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                    // EDHREC's own order: its top cards first, then its lists (Creatures, Mana Artifacts…), most played first.
                    upgradeGroups(state.ideas).forEach { (title, ideas) ->
                        item(key = "h-$title") {
                            Text(
                                "$title (${ideas.size})", style = MaterialTheme.typography.titleMedium, color = Palette.Muted,
                                modifier = Modifier.padding(top = Space.s4, bottom = Space.s1),
                            )
                        }
                        items(ideas, key = { "$title-${it.name}" }) { idea ->
                            val added = idea.name in state.added
                            UpgradeRow(idea, added) { if (!added) picking = idea }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
    picking?.let { idea -> TakeFromSheet(idea, onPick = { src -> picking = null; tools.add(idea, src) }, onDismiss = { picking = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CutSheet(state: CutUi, tools: DeckTools, onCut: (String) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = tools::closeCuts,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4).navigationBarsPadding()) {
            Text("What to cut", style = MaterialTheme.typography.titleLarge)
            Text(
                "Cards that EDHREC's decks with this commander play least, the ones they never play first",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            Spacer(Modifier.height(Space.s2))
            when {
                state.loading -> com.mtgscanner.ui.components.PlaceholderRows(4, Modifier.padding(vertical = Space.s3))
                state.failed -> EmptyState(
                    "Couldn't get cuts from EDHREC", Modifier.padding(vertical = Space.s6),
                    action = "Try again", onAction = tools.retryCuts, body = "Check your connection. A new commander may not have enough EDHREC decks yet",
                )
                state.ideas.isEmpty() -> EmptyState("Nothing stands out", Modifier.padding(vertical = Space.s6))
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                    items(state.ideas, key = { it.name }) { idea ->
                        val gone = idea.name in state.cut
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(idea.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        !idea.onPage -> "Not in EDHREC's lists"
                                        (idea.share ?: 0.0) < 0.01 -> "In under 1% of decks"
                                        else -> "In ${((idea.share ?: 0.0) * 100).toInt()}% of decks"
                                    } + (idea.synergy?.takeIf { idea.onPage }?.let { " · synergy ${if (it >= 0) "+" else "−"}${kotlin.math.abs((it * 100).toInt())}%" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted, maxLines = 1,
                                )
                            }
                            if (gone) Text("Cut", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(horizontal = Space.s3))
                            else TextButton(onClick = { onCut(idea.name) }) { Text("Cut") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
}

/** Ideas under their EDHREC list, Top cards first; without EDHREC, by card type. Each list most played first. */
private fun upgradeGroups(ideas: List<UpgradeIdea>): List<Pair<String, List<UpgradeIdea>>> {
    val byGroup = ideas.groupBy { it.group ?: DeckTypes.of(it.typeLine).many }
    val order = listOf(DeckUpgradeMath.TOP) + ideas.mapNotNull { it.group }.distinct() + DeckType.entries.map { it.many }
    return order.distinct().mapNotNull { g -> byGroup[g]?.let { g to it.sortedByDescending { i -> i.share ?: -1.0 } } }
}

@Composable
private fun UpgradeRow(idea: UpgradeIdea, added: Boolean, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        CardImage(idea.imageUrl, Modifier.width(40.dp), zoom = true)
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(idea.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            idea.share?.let {
                Text("In ${(it * 100).toInt()}% of decks", style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted, maxLines = 1)
            }
            PlacesLine(idea.sources)
        }
        IconButton(onClick = onAdd, enabled = !added) {
            if (added) Icon(Icons.Outlined.Check, "Added", tint = Palette.Text)
            else Icon(Icons.Outlined.Add, "Add to deck", tint = Palette.Icon)
        }
    }
}

/** Where the copies are: boxes with a box icon in ink, decks with a deck icon in muted text. */
@Composable
private fun PlacesLine(sources: List<UpgradeSource>) {
    val boxes = sources.filter { !it.inDeck }.groupBy { it.place }.map { (p, l) -> p to l.sumOf { it.copies } }
    val decks = sources.filter { it.inDeck }.map { it.place }.distinct()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (boxes.isNotEmpty()) {
            Icon(Icons.Outlined.Inventory2, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s1))
            Text(
                boxes.joinToString(", ") { (p, n) -> if (n > 1) "$p ×$n" else p },
                style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (boxes.isNotEmpty() && decks.isNotEmpty()) Spacer(Modifier.width(Space.s3))
        if (decks.isNotEmpty()) {
            Icon(Icons.Outlined.Style, null, tint = Palette.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s1))
            Text(
                if (decks.size == 1) "In ${decks[0]}" else "In ${decks.size} decks",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Where to take the copy from: each box or deck holding it, or only add it to the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TakeFromSheet(idea: UpgradeIdea, onPick: (UpgradeSource?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                "Take ${idea.name} from", style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Space.s4),
            )
            Text(
                "One copy moves into this deck", style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                modifier = Modifier.padding(horizontal = Space.s4),
            )
            val boxes = idea.sources.filter { !it.inDeck }
            val decks = idea.sources.filter { it.inDeck }
            if (boxes.isNotEmpty()) DeckActionGroup("Boxes")
            boxes.forEach { s -> DeckActionRow(Icons.Outlined.Inventory2, s.place, "${s.printing} · ${s.copies} ${if (s.copies == 1) "copy" else "copies"}") { onPick(s) } }
            if (decks.isNotEmpty()) DeckActionGroup("Other decks")
            decks.forEach { s -> DeckActionRow(Icons.Outlined.Style, s.place, "${s.printing} · ${s.copies} ${if (s.copies == 1) "copy" else "copies"}") { onPick(s) } }
            DeckActionGroup("Or")
            DeckActionRow(Icons.Outlined.PlaylistAdd, "Only add it to the list", "Leave the copies where they are") { onPick(null) }
            Spacer(Modifier.height(Space.s4))
        }
    }
}

