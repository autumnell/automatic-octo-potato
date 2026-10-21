package com.mtgscanner.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            DriveBackup.enable(app, uri)
            backUp()
        }
    }
    if (!DriveBackupReminder.due(now, last, snoozed, hasCards)) return

    val days = DriveBackupReminder.daysSince(now, last)
    Surface(modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudUpload, null, tint = Palette.Accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (days == null) "No backup yet" else "Last backup $days days ago", style = MaterialTheme.typography.titleSmall)
                    Text("Keep your cards safe.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { DriveBackupReminder.snooze(app) }) { Text("Later", color = Palette.Muted) }
                TextButton(
                    onClick = { if (DriveBackup.uri != null) backUp() else pick.launch("mtg-scanner-backup.json") },
                    enabled = busy == null,
                ) { Text("Back up now") }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}
