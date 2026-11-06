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
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
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
fun DeckScreen(deckId: Long, onBack: () -> Unit, onOpenSet: ((String) -> Unit)? = null, onPlaytest: (() -> Unit)? = null) {
    val vm: DeckViewModel = viewModel(key = "deck$deckId", factory = AppViewModels.deck(deckId))
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Keeps the deck check running only while this screen is showing.
    vm.checkRunner.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var view by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var addingAll by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sampling by remember { mutableStateOf(false) }
    var odds by remember { mutableStateOf(false) }
    val inDeck by vm.inDeck.collectAsStateWithLifecycle()
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
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
                    IconButton(onClick = { if (inDeck > 0) confirmDelete = true else vm.delete(false, onBack) }) {
                        Icon(Icons.Outlined.DeleteOutline, "Delete deck")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Deck options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Add to collection") }, onClick = { menu = false; addingAll = true })
                            DropdownMenuItem(text = { Text("Update from link") }, onClick = { menu = false; vm.changes.updateFromLink(ui.id, vm.savedText) })
                            if (ui.id != 0L && onPlaytest != null) DropdownMenuItem(text = { Text("Playtest") }, onClick = { menu = false; onPlaytest() })
                            if (ui.check != null) DropdownMenuItem(text = { Text("Sample hand") }, onClick = { menu = false; sampling = true })
                            DropdownMenuItem(text = { Text("Draw odds") }, onClick = { menu = false; odds = true })
                            DeckListMenuItems(ui, close = { menu = false }, onDuplicate = vm::duplicate)
                            DeckToolsMenuItems(vm.tools, ui, inDeck, close = { menu = false }, onPrint = { printProxies() })
                        }
                    }
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
            if (ui.editing) Editor(ui, vm)
            else {
                val ctx = LocalContext.current
                val edhrec = ui.edhrec
                LaunchedEffect(Unit) { runCatching { com.mtgscanner.data.SaltScores.load(ctx) } }
                val saltScores = com.mtgscanner.data.SaltScores.scores
                val salt = remember(ui.text, saltScores) { com.mtgscanner.data.SaltMath.deck(com.mtgscanner.util.DecklistParser.parse(ui.text), saltScores) }
                com.mtgscanner.ui.components.CollapsingHeader(collapse) {
                    DeckHeader(
                        ui.format, ui.commanders, ui.leadArt, ui.issues,
                        onEdhrec = if (edhrec == null) null else ({ openLink(ctx, edhrec) }),
                        salt = salt,
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
                    // Pull down to refresh prices.
                    else -> com.mtgscanner.ui.components.PriceRefreshBox(Modifier.fillMaxSize()) { Results(ui, vm, onOpenSet) }
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
    if (sampling) ui.check?.let { check ->
        SampleHandSheet(
            check.lines,
            leads = if (DeckFormats.isCommander(ui.format)) ui.commanders.commanders else emptyList(),
            onDismiss = { sampling = false },
        )
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
private fun openLink(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
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
private fun Editor(ui: DeckUi, vm: DeckViewModel) {
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
        Spacer(Modifier.height(Space.s2))
        GradientButton("Check my collection", vm::save, Modifier.fillMaxWidth(), enabled = ui.text.isNotBlank())
        Spacer(Modifier.height(Space.s6))
    }
}

@Composable
private fun Results(ui: DeckUi, vm: DeckViewModel, onOpenSet: ((String) -> Unit)?) {
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var find by rememberSaveable { mutableStateOf("") }
    var showShared by rememberSaveable { mutableStateOf(false) }
    val shared by vm.shared.collectAsStateWithLifecycle()
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
    val missing = rest.filter { it.section in PLAYED && it.missing > 0 }
    val otherMissing = rest.filter { it.section !in PLAYED && it.missing > 0 }
    val owned = rest.filter { it.owned > 0 }
    val inDeck = played.sumOf { minOf(it.inDeck, it.needed) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ui.shape?.let { DeckShapeView(it) }
            // A complete deck has nothing missing, so the owned and missing numbers would only say so.
            if (have < needed) Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                Stat("Owned", "$have / $needed", Modifier.weight(1f))
                if (AppSettings.showPrices) Stat("Missing cost", missingCostText, Modifier.weight(1f))
                else Stat("Missing", "${needed - have}", Modifier.weight(1f))
            }
            // Small print: the deck's value and where its owned copies are.
            val value = played.sumOf { (it.price ?: 0.0) * it.needed }
            val valueEur = played.sumOf { (it.priceEur ?: 0.0) * it.needed }
            val small = listOfNotNull(
                if (AppSettings.showPrices && primaryValue(value, valueEur) > 0) "Value ${formatMoney(value, valueEur)}" else null,
                if (have > 0 && inDeck < needed) "$inDeck in the deck · ${have - inDeck} elsewhere" else null,
            )
            if (ui.id != 0L) DeckRecordRow(ui.id)
            if (small.isNotEmpty()) {
                Text(
                    small.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted,
                    modifier = Modifier.padding(start = Space.s4, end = Space.s4, bottom = Space.s2),
                )
            }
            if (shared.isNotEmpty()) {
                Text(
                    "${shared.size} ${if (shared.size == 1) "card" else "cards"} also in other decks " + if (showShared) "▴" else "▾",
                    style = MaterialTheme.typography.bodySmall, color = Palette.AccentText,
                    modifier = Modifier.clickable { showShared = !showShared }.padding(start = Space.s4, end = Space.s4, top = Space.s1, bottom = Space.s2),
                )
                if (showShared) {
                    Column(Modifier.padding(start = Space.s4, end = Space.s4, bottom = Space.s2)) {
                        shared.forEach { c ->
                            Row(Modifier.padding(vertical = Space.s1)) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(Space.s2))
                                Text(
                                    c.decks.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
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
            SearchField(find, { find = it }, Modifier.padding(horizontal = Space.s4, vertical = Space.s2), placeholder = "Find in deck")
        }
        if (leads.isEmpty() && missing.isEmpty() && otherMissing.isEmpty() && owned.isEmpty() && find.isNotBlank()) {
            item { EmptyState("No matches") }
        }
        if (leads.isNotEmpty()) {
            item { SectionHeader(if (ui.commanders.commanders.size > 1) "Commanders" else "Commander") }
            itemsIndexed(leads, key = { i, _ -> "c$i" }) { i, l -> DeckLineRow(l, missingView = l.missing > 0, first = i == 0, last = i == leads.lastIndex, onClick = { vm.openPrintings(l) }, onOpenSet = onOpenSet) }
        }
        // Keys by position: the same card can be listed twice (two printings).
        if (missing.isNotEmpty()) {
            item { SectionHeader("Missing (${missing.sumOf { it.missing }})") }
            itemsIndexed(missing, key = { i, _ -> "m$i" }) { i, l -> DeckLineRow(l, missingView = true, first = i == 0, last = i == missing.lastIndex, onClick = { vm.openPrintings(l) }, onOpenSet = onOpenSet) }
        }
        if (otherMissing.isNotEmpty()) {
            item { SectionHeader("Sideboard and more (${otherMissing.sumOf { it.missing }})") }
            itemsIndexed(otherMissing, key = { i, _ -> "x$i" }) { i, l -> DeckLineRow(l, missingView = true, first = i == 0, last = i == otherMissing.lastIndex, onClick = { vm.openPrintings(l) }, onOpenSet = onOpenSet) }
        }
        if (owned.isNotEmpty()) {
            item { SectionHeader("In your collection") }
            itemsIndexed(owned, key = { i, _ -> "o$i" }) { i, l -> DeckLineRow(l, missingView = false, first = i == 0, last = i == owned.lastIndex, onClick = { vm.openPrintings(l) }, onOpenSet = onOpenSet) }
        }
        item { Spacer(Modifier.height(Space.s6)) }
    }
    if (sharing) ShareDialog("${ui.name.ifBlank { "Deck" }} missing", scope, carts = true, onDismiss = { sharing = false }) {
        played.filter { it.missing > 0 }.map { it.shareLine() }
    }
    ui.printingLine?.let { line ->
        val chosen = ui.printings?.firstOrNull { p ->
            line.setCode != null && PrintingLookup.samePrinting(line.setCode, line.shownNumber, p.set, p.collectorNumber)
        }
        PrintingsSheet(ui.printings, chosen?.id, vm::pickPrinting, vm::closePrintings)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
        Column(Modifier.padding(Space.s4)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
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
            // "LCI #287 · Borderless" opens the set when tapped; the rest of the line picks the printing.
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
private fun DeckRecordRow(deckId: Long) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val r = remember(deckId, version) { com.mtgscanner.game.DeckResults.of(ctx, deckId) }
    var editing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Text("Win rate", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        Spacer(Modifier.width(Space.s3))
        Text(
            r.winRate?.let { "$it% · ${r.wins}–${r.losses}–${r.draws}" } ?: "No games yet",
            style = MaterialTheme.typography.bodyMedium.merge(TabularNums),
            color = if (r.games > 0) Palette.Text else Palette.Muted, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { editing = true }) { Text("Edit") }
    }
    if (editing) DeckRecordDialog(r, onDismiss = { editing = false }) { new ->
        com.mtgscanner.game.DeckResults.set(ctx, deckId, new)
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
