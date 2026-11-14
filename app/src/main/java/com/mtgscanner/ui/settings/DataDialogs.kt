package com.mtgscanner.ui.settings

import com.mtgscanner.util.plural
import com.mtgscanner.ui.theme.Space
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.minimumInteractiveComponentSize
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.components.PlainGroup

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
                    "${plural(p.cards, "card")}, ${plural(p.boxes, "box", "boxes")}" + (if (p.savedAt > 0) ", saved ${backupDay(p.savedAt, withYear = true)}" else "") +
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
                    Spacer(Modifier.padding(top = Space.s1))
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
internal fun Choice(label: String, selected: Boolean, icon: ImageVector?, onClick: () -> Unit) {
    // One target for TalkBack: the whole row is the radio button, read with its label and "selected".
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Palette.Text),
            // Keeps the 48dp row height the button had when it took taps itself.
            modifier = Modifier.minimumInteractiveComponentSize(),
        )
        if (icon != null) {
            Icon(icon, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s2))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
