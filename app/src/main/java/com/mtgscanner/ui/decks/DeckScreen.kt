package com.mtgscanner.ui.decks

import com.mtgscanner.ui.theme.Space
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.CommanderRules
import com.mtgscanner.data.CommanderSlot
import com.mtgscanner.data.DeckAdd
import com.mtgscanner.data.DeckCommanders
import com.mtgscanner.data.DeckFormats
import com.mtgscanner.data.RulesCard
import com.mtgscanner.data.Edhrec
import com.mtgscanner.data.addDeckRows
import com.mtgscanner.data.deckSlotId
import com.mtgscanner.data.deleteDeckAndCards
import com.mtgscanner.data.observePlaces
import com.mtgscanner.data.undoDeckDelete
import com.mtgscanner.data.PrintingLookup
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.scan.DeckText
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.ui.components.SetSymbol
import com.mtgscanner.data.DeckCheck
import com.mtgscanner.data.DeckCheckLine
import com.mtgscanner.data.db.Deck
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.collection.SearchField
import com.mtgscanner.ui.collection.ShareDialog
import com.mtgscanner.ui.collection.shareLine
import com.mtgscanner.data.wantAllWithUndo
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.Mana
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.UnderlineTabs
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.ui.components.formatMoneyShort
import com.mtgscanner.ui.components.primaryValue
import com.mtgscanner.util.PriceCurrency
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.DeckLine
import com.mtgscanner.util.DecklistParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.components.PlaceholderRows
import com.mtgscanner.ui.components.panelRow

