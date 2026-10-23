package com.mtgscanner.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.History
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
import com.mtgscanner.data.Backup
import com.mtgscanner.data.BackupIO
import com.mtgscanner.data.BadBackupException
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.DeckLinks
import com.mtgscanner.data.ImportPlan
import com.mtgscanner.data.canUndo
import com.mtgscanner.data.undoImport
import com.mtgscanner.work.Notifications
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.PendingScan
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.CsvExporter
import com.mtgscanner.util.CsvFormat
import com.mtgscanner.util.CsvImport
import com.mtgscanner.util.InsuranceReport
import com.mtgscanner.ui.components.SelectPill
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableDoubleStateOf
import com.mtgscanner.work.DriveBackup
import com.mtgscanner.util.ImportRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.components.PlainGroup

/** Settings jobs (backup, import, restore…) run one at a time, in the app's scope, so leaving Settings doesn't stop them. */
object DataJobs {
    /** What's running, like "Importing 40 cards…"; null when idle. */
    val busy = MutableStateFlow<String?>(null)
}

/** A backup that's been read and checked, waiting for the user to confirm the restore. */
class RestorePreview(val backup: Backup) {
    val cards: Int get() = backup.cardCount
    val boxes: Int get() = backup.boxes.size
    val savedAt: Long get() = backup.exportedAt
    /** It holds settings (deck folders, saved searches…) that need a fresh start to show. */
    val restarts: Boolean get() = BackupIO.needsRestart(backup)
}

class SettingsViewModel(private val repo: CollectionRepository) : ViewModel() {
    val boxes: StateFlow<List<Box>> = repo.observeBoxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pending: StateFlow<List<PendingScan>> = repo.observePending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val busy: StateFlow<String?> = DataJobs.busy
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** Rows read from a CSV, waiting for the user to pick a box. */
    private val _importRows = MutableStateFlow<List<ImportRow>?>(null)
    val importRows: StateFlow<List<ImportRow>?> = _importRows

    private val _restore = MutableStateFlow<RestorePreview?>(null)
    val restorePreview: StateFlow<RestorePreview?> = _restore

    private val _canUndoRestore = MutableStateFlow(BackupIO.safetyFile(repo.appContext).exists())
    /** A safety copy from before the last restore exists. */
    val canUndoRestore: StateFlow<Boolean> = _canUndoRestore

