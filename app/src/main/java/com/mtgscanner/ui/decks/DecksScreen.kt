package com.mtgscanner.ui.decks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.PrintingLookup
import com.mtgscanner.data.DeckCheck
import com.mtgscanner.data.DeckCheckLine
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.collection.ShareDialog
import com.mtgscanner.ui.collection.shareLine
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.Mana
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.DeckLine
import com.mtgscanner.util.DecklistParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val cover: String? = null,
    /** Distinct card names, for picking a cover. */
    val names: List<String> = emptyList(),
)

/** Sections that are played; sideboard, maybeboard and tokens don't count toward owning a deck. */
private val PLAYED = setOf("Main", "Commander", "Companion")

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
        fun key(n: String) = com.mtgscanner.scan.CardTextParser.normalize(n.substringBefore(" // "))
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
                subtitle = if (lines.any { it.section == "Commander" }) "Commander · $total" else "$total cards",
                folderId = shelf.folderOf[d.id]?.takeIf { it in folderIds },
                identity = meta?.identity.orEmpty(),
                art = meta?.art,
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
            val coverName = cover[d.id]
                ?: (played.firstOrNull { it.section == "Commander" } ?: played.firstOrNull() ?: lines.firstOrNull())?.name
            played to coverName
        }
        val names = byDeck.values.flatMap { (played, coverName) -> played.map { it.name } + listOfNotNull(coverName) }.toSet()
        val info = runCatching { repo.deckCardInfo(names) }.getOrDefault(emptyMap())
        val done = HashMap<Long, DeckMeta>()
        val partial = HashMap<Long, DeckMeta>()
        for ((d, pc) in byDeck) {
            val (played, coverName) = pc
            val st = stamp(d, cover[d.id])
            tried += st
            val found = played.mapNotNull { info[DeckNames.key(it.name)] }
            val identity = "WUBRG".filter { c -> found.any { c in it.colorIdentity.uppercase() } }
            val art = coverName?.let { info[DeckNames.key(it)]?.imageUrl }?.let { artCropUrl(it) }
            val meta = DeckMeta(st, identity, art)
            // Names Scryfall says don't exist (typos) won't turn up later, so they don't keep the tile from being saved.
            fun settled(n: String) = info.containsKey(DeckNames.key(n)) || deckCardUnknown(n)
            val complete = played.all { settled(it.name) } && (coverName == null || art != null || deckCardUnknown(coverName))
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
                    IconButton(onClick = { folderId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "All decks") }
                    Text(
                        folder.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, "Rename folder", tint = Palette.Icon) }
                    IconButton(onClick = { vm.deleteFolder(folder); folderId = null }) {
                        Icon(Icons.Filled.DeleteOutline, "Delete folder", tint = Palette.Icon)
                    }
                } else {
                    Text("Decks", style = MaterialTheme.typography.titleMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                    IconButton(onClick = { naming = true }) { Icon(Icons.Filled.CreateNewFolder, "New folder", tint = Palette.AccentText) }
                    Box {
                        TextButton(onClick = { newMenu = true }) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
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
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Palette.Icon) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
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
            Icon(Icons.Filled.Style, null, tint = Palette.Icon, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
    }
}

@Composable
private fun DeckTile(row: DeckRow, onOpen: () -> Unit, onMove: () -> Unit, onCover: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(onClick = onOpen, shape = TileShape, color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.35f)) {
                TileArt(row.art, Modifier.matchParentSize())
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
                        Icon(Icons.Filled.MoreVert, "Deck options", tint = Palette.Icon, modifier = Modifier.size(20.dp))
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
                Icon(Icons.Filled.Folder, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(28.dp))
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
)

class DeckViewModel(private val repo: CollectionRepository, deckId: Long) : ViewModel() {
    private val _ui = MutableStateFlow(DeckUi(id = deckId))
    val ui: StateFlow<DeckUi> = _ui
    private var saving = false
    private var wishing = false
    private var deleting = false

    init {
        DeckShelfStore.init(repo.appContext)
        PullChecksStore.init(repo.appContext)
        viewModelScope.launch {
            val deck = if (deckId != 0L) repo.observeDeck(deckId).first() else null
            _ui.update { it.copy(loaded = true, name = deck?.name ?: "", text = deck?.text ?: "", editing = deck == null) }
            if (deck != null) check()
        }
    }

    /** Owned copies to take out of boxes for the saved list, kept up to date with the collection. */
    val pull: StateFlow<PullList?> = combine(
        _ui.map { it.text to it.name }.distinctUntilChanged(), repo.observeAll(), repo.observeBoxes(),
    ) { (text, name), cards, boxes ->
        PullListMath.build(DecklistParser.parse(text), cards, boxes, name)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Pull list checkmarks for this deck. */
    val pullChecks: StateFlow<Set<String>> = combine(_ui.map { it.id }.distinctUntilChanged(), PullChecksStore.state) { id, m -> m[id].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun togglePulled(key: String) { _ui.value.id.takeIf { it != 0L }?.let { PullChecksStore.toggle(it, key) } }
    fun clearPulled() = PullChecksStore.clear(_ui.value.id)

    private var filing = false

    /** Moves the checked cards into this deck's deck box (made if there isn't one), with Undo. */
    fun moveCheckedToDeckBox() {
        val id = _ui.value.id
        val name = _ui.value.name.ifBlank { "Untitled deck" }
        val list = pull.value ?: return
        val checks = pullChecks.value
        if (filing || id == 0L || checks.isEmpty()) return
        filing = true
        viewModelScope.launch {
            try {
                val existing = repo.observeBoxes().first().firstOrNull { PullListMath.isDeckBoxFor(it, name) }
                val moves = list.movesFor(checks, existing?.id)
                if (moves.isEmpty()) return@launch
                val before = repo.snapshot(moves.map { it.first })
                val boxId = existing?.id ?: repo.createBox(name, "deck")
                repo.moveCopiesMany(moves, boxId)
                PullChecksStore.clear(id)
                val n = moves.sumOf { it.second }
                Undo.offer(if (n == 1) "Moved 1 card to $name" else "Moved $n cards to $name") {
                    repo.restore(before)
                    if (existing == null) repo.deleteBox(boxId, com.mtgscanner.data.db.Box.AUTO_SORT)
                    PullChecksStore.set(id, checks)
                }
            } finally {
                filing = false
            }
        }
    }

    fun setName(v: String) = _ui.update { it.copy(name = v) }
    fun setText(v: String) = _ui.update { it.copy(text = v) }
    fun edit() = _ui.update { it.copy(editing = true) }

    fun save() {
        val s = _ui.value
        // A second tap while the first save runs would save a new deck twice.
        if (s.text.isBlank() || saving) return
        saving = true
        viewModelScope.launch {
            try {
                val name = s.name.ifBlank { "Untitled deck" }
                val id = repo.saveDeck(s.id, name, s.text)
                _ui.update { it.copy(id = id, name = name, editing = false) }
                check()
            } finally {
                saving = false
            }
        }
    }

    private var checkJob: Job? = null

    /** Checks the saved list against the collection, and again whenever the collection changes. */
    private fun check() {
        _ui.update { it.copy(checking = true) }
        val text = _ui.value.text
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            var initial = true
            combine(repo.observeAll(), repo.observeBoxes()) { cards, boxes -> cards to boxes }.collectLatest { (cards, boxes) ->
                // Later changes come in bursts (a scan, a bulk move): wait for them to settle.
                if (!initial) delay(400)
                initial = false
                val result = repo.checkDeck(text, cards, boxes)
                _ui.update { it.copy(checking = false, check = result) }
            }
        }
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
                val msg = runCatching {
                    // The printing the list names, when it has one.
                    val wants = missing.map { (l, _) -> PrintingLookup.Wanted(l.name, l.setCode, l.collectorNumber) }
                    val cards = PrintingLookup.resolve(wants) { ids -> repo.api.identify(ids.map(PrintingLookup::json)) }
                    var n = 0
                    missing.zip(cards).forEach { (m, c) -> if (c != null) { repo.wantAtLeast(c, m.second); n++ } }
                    n
                }.fold({ "Added $it cards to your wishlist" }, { "Couldn't reach Scryfall" })
                _ui.update { it.copy(message = msg) }
            } finally {
                wishing = false
            }
        }
    }

    /** Deletes the deck, with Undo bringing it back in its folder with its cover. */
    fun delete(onDone: () -> Unit) {
        val id = _ui.value.id
        if (deleting || id == 0L) return
        deleting = true
        viewModelScope.launch {
            val deck = repo.observeDeck(id).first()
            val shelf = DeckShelfStore.state.value
            val folder = shelf.folderOf[id]
            val cover = shelf.cover[id]
            repo.deleteDeck(id)
            if (deck != null) Undo.offer("Deck deleted") {
                val newId = repo.saveDeck(0L, deck.name, deck.text)
                if (folder != null) DeckShelfStore.moveDeck(newId, folder)
                if (cover != null) DeckShelfStore.setCover(newId, cover)
            }
            onDone()
        }
    }

    fun messageShown() = _ui.update { it.copy(message = null) }
}

