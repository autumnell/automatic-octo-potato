package com.mtgscanner.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.PendingScan
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.CsvImport
import com.mtgscanner.util.ImportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SettingsViewModel(private val repo: CollectionRepository) : ViewModel() {
    val boxes: StateFlow<List<Box>> = repo.observeBoxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pending: StateFlow<List<PendingScan>> = repo.observePending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** Rows read from a CSV, waiting for the user to pick a box. */
    private val _importRows = MutableStateFlow<List<ImportRow>?>(null)
    val importRows: StateFlow<List<ImportRow>?> = _importRows

    /** Runs one action at a time. Returns false (and says why) if another is still running. */
    private fun run(label: String, block: suspend () -> String): Boolean {
        _busy.value?.let { _message.value = "Wait for \"${it.removeSuffix("…")}\" to finish"; return false }
        _busy.value = label
        viewModelScope.launch {
            _message.value = runCatching { block() }.getOrElse { "Something went wrong: ${it.message}" }
            _busy.value = null
        }
        return true
    }

    fun backup(ctx: android.content.Context, uri: Uri) = run("Saving backup…") {
        val out = withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri) } ?: return@run "Couldn't save to that file"
        repo.exportBackup(out)
        "Backup saved"
    }

    fun restore(ctx: android.content.Context, uri: Uri) = run("Restoring…") {
        val input = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri) } ?: return@run "Couldn't open that file"
        val n = repo.restoreBackup(input)
        "Restored $n cards"
    }

    fun readCsv(ctx: android.content.Context, uri: Uri) = run("Reading file…") {
        val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
            ?: return@run "Couldn't open that file"
        val rows = CsvImport.parse(text)
        if (rows.isEmpty()) return@run "No cards found. Is it a collection CSV?"
        _importRows.value = rows
        "Found ${rows.sumOf { it.quantity }} cards"
    }

    /** [choice] is a box id, [Box.AUTO_SORT], or 0 for a new "Imported" box. */
    fun import(choice: Long) {
        val rows = _importRows.value ?: return
        // Keep the rows if something else is still running, so the import can be tried again.
        val started = run("Importing ${rows.size} cards…") {
            val newBox: suspend () -> Long = { repo.createBox("Imported", "box") }
            val target = if (choice == 0L) newBox() else choice
            val r = repo.importRows(rows, target, newBox)
            buildString {
                append("Imported ${r.added} cards")
                if (r.notFound.isNotEmpty()) append(". Not found: ${r.notFound.take(5).joinToString()}${if (r.notFound.size > 5) " and ${r.notFound.size - 5} more" else ""}")
            }
        }
        if (started) _importRows.value = null
    }

    fun cancelImport() { _importRows.value = null }

    fun refreshRoles() = run("Looking up card roles…") {
        repo.refreshAllRoles()
        "Card roles updated"
    }

    fun resolvePending() = run("Looking up saved scans…") {
        val n = repo.resolvePending()
        "Added $n saved cards"
    }

    fun messageShown() { _message.value = null }
}

/** Backup, restore, import, and maintenance actions. The caller shows [vm]'s messages, so they still appear after leaving this tab. */
@Composable
fun DataSection(vm: SettingsViewModel = viewModel(factory = AppViewModels.Factory)) {
    val ctx = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val rows by vm.importRows.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.backup(ctx, uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readCsv(ctx, uri)
    }

    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column {
            busy?.let {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = Palette.Outline)
            }
            Action(Icons.Filled.FileUpload, "Import CSV", "ManaBox, Moxfield, Archidekt, Deckbox…") {
                csvLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
            }
            HorizontalDivider(color = Palette.Outline)
            Action(Icons.Filled.Save, "Back up", "Save everything to a file") {
                backupLauncher.launch("mtg-scanner-backup-${LocalDate.now()}.json")
            }
            HorizontalDivider(color = Palette.Outline)
            Action(Icons.Filled.Restore, "Restore backup", "Replaces everything in the app") {
                restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
            }
            HorizontalDivider(color = Palette.Outline)
            Action(Icons.Filled.Sell, "Refresh card roles", "Removal, ramp, draw tags") { vm.refreshRoles() }
            if (pending.isNotEmpty()) {
                HorizontalDivider(color = Palette.Outline)
                Action(Icons.Filled.CloudSync, "Add ${pending.sumOf { it.quantity }} offline scans", "Scanned while offline") { vm.resolvePending() }
            }
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            containerColor = Palette.Surface,
            title = { Text("Restore this backup?") },
            text = { Text("Everything currently in the app is replaced by the backup.") },
            confirmButton = { TextButton(onClick = { vm.restore(ctx, uri); confirmRestore = null }) { Text("Restore", color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }

    rows?.let { r -> ImportDialog(r, boxes, onCancel = vm::cancelImport, onImport = vm::import) }
}

@Composable
private fun ImportDialog(rows: List<ImportRow>, boxes: List<Box>, onCancel: () -> Unit, onImport: (Long) -> Unit) {
    var choice by remember { mutableLongStateOf(if (boxes.isEmpty()) 0L else Box.AUTO_SORT) }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Palette.Surface,
        title = { Text("Import ${rows.sumOf { it.quantity }} cards") },
        text = {
            Column {
                if (boxes.isEmpty()) {
                    Text("They'll go into a new box called \"Imported\".")
                } else {
                    Text("Where should they go?")
                    Spacer(Modifier.padding(top = 4.dp))
                    Choice("Auto-sort by box rules", choice == Box.AUTO_SORT, Icons.Filled.AutoFixHigh) { choice = Box.AUTO_SORT }
                    boxes.forEach { b -> Choice(b.name, choice == b.id, null) { choice = b.id } }
                    Choice("A new box called \"Imported\"", choice == 0L, null) { choice = 0L }
                }
            }
        },
        confirmButton = {
            // 0 = new box. With auto-sort, cards that fit no box go to a new "Imported" box too.
            TextButton(onClick = { onImport(choice) }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, icon: ImageVector?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Palette.Accent))
        if (icon != null) {
            Icon(icon, null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Action(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.Accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