    /**
     * Runs one action at a time in the app's scope, so it finishes even after leaving Settings.
     * Its result shows app-wide. Returns false (and says why) if another is still running.
     */
    private fun run(label: String, block: suspend () -> String?): Boolean {
        if (!DataJobs.busy.compareAndSet(null, label)) {
            _message.value = "Wait for \"${DataJobs.busy.value?.removeSuffix("…")}\" to finish"
            return false
        }
        repo.appScope.launch {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BadBackupException) {
                e.message
            } catch (e: Throwable) {
                "Something went wrong: ${e.message}"
            } finally {
                DataJobs.busy.value = null
            }
            result?.let { com.mtgscanner.ui.components.Undo.offer(it) }
        }
        return true
    }

    fun backup(ctx: android.content.Context, uri: Uri) {
        val app = ctx.applicationContext
        run("Saving backup…") {
            if (BackupIO.export(repo) { openForWrite(app, uri) }) "Backup saved" else "Couldn't save to that file"
        }
    }

    fun exportCsv(ctx: android.content.Context, uri: Uri, format: CsvFormat) {
        val app = ctx.applicationContext
        run("Exporting…") {
            val cards = repo.allCards()
            // Built in memory first, so a failure doesn't leave a half-written file.
            val text = java.io.StringWriter().also { CsvExporter.write(cards, it, format) }.toString()
            val ok = withContext(Dispatchers.IO + NonCancellable) {
                openForWrite(app, uri)?.bufferedWriter()?.use { it.write(text) } != null
            }
            if (ok) "Exported ${cards.size} entries" else "Couldn't save to that file"
        }
    }

    /** Writes the weekly backup file now. */
    fun backupNow(ctx: android.content.Context) {
        val app = ctx.applicationContext
        run("Backing up…") {
            if (DriveBackup.backupNow(app)) "Backup saved" else "Backup failed"
        }
    }

    /** Saves a PDF of every card worth at least [min] per copy. */
    fun insuranceReport(ctx: android.content.Context, uri: Uri, min: Double) {
        val app = ctx.applicationContext
        run("Making report…") {
            val cards = repo.allCards()
            val n = withContext(Dispatchers.IO) {
                openForWrite(app, uri)?.use { InsuranceReport.write(cards, min, it, app.filesDir) }
            } ?: return@run "Couldn't save to that file"
            if (n == 1) "Report saved: 1 card" else "Report saved: $n cards"
        }
    }

    /** Reads and checks a backup; the user then confirms with its card and box counts in front of them. */
    fun previewRestore(ctx: android.content.Context, uri: Uri) {
        val app = ctx.applicationContext
        run("Reading backup…") {
            val input = withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri) } ?: return@run "Couldn't open that file"
            _restore.value = RestorePreview(BackupIO.read(input, app))
            null
        }
    }

    fun cancelRestore() { _restore.value = null }

    fun restore(ctx: android.content.Context, preview: RestorePreview) {
        val app = ctx.applicationContext
        _restore.value = null
        run("Restoring…") {
            val n = BackupIO.restore(repo, preview.backup)
            _canUndoRestore.value = true
            val message = if (n == 1) "Restored 1 card" else "Restored $n cards"
            // Deck folders and saved searches are only read at start-up, so restored ones need a fresh start.
            // The result and its Undo then show after the restart instead of being lost with it.
            if (BackupIO.needsRestart(preview.backup)) {
                RestartNotice.stash(app, message, undoRestore = true)
                restartApp(app)
            }
            com.mtgscanner.ui.components.Undo.offer(message) { undoRestore(app) }
            null
        }
    }

    /** Puts back what the app held before the last restore. */
    fun undoRestore(ctx: android.content.Context) {
        val app = ctx.applicationContext
        run("Undoing restore…") {
            val n = BackupIO.undoRestore(repo) ?: return@run "Nothing to undo"
            val message = if (n == 1) "Back to 1 card" else "Back to $n cards"
            RestartNotice.stash(app, message, undoRestore = false)
            restartApp(app)
            message
        }
    }

    fun readCsv(ctx: android.content.Context, uri: Uri) {
        val app = ctx.applicationContext
        run("Reading file…") {
            val text = withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
                ?: return@run "Couldn't open that file"
            val rows = withContext(Dispatchers.Default) { CsvImport.parseAny(text) }
            if (rows.isEmpty()) return@run "No cards found. Is it a collection CSV or decklist?"
            _importRows.value = rows
            "Found ${rows.sumOf { it.quantity }} cards"
        }
    }

    /** Reads pasted or shared text: a collection CSV or a decklist. */
    fun readText(text: String) {
        run("Reading…") {
            val rows = withContext(Dispatchers.Default) { CsvImport.parseAny(text) }
            if (rows.isEmpty()) return@run "No cards found"
            _importRows.value = rows
            "Found ${rows.sumOf { it.quantity }} cards"
        }
    }

    /** Saves the deck behind a public Moxfield or Archidekt link as a deck list. */
    fun importDeckLink(url: String) {
        run("Getting deck…") {
            val deck = try {
                DeckLinks.fetch(url.trim())
            } catch (e: java.io.IOException) {
                return@run e.message ?: "Couldn't get that deck"
            }
            val id = repo.saveDeck(0L, deck.name, deck.text)
            com.mtgscanner.ui.components.Undo.offer("Saved deck: ${deck.name}") { repo.deleteDeck(id) }
            null
        }
    }

    /** The app holds no cards and no boxes (a new install). */
    suspend fun isEmpty(): Boolean = repo.allCards().isEmpty() && repo.observeBoxes().first().isEmpty()

    /** What an import will add, shown before anything is saved. */
    private val _importPlan = MutableStateFlow<ImportPlan?>(null)
    val importPlan: StateFlow<ImportPlan?> = _importPlan

    /** The last lookup couldn't reach Scryfall. */
    private val _importOffline = MutableStateFlow(false)
    val importOffline: StateFlow<Boolean> = _importOffline

    /** Box id → name and kind, for the preview. */
    private val _importBoxes = MutableStateFlow<Map<Long, Box>>(emptyMap())
    val importBoxes: StateFlow<Map<Long, Box>> = _importBoxes

    /**
     * Looks the cards up and works out where each goes, without adding anything.
     * [choice] is a box id, [Box.AUTO_SORT], or 0 for a new "Imported" box.
     */
    fun import(choice: Long) {
        val rows = _importRows.value ?: return
        // Keep the rows if something else is still running, so the import can be tried again.
        val started = run("Looking up ${rows.sumOf { it.quantity }} cards…") {
            val found = try {
                BackupIO.lookUp(repo, rows).also { _importOffline.value = false }
            } catch (e: java.io.IOException) {
                // Offline: show every card as not found, with Retry.
                _importOffline.value = true
                List(rows.size) { null }
            }
            showPlan(BackupIO.plan(repo, rows, found, choice))
            null
        }
        if (started) _importRows.value = null
    }

    private suspend fun showPlan(plan: ImportPlan) {
        _importBoxes.value = repo.observeBoxes().first().associateBy { it.id }
        _importPlan.value = plan
    }

    /** Looks [index] up again, loosely (typos are fine), with [name] when the user edited it. */
    fun retryImportRow(index: Int, name: String? = null) {
        val plan = _importPlan.value ?: return
        val old = plan.rows.getOrNull(index) ?: return
        val row = if (name != null && name.isNotBlank() && name.trim() != old.name) {
            // A new name: the old printing and id no longer apply.
            old.copy(name = name.trim(), scryfallId = null, collectorNumber = null)
        } else old
        run("Looking up ${row.name}…") {
            val card = try {
                BackupIO.lookUpLoosely(repo, row).also { _importOffline.value = false }
            } catch (e: java.io.IOException) {
                _importOffline.value = true
                return@run "Still offline"
            }
            val current = _importPlan.value ?: return@run null
            if (current.rows.getOrNull(index) != old) return@run null
            val rows = current.rows.toMutableList().also { it[index] = row }
            val found = current.found.toMutableList().also { it[index] = card }
            showPlan(BackupIO.plan(repo, rows, found, current.choice))
            if (card == null) "Not found: ${row.name}" else "Found ${card.name}"
        }
    }

    /** Looks up every card still not found: all at once, then loosely one by one (up to [LOOSE_RETRIES]). */
    fun retryImportAll() {
        val plan = _importPlan.value ?: return
        val missing = plan.notFound
        if (missing.isEmpty()) return
        run("Looking up ${missing.size} cards…") {
            val found = plan.found.toMutableList()
            try {
                BackupIO.lookUp(repo, missing.map { plan.rows[it] }).forEachIndexed { k, c -> found[missing[k]] = c }
                missing.filter { found[it] == null }.take(LOOSE_RETRIES).forEach { i -> found[i] = BackupIO.lookUpLoosely(repo, plan.rows[i]) }
                _importOffline.value = false
            } catch (e: java.io.IOException) {
                _importOffline.value = true
            }
            if (_importPlan.value !== plan) return@run null
            val next = BackupIO.plan(repo, plan.rows, found, plan.choice)
            showPlan(next)
            val fixed = missing.size - next.notFound.size
            when {
                _importOffline.value -> if (fixed > 0) "Found $fixed. Still offline" else "Still offline"
                fixed == 0 -> "None found"
                else -> "Found $fixed of ${missing.size}"
            }
        }
    }

    /** Leaves a card out of the import. */
    fun removeImportRow(index: Int) {
        val plan = _importPlan.value ?: return
        // Only cards not found can be left out, so every other card keeps its box.
        if (index !in plan.rows.indices || plan.found[index] != null) return
        fun <T> List<T>.without() = filterIndexed { i, _ -> i != index }
        val rows = plan.rows.without()
        if (rows.isEmpty()) { cancelImport(); return }
        _importPlan.value = plan.copy(rows = rows, found = plan.found.without(), targets = plan.targets.without())
    }

    /** Back to picking where the cards go. */
    fun changeImportBox() {
        val plan = _importPlan.value ?: return
        _importPlan.value = null
        _importRows.value = plan.rows
    }

    /** Adds what the preview showed. */
    fun confirmImport() {
        val plan = _importPlan.value ?: return
        val other = plan.otherPrinting.size
        val started = run("Importing ${plan.added} cards…") {
            val newBox: suspend () -> Long = { repo.createBox("Imported", "box") }
            // Undo takes out exactly what the import added, and the "Imported" box once it's empty.
            val r = BackupIO.applyPlan(repo, plan, newBox)
            val msg = buildString {
                append(if (r.added == 1) "Imported 1 card" else "Imported ${r.added} cards")
                if (other > 0) append(". $other in another printing")
                if (r.notFound.isNotEmpty()) append(". Skipped ${r.notFound.size} not found")
            }
            if (!r.canUndo) msg else {
                com.mtgscanner.ui.components.Undo.offer(msg) { repo.undoImport(r) }
                null
            }
        }
        if (started) _importPlan.value = null
    }

    fun cancelImport() {
        _importRows.value = null
        _importPlan.value = null
        _importOffline.value = false
    }

    fun resolvePending() = run("Looking up saved scans…") {
        val n = repo.resolvePending()
        "Added $n saved cards"
    }

    fun messageShown() { _message.value = null }
}

