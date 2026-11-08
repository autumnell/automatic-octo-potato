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
import com.mtgscanner.data.observeLoans
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
import com.mtgscanner.data.almostDecks
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.components.PlaceholderRows
import com.mtgscanner.ui.components.panelRow

/** A saved deck with how much of it you own, plus what its tile shows. */
data class DeckRow(
    val deck: Deck,
    val cards: Int,
    val owned: Int,
    val subtitle: String = "",
    val folderId: Long? = null,
    /** Color identity letters in WUBRG order. */
    val identity: String = "",
    val art: String? = null,
    /** The second commander's art, shown beside the first. */
    val art2: String? = null,
    val cover: String? = null,
    /** Distinct card names, for picking a cover. */
    val names: List<String> = emptyList(),
)

/** Sections that are played; sideboard, maybeboard and tokens don't count toward owning a deck. */
internal val PLAYED = DeckCheck.PLAYED

/** Parsed lists by deck id, so a collection change doesn't reparse every deck. */
private fun parsed(d: Deck): List<DeckLine> = com.mtgscanner.data.ParsedDecks.of(d)

class DecksViewModel(private val repo: CollectionRepository) : ViewModel() {
    init {
        DeckShelfStore.init(repo.appContext)
        PullChecksStore.init(repo.appContext)
    }

    val shelf: StateFlow<DeckShelf> = DeckShelfStore.state

    /** Tile data worked out this session that isn't saved yet (some cards weren't found). */
    private val liveMeta = MutableStateFlow<Map<Long, DeckMeta>>(emptyMap())
    private val tried = mutableSetOf<String>()

