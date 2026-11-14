package com.mtgscanner.ui.settings

import com.mtgscanner.ui.theme.Space
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.room.withTransaction
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
    /** The automatic backups, while their list is open. */
    var autos by remember { mutableStateOf<List<BackupIO.AutoBackup>?>(null) }
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
            if (DriveBackup.enable(ctx.applicationContext, uri)) vm.backupNow(ctx)
            else com.mtgscanner.ui.components.Undo.offer("That place can't keep weekly backups: pick a folder on Drive or the phone")
        }
    }
    // A folder keeps a dated copy each week. Google Drive can't be picked as a folder, so one file is offered too.
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (DriveBackup.enable(ctx.applicationContext, uri)) vm.backupNow(ctx)
            else com.mtgscanner.ui.components.Undo.offer("That folder can't keep weekly backups: pick another")
        }
    }
    var choosingPlace by remember { mutableStateOf(false) }
    /** The dated backups in the picked folder, while their list is open. */
    var inFolder by remember { mutableStateOf<List<DriveBackup.FolderBackup>?>(null) }
    val scope = rememberCoroutineScope()
    // Ask for notifications first (Android 13+), or a failed weekly backup could never say so.
    val startWeekly = rememberAskNotifyThen { choosingPlace = true }

    val printsState by com.mtgscanner.scan.CardPrints.state.collectAsStateWithLifecycle()

    // Work in progress and scans waiting to be added come first: they need attention.
    if (busy != null || pending.isNotEmpty()) {
        Spacer(Modifier.height(Space.s2))
        Group {
            busy?.let {
                Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s3), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Text)
                    Spacer(Modifier.width(Space.s3))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (pending.isNotEmpty()) {
                if (busy != null) HorizontalDivider(color = Palette.Rule)
                Action(Icons.Outlined.CloudSync, "Add ${pending.sumOf { it.quantity }} offline scans", "Scanned while offline") { vm.resolvePending() }
            }
        }
    }

    SectionLabel("Backups")
    Group {
        Action(Icons.Outlined.Save, "Back up", "Save everything to a file") {
            backupLauncher.launch("grimoire-backup-${LocalDate.now()}.zip")
        }
        HorizontalDivider(color = Palette.Rule)
        val weekly = DriveBackup.uri != null
        ToggleRow("Weekly backup", if (weekly) "Last backup: ${backupDay(DriveBackup.lastBackup)}" else "To Drive or a folder", weekly) { on ->
            if (on) startWeekly() else DriveBackup.disable(ctx.applicationContext)
        }
        if (weekly) RowButton("Back up now") { vm.backupNow(ctx) }
        if (weekly && DriveBackup.toFolder) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                Text("Keep the last", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                listOf(4, 8, 12).forEach { n -> SelectPill("$n", DriveBackup.keep == n, { DriveBackup.setKeep(ctx.applicationContext, n) }) }
            }
            HorizontalDivider(color = Palette.Rule)
            Action(Icons.Outlined.FolderOpen, "Backups in the folder", "Open any of them to restore") {
                scope.launch { inFolder = DriveBackup.folderBackups(ctx.applicationContext) }
            }
        }
        HorizontalDivider(color = Palette.Rule)
        Action(Icons.Outlined.Restore, "Restore backup", "Replaces everything in the app") {
            restoreLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "text/*"))
        }
        HorizontalDivider(color = Palette.Rule)
        Action(Icons.Outlined.History, "Automatic backups", "Saved before big changes") { autos = BackupIO.autoBackups(ctx) }
        if (canUndoRestore) {
            HorizontalDivider(color = Palette.Rule)
            Action(Icons.AutoMirrored.Outlined.Undo, "Undo last restore", "Back to before you restored") { confirmUndo = true }
        }
    }

    SectionLabel("Import and export")
    Group {
        Action(Icons.Outlined.FileUpload, "Import CSV", "ManaBox, Moxfield, Archidekt, Deckbox…") {
            csvLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
        }
        HorizontalDivider(color = Palette.Rule)
        Action(Icons.Outlined.FileDownload, "Export", "CSV or insurance PDF") { choosingExport = true }
    }

    if (printsState == com.mtgscanner.scan.CardPrints.State.Ready) {
        SectionLabel("Test photos")
        Group { TestPhotosRows() }
    }

    SectionLabel("Other")
    Group {
        ToggleRow("Quiet at night", "No sound 10 pm to 8 am", quietNights) { on ->
            quietNights = on
            Notifications.setQuietNights(ctx.applicationContext, on)
        }
        HorizontalDivider(color = Palette.Rule)
        Action(Icons.Outlined.History, "Recent actions", "Undo any of the last 10") { com.mtgscanner.ui.components.Undo.showRecent.value = true }
    }

    // The restore and import dialogs are drawn by DataHost, over every screen, so they show wherever a job ends.

    if (choosingPlace) AlertDialog(
        onDismissRequest = { choosingPlace = false },
        containerColor = Palette.Surface,
        title = { Text("Where should weekly backups go?") },
        text = {
            Column {
                Action(Icons.Outlined.FolderOpen, "A folder", "Keeps a dated copy each week") {
                    choosingPlace = false; folderLauncher.launch(null)
                }
                Action(Icons.Outlined.Save, "One file", "For Google Drive, which keeps its own past versions") {
                    choosingPlace = false; weeklyLauncher.launch("grimoire-backup.zip")
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosingPlace = false }) { Text("Cancel") } },
    )

    inFolder?.let { list ->
        AlertDialog(
            onDismissRequest = { inFolder = null },
            containerColor = Palette.Surface,
            title = { Text("Backups in the folder") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text("None yet", color = Palette.Muted)
                    list.forEach { b ->
                        Row(
                            Modifier.fillMaxWidth().clickable { inFolder = null; vm.previewRestore(ctx, b.uri) }.padding(vertical = Space.s3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                val day = b.name.removePrefix("grimoire-backup-").removeSuffix(".zip")
                                Text(runCatching { backupDay(LocalDate.parse(day).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), withYear = true) }.getOrDefault(b.name), style = MaterialTheme.typography.titleMedium)
                                if (b.size > 0) Text("${(b.size + 1023) / 1024} KB", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                            }
                            Text("Restore", style = MaterialTheme.typography.labelLarge, color = Palette.AccentText)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { inFolder = null }) { Text("Close") } },
        )
    }

    autos?.let { list ->
        AlertDialog(
            onDismissRequest = { autos = null },
            containerColor = Palette.Surface,
            title = { Text("Automatic backups") },
            text = {
                Column {
                    Text(
                        "Saved by the app before deleting a box, restoring, moving 50 or more cards, or Sort out. The newest 3 are kept.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                    )
                    Spacer(Modifier.size(Space.s2))
                    if (list.isEmpty()) Text("None yet", color = Palette.Muted)
                    list.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { autos = null; vm.previewRestoreFile(ctx, a.file) }.padding(vertical = Space.s3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(a.reason.replaceFirstChar { it.uppercase() }.ifBlank { "Backup" }, style = MaterialTheme.typography.titleSmall)
                                Text(autoTime(a.at), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                            }
                            Text("Restore", style = MaterialTheme.typography.labelLarge, color = Palette.AccentText)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { autos = null }) { Text("Close") } },
        )
    }

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

/** The insurance PDF's floor: cards worth at least this much a copy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportMin(min: Double, onMin: (Double) -> Unit) {
    Column(Modifier.padding(start = Space.s3, top = Space.s1)) {
        Text("Cards worth at least", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        Spacer(Modifier.padding(top = Space.s2))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            listOf(1.0, 5.0, 10.0, 25.0, 100.0).forEach { v ->
                SelectPill("$${v.toInt()}", min == v, { onMin(v) })
            }
        }
    }
}

private fun autoTime(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

internal fun backupDay(millis: Long, withYear: Boolean = false): String =
    if (millis <= 0) "never"
    else DateTimeFormatter.ofPattern(if (withYear) "MMM d, yyyy" else "MMM d", Locale.US).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** One panel of rows, with the screen's side margin. */
@Composable
private fun Group(content: @Composable () -> Unit) {
    PlainGroup(Modifier.padding(horizontal = Space.s1)) { Column { content() } }
}

/** A text button under the row above, lined up with its title. */
@Composable
private fun RowButton(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Space.s1, bottom = Space.s1)) { TextButton(onClick = onClick) { Text(label) } }
}

@Composable
private fun Action(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.s4, vertical = Space.s3), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.Icon)
        Spacer(Modifier.width(Space.s4))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            // Up to three lines, like the toggle rows, so big text isn't cut off.
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Keeping scans as test photos, checking accuracy on them, and tuning to them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestPhotosRows() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var keep by remember { mutableStateOf(com.mtgscanner.scan.TestPhotos.isOn(ctx)) }
    var kept by remember { mutableIntStateOf(com.mtgscanner.scan.TestPhotos.count(ctx)) }
    ToggleRow("Keep test photos", if (keep) "$kept kept from your scans" else "Saves each scanned card's picture, to measure accuracy", keep, lines = 2) { v ->
        keep = v
        com.mtgscanner.scan.TestPhotos.setOn(ctx, v)
    }
    if (!keep && kept == 0) return
    var result by remember { mutableStateOf<String?>(null) }
    // Names of printings the picture gallery lacks, from the card list on the phone.
    val localCards = (app as com.mtgscanner.MtgApp).repository.localCards
    val nameOf: (String) -> String? = { id -> localCards.byId(id)?.name }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { app.contentResolver.openOutputStream(uri)?.use { com.mtgscanner.scan.TestPhotos.zipTo(app, it) } }
        }
    }
    HorizontalDivider(color = Palette.Rule)
    FlowRow(Modifier.fillMaxWidth().padding(start = Space.s1, top = Space.s1, bottom = Space.s1)) {
        TextButton(onClick = {
            kept = com.mtgscanner.scan.TestPhotos.count(ctx)
            result = "Checking…"
            scope.launch {
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.mtgscanner.scan.TestPhotos.check(app, nameOf) }
                result = when {
                    r == null || r.total == 0 -> "No photos to check yet"
                    else -> "By picture alone: ${r.right} of ${r.total} right (${r.right * 100 / r.total}%)" +
                        (if (r.wrong > 0) ", ${r.wrong} wrong" else "") + (if (r.unsure > 0) ", ${r.unsure} unsure" else "")
                }
            }
        }) { Text("Check accuracy") }
        TextButton(onClick = {
            result = "Tuning…"
            scope.launch {
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.mtgscanner.scan.TestPhotos.tune(app, nameOf) }
                result = when {
                    r == null -> "Tuning needs ${com.mtgscanner.scan.ThresholdTuner.MIN_SAMPLES} kept photos or more"
                    r.thresholds.isDefault -> "The usual settings already suit your scans: ${r.rightAfter} of ${r.total} right, ${r.wrongAfter} wrong"
                    else -> "Tuned to your scans: ${r.rightAfter} of ${r.total} right, ${r.wrongAfter} wrong (was ${r.rightBefore} right, ${r.wrongBefore} wrong)"
                }
            }
        }) { Text("Tune") }
        TextButton(onClick = { saveLauncher.launch("grimoire-test-photos.zip") }) { Text("Save as zip") }
        TextButton(onClick = { com.mtgscanner.scan.TestPhotos.clear(ctx); kept = 0; result = null }) { Text("Clear") }
    }
    result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(start = Space.s4, bottom = Space.s2)) }
}