/** Cards still not found that one "Retry all" looks up loosely, one request each. */
private const val LOOSE_RETRIES = 25

/** Opens [uri] to replace its contents ("wt" truncates; some providers only accept "w"). */
private fun openForWrite(context: android.content.Context, uri: Uri): java.io.OutputStream? =
    runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
        ?: context.contentResolver.openOutputStream(uri)

/** Starts the app afresh, so every screen reads restored settings. */
private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
    Runtime.getRuntime().exit(0)
}

/** Backup, restore, import, and maintenance actions. The caller shows [vm]'s messages, so they still appear after leaving this tab. */
@Composable
fun DataSection(vm: SettingsViewModel = viewModel(factory = AppViewModels.Factory)) {
    val ctx = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    var quietNights by remember { mutableStateOf(Notifications.quietNights(ctx)) }
    val canUndoRestore by vm.canUndoRestore.collectAsStateWithLifecycle()
    var choosingExport by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf(CsvFormat.FULL) }
    /** "Insurance PDF" picked in Export instead of a CSV format. */
    var exportPdf by remember { mutableStateOf(false) }
    var reportMin by remember { mutableDoubleStateOf(10.0) }
    var confirmUndo by remember { mutableStateOf(false) }
    remember { DriveBackup.load(ctx.applicationContext) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.backup(ctx, uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.previewRestore(ctx, uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readCsv(ctx, uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(ctx, uri, exportFormat)
    }
    val reportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.insuranceReport(ctx, uri, reportMin)
    }
    // Picked once; Google Drive shows up in the picker. The weekly job then overwrites this file.
    val weeklyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            DriveBackup.enable(ctx.applicationContext, uri)
            vm.backupNow(ctx)
        }
    }

    PlainGroup(Modifier.padding(horizontal = 4.dp)) {
        Column {
            busy?.let {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = Palette.Rule)
            }
            Action(Icons.Outlined.FileUpload, "Import CSV", "ManaBox, Moxfield, Archidekt, Deckbox…") {
                csvLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
            }
            HorizontalDivider(color = Palette.Rule)
            Action(Icons.Outlined.FileDownload, "Export", "CSV or insurance PDF") { choosingExport = true }
            HorizontalDivider(color = Palette.Rule)
            Action(Icons.Outlined.Save, "Back up", "Save everything to a file") {
                backupLauncher.launch("grimoire-backup-${LocalDate.now()}.zip")
            }
            HorizontalDivider(color = Palette.Rule)
            val weekly = DriveBackup.uri != null
            ToggleRow("Weekly backup", if (weekly) "Last backup: ${backupDay(DriveBackup.lastBackup)}" else "To Drive or a folder", weekly) { on ->
                if (on) weeklyLauncher.launch("grimoire-backup.zip") else DriveBackup.disable(ctx.applicationContext)
            }
            if (weekly) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp)) {
                    TextButton(onClick = { vm.backupNow(ctx) }) { Text("Back up now") }
                }
            }
            HorizontalDivider(color = Palette.Rule)
            ToggleRow("Quiet at night", "No sound 10 pm to 8 am", quietNights) { on ->
                quietNights = on
                Notifications.setQuietNights(ctx.applicationContext, on)
            }
            HorizontalDivider(color = Palette.Rule)
            Action(Icons.Outlined.Restore, "Restore backup", "Replaces everything in the app") {
                restoreLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "text/*"))
            }
            if (canUndoRestore) {
                HorizontalDivider(color = Palette.Rule)
                Action(Icons.AutoMirrored.Outlined.Undo, "Undo last restore", "Back to before you restored") { confirmUndo = true }
            }
            HorizontalDivider(color = Palette.Rule)
            Action(Icons.Outlined.History, "Recent actions", "Undo any of the last 10") { com.mtgscanner.ui.components.Undo.showRecent.value = true }
            if (pending.isNotEmpty()) {
                HorizontalDivider(color = Palette.Rule)
                Action(Icons.Outlined.CloudSync, "Add ${pending.sumOf { it.quantity }} offline scans", "Scanned while offline") { vm.resolvePending() }
            }
        }
    }

    RestorePreviewDialog(vm)

    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            containerColor = Palette.Surface,
            title = { Text("Undo last restore?") },
            text = { Text("Puts back what was in the app before you restored. The app restarts.") },
            confirmButton = { TextButton(onClick = { confirmUndo = false; vm.undoRestore(ctx) }) { Text("Undo restore", color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("Cancel") } },
        )
    }

    ImportDialogs(vm)

    if (choosingExport) {
        AlertDialog(
            onDismissRequest = { choosingExport = false },
            containerColor = Palette.Surface,
            title = { Text("Export") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CsvFormat.entries.forEach { f -> Choice(f.label, !exportPdf && exportFormat == f, null) { exportFormat = f; exportPdf = false } }
                    Choice("Insurance PDF", exportPdf, null) { exportPdf = true }
                    if (exportPdf) ReportMin(reportMin) { reportMin = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    choosingExport = false
                    if (exportPdf) reportLauncher.launch("mtg-insurance-${LocalDate.now()}.pdf")
                    else exportLauncher.launch("mtg-${exportFormat.fileTag}-${LocalDate.now()}.csv")
                }) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { choosingExport = false }) { Text("Cancel") } },
        )
    }
}

