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
import com.mtgscanner.data.deleteDeckAndCards
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
import com.mtgscanner.data.db.placeName
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
import kotlinx.coroutines.CoroutineScope
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

    /**
     * Runs [block] in [scope] (the app's, so it finishes after leaving the screen), one job at a time.
     * Returns false without running it while another job is still going; see [waitMessage].
     */
    fun launch(label: String, scope: CoroutineScope, block: suspend () -> Unit): Boolean {
        if (!busy.compareAndSet(null, label)) return false
        scope.launch {
            try { block() } finally { busy.value = null }
        }
        return true
    }

    /** Why a job didn't start: "Wait for "Restoring" to finish". */
    fun waitMessage(): String = "Wait for \"${busy.value?.removeSuffix("…")}\" to finish"
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
        val started = DataJobs.launch(label, repo.appScope) {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BadBackupException) {
                e.message
            } catch (e: Throwable) {
                "Something went wrong: ${e.message}"
            }
            result?.let { com.mtgscanner.ui.components.Undo.offer(it) }
        }
        if (!started) _message.value = DataJobs.waitMessage()
        return started
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
            val places = repo.dao.allPlaces().associate { it.id to it.placeName() }
            val text = java.io.StringWriter().also { CsvExporter.write(cards, it, format) { id -> places[id] } }.toString()
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
    /** Returns false when another data job is running and the preview didn't start. */
    fun previewRestore(ctx: android.content.Context, uri: Uri): Boolean {
        val app = ctx.applicationContext
        return run("Reading backup…") {
            val input = withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri) } ?: return@run "Couldn't open that file"
            _restore.value = RestorePreview(BackupIO.read(input, app))
            null
        }
    }

    /** Like [previewRestore], for a backup file the app saved itself. */
    fun previewRestoreFile(ctx: android.content.Context, file: java.io.File) {
        val app = ctx.applicationContext
        run("Reading backup…") {
            if (!file.isFile) return@run "That backup is gone"
            _restore.value = RestorePreview(BackupIO.read(file.inputStream(), app))
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
                RestartNotice.restart(app)
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
            RestartNotice.restart(app)
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
            com.mtgscanner.ui.components.Undo.offer("Saved deck: ${deck.name}") { repo.deleteDeckAndCards(id, false) }
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
        val r = repo.resolvePendingWithUndo()
        val message = if (r.added == 1) "Added 1 saved card" else "Added ${r.added} saved cards"
        if (r.rows.isEmpty()) return@run message
        com.mtgscanner.ui.components.Undo.offer(message) { repo.undoPending(r) }
        null
    }

    fun messageShown() { _message.value = null }
}

/** Cards still not found that one "Retry all" looks up loosely, one request each. */
private const val LOOSE_RETRIES = 25

/** Opens [uri] to replace its contents ("wt" truncates; some providers only accept "w"). */
private fun openForWrite(context: android.content.Context, uri: Uri): java.io.OutputStream? =
    runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
        ?: context.contentResolver.openOutputStream(uri)
