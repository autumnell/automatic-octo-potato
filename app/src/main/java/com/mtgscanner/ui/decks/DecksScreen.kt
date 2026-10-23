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
import com.mtgscanner.ui.collection.wantAllWithUndo
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
private val PLAYED = DeckCheck.PLAYED

/** Parsed lists by deck id, so a collection change doesn't reparse every deck. */
private val parsedDecks = java.util.concurrent.ConcurrentHashMap<Long, Pair<String, List<DeckLine>>>()

private fun parsed(d: Deck): List<DeckLine> =
    parsedDecks[d.id]?.takeIf { it.first == d.text }?.second
        ?: DecklistParser.parse(d.text).also { parsedDecks[d.id] = d.text to it }

class DecksViewModel(private val repo: CollectionRepository) : ViewModel() {
    init {
        DeckShelfStore.init(repo.appContext)
        PullChecksStore.init(repo.appContext)
    }

    val shelf: StateFlow<DeckShelf> = DeckShelfStore.state

    /** Tile data worked out this session that isn't saved yet (some cards weren't found). */
    private val liveMeta = MutableStateFlow<Map<Long, DeckMeta>>(emptyMap())
    private val tried = mutableSetOf<String>()

    val decks: StateFlow<List<DeckRow>?> = combine(repo.observeDecks(), repo.observeAll(), DeckShelfStore.state, liveMeta) { decks, cards, shelf, live ->
        // Same name matching as the deck check, and each owned copy counts toward one line only.
        fun key(n: String) = com.mtgscanner.util.CardNames.key(n)
        val owned = cards.groupBy { key(it.name) }.mapValues { (_, v) -> v.sumOf { it.quantity } }
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
        val info = runCatching { repo.deckCardInfo(names) }.getOrDefault(emptyMap())
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

/** The Decks tab on the Collection screen: folders and decks as art tiles. */
@Composable
fun DecksTab(onOpenDeck: (Long) -> Unit, vm: DecksViewModel = viewModel(factory = AppViewModels.Factory)) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    var folderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var naming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<DeckRow?>(null) }
    var newFolderFor by remember { mutableStateOf<DeckRow?>(null) }
    var covering by remember { mutableStateOf<DeckRow?>(null) }
    var newMenu by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }

    val folder = shelf.folders.firstOrNull { it.id == folderId }
    BackHandler(enabled = folder != null) { folderId = null }
    val all = decks.orEmpty()
    val q = query.trim()
    val searching = q.isNotEmpty()
    val shown = when {
        searching -> all.filter { it.deck.name.contains(q, ignoreCase = true) }
        folder != null -> all.filter { it.folderId == folder.id }
        else -> all.filter { it.folderId == null }
    }
    val folderTiles = when {
        searching -> shelf.folders.filter { it.name.contains(q, ignoreCase = true) }
        folder != null -> emptyList()
        else -> shelf.folders
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (folder != null) {
                    IconButton(onClick = { folderId = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "All decks") }
                    Text(
                        folder.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, "Rename folder", tint = Palette.Icon) }
                    IconButton(onClick = { vm.deleteFolder(folder); folderId = null }) {
                        Icon(Icons.Outlined.DeleteOutline, "Delete folder", tint = Palette.Icon)
                    }
                } else {
                    Text("Decks", style = MaterialTheme.typography.titleMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                    IconButton(onClick = { naming = true }) { Icon(Icons.Outlined.CreateNewFolder, "New folder", tint = Palette.AccentText) }
                    Box {
                        TextButton(onClick = { newMenu = true }) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New")
                        }
                        DropdownMenu(expanded = newMenu, onDismissRequest = { newMenu = false }) {
                            DropdownMenuItem(text = { Text("Paste a list") }, onClick = { newMenu = false; onOpenDeck(0L) })
                            DropdownMenuItem(text = { Text("From a link") }, onClick = { newMenu = false; linking = true })
                        }
                    }
                }
            }
        }
        if (decks?.isEmpty() == true && shelf.folders.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                EmptyState("No decks yet", action = "Paste a decklist", onAction = { onOpenDeck(0L) }, fullScreen = true)
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search decks") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Palette.Icon) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(Corners.Medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(folderTiles, key = { "f" + it.id }) { f ->
                val inside = all.filter { it.folderId == f.id }
                FolderTile(f, inside) { query = ""; folderId = f.id }
            }
            items(shown, key = { "d" + it.deck.id }) { row ->
                DeckTile(
                    row = row,
                    onOpen = { onOpenDeck(row.deck.id) },
                    onMove = { moving = row },
                    onCover = { covering = row },
                )
            }
            if (decks != null && shown.isEmpty() && folderTiles.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                    if (searching) {
                        EmptyState("No decks found")
                    } else if (folder != null) {
                        EmptyState("Empty folder")
                    } else {
                        EmptyState("No decks here", action = "New deck", onAction = { onOpenDeck(0L) })
                    }
                }
            }
        }
    }

    if (linking) DeckLinkDialog(onDismiss = { linking = false }) { url -> linking = false; vm.importLink(url) }
    if (naming) {
        FolderNameDialog("New folder", "", onSave = { vm.createFolder(it); naming = false }, onDismiss = { naming = false })
    }
    if (renaming && folder != null) {
        FolderNameDialog("Rename folder", folder.name, onSave = { vm.renameFolder(folder.id, it); renaming = false }, onDismiss = { renaming = false })
    }
    moving?.let { row ->
        MoveToFolderDialog(
            folders = shelf.folders,
            current = row.folderId,
            onPick = { vm.moveDeck(row.deck.id, it); moving = null },
            onNewFolder = { newFolderFor = row; moving = null },
            onDismiss = { moving = null },
        )
    }
    newFolderFor?.let { row ->
        FolderNameDialog(
            "New folder", "",
            onSave = { vm.moveDeck(row.deck.id, vm.createFolder(it)); newFolderFor = null },
            onDismiss = { newFolderFor = null },
        )
    }
    covering?.let { row ->
        CoverPickerDialog(
            names = row.names,
            current = row.cover,
            onPick = { vm.setCover(row.deck.id, it); covering = null },
            onDismiss = { covering = null },
        )
    }
}