/** "Restore this backup?" once a backup has been read. */
@Composable
internal fun RestorePreviewDialog(vm: SettingsViewModel) {
    val ctx = LocalContext.current
    val restorePreview by vm.restorePreview.collectAsStateWithLifecycle()
    restorePreview?.let { p ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            containerColor = Palette.Surface,
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "${p.cards} cards, ${p.boxes} boxes" + (if (p.savedAt > 0) ", saved ${backupDay(p.savedAt, withYear = true)}" else "") +
                        ".\n\nIt replaces everything in the app" + (if (p.restarts) ", which then restarts" else "") +
                        ". A copy of what's here now is kept, so you can undo.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.restore(ctx, p) }) { Text("Restore", color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Cancel") } },
        )
    }

}

/** Picking where imported cards go, then the preview of what will be added. */
@Composable
internal fun ImportDialogs(vm: SettingsViewModel) {
    val rows by vm.importRows.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    rows?.let { r -> ImportDialog(r, boxes, onCancel = vm::cancelImport, onImport = vm::import) }
    val plan by vm.importPlan.collectAsStateWithLifecycle()
    plan?.let { p ->
        val planBoxes by vm.importBoxes.collectAsStateWithLifecycle()
        val offline by vm.importOffline.collectAsStateWithLifecycle()
        ImportPreviewDialog(
            plan = p,
            boxes = planBoxes,
            offline = offline,
            busy = busy,
            onRetry = vm::retryImportRow,
            onRetryAll = vm::retryImportAll,
            onRemove = vm::removeImportRow,
            onChangeBox = vm::changeImportBox,
            onConfirm = vm::confirmImport,
            onCancel = vm::cancelImport,
        )
    }

}

