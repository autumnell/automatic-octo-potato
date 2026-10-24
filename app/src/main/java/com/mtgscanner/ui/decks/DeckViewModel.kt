package com.mtgscanner.ui.decks

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
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
import com.mtgscanner.data.checkDeck
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
import com.mtgscanner.data.db.artCropUrl
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
import com.mtgscanner.ui.components.formatUsd
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

data class DeckUi(
    val loaded: Boolean = false,
    val id: Long = 0,
    val name: String = "",
    val text: String = "",
    val editing: Boolean = true,
    val checking: Boolean = false,
    val check: DeckCheck? = null,
    val message: String? = null,
    /** Format id ([DeckFormats]); empty = none. */
    val format: String = "",
    val commanders: DeckCommanders = DeckCommanders(),
    /** What's known about the commanders, by lower-case name. */
    val leadInfo: Map<String, RulesCard> = emptyMap(),
    /** Commander art for the header. */
    val leadArt: List<String> = emptyList(),
    /** Cards outside the commander's colors or over the copy limit. */
    val issues: CommanderRules.Issues = CommanderRules.Issues(),
    /** The commander slot being picked, or null. */
    val picker: CommanderSlot? = null,
    val pickerQuery: String = "",
    val pickerLoading: Boolean = false,
    val pickerOptions: List<PickOption> = emptyList(),
    val adding: Boolean = false,
    /** The commander's EDHREC page; null when the deck has no commander. */
    val edhrec: String? = null,
    /** The line whose printing is being picked, and its printings (null while loading). */
    val printingLine: DeckCheckLine? = null,
    val printings: List<ScryfallCard>? = null,
    /** Mana curve and color split of the played cards; null until looked up. */
    val shape: DeckShape? = null,
)

class DeckViewModel(private val repo: CollectionRepository, deckId: Long) : ViewModel() {
    private val _ui = MutableStateFlow(DeckUi(id = deckId))
    val ui: StateFlow<DeckUi> = _ui
    /** The list as last saved, to tell unsaved edits and to compare a new list with. */
    var savedText = ""
        private set

    /** The Changes sheet for a saved deck's new list (DeckChanges.kt). */
    val changes = DeckChangesHost(repo, viewModelScope, saveList = { text, after -> _ui.update { it.copy(text = text) }; commit(after) }, restore = ::showSaved)

    private var saving = false
    private var wishing = false
    private var deleting = false
    /** Once the user picks a format or commander, a pasted list no longer fills it in. */
    private var formatTouched = false
    private var commandersTouched = false

    init {
        DeckShelfStore.init(repo.appContext)
        PullChecksStore.init(repo.appContext)
        viewModelScope.launch {
            val deck = if (deckId != 0L) repo.observeDeck(deckId).first() else null
            val text = deck?.text ?: ""
            val lines = DecklistParser.parse(text)
            formatTouched = deck?.format?.isNotEmpty() == true
            commandersTouched = deck?.commanders?.isNotBlank() == true
            // Decks saved before formats existed show what their list says until they're saved again.
            val format = deck?.format?.ifEmpty { null } ?: DeckFormats.guess(lines)
            val commanders = DeckCommanders.decode(deck?.commanders).takeUnless { it.isEmpty } ?: DeckCommanders.fromLines(lines)
            savedText = text
            _ui.update {
                it.copy(loaded = true, name = deck?.name ?: "", text = text, editing = deck == null, format = format, commanders = commanders)
            }
            if (deck != null) { check(); refreshLeads() }
        }
    }

    /** Boxes, then decks' card rows (see DeckSlots.kt), with this deck's own row id once it holds cards. */
    private val places = combine(_ui.map { it.id }.distinctUntilChanged(), repo.observePlaces()) { id, places ->
        places to places.firstOrNull { id != 0L && it.deckId == id }?.id
    }