/** One deck: paste or edit the list, then see what you own, where it is, and what's missing. */
@Composable
fun DeckScreen(
    deckId: Long, onBack: () -> Unit, onOpenSet: ((String) -> Unit)? = null, onPlaytest: (() -> Unit)? = null,
    /** Opens on the picture view (true) or the list (false); null picks pictures for a complete deck. */
    startPictures: Boolean? = null,
    /** Opens with the actions sheet up, for the screen pictures. */
    startMenu: Boolean = false,
    /** Opens in edit mode on this tab, for the screen pictures. */
    startEditTab: EditTab? = null,
) {
    val vm: DeckViewModel = viewModel(key = "deck$deckId", factory = AppViewModels.deck(deckId))
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Keeps the deck check running only while this screen is showing.
    vm.checkRunner.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var view by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(startMenu) }
    var addingAll by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var odds by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    val inDeck by vm.inDeck.collectAsStateWithLifecycle()
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    LaunchedEffect(ui.loaded) { if (startEditTab != null && ui.loaded && ui.id != 0L) vm.edit() }
    val dirty = ui.editing && ui.text.trim() != vm.savedText.trim()
    val repo = (LocalContext.current.applicationContext as com.mtgscanner.MtgApp).repository
    val printProxies = DeckToolsLayer(vm.tools, ui, repo)
    DeckChangesLayer(vm.changes, dirty, onDiscard = { if (ui.id == 0L) onBack() else vm.cancelEdit() }) { url ->
        vm.changes.updateFromLink(ui.id, vm.savedText, url)
    }

    // Neutral page: no color wash from the art (design/Design.md, unslop). The art itself carries the color.
    Box(
        Modifier.fillMaxSize().background(Palette.Background),
    ) {
        // The commander header slides away while the card list scrolls.
        val collapse = com.mtgscanner.ui.components.rememberCollapseState()
        LaunchedEffect(view, ui.editing) { collapse.show() }
        Column(Modifier.fillMaxSize().statusBarsPadding().nestedScroll(collapse.connection)) {
            Row(Modifier.fillMaxWidth().padding(Space.s1), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (dirty) vm.changes.askDiscard = true else onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                Text(
                    if (ui.editing) (if (ui.id == 0L) "New deck" else "Edit deck") else ui.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!ui.editing) {
                    IconButton(onClick = vm::edit) { Icon(Icons.Outlined.Edit, "Edit list") }
                    // Playtest sits in the bar, one tap from the deck.
                    if (ui.id != 0L && onPlaytest != null) IconButton(onClick = onPlaytest) { Icon(Icons.Outlined.PlayArrow, "Playtest") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Deck options") }
                }
            }
            val lentTo by vm.tools.lentTo.collectAsStateWithLifecycle()
            if (!ui.editing) lentTo?.let {
                Text(
                    "Lent to $it", style = MaterialTheme.typography.bodySmall, color = Palette.AccentText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Space.s4, bottom = Space.s1),
                )
            }
            if (!ui.loaded) return@Column
            if (ui.editing) Editor(ui, vm, startEditTab)
            else {
                val ctx = LocalContext.current
                val edhrec = ui.edhrec
                LaunchedEffect(Unit) { runCatching { com.mtgscanner.data.SaltScores.load(ctx) } }
                val saltScores = com.mtgscanner.data.SaltScores.scores
                val salt = remember(ui.text, saltScores) { com.mtgscanner.data.SaltMath.deck(com.mtgscanner.util.DecklistParser.parse(ui.text), saltScores) }
                val totals = ui.check?.let { deckTotals(it.lines.filter { l -> l.section in PLAYED }) }
                com.mtgscanner.ui.components.CollapsingHeader(collapse) {
                    DeckArtHeader(
                        ui.format, ui.commanders, ui.leadArt, ui.issues,
                        onEdhrec = if (edhrec == null) null else ({ openLink(ctx, edhrec) }),
                        salt = salt, totals = totals, shape = ui.shape,
                    )
                }
                // Every played card already in the deck: nothing to pull, so the Pull list and the tab row go.
                val allInDeck = ui.check?.lines?.filter { it.section in PLAYED }?.let { p -> p.isNotEmpty() && p.all { it.inDeck >= it.needed } } == true
                if (!allInDeck) UnderlineTabs(listOf("Cards", "Pull list"), view, { view = it }, Modifier.padding(bottom = Space.s1), horizontalPadding = 16.dp)
                when (if (allInDeck) 0 else view) {
                    1 -> {
                        val pull by vm.pull.collectAsStateWithLifecycle()
                        val checks by vm.pullChecks.collectAsStateWithLifecycle()
                        PullListView(pull, checks, vm::togglePulled, vm::clearPulled, vm::moveCheckedToDeck, leads = leadsOf(ui))
                    }
                    // Pull down to refresh the prices of this deck's cards only.
                    else -> com.mtgscanner.ui.components.PriceRefreshBox(
                        Modifier.fillMaxSize(),
                        cards = {
                            val keys = ui.check?.lines.orEmpty().map { DeckNames.key(it.name) }.toSet()
                            repo.observeAll().first().filter { DeckNames.key(it.name) in keys }.map { it.scryfallId }
                        },
                    ) { Results(ui, vm, onOpenSet, startPictures) }
                }
            }
        }
        com.mtgscanner.ui.components.SlimSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    ui.picker?.let { slot ->
        val first = ui.commanders.commanders.firstOrNull()?.let { ui.leadInfo[it.lowercase()] }
        CommanderPickerSheet(
            title = slotLabel(slot, ui.format, first),
            query = ui.pickerQuery,
            onQuery = vm::setPickerQuery,
            loading = ui.pickerLoading,
            options = ui.pickerOptions,
            onPick = vm::pick,
            onDismiss = vm::closePicker,
        )
    }
    if (addingAll) {
        val counts by vm.addCounts.collectAsStateWithLifecycle()
        AddDeckSheet(
            counts = counts, busy = ui.adding,
            onAdd = { onlyMissing -> vm.addToCollection(onlyMissing) { addingAll = false } },
            onDismiss = { addingAll = false },
        )
    }
    if (odds) {
        val oddsCards = remember(ui.text, ui.cardTexts) {
            DrawOddsMath.library(com.mtgscanner.util.DecklistParser.parse(ui.text)) { n -> ui.cardTexts[DeckNames.key(n)] }
        }
        DrawOddsSheet(oddsCards, onDismiss = { odds = false })
    }
    if (comparing) CompareDecksSheet(ui, onDismiss = { comparing = false })
    if (menu) DeckActionsSheet(ui.name.ifBlank { "Deck" }, onDismiss = { menu = false }) {
        val close = { menu = false }
        DeckActionGroup("Play")
        if (ui.id != 0L && onPlaytest != null) DeckActionRow(Icons.Outlined.PlayArrow, "Playtest", "Play turns on a table, alone or against a deck of the same format") { close(); onPlaytest() }
        DeckActionRow(Icons.Outlined.Percent, "Draw odds", "How likely you are to draw a card by a given turn") { close(); odds = true }
        DeckActionRow(Icons.Outlined.CompareArrows, "Compare", "See the cards only in this deck, only in another, or in both") { close(); comparing = true }
        DeckActionGroup("Cards")
        DeckActionRow(Icons.Outlined.LibraryAdd, "Add to collection", "Count the deck's cards as yours, kept in the deck") { close(); addingAll = true }
        DeckToolsMenuItems(vm.tools, ui, inDeck, close = close, onPrint = { printProxies() })
        DeckActionGroup("List")
        DeckActionRow(Icons.Outlined.Link, "Update from link", "Get the newest list from Moxfield or Archidekt") { close(); vm.changes.updateFromLink(ui.id, vm.savedText) }
        DeckListMenuItems(ui, close = close, onDuplicate = vm::duplicate)
        DeckActionGroup("Deck")
        DeckActionRow(Icons.Outlined.DeleteOutline, "Delete deck", null, danger = true) {
            close()
            if (inDeck > 0) confirmDelete = true else vm.delete(false, onBack)
        }
    }
    if (confirmDelete) {
        DeleteDeckDialog(
            inDeck,
            onDelete = { removeCards -> confirmDelete = false; vm.delete(removeCards, onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Opens [url] in the browser; nothing happens when no app can. */
/** The list section for cards being considered: shown on the deck page, never counted or played. */
private const val MAYBE = "Maybeboard"

internal fun openLink(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** "100 cards · $123.51" for the played lines; the value only when prices are shown and known. */
private fun deckTotals(played: List<DeckCheckLine>): String {
    val cards = played.sumOf { it.needed }
    return listOfNotNull(if (cards == 1) "1 card" else "$cards cards", deckValue(played)).joinToString(" · ")
}

/** What the played lines are worth, every copy counted; null when prices are hidden or none is known. */
private fun deckValue(played: List<DeckCheckLine>): String? {
    val value = played.sumOf { (it.price ?: 0.0) * it.needed }
    val valueEur = played.sumOf { (it.priceEur ?: 0.0) * it.needed }
    return if (AppSettings.showPrices && primaryValue(value, valueEur) > 0) formatMoney(value, valueEur) else null
}

/** A commander-style deck's commanders, companion and signature spell, shown first in its card lists. */
private fun leadsOf(ui: DeckUi): List<String> = if (DeckFormats.isCommander(ui.format)) ui.commanders.all else emptyList()

/** Deleting a deck that holds cards: they go back to boxes (Auto-sort), or leave the collection. */
@Composable
private fun DeleteDeckDialog(copies: Int, onDelete: (removeCards: Boolean) -> Unit, onDismiss: () -> Unit) {
    var remove by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Delete deck?") },
        text = {
            Column {
                Text(if (copies == 1) "1 card is in this deck" else "$copies cards are in this deck", color = Palette.Muted)
                Spacer(Modifier.height(Space.s3))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    SelectPill("Move to boxes", !remove, { remove = false })
                    SelectPill("Remove cards", remove, { remove = true })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDelete(remove) }) { Text("Delete", color = Palette.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Editor(ui: DeckUi, vm: DeckViewModel, startTab: EditTab? = null) {
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Text, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Text)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.s4)) {
        OutlinedTextField(
            value = ui.name, onValueChange = vm::setName, label = { Text("Deck name") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), colors = colors,
        )
        Spacer(Modifier.height(Space.s3))
        Text("Format", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        Spacer(Modifier.height(Space.s2))
        FormatPicker(ui.format, vm::setFormat)
        if (DeckFormats.isCommander(ui.format)) {
            Spacer(Modifier.height(Space.s3))
            CommanderSlots(ui.format, ui.commanders, ui.leadInfo, vm::openPicker, vm::clearSlot)
        }
        Spacer(Modifier.height(Space.s3))
        val tabs = editTabs(ui.format, ui.commanders)
        var picked by rememberSaveable { mutableStateOf(startTab ?: EditTab.LIST) }
        val tab = if (picked in tabs) picked else EditTab.LIST
        EditTabRow(tabs, tab) { picked = it }
        Spacer(Modifier.height(Space.s3))
        when (tab) {
            EditTab.LIST -> {
                OutlinedTextField(
                    value = ui.text, onValueChange = vm::setText,
                    placeholder = { Text("4 Lightning Bolt\n1 Sol Ring (CMR) 263\n\nSideboard\n2 Duress") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 420.dp),
                    shape = RoundedCornerShape(Corners.Medium), colors = colors,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("One card per line", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                    // A photo of a printed list: its lines join what's here, to check before saving.
                    FromPhotoButton({ vm.setText(appendList(vm.ui.value.text, it)) })
                }
            }
            EditTab.CARDS -> DeckCardsTab(ui, vm)
            EditTab.COMBOS -> CombosTab(ui, vm)
            EditTab.EDHREC -> EdhrecTab(ui, vm)
        }
        Spacer(Modifier.height(Space.s2))
        GradientButton("Check my collection", vm::save, Modifier.fillMaxWidth(), enabled = ui.text.isNotBlank())
        Spacer(Modifier.height(Space.s6))
    }
    ui.printingLine?.let { line ->
        val chosen = ui.printings?.firstOrNull { p ->
            line.setCode != null && PrintingLookup.samePrinting(line.setCode, line.shownNumber, p.set, p.collectorNumber)
        }
        PrintingsSheet(ui.printings, chosen?.id, vm::pickPrinting, vm::closePrintings)
    }
}

@Composable
private fun Results(ui: DeckUi, vm: DeckViewModel, onOpenSet: ((String) -> Unit)?, startPictures: Boolean? = null) {
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var find by rememberSaveable { mutableStateOf("") }
    // Pictures by type, or the list by what's owned. A complete deck starts on pictures.
    var picturesPick by rememberSaveable { mutableStateOf(startPictures) }
    // Tapping a card slides up its details; Other printings there opens the picker.
    var tapped by remember { mutableStateOf<DeckCheckLine?>(null) }
    val open: (DeckCheckLine) -> Unit = { tapped = it }
    val check = ui.check
    if (ui.checking || check == null) {
        PlaceholderRows(6, Modifier.padding(Space.s4))
        return
    }
    // Totals count played cards only, the same as the deck tiles.
    val played = check.lines.filter { it.section in PLAYED }
    val needed = played.sumOf { it.needed }
    val have = played.sumOf { minOf(it.owned, it.needed) }
    val missingCost = played.sumOf { (it.price ?: 0.0) * it.missing }
    val missingEur = played.sumOf { (it.priceEur ?: 0.0) * it.missing }
    // A missing card with no price: "$12.30 + ?", or "--" when none has one, never a short total.
    val euros = AppSettings.currency == PriceCurrency.EUR
    val partial = played.any { it.missing > 0 && (if (euros) it.priceEur else it.price) == null }
    val shown = if (euros) missingEur else missingCost
    val missingCostText = when {
        !partial -> formatMoney(missingCost, missingEur)
        shown > 0 -> formatMoney(missingCost, missingEur) + " + ?"
        else -> formatMoney(null, null)
    }
    // A commander deck lists its commanders (and companion, signature spell) first.
    val (leads, rest) = PullListMath.leadsFirst(check.lines.filter { DeckFind.matches(it.name, find) }, leadsOf(ui), { it.name }, DeckNames::key)
    // The maybeboard (cards being considered) is shown on its own, not as missing or owned deck cards.
    val maybe = rest.filter { it.section == MAYBE }
    val missing = rest.filter { it.section in PLAYED && it.missing > 0 }
    val otherMissing = rest.filter { it.section !in PLAYED && it.section != MAYBE && it.missing > 0 }
    val owned = rest.filter { it.owned > 0 && it.section != MAYBE }
    val inDeck = played.sumOf { minOf(it.inDeck, it.needed) }
    val complete = played.isNotEmpty() && played.all { it.inDeck >= it.needed }
    val pictures = picturesPick ?: complete
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            // The curve sits on the commander art when there is one.
            if (!hasArtHeader(ui.format, ui.commanders, ui.leadArt)) ui.shape?.let { DeckShapeView(it) }
            // A complete deck has nothing missing, so the owned and missing numbers would only say so.
            // Two plain figures rather than two equal panels (unslop: compact stats).
            if (have < needed) Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                Stat("Owned", "$have / $needed")
                if (AppSettings.showPrices) Stat("To finish", missingCostText)
                else Stat("Missing", "${needed - have}")
            }
            // Small print: where the owned copies are. The value is in the header.
            val small = listOfNotNull(
                if (have > 0 && inDeck < needed) "$inDeck in the deck · ${have - inDeck} elsewhere" else null,
            )
            DeckInsights(ui, vm)
            if (small.isNotEmpty()) {
                Text(
                    small.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted,
                    modifier = Modifier.padding(start = Space.s4, end = Space.s4, bottom = Space.s2),
                )
            }
            if (played.any { it.missing > 0 }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4), verticalAlignment = Alignment.CenterVertically) {
                    com.mtgscanner.ui.components.QuietButton(
                        onClick = vm::wishlistMissing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Corners.Medium),
                    ) {
                        Icon(Icons.Outlined.FavoriteBorder, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s2))
                        Text("Wishlist missing")
                    }
                    IconButton(onClick = { sharing = true }) { Icon(Icons.Outlined.Share, "Share missing cards", tint = Palette.Icon) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                SearchField(find, { find = it }, Modifier.weight(1f).padding(vertical = Space.s2), placeholder = "Find in deck")
                // Shows the view you get next.
                IconButton(onClick = { picturesPick = !pictures }) {
                    if (pictures) Icon(Icons.Outlined.ViewAgenda, "List view", tint = Palette.Icon)
                    else Icon(Icons.Outlined.GridView, "Pictures by type", tint = Palette.Icon)
                }
            }
        }
        if (leads.isEmpty() && missing.isEmpty() && otherMissing.isEmpty() && owned.isEmpty() && maybe.isEmpty() && find.isNotBlank()) {
            item { EmptyState("No matches") }
        }
        if (pictures) {
            val typeOf = { l: DeckCheckLine -> ui.cardTexts[DeckNames.key(l.name)]?.first }
            val leadTitle = if (ui.commanders.commanders.size > 1) "Commanders" else "Commander"
            if (leads.isNotEmpty()) item(key = "p-lead") { DeckPiles(leadTitle, leads, open) }
            val groups = DeckTypes.group(rest.filter { it.section in PLAYED }.sortedBy { it.name.lowercase() }, typeOf)
            groups.forEach { (t, lines) -> item(key = "p-${t.name}") { DeckPiles(t.many, lines, open) } }
            val side = rest.filter { it.section == "Sideboard" }
            if (side.isNotEmpty()) item(key = "p-side") { DeckPiles("Sideboard", side, open) }
            if (maybe.isNotEmpty()) item(key = "p-maybe") { DeckPiles("Maybeboard", maybe, open) }
            val others = rest.filter { it.section !in PLAYED && it.section != "Sideboard" && it.section != MAYBE }
            if (others.isNotEmpty()) item(key = "p-other") { DeckPiles("Tokens and more", others, open) }
        } else {
            if (leads.isNotEmpty()) {
                item { SectionHeader(if (ui.commanders.commanders.size > 1) "Commanders" else "Commander") }
                itemsIndexed(leads, key = { i, _ -> "c$i" }) { i, l -> DeckLineRow(l, missingView = l.missing > 0, first = i == 0, last = i == leads.lastIndex, onClick = { open(l) }, onOpenSet = onOpenSet) }
            }
            // Keys by position: the same card can be listed twice (two printings).
            if (missing.isNotEmpty()) {
                item { SectionHeader("Missing (${missing.sumOf { it.missing }})") }
                itemsIndexed(missing, key = { i, _ -> "m$i" }) { i, l -> DeckLineRow(l, missingView = true, first = i == 0, last = i == missing.lastIndex, onClick = { open(l) }, onOpenSet = onOpenSet) }
            }
            if (otherMissing.isNotEmpty()) {
                item { SectionHeader("Sideboard and more (${otherMissing.sumOf { it.missing }})") }
                itemsIndexed(otherMissing, key = { i, _ -> "x$i" }) { i, l -> DeckLineRow(l, missingView = true, first = i == 0, last = i == otherMissing.lastIndex, onClick = { open(l) }, onOpenSet = onOpenSet) }
            }
            if (owned.isNotEmpty()) {
                item { SectionHeader("In your collection") }
                itemsIndexed(owned, key = { i, _ -> "o$i" }) { i, l -> DeckLineRow(l, missingView = false, first = i == 0, last = i == owned.lastIndex, onClick = { open(l) }, onOpenSet = onOpenSet) }
            }
            if (maybe.isNotEmpty()) {
                item { SectionHeader("Maybeboard (${maybe.sumOf { it.needed }})") }
                itemsIndexed(maybe, key = { i, _ -> "y$i" }) { i, l -> DeckLineRow(l, missingView = false, first = i == 0, last = i == maybe.lastIndex, onClick = { open(l) }, onOpenSet = onOpenSet) }
            }
        }
        item { Spacer(Modifier.height(Space.s6)) }
    }
    if (sharing) ShareDialog("${ui.name.ifBlank { "Deck" }} missing", scope, carts = true, onDismiss = { sharing = false }) {
        played.filter { it.missing > 0 }.map { it.shareLine() }
    }
    tapped?.let { l ->
        DeckCardSheet(
            l, ui.cardInfo[DeckNames.key(l.name)],
            onPrintings = { tapped = null; vm.openPrintings(l) },
            onDismiss = { tapped = null },
        )
    }
    ui.printingLine?.let { line ->
        val chosen = ui.printings?.firstOrNull { p ->
            line.setCode != null && PrintingLookup.samePrinting(line.setCode, line.shownNumber, p.set, p.collectorNumber)
        }
        PrintingsSheet(ui.printings, chosen?.id, vm::pickPrinting, vm::closePrintings)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        Text(value, style = MaterialTheme.typography.titleLarge.merge(TabularNums))
    }
}

@Composable
private fun DeckLineRow(
    l: DeckCheckLine, missingView: Boolean, first: Boolean, last: Boolean, onClick: () -> Unit, onOpenSet: ((String) -> Unit)? = null,
) {
    // Tap to pick the printing. Each section's rows share one panel.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.s4).panelRow(first, last).clickable(onClick = onClick).padding(horizontal = Space.s3, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(l.imageUrl, Modifier.width(40.dp), zoom = true)
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(
                (if (missingView) "${l.missing}× " else "${minOf(l.owned, l.needed)}/${l.needed} ") + l.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val listed = l.setCode?.let { it.uppercase() + (l.collectorNumber?.let { n -> " #$n" } ?: "") }
            // "LCI #287 · Borderless" opens the set when tapped; the rest of the line opens the card sheet.
            val set = l.setCode?.takeIf { onOpenSet != null && !l.otherPrinting }
            val link = when {
                l.otherPrinting -> null
                missingView -> l.printing
                l.ownedOtherPrinting -> null
                else -> l.printing
            }?.takeIf { set != null }
            val where = when {
                l.otherPrinting -> "No $listed · other printing shown"
                missingView -> listOfNotNull(if (l.owned > 0) "Have ${l.owned} of ${l.needed}" else l.section, l.printing.takeIf { link == null }).joinToString(" · ")
                else -> listOfNotNull(
                    l.whereText.ifEmpty { null },
                    if (l.ownedOtherPrinting) "Other printing" else l.printing.takeIf { link == null },
                ).joinToString(" · ")
            }
            // The set symbol sits next to the printing: before the link, or at the start when the printing is plain text.
            val symbolCode = l.setCode?.takeIf { !l.otherPrinting && l.printing != null && (missingView || !l.ownedOtherPrinting) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (symbolCode != null && link == null) SetSymbol(symbolCode, modifier = Modifier.padding(end = Space.s1))
                if (where.isNotEmpty()) Text(
                    if (link != null) "$where · " else where,
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (symbolCode != null && link != null) SetSymbol(symbolCode, modifier = Modifier.padding(end = Space.s1))
                if (link != null && set != null && onOpenSet != null) Text(
                    link, style = MaterialTheme.typography.bodySmall, color = Palette.AccentText, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(Corners.Small)).clickable { onOpenSet(set.lowercase()) },
                )
            }
        }
        if (missingView) {
            // A fixed right-aligned slot so prices line up down the list.
            if (AppSettings.showPrices) Text(
                formatMoneyShort(l.price?.times(l.missing), l.priceEur?.times(l.missing)), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.Text,
                textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.padding(start = Space.s2).widthIn(min = 64.dp),
            )
        }
        else Icon(Icons.Outlined.CheckCircle, null, tint = if (l.inDeck >= l.needed) Palette.Success else Palette.Gold, modifier = Modifier.size(18.dp))
    }
}

/**
 * The deck's wins, losses and draws from games where a seat picked it (Game tab), with Edit to set them by hand.
 */
@Composable
private fun DeckInsights(ui: DeckUi, vm: DeckViewModel) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val r = remember(ui.id, version) { com.mtgscanner.game.DeckResults.of(ctx, ui.id) }
    var editing by remember { mutableStateOf(false) }
    // The win rate leads the insight grid; tapping it edits the record.
    val win = if (ui.id == 0L) null else InsightStat(
        "Win rate",
        r.winRate?.let { "$it%" } ?: "No games",
        if (r.games > 0) "${r.wins}–${r.losses}–${r.draws}" else "Tap to add",
    ) { editing = true }
    DeckInsightRows(ui, vm, win)
    if (editing) DeckRecordDialog(r, onDismiss = { editing = false }) { new ->
        com.mtgscanner.game.DeckResults.set(ctx, ui.id, new)
        editing = false
        version++
    }
}

@Composable
private fun DeckRecordDialog(start: com.mtgscanner.game.DeckResult, onDismiss: () -> Unit, onSave: (com.mtgscanner.game.DeckResult) -> Unit) {
    var w by remember { mutableIntStateOf(start.wins) }
    var l by remember { mutableIntStateOf(start.losses) }
    var d by remember { mutableIntStateOf(start.draws) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Win rate") },
        text = {
            Column {
                Text("Games from the Game tab count on their own when a seat picks this deck", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Spacer(Modifier.height(Space.s2))
                RecordStepper("Wins", w) { w = (w + it).coerceAtLeast(0) }
                RecordStepper("Losses", l) { l = (l + it).coerceAtLeast(0) }
                RecordStepper("Draws", d) { d = (d + it).coerceAtLeast(0) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(com.mtgscanner.game.DeckResult(w, l, d)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RecordStepper(label: String, n: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(-1) }, enabled = n > 0) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$n", style = MaterialTheme.typography.titleMedium.merge(TabularNums), modifier = Modifier.widthIn(min = 32.dp))
        TextButton(onClick = { onChange(1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