/** The insurance PDF's floor: cards worth at least this much a copy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportMin(min: Double, onMin: (Double) -> Unit) {
    Column(Modifier.padding(start = 12.dp, top = 4.dp)) {
        Text("Cards worth at least", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        Spacer(Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.0, 5.0, 10.0, 25.0, 100.0).forEach { v ->
                SelectPill("$${v.toInt()}", min == v, { onMin(v) })
            }
        }
    }
}

private fun backupDay(millis: Long, withYear: Boolean = false): String =
    if (millis <= 0) "never"
    else DateTimeFormatter.ofPattern(if (withYear) "MMM d, yyyy" else "MMM d", Locale.US).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
private fun ImportDialog(rows: List<ImportRow>, boxes: List<Box>, onCancel: () -> Unit, onImport: (Long) -> Unit) {
    var choice by remember { mutableLongStateOf(if (boxes.isEmpty()) 0L else Box.AUTO_SORT) }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Palette.Surface,
        title = { Text("Import ${rows.sumOf { it.quantity }} cards") },
        text = {
            // Many boxes would push the buttons off screen without scrolling.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (boxes.isEmpty()) {
                    Text("They'll go into a new box called \"Imported\".")
                } else {
                    Text("Where should they go?")
                    Spacer(Modifier.padding(top = 4.dp))
                    Choice("Auto-sort by box rules", choice == Box.AUTO_SORT, Icons.AutoMirrored.Outlined.Sort) { choice = Box.AUTO_SORT }
                    boxes.forEach { b -> Choice(b.name, choice == b.id, null) { choice = b.id } }
                    Choice("A new box called \"Imported\"", choice == 0L, null) { choice = 0L }
                }
            }
        },
        confirmButton = {
            // 0 = new box. With auto-sort, cards that fit no box go to a new "Imported" box too.
            TextButton(onClick = { onImport(choice) }) { Text("Next") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, icon: ImageVector?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Palette.Accent))
        if (icon != null) {
            Icon(icon, null, tint = Palette.Icon, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Action(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.Icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