    val decks: StateFlow<List<DeckRow>?> = combine(repo.observeDecks(), repo.observeOwnedByName(), DeckShelfStore.state, liveMeta) { decks, owned, shelf, live ->
        // Same name matching as the deck check, and each owned copy counts toward one line only.
        fun key(n: String) = com.mtgscanner.util.CardNames.key(n)
        val folderIds = shelf.folders.map { it.id }.toSet()
        decks.map { d ->
            val lines = parsed(d)
            val played = lines.filter { it.section in PLAYED }
            val left = owned.toMutableMap()
            val have = played.sumOf { l ->
                val k = key(l.name)
                minOf(l.quantity, left[k] ?: 0).also { left[k] = (left[k] ?: 0) - it }
            }
            val total = played.sumOf { it.quantity }
            val cover = shelf.cover[d.id]
            val st = stamp(d, cover)
            val meta = shelf.meta[d.id]?.takeIf { it.stamp == st } ?: live[d.id] ?: shelf.meta[d.id]
            DeckRow(
                deck = d,
                cards = total,
                owned = have,
                subtitle = when {
                    d.format.isNotEmpty() -> "${DeckFormats.of(d.format).label} · $total"
                    lines.any { it.section == "Commander" } -> "Commander · $total"
                    else -> "$total cards"
                },
                folderId = shelf.folderOf[d.id]?.takeIf { it in folderIds },
                identity = meta?.identity.orEmpty(),
                art = meta?.art,
                art2 = meta?.art2,
                cover = cover,
                names = lines.map { it.name }.distinct(),
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            combine(repo.observeDecks(), DeckShelfStore.state.map { it.cover }.distinctUntilChanged()) { d, c -> d to c }
                .collectLatest { (decks, cover) -> refreshMeta(decks, cover) }
        }
    }

    /** Who has each lent deck, by deck id (only decks with every copy out). */
    val lentTo: StateFlow<Map<Long, String>> = combine(repo.observeAll(), repo.observePlaces(), repo.observeLoans()) { cards, places, loans ->
        com.mtgscanner.data.DeckLoanMath.lentTo(cards, places, loans)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun stamp(d: Deck, cover: String?) = "${d.updatedAt}|${cover.orEmpty()}"

    /** Works out color identity and cover art for decks whose tile data is out of date. */
    private suspend fun refreshMeta(decks: List<Deck>, cover: Map<Long, String>) {
        DeckShelfStore.prune(decks.map { it.id }.toSet())
        PullChecksStore.prune(decks.map { it.id }.toSet())
        val saved = DeckShelfStore.state.value.meta
        val stale = decks.filter { d -> val st = stamp(d, cover[d.id]); saved[d.id]?.stamp != st && st !in tried }
        if (stale.isEmpty()) return
        // Only played cards (and the cover) are looked up, not the sideboard, maybeboard or tokens.
        val byDeck = stale.associateWith { d ->
            val lines = parsed(d)
            val played = lines.filter { it.section in PLAYED }
            val leads = DeckCommanders.decode(d.commanders).commanders
            val coverName = cover[d.id] ?: leads.firstOrNull()
                ?: (played.firstOrNull { it.section == "Commander" } ?: played.firstOrNull() ?: lines.firstOrNull())?.name
            // Partners show side by side, unless a cover card is picked.
            val second = if (cover[d.id] == null) leads.getOrNull(1) else null
            Triple(played, coverName, second)
        }
        val names = byDeck.values.flatMap { (played, coverName, second) -> played.map { it.name } + listOfNotNull(coverName, second) }.toSet()
        val info = com.mtgscanner.util.suspendRunCatching { repo.deckCardInfo(names) }.getOrDefault(emptyMap())
        val done = HashMap<Long, DeckMeta>()
        val partial = HashMap<Long, DeckMeta>()
        for ((d, pc) in byDeck) {
            val (played, coverName, second) = pc
            val st = stamp(d, cover[d.id])
            tried += st
            val found = played.mapNotNull { info[DeckNames.key(it.name)] }
            val identity = "WUBRG".filter { c -> found.any { c in it.colorIdentity.uppercase() } }
            val art = coverName?.let { info[DeckNames.key(it)]?.imageUrl }?.let { artCropUrl(it) }
            val art2 = second?.let { info[DeckNames.key(it)]?.imageUrl }?.let { artCropUrl(it) }
            val meta = DeckMeta(st, identity, art, art2)
            // Names Scryfall says don't exist (typos) won't turn up later, so they don't keep the tile from being saved.
            fun settled(n: String) = info.containsKey(DeckNames.key(n)) || deckCardUnknown(n)
            val complete = played.all { settled(it.name) } && (coverName == null || art != null || deckCardUnknown(coverName)) &&
                (second == null || art2 != null || deckCardUnknown(second))
            if (complete) done[d.id] = meta else partial[d.id] = meta
        }
        DeckShelfStore.putMeta(done)
        if (partial.isNotEmpty()) liveMeta.update { it + partial }
    }

    fun createFolder(name: String): Long = DeckShelfStore.createFolder(name)
    fun renameFolder(id: Long, name: String) = DeckShelfStore.renameFolder(id, name)
    fun moveDeck(deckId: Long, folderId: Long?) = DeckShelfStore.moveDeck(deckId, folderId)
    fun setCover(deckId: Long, name: String?) = DeckShelfStore.setCover(deckId, name)

    /** Decks you could nearly build: null before the first look; [AlmostRun.loading] while EDHREC is read. */
    val almost = kotlinx.coroutines.flow.MutableStateFlow<AlmostRun?>(null)
    private var almostJob: kotlinx.coroutines.Job? = null

    /** Looks at the commanders you own and the week's popular ones (again with [fresh]). */
    fun loadAlmost(fresh: Boolean = false) {
        val now = almost.value
        if (now != null && (now.loading || (!fresh && now.decks.isNotEmpty()))) return
        almostJob?.cancel()
        almost.value = AlmostRun(loading = true)
        almostJob = viewModelScope.launch {
            val decks = runCatching {
                repo.almostDecks { p -> almost.update { r -> r?.copy(progress = p) } }
            }.getOrDefault(emptyList())
            almost.value = AlmostRun(loading = false, decks = decks, failed = decks.isEmpty())
        }
    }

    /** Saves [deck]'s typical list as a new Commander deck, with Undo. */
    fun saveAlmost(deck: com.mtgscanner.data.AlmostDeck) = viewModelScope.launch {
        val text = deck.asText()
        val lines = com.mtgscanner.util.DecklistParser.parse(text)
        val id = repo.saveDeck(0L, deck.commander, text, "commander", com.mtgscanner.data.DeckCommanders.fromLines(lines).encode())
        Undo.offer("Saved deck: ${deck.commander}") { repo.deleteDeckAndCards(id, false) }
    }

    /** Adds the cards [deck] is missing to the wishlist, one of each (never on top of what's wanted). */
    fun wishAlmost(deck: com.mtgscanner.data.AlmostDeck) = viewModelScope.launch {
        val names = deck.missing
        val cards = runCatching {
            com.mtgscanner.data.PrintingLookup.resolve(names.map { com.mtgscanner.data.PrintingLookup.Wanted(it) }) { ids ->
                repo.api.identify(ids.map(com.mtgscanner.data.PrintingLookup::json))
            }
        }.getOrNull()
        if (cards == null) { Undo.offer("Couldn't reach Scryfall"); return@launch }
        var n = 0
        cards.forEach { c -> if (c != null) { repo.wantAtLeast(c, 1); n++ } }
        Undo.offer(if (n == 1) "1 card on the wishlist" else "$n cards on the wishlist")
    }

    /** Copies owned by card name ([com.mtgscanner.util.CardNames.key]), for the shortages across decks. */
    val ownedByName: StateFlow<Map<String, Int>> = repo.observeOwnedByName()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Puts the copies short across decks on the wishlist (at least that many wanted of each). */
    fun wishShort(rows: List<DeckShortage>) = viewModelScope.launch {
        val cards = runCatching {
            com.mtgscanner.data.PrintingLookup.resolve(rows.map { com.mtgscanner.data.PrintingLookup.Wanted(it.name) }) { ids ->
                repo.api.identify(ids.map(com.mtgscanner.data.PrintingLookup::json))
            }
        }.getOrNull()
        if (cards == null) { Undo.offer("Couldn't reach Scryfall"); return@launch }
        var n = 0
        cards.zip(rows).forEach { (c, r) -> if (c != null) { repo.wantAtLeast(c, r.short); n++ } }
        Undo.offer(if (n == 1) "1 card on the wishlist" else "$n cards on the wishlist")
    }

    /** Decks › New › Precon decks: MTGJSON's list (null while loading, empty when it couldn't be read). */
    val precons = kotlinx.coroutines.flow.MutableStateFlow<List<com.mtgscanner.data.PreconEntry>?>(null)
    /** The precon opened, its cards (null while loading), and whether loading failed. */
    val precon = kotlinx.coroutines.flow.MutableStateFlow<Triple<com.mtgscanner.data.PreconEntry, com.mtgscanner.data.PreconDeck?, Boolean>?>(null)

    fun loadPrecons(fresh: Boolean = false) {
        if (!fresh && !precons.value.isNullOrEmpty()) return
        precons.value = null
        viewModelScope.launch { precons.value = com.mtgscanner.data.Precons.list(repo.appContext, fresh).orEmpty() }
    }

    fun openPrecon(entry: com.mtgscanner.data.PreconEntry?) {
        if (entry == null) { precon.value = null; return }
        precon.value = Triple(entry, null, false)
        viewModelScope.launch {
            val deck = com.mtgscanner.data.Precons.deck(repo.appContext, entry)
            if (precon.value?.first == entry) precon.value = Triple(entry, deck, deck == null)
        }
    }

    /** Saves a precon as a deck, each card's printing named, with Undo. */
    fun savePrecon(deck: com.mtgscanner.data.PreconDeck) = viewModelScope.launch {
        val text = deck.asText()
        val lines = com.mtgscanner.util.DecklistParser.parse(text)
        val format = if (deck.entry.isCommander) "commander" else null
        val leads = if (deck.entry.isCommander) com.mtgscanner.data.DeckCommanders.fromLines(lines).encode() else null
        val id = repo.saveDeck(0L, deck.entry.name, text, format, leads)
        Undo.offer("Saved deck: ${deck.entry.name}") { repo.deleteDeckAndCards(id, false) }
    }

    /** Puts what a precon is missing on the wishlist. */
    fun wishPrecon(deck: com.mtgscanner.data.PreconDeck) {
        val missing = deck.missing(ownedByName.value)
        wishShort(missing.map { (n, c) -> DeckShortage(n, listOf(deck.entry.name to c), 0) })
    }

    /** Decks › New › From a link. */
    fun importLink(url: String) = viewModelScope.launch {
        Undo.offer("Getting deck…")
        repo.saveDeckFromLink(url)
    }

    fun deleteFolder(folder: DeckFolder) {
        val ids = DeckShelfStore.state.value.folderOf.filterValues { it == folder.id }.keys.toList()
        DeckShelfStore.deleteFolder(folder.id)
        Undo.offer("Folder deleted") { DeckShelfStore.restoreFolder(folder, ids) }
    }
}
