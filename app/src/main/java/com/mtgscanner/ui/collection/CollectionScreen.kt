package com.mtgscanner.ui.collection

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.StatTile
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OverviewUi(
    val totalCards: Int = 0,
    val totalValue: Double = 0.0,
    val boxes: List<BoxSummary> = emptyList(),
)

class CollectionViewModel(private val repo: CollectionRepository) : ViewModel() {
    val ui: StateFlow<OverviewUi> = combine(repo.observeAll(), repo.observeBoxSummaries()) { all, boxes ->
        OverviewUi(all.sumOf { it.quantity }, all.sumOf { it.totalPrice() }, boxes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUi())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    fun createBox(name: String, kind: String) = viewModelScope.launch { repo.createBox(name, kind) }
    fun updateBox(id: Long, name: String, kind: String) = viewModelScope.launch { repo.updateBox(id, name, kind) }
    fun deleteBox(id: Long) = viewModelScope.launch { repo.deleteBox(id) }

    fun refreshPrices() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            _message.value = runCatching { repo.refreshPrices() }
                .fold({ "Updated prices for $it cards" }, { "Couldn't refresh prices: ${it.message}" })
            _refreshing.value = false
        }
    }

    fun export(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _message.value = runCatching {
                val cards = repo.allCards()
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.bufferedWriter()?.use { CsvExporter.write(cards, it) }
                }
                cards.size
            }.fold({ "Exported $it entries" }, { "Export failed: ${it.message}" })
        }
    }

    fun messageShown() { _message.value = null }
}

/** Collection overview: totals, then boxes. Tapping a box (or "Browse entire collection") opens the card list. */
@Composable
fun CollectionScreen(
    onBrowse: (boxId: Long) -> Unit,
    onSettings: () -> Unit,
    vm: CollectionViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BoxSummary?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BoxSummary?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(ctx.contentResolver, uri)
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Collection", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::refreshPrices) { Icon(Icons.Filled.Refresh, "Refresh prices") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Filled.Settings, null) },
                            onClick = { menu = false; onSettings() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            onClick = {
                                menu = false
                                exportLauncher.launch("mtg-collection-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.csv")
                            },
                        )
                    }
                }
            }
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Palette.Accent)

            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Number of cards", "${ui.totalCards}", Modifier.weight(1f))
                StatTile("Total value", formatUsd(ui.totalValue), Modifier.weight(1f))
            }

            Row(
                Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onBrowse(-1L) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Browse entire collection", style = MaterialTheme.typography.titleMedium, color = Palette.Muted)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.Text)
            }

            SectionHeader("Boxes") {
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
            ui.boxes.forEach { b ->
                BoxCard(
                    b,
                    onClick = { onBrowse(b.id) },
                    onEdit = { editing = b },
                    onDelete = if (b.id == CardBox.BULK_ID) null else ({ deleting = b }),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (creating) {
        BoxDialog(title = "New box", initialName = "", initialKind = "binder", onDismiss = { creating = false }) { name, kind ->
            vm.createBox(name, kind); creating = false
        }
    }
    editing?.let { b ->
        BoxDialog(title = "Edit box", initialName = b.name, initialKind = b.kind, onDismiss = { editing = null }) { name, kind ->
            vm.updateBox(b.id, name, kind); editing = null
        }
    }
    deleting?.let { b ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Palette.Surface,
            title = { Text("Delete ${b.name}?") },
            text = { Text("Its ${b.cards} cards move to Bulk. Nothing is removed from your collection.") },
            confirmButton = { TextButton(onClick = { vm.deleteBox(b.id); deleting = null }) { Text("Delete", color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BoxCard(b: BoxSummary, onClick: () -> Unit, onEdit: () -> Unit, onDelete: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = Palette.BoxCard,
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(boxIcon(b.kind), null, tint = Palette.Accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(b.name, style = MaterialTheme.typography.titleLarge, color = Palette.Accent)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (b.cards == 0) "No cards" else "${b.cards} cards",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                    )
                    Text(formatUsd(b.value), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Box options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onEdit() })
                if (onDelete != null) {
                    DropdownMenuItem(text = { Text("Delete", color = Palette.Danger) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun BoxDialog(
    title: String,
    initialName: String,
    initialKind: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var kind by remember { mutableStateOf(initialKind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Trade binder, Commander deck…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardBox.KINDS.forEach { k ->
                        SelectPill(k.replaceFirstChar(Char::uppercase), kind == k, onClick = { kind = k }, leading = {
                            Icon(boxIcon(k), null, modifier = Modifier.size(14.dp), tint = if (kind == k) Palette.Accent else Palette.Muted)
                        })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, kind) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