private val TileShape = RoundedCornerShape(Corners.Medium)

/** Art, or a plain panel with a deck icon when there's no art yet. */
@Composable
private fun TileArt(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(Palette.SurfaceHigh)) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.Outlined.Style, null, tint = Palette.Icon, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
    }
}

@Composable
private fun DeckTile(row: DeckRow, onOpen: () -> Unit, onMove: () -> Unit, onCover: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(onClick = onOpen, shape = TileShape, color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.35f)) {
                if (row.art2 != null && row.art != null) {
                    Row(Modifier.matchParentSize()) {
                        TileArt(row.art, Modifier.weight(1f).fillMaxSize())
                        TileArt(row.art2, Modifier.weight(1f).fillMaxSize())
                    }
                } else {
                    TileArt(row.art, Modifier.matchParentSize())
                }
                if (row.identity.isNotEmpty()) {
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .clip(RoundedCornerShape(Corners.Small)).background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        row.identity.forEach { c -> Mana.Symbol(c.toString(), 15.dp) }
                    }
                }
                if (row.cards > 0 && row.owned >= row.cards) {
                    Icon(
                        Icons.Filled.CheckCircle, "Complete", tint = Palette.Success,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(20.dp)
                            .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)),
                    )
                }
            }
            Row(Modifier.padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        row.deck.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.MoreVert, "Deck options", tint = Palette.Icon, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Move to folder") }, onClick = { menu = false; onMove() })
                        DropdownMenuItem(text = { Text("Cover card") }, onClick = { menu = false; onCover() })
                    }
                }
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp)) {
                LinearProgressIndicator(
                    progress = { if (row.cards == 0) 0f else row.owned.toFloat() / row.cards },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Palette.Accent,
                    trackColor = Palette.SurfaceHigh,
                )
                Spacer(Modifier.height(4.dp))
                Text("You own ${row.owned} of ${row.cards}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FolderTile(folder: DeckFolder, decks: List<DeckRow>, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = TileShape, color = Palette.BoxCard, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.35f)) {
                val arts = decks.mapNotNull { it.art }.take(2)
                if (arts.size == 2) {
                    Row(Modifier.matchParentSize()) {
                        arts.forEach { TileArt(it, Modifier.weight(1f).fillMaxSize()) }
                    }
                } else {
                    TileArt(arts.firstOrNull(), Modifier.matchParentSize())
                }
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                Icon(Icons.Outlined.Folder, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(28.dp))
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    folder.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(if (decks.size == 1) "1 deck" else "${decks.size} decks", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
    }
}

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

/** One deck: paste or edit the list, then see what you own, where it is, and what's missing. */
@Composable
fun DeckScreen(deckId: Long, onBack: () -> Unit, onOpenSet: ((String) -> Unit)? = null) {
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
    val inDeck by vm.inDeck.collectAsStateWithLifecycle()
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    val dirty = ui.editing && ui.text.trim() != vm.savedText.trim()
    DeckChangesLayer(vm.changes, dirty, onDiscard = { if (ui.id == 0L) onBack() else vm.cancelEdit() }) { url ->
        vm.changes.updateFromLink(ui.id, vm.savedText, url)
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            if (ui.check != null) DropdownMenuItem(text = { Text("Sample hand") }, onClick = { menu = false; sampling = true })
                            DeckListMenuItems(ui, close = { menu = false }, onDuplicate = vm::duplicate)
                        }
                    }
                }
            }
            if (!ui.loaded) return@Column
            if (ui.editing) Editor(ui, vm)
            else {
                val ctx = LocalContext.current
                val edhrec = ui.edhrec
                DeckHeader(
                    ui.format, ui.commanders, ui.leadArt, ui.issues,
                    onEdhrec = if (edhrec == null) null else ({ openLink(ctx, edhrec) }),
                )
                UnderlineTabs(listOf("Cards", "Pull list"), view, { view = it }, Modifier.padding(bottom = 4.dp), horizontalPadding = 16.dp)
                when (view) {
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
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
                Text(if (copies == 1) "1 card is in this deck." else "$copies cards are in this deck.", color = Palette.Muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Accent)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = ui.name, onValueChange = vm::setName, label = { Text("Deck name") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), colors = colors,
        )
        Spacer(Modifier.height(10.dp))
        Text("Format", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        Spacer(Modifier.height(6.dp))
        FormatPicker(ui.format, vm::setFormat)
        if (DeckFormats.isCommander(ui.format)) {
            Spacer(Modifier.height(10.dp))
            CommanderSlots(ui.format, ui.commanders, ui.leadInfo, vm::openPicker, vm::clearSlot)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = ui.text, onValueChange = vm::setText,
            placeholder = { Text("4 Lightning Bolt\n1 Sol Ring (CMR) 263\n\nSideboard\n2 Duress") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 420.dp),
            shape = RoundedCornerShape(Corners.Medium), colors = colors,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("One card per line.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f))
            // A photo of a printed list: its lines join what's here, to check before saving.
            FromPhotoButton({ vm.setText(appendList(vm.ui.value.text, it)) })
        }
        Spacer(Modifier.height(8.dp))
        GradientButton("Check my collection", vm::save, Modifier.fillMaxWidth(), enabled = ui.text.isNotBlank())
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Results(ui: DeckUi, vm: DeckViewModel, onOpenSet: ((String) -> Unit)?) {
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var find by rememberSaveable { mutableStateOf("") }
    val check = ui.check
    if (ui.checking || check == null) {
        PlaceholderRows(6, Modifier.padding(16.dp))
        return
    }
    // Totals count played cards only, the same as the deck tiles.
    val played = check.lines.filter { it.section in PLAYED }
    val needed = played.sumOf { it.needed }
    val have = played.sumOf { minOf(it.owned, it.needed) }
    val missingCost = played.sumOf { (it.price ?: 0.0) * it.missing }
    // A missing card with no price: "$12.30 + ?", or "--" when none has one, never a short total.
    val missingCostText = when {
        !check.missingCostPartial -> formatUsd(missingCost)
        missingCost > 0 -> formatUsd(missingCost) + " + ?"
        else -> formatUsd(null as Double?)
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
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Owned", "$have / $needed", Modifier.weight(1f))
                if (AppSettings.showPrices) Stat("Missing cost", missingCostText, Modifier.weight(1f))
                else Stat("Missing", "${needed - have}", Modifier.weight(1f))
            }
            if (have > 0) {
                Text(
                    "$inDeck in the deck · ${have - inDeck} elsewhere",
                    style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 6.dp),
                )
            }
            if (played.any { it.missing > 0 }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = vm::wishlistMissing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Corners.Medium),
                    ) {
                        Icon(Icons.Outlined.FavoriteBorder, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Wishlist missing")
                    }
                    IconButton(onClick = { sharing = true }) { Icon(Icons.Outlined.Share, "Share missing cards", tint = Palette.Icon) }
                }
            }
            SearchField(find, { find = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), placeholder = "Find in deck")
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
        item { Spacer(Modifier.height(24.dp)) }
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
        Column(Modifier.padding(14.dp)) {
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
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).panelRow(first, last).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(l.imageUrl, Modifier.width(40.dp), zoom = true)
        Spacer(Modifier.width(12.dp))
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
                if (symbolCode != null && link == null) SetSymbol(symbolCode, modifier = Modifier.padding(end = 4.dp))
                if (where.isNotEmpty()) Text(
                    if (link != null) "$where · " else where,
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (symbolCode != null && link != null) SetSymbol(symbolCode, modifier = Modifier.padding(end = 4.dp))
                if (link != null && set != null && onOpenSet != null) Text(
                    link, style = MaterialTheme.typography.bodySmall, color = Palette.AccentText, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(Corners.Small)).clickable { onOpenSet(set.lowercase()) },
                )
            }
        }
        if (missingView) {
            // A fixed right-aligned slot so prices line up down the list.
            if (AppSettings.showPrices) Text(
                formatUsd(l.price?.times(l.missing)), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.Text,
                textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.padding(start = 8.dp).widthIn(min = 64.dp),
            )
        }
        else Icon(Icons.Outlined.CheckCircle, null, tint = if (l.inDeck >= l.needed) Palette.Success else Palette.Gold, modifier = Modifier.size(18.dp))
    }
}
