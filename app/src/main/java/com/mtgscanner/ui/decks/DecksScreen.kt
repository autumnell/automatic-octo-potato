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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.outlined.Check
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
import com.mtgscanner.data.checkDeck
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
    val ctx = LocalContext.current
    val owned = row.cards > 0 && row.owned >= row.cards
    val status = DeckStatusStore.get(ctx, row.deck.id)
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
                // Top left: "Owned" once every card is owned, else the status picked from the menu.
                val tag = if (owned) "Owned" else status?.label
                if (tag != null) {
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                            .clip(RoundedCornerShape(Corners.Small)).background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
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
                        // A fully owned deck always shows "Owned".
                        if (!owned) {
                            HorizontalDivider(color = Palette.Rule)
                            DeckStatus.entries.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text(st.label) },
                                    onClick = { menu = false; DeckStatusStore.set(ctx, row.deck.id, if (status == st) null else st) },
                                    trailingIcon = { if (status == st) Icon(Icons.Outlined.Check, null, tint = Palette.Accent) },
                                )
                            }
                        }
                    }
                }
            }
            if (owned) {
                Spacer(Modifier.height(8.dp))
            } else Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp)) {
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