/** One deck: paste or edit the list, then see what you own, where it is, and what's missing. */
@Composable
fun DeckScreen(deckId: Long, onBack: () -> Unit) {
    val vm: DeckViewModel = viewModel(key = "deck$deckId", factory = AppViewModels.deck(deckId))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var view by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    if (ui.editing) (if (ui.id == 0L) "New deck" else "Edit deck") else ui.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!ui.editing) {
                    IconButton(onClick = vm::edit) { Icon(Icons.Filled.Edit, "Edit list") }
                    IconButton(onClick = { vm.delete(onBack) }) { Icon(Icons.Filled.DeleteOutline, "Delete deck") }
                }
            }
            if (!ui.loaded) return@Column
            if (ui.editing) Editor(ui, vm)
            else {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectPill("Cards", view == 0, { view = 0 })
                    SelectPill("Pull list", view == 1, { view = 1 })
                }
                when (view) {
                    1 -> {
                        val pull by vm.pull.collectAsStateWithLifecycle()
                        val checks by vm.pullChecks.collectAsStateWithLifecycle()
                        PullListView(pull, checks, vm::togglePulled, vm::clearPulled, vm::moveCheckedToDeckBox)
                    }
                    else -> Results(ui, vm)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Editor(ui: DeckUi, vm: DeckViewModel) {
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Accent)
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = ui.name, onValueChange = vm::setName, label = { Text("Deck name") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), colors = colors,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = ui.text, onValueChange = vm::setText,
            placeholder = { Text("4 Lightning Bolt\n1 Sol Ring (CMR) 263\n\nSideboard\n2 Duress") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 420.dp),
            shape = RoundedCornerShape(Corners.Medium), colors = colors,
        )
        Spacer(Modifier.height(6.dp))
        Text("One card per line.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        Spacer(Modifier.height(14.dp))
        GradientButton("Check my collection", vm::save, Modifier.fillMaxWidth(), enabled = ui.text.isNotBlank())
    }
}

