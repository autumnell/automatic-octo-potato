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
import com.mtgscanner.data.label
import com.mtgscanner.data.ART_AUTO_SETS
import com.mtgscanner.data.artForBiggestSets
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
    // Ask for notifications first (Android 13+), or a failed weekly backup could never say so.
    val startWeekly = rememberAskNotifyThen { weeklyLauncher.launch("grimoire-backup.zip") }

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
                if (on) startWeekly() else DriveBackup.disable(ctx.applicationContext)
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
            CardListRow()
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

internal fun backupDay(millis: Long, withYear: Boolean = false): String =
    if (millis <= 0) "never"
    else DateTimeFormatter.ofPattern(if (withYear) "MMM d, yyyy" else "MMM d", Locale.US).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

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

/** Every card kept on the phone for the scanner: on or off, how far along, and a button to fetch it now. */
@Composable
private fun CardListRow() {
    val repo = (LocalContext.current.applicationContext as com.mtgscanner.MtgApp).repository
    val local = repo.localCards
    val st by local.status.collectAsStateWithLifecycle()
    var on by remember { mutableStateOf(local.enabled) }
    val body = st.label(on) + if (on && st.cards > 0 && st.updated.isNotEmpty()) "\nChecked ${st.updated}" else ""
    ToggleRow("Card list on phone", body, on) { v ->
        on = v
        local.enabled = v
        if (v) com.mtgscanner.work.CardListWorker.start(repo.appContext) else repo.appScope.launch { local.clear() }
    }
    if (on && st.progress == null) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp)) {
            TextButton(onClick = { com.mtgscanner.work.CardListWorker.start(repo.appContext, force = true) }) {
                Text(if (st.cards > 0) "Check for new cards" else "Download now")
            }
        }
    }
    if (on) {
        HorizontalDivider(color = Palette.Rule)
        var art by remember { mutableStateOf(local.artAuto) }
        ToggleRow("Match by art", "Your $ART_AUTO_SETS biggest sets, so cards with hard-to-read names are found offline. On Wi-Fi.", art) { v ->
            art = v
            local.artAuto = v
            if (v) {
                local.artCheckedDay = ""
                repo.appScope.launch { runCatching { repo.artForBiggestSets() } }
            }
        }
    }
}