    /** This deck's cards that other decks play too. */
    val shared: StateFlow<List<SharedCard>> = combine(_ui.map { it.id to it.text }.distinctUntilChanged(), repo.observeDecks()) { (id, text), decks ->
        SharedCards.of(DecklistParser.parse(text), decks.filter { it.id != id }.map { it.name to com.mtgscanner.data.ParsedDecks.of(it) })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Copies kept in this deck. */
    val inDeck: StateFlow<Int> = combine(places, repo.observeAll()) { (_, slot), cards ->
        if (slot == null) 0 else cards.filter { it.boxId == slot }.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Copies "Add to collection" would add: every card listed, and only those you don't own. */
    val addCounts: StateFlow<Pair<Int, Int>?> = combine(_ui.map { it.text }.distinctUntilChanged(), repo.observeAll()) { text, cards ->
        val lines = DecklistParser.parse(text)
        val owned = cards.groupBy { DeckNames.key(it.name) }.mapValues { (_, v) -> v.sumOf { it.quantity } }
        DeckAdd.rows(lines, owned, false, DeckNames::key).sumOf { it.quantity } to
            DeckAdd.rows(lines, owned, true, DeckNames::key).sumOf { it.quantity }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Adds the list's cards to the collection in one go, kept in this deck, with one Undo: the printing and foil
     * each line names. With [onlyMissing], copies already owned (any printing, anywhere) are left out.
     */
    fun addToCollection(onlyMissing: Boolean, onDone: () -> Unit) {
        val deckId = _ui.value.id
        if (_ui.value.adding || deckId == 0L) return
        _ui.update { it.copy(adding = true) }
        viewModelScope.launch {
            try {
                val lines = DecklistParser.parse(_ui.value.text)
                val owned = repo.observeAll().first().groupBy { DeckNames.key(it.name) }.mapValues { (_, v) -> v.sumOf { it.quantity } }
                val rows = DeckAdd.rows(lines, owned, onlyMissing, DeckNames::key)
                val r = try {
                    repo.addDeckRows(rows, deckId)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Undo.offer("Couldn't reach Scryfall")
                    return@launch
                }
                Undo.offer(DeckAdd.resultLine(r.added, r.notFound), r.undo)
                onDone()
            } finally {
                _ui.update { it.copy(adding = false) }
            }
        }
    }

    fun setFormat(id: String) {
        formatTouched = true
        _ui.update { it.copy(format = id) }
    }

    private var leadsJob: Job? = null

    /** Looks up the commanders (art, colors, partner abilities) and checks the list against the format. */
    private fun refreshLeads() {
        leadsJob?.cancel()
        leadsJob = viewModelScope.launch {
            val s = _ui.value
            val lines = DecklistParser.parse(s.text)
            val names = s.commanders.all + lines.filter { it.section in PLAYED || it.section == "Sideboard" }.map { it.name }
            val info = runCatching { repo.deckCardInfo(names, s.format) }.getOrDefault(emptyMap())
            fun rulesOf(n: String) = info[DeckNames.key(n)]?.rules()
            val leads = s.commanders.all.mapNotNull { n -> rulesOf(n)?.let { n.lowercase() to it } }.toMap()
            val art = s.commanders.commanders.mapNotNull { artCropUrl(info[DeckNames.key(it)]?.imageUrl) }
            val issues = CommanderRules.issues(lines, s.format, s.commanders) { n -> rulesOf(n) }
            val leadNames = if (DeckFormats.isCommander(s.format)) s.commanders.commanders else emptyList()
            val edhrec = Edhrec.linkFor(leadNames, leadNames.singleOrNull()?.let { info[DeckNames.key(it)]?.edhrec })
            // Curve and colors: played lines, plus commanders the list itself doesn't name.
            val played = lines.filter { it.section in PLAYED }
            val playedKeys = played.map { DeckNames.key(it.name) }.toSet()
            val shapeCards = (played.map { it.name to it.quantity } + s.commanders.all.filter { DeckNames.key(it) !in playedKeys }.map { it to 1 })
                .mapNotNull { (n, q) -> info[DeckNames.key(n)]?.let { ShapeCard(q, it.cmc, it.typeLine, it.manaCost) } }
            val shape = DeckShapeMath.of(shapeCards)
            _ui.update { it.copy(leadInfo = it.leadInfo + leads, leadArt = art, issues = issues, edhrec = edhrec, shape = shape) }
        }
    }

    fun openPicker(slot: CommanderSlot) {
        _ui.update { it.copy(picker = slot, pickerQuery = "", pickerOptions = emptyList()) }
        loadPicker(debounce = false)
    }

    fun closePicker() {
        pickJob?.cancel()
        _ui.update { it.copy(picker = null, pickerLoading = false) }
    }

    fun setPickerQuery(q: String) {
        _ui.update { it.copy(pickerQuery = q) }
        loadPicker(debounce = true)
    }

    private var pickJob: Job? = null

    /** With nothing typed, the deck's own cards that fit the slot; otherwise a Scryfall search for ones that do. */
    private fun loadPicker(debounce: Boolean) {
        pickJob?.cancel()
        pickJob = viewModelScope.launch {
            if (debounce) delay(350)
            val s = _ui.value
            val slot = s.picker ?: return@launch
            val first = s.commanders.commanders.firstOrNull()?.let { s.leadInfo[it.lowercase()] }
            _ui.update { it.copy(pickerLoading = true) }
            val q = s.pickerQuery.trim()
            val options = if (q.isEmpty()) {
                val names = DecklistParser.parse(s.text).map { it.name }.distinct()
                val info = runCatching { repo.deckCardInfo(names) }.getOrDefault(emptyMap())
                names.mapNotNull { info[DeckNames.key(it)]?.option() }.distinctBy { it.name }
                    .filter { CommanderRules.fits(it.rules, slot, s.format, first) }
            } else {
                val filter = CommanderRules.searchFilter(slot, s.format, first)
                runCatching { repo.api.search("$filter $q", order = "edhrec") }.getOrNull()?.take(40)?.map { it.option() }?.distinctBy { it.name }
            }
            _ui.update { it.copy(pickerLoading = false, pickerOptions = options.orEmpty()) }
            if (options == null) _ui.update { it.copy(message = "Couldn't reach Scryfall") }
        }
    }

    /** Puts [o] in the slot being picked. A new first commander keeps the second only if they still pair. */
    fun pick(o: PickOption) {
        val s = _ui.value
        val slot = s.picker ?: return
        commandersTouched = true
        val c = s.commanders
        val next = when (slot) {
            CommanderSlot.COMMANDER -> {
                val second = c.commanders.getOrNull(1)
                    ?.takeIf { n -> s.leadInfo[n.lowercase()]?.let { CommanderRules.canPair(o.rules, it) } == true }
                c.copy(commanders = listOfNotNull(o.name, second))
            }
            CommanderSlot.SECOND -> c.copy(commanders = listOfNotNull(c.commanders.firstOrNull(), o.name))
            CommanderSlot.COMPANION -> c.copy(companion = o.name)
            CommanderSlot.SIGNATURE -> c.copy(signature = o.name)
        }
        pickJob?.cancel()
        _ui.update {
            it.copy(commanders = next, picker = null, pickerLoading = false, leadInfo = it.leadInfo + (o.name.lowercase() to o.rules))
        }
    }

    fun clearSlot(slot: CommanderSlot) {
        commandersTouched = true
        _ui.update {
            val c = it.commanders
            it.copy(
                commanders = when (slot) {
                    CommanderSlot.COMMANDER -> c.copy(commanders = emptyList())
                    CommanderSlot.SECOND -> c.copy(commanders = c.commanders.take(1))
                    CommanderSlot.COMPANION -> c.copy(companion = null)
                    CommanderSlot.SIGNATURE -> c.copy(signature = null)
                },
            )
        }
    }

    /** Owned copies to take out of boxes for the saved list, kept up to date with the collection. */
    val pull: StateFlow<PullList?> = combine(
        _ui.map { it.text to it.name }.distinctUntilChanged(), repo.observeAll(), places,
    ) { (text, name), cards, (all, slot) ->
        PullListMath.build(DecklistParser.parse(text), cards, all, name, slot)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Pull list checkmarks for this deck. */
    val pullChecks: StateFlow<Set<String>> = combine(_ui.map { it.id }.distinctUntilChanged(), PullChecksStore.state) { id, m -> m[id].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun togglePulled(key: String) { _ui.value.id.takeIf { it != 0L }?.let { PullChecksStore.toggle(it, key) } }
    fun clearPulled() = PullChecksStore.clear(_ui.value.id)

    private var filing = false

    /** Moves the checked cards into this deck (out of their boxes), with Undo. */
    fun moveCheckedToDeck() {
        val id = _ui.value.id
        val list = pull.value ?: return
        val checks = pullChecks.value
        if (filing || id == 0L || checks.isEmpty()) return
        filing = true
        viewModelScope.launch {
            try {
                val slot = repo.deckSlotId(id)
                val moves = list.movesFor(checks, slot)
                if (moves.isEmpty()) return@launch
                val before = repo.snapshot(moves.map { it.first })
                repo.moveCopiesMany(moves, slot)
                PullChecksStore.clear(id)
                val n = moves.sumOf { it.second }
                Undo.offer(if (n == 1) "Moved 1 card to the deck" else "Moved $n cards to the deck") {
                    repo.restore(before)
                    PullChecksStore.set(id, checks)
                }
            } finally {
                filing = false
            }
        }
    }

    fun setName(v: String) = _ui.update { it.copy(name = v) }
    /** Until the user picks them, a pasted list's Commander sections fill in the format and commanders. */
    fun setText(v: String) {
        if (formatTouched && commandersTouched) { _ui.update { it.copy(text = v) }; return }
        val lines = DecklistParser.parse(v)
        val before = _ui.value.commanders
        _ui.update {
            it.copy(
                text = v,
                format = if (formatTouched) it.format else DeckFormats.guess(lines),
                commanders = if (commandersTouched) it.commanders else DeckCommanders.fromLines(lines),
            )
        }
        if (_ui.value.commanders != before) refreshLeads()
    }
    fun edit() = _ui.update { it.copy(editing = true) }

    /** Saves the list. A saved deck's changed list opens the Changes sheet first. */
    fun save() {
        val s = _ui.value
        if (s.text.isBlank() || saving) return
        if (s.id != 0L && s.text.trim() != savedText.trim()) changes.review(s.id, savedText, s.text) else commit()
    }

    /** Drops unsaved edits and shows the deck as saved. */
    fun cancelEdit() {
        val id = _ui.value.id
        if (id == 0L) return
        viewModelScope.launch { repo.observeDeck(id).first()?.let { showSaved(it) } }
    }

    /** Saves [d] as it is and shows it (Undo of a list change). */
    private suspend fun showSaved(d: Deck) {
        if (repo.observeDeck(d.id).first() != d) repo.saveDeck(d.id, d.name, d.text, d.format, d.commanders)
        val lines = DecklistParser.parse(d.text)
        formatTouched = d.format.isNotEmpty()
        commandersTouched = d.commanders.isNotBlank()
        savedText = d.text
        _ui.update {
            it.copy(
                name = d.name, text = d.text, editing = false, format = d.format.ifEmpty { DeckFormats.guess(lines) },
                commanders = DeckCommanders.decode(d.commanders).takeUnless { c -> c.isEmpty } ?: DeckCommanders.fromLines(lines),
            )
        }
        check()
        refreshLeads()
    }

    private fun commit(after: suspend (Long) -> Unit = {}) {
        val s = _ui.value
        // A second tap while the first save runs would save a new deck twice.
        if (s.text.isBlank() || saving) return
        saving = true
        viewModelScope.launch {
            try {
                val name = s.name.ifBlank { "Untitled deck" }
                // A commander picked from search goes into the list too, so the check counts it.
                val commanderDeck = DeckFormats.isCommander(s.format)
                val leads = if (commanderDeck) s.commanders else DeckCommanders()
                val text = if (commanderDeck) DeckCommanders.addMissingTo(s.text, DecklistParser.parse(s.text), leads) else s.text
                val id = repo.saveDeck(s.id, name, text, s.format, leads.encode())
                savedText = text
                formatTouched = true
                commandersTouched = true
                _ui.update { it.copy(id = id, name = name, text = text, editing = false, commanders = leads) }
                check()
                refreshLeads()
                after(id)
            } finally {
                saving = false
            }
        }
    }

    /** The list to check; [n] goes up on every request, so the same text is checked again too. */
    private data class CheckRequest(val n: Int, val text: String)
    private val checkRequest = MutableStateFlow<CheckRequest?>(null)

    /**
     * Checks the requested list against the collection, and again whenever the collection changes. Only runs
     * while the deck screen collects it, so a deck hidden under another screen stops checking.
     */
    val checkRunner: StateFlow<Unit> = flow<Unit> {
        checkRequest.collectLatest { req ->
            if (req == null) return@collectLatest
            var initial = true
            combine(repo.observeAll(), places) { cards, p -> cards to p }.collectLatest { (cards, p) ->
                // Later changes come in bursts (a scan, a bulk move): wait for them to settle.
                if (!initial) delay(400)
                initial = false
                val result = repo.checkDeck(req.text, cards, p.first, p.second)
                _ui.update { it.copy(checking = false, check = result) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Unit)

    /** Checks the current list (see [checkRunner]). */
    private fun check() {
        _ui.update { it.copy(checking = true) }
        val text = _ui.value.text
        checkRequest.update { CheckRequest((it?.n ?: 0) + 1, text) }
    }

    /** Opens the printing picker for a line of the saved list. */
    fun openPrintings(line: DeckCheckLine) {
        if (_ui.value.id == 0L || _ui.value.editing) return
        _ui.update { it.copy(printingLine = line, printings = null) }
        viewModelScope.launch {
            val list = try {
                repo.api.printingsOf(line.oracleId, line.name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (_ui.value.printingLine != line) return@launch
            if (list.isNullOrEmpty()) {
                _ui.update { it.copy(printingLine = null, printings = null, message = "Couldn't reach Scryfall") }
            } else {
                _ui.update { it.copy(printings = list) }
            }
        }
    }

    fun closePrintings() = _ui.update { it.copy(printingLine = null, printings = null) }

    /** Saves [card]'s set and number into the list for the line being picked, as "(SET) number". */
    fun pickPrinting(card: ScryfallCard) {
        val line = _ui.value.printingLine ?: return
        val text = DeckText.setPrinting(_ui.value.text, line.name, line.setCode, line.collectorNumber, card.set, card.collectorNumber)
        _ui.update { it.copy(printingLine = null, printings = null, text = text) }
        commit()
    }

    /** Wants enough copies of each missing played card; tapping again doesn't add more. */
    fun wishlistMissing() {
        val check = _ui.value.check ?: return
        if (wishing) return
        wishing = true
        viewModelScope.launch {
            try {
                val missing = check.lines.filter { it.section in PLAYED && it.missing > 0 }
                    .groupBy { DeckNames.key(it.name) }.values
                    .map { ls -> ls.first() to ls.sumOf { it.missing } }
                val batch = runCatching {
                    // The printing the list names, when it has one.
                    val wants = missing.map { (l, _) -> PrintingLookup.Wanted(l.name, l.setCode, l.collectorNumber) }
                    val cards = PrintingLookup.resolve(wants) { ids -> repo.api.identify(ids.map(PrintingLookup::json)) }
                    repo.wantAllWithUndo(missing.zip(cards).mapNotNull { (m, c) -> c?.let { it to m.second } })
                }.getOrNull()
                if (batch == null) _ui.update { it.copy(message = "Couldn't reach Scryfall") }
                else {
                    val msg = "Added ${batch.count} cards to your wishlist"
                    if (batch.changed > 0) Undo.offer(msg, batch.undo) else _ui.update { it.copy(message = msg) }
                }
            } finally {
                wishing = false
            }
        }
    }

    /**
     * Deletes the deck. Cards kept in it go to boxes by Auto-sort, or leave the collection with [removeCards].
     * Undo brings the deck back in its folder with its cover, and its cards as they were.
     */
    fun delete(removeCards: Boolean, onDone: () -> Unit) {
        val id = _ui.value.id
        if (deleting || id == 0L) return
        deleting = true
        viewModelScope.launch {
            val shelf = DeckShelfStore.state.value
            val folder = shelf.folderOf[id]
            val cover = shelf.cover[id]
            val undo = repo.deleteDeckAndCards(id, removeCards)
            if (undo != null) Undo.offer(if (removeCards) "Deck and cards deleted" else "Deck deleted") {
                repo.undoDeckDelete(undo)
                if (folder != null) DeckShelfStore.moveDeck(id, folder)
                if (cover != null) DeckShelfStore.setCover(id, cover)
            }
            onDone()
        }
    }

    /** Copies the deck (not its cards), with Undo. */
    fun duplicate() {
        val id = _ui.value.id
        if (id == 0L) return
        viewModelScope.launch {
            val copy = repo.duplicateDeck(id) ?: return@launch
            Undo.offer("Copy made") { repo.deleteDeckAndCards(copy, false) }
        }
    }

    fun messageShown() = _ui.update { it.copy(message = null) }
}