@Composable
private fun Results(ui: DeckUi, vm: DeckViewModel) {
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    val check = ui.check
    if (ui.checking || check == null) {
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
        return
    }
    // Totals count played cards only, the same as the deck tiles.
    val played = check.lines.filter { it.section in PLAYED }
    val needed = played.sumOf { it.needed }
    val have = played.sumOf { minOf(it.owned, it.needed) }
    val missingCost = played.sumOf { (it.price ?: 0.0) * it.missing }
    val missing = played.filter { it.missing > 0 }
    val otherMissing = check.lines.filter { it.section !in PLAYED && it.missing > 0 }
    val owned = check.lines.filter { it.owned > 0 }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Owned", "$have / $needed", Modifier.weight(1f))
                if (AppSettings.showPrices) Stat("Missing cost", formatUsd(missingCost), Modifier.weight(1f))
                else Stat("Missing", "${needed - have}", Modifier.weight(1f))
            }
            if (missing.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = vm::wishlistMissing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Corners.Medium),
                    ) {
                        Icon(Icons.Filled.FavoriteBorder, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Wishlist missing")
                    }
                    IconButton(onClick = { sharing = true }) { Icon(Icons.Filled.Share, "Share missing cards", tint = Palette.Icon) }
                }
            }
        }
        // Keys by position: the same card can be listed twice (two printings).
        if (missing.isNotEmpty()) {
            item { SectionHeader("Missing (${missing.sumOf { it.missing }})") }
            itemsIndexed(missing, key = { i, _ -> "m$i" }) { _, l -> DeckLineRow(l, missingView = true) }
        }
        if (otherMissing.isNotEmpty()) {
            item { SectionHeader("Sideboard and more (${otherMissing.sumOf { it.missing }})") }
            itemsIndexed(otherMissing, key = { i, _ -> "x$i" }) { _, l -> DeckLineRow(l, missingView = true) }
        }
        if (owned.isNotEmpty()) {
            item { SectionHeader("In your collection") }
            itemsIndexed(owned, key = { i, _ -> "o$i" }) { _, l -> DeckLineRow(l, missingView = false) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (sharing) ShareDialog("${ui.name.ifBlank { "Deck" }} missing", scope, carts = true, onDismiss = { sharing = false }) {
        missing.map { it.shareLine() }
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
private fun DeckLineRow(l: DeckCheckLine, missingView: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        CardImage(l.imageUrl, Modifier.width(40.dp), zoom = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (missingView) "${l.missing}× " else "${minOf(l.owned, l.needed)}/${l.needed} ") + l.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val printing = l.setCode?.let { it.uppercase() + (l.collectorNumber?.let { n -> " #$n" } ?: "") }
            val where = when {
                l.otherPrinting -> "No $printing · other printing shown"
                missingView -> listOfNotNull(if (l.owned > 0) "Have ${l.owned} of ${l.needed}" else l.section, printing).joinToString(" · ")
                else -> l.boxes.joinToString(" · ") { (b, n) -> "$b ×$n" }
            }
            Text(where, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (missingView) { if (AppSettings.showPrices) Text(formatUsd(l.price?.times(l.missing)), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.AccentText) }
        else Icon(Icons.Filled.CheckCircle, null, tint = if (l.owned >= l.needed) Palette.Success else Palette.Gold, modifier = Modifier.size(18.dp))
    }
}
