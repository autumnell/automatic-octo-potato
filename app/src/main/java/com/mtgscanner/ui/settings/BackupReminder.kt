package com.mtgscanner.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.work.DriveBackup
import com.mtgscanner.work.DriveBackupReminder
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow

/**
 * A small card asking for a backup when the weekly one is over two weeks old, or there's never been one.
 * [hasCards]: the collection isn't empty (no point backing up nothing). "Later" hides it for a week.
 * Draws nothing (and takes no space) when no reminder is due.
 */
@Composable
fun BackupReminder(hasCards: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    remember { DriveBackup.load(app); DriveBackupReminder.load(app) }
    val busy by DataJobs.busy.collectAsState()
    // Read as state, so a finished backup or a snooze hides the card at once.
    val last = DriveBackup.lastBackup
    val snoozed = DriveBackupReminder.snoozedUntil
    val now = remember(last, snoozed) { System.currentTimeMillis() }

    fun backUp() {
        if (!DataJobs.busy.compareAndSet(null, "Backing up…")) {
            Undo.offer("Wait for \"${DataJobs.busy.value?.removeSuffix("…")}\" to finish")
            return
        }
        (app as MtgApp).repository.appScope.launch {
            val ok = try { DriveBackup.backupNow(app) } finally { DataJobs.busy.value = null }
            Undo.offer(if (ok) "Backup saved" else "Backup failed")
        }
    }
    // No weekly backup yet: pick where it goes (Drive shows in the picker), then save the first one.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            DriveBackup.enable(app, uri)
            backUp()
        }
    }
    if (!DriveBackupReminder.due(now, last, snoozed, hasCards)) return

    val days = DriveBackupReminder.daysSince(now, last)
    // A plain strip under the value card, not another card: icon, one line, two text buttons.
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudUpload, null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            if (days == null) "No backup yet" else "Last backup ${days}d ago",
            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { DriveBackupReminder.snooze(app) }) { Text("Later", color = Palette.Muted) }
        TextButton(
            onClick = { if (DriveBackup.uri != null) backUp() else pick.launch("grimoire-backup.zip") },
            enabled = busy == null,
        ) { Text("Back up") }
    }
    Spacer(Modifier.height(4.dp))
}
