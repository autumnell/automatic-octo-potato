package com.mtgscanner.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.DeckLinks
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.CsvImport
import com.mtgscanner.util.Incoming
import com.mtgscanner.util.IncomingShare
import com.mtgscanner.work.DriveBackup

/**
 * Drawn over every screen: imports shared to the app or opened with it (CSV, decklist, Moxfield or
 * Archidekt link), and on a new phone the offer to restore the weekly backup. Uses its own
 * [SettingsViewModel], so it works whichever screen is showing.
 */
@Composable
fun DataHost(vm: SettingsViewModel = viewModel(factory = AppViewModels.Factory)) {
    val ctx = LocalContext.current
    val incoming by IncomingShare.pending.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        val i = incoming ?: return@LaunchedEffect
        IncomingShare.pending.value = null
        when (i) {
            is Incoming.File -> vm.readCsv(ctx, i.uri)
            is Incoming.Text -> {
                // A shared deck page is a link, often with a line of text; a pasted list is cards.
                val link = DeckLinks.find(i.text)
                if (link != null && CsvImport.parseAny(i.text).isEmpty()) vm.importDeckLink(link) else vm.readText(i.text)
            }
        }
    }
    RestoreOffer(vm)
    ImportDialogs(vm)
    RestorePreviewDialog(vm)
}

/** "Restore your backup?" on a new install whose old phone had a weekly backup set up. */
@Composable
private fun RestoreOffer(vm: SettingsViewModel) {
    val app = LocalContext.current.applicationContext
    remember { DriveBackup.load(app) }
    if (!DriveBackup.offerRestore) return
    val empty by produceState<Boolean?>(null) { value = runCatching { vm.isEmpty() }.getOrDefault(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            DriveBackup.dismissRestoreOffer(app)
            vm.previewRestore(app, uri)
        }
    }
    when (empty) {
        null -> return
        false -> { LaunchedEffect(Unit) { DriveBackup.dismissRestoreOffer(app) }; return }
        true -> Unit
    }
    AlertDialog(
        onDismissRequest = { DriveBackup.dismissRestoreOffer(app) },
        containerColor = Palette.Surface,
        title = { Text("Restore your backup?") },
        text = { Text("Pick your Grimoire backup on Drive.") },
        confirmButton = {
            TextButton(onClick = { pick.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) }) { Text("Pick file") }
        },
        dismissButton = { TextButton(onClick = { DriveBackup.dismissRestoreOffer(app) }) { Text("Not now") } },
    )
}
