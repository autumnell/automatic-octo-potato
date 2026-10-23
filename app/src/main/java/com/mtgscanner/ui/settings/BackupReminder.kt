package com.mtgscanner.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    val busy by DataJobs.busy.collectAsStateWithLifecycle()
    // Read as state, so a finished backup or a snooze hides the card at once.
    val last = DriveBackup.lastBackup
    val snoozed = DriveBackupReminder.snoozedUntil
    val now = remember(last, snoozed) { System.currentTimeMillis() }

    fun backUp() {
        val started = DataJobs.launch("Backing up…", (app as MtgApp).repository.appScope) {
            val ok = runCatching { DriveBackup.backupNow(app) }.getOrDefault(false)
            Undo.offer(if (ok) "Backup saved" else "Backup failed")
        }
        if (!started) Undo.offer(DataJobs.waitMessage())
    }
    // No weekly backup yet: pick where it goes (Drive shows in the picker), then save the first one.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            DriveBackup.enable(app, uri)
            backUp()
        }
    }
    // Turning on weekly backup: ask for notifications first (Android 13+), so a failed one can say so.
    val startWeekly = rememberAskNotifyThen { pick.launch("grimoire-backup.zip") }
    if (!DriveBackupReminder.due(now, last, snoozed, hasCards)) return

    val days = DriveBackupReminder.daysSince(now, last)
    // A plain strip under the value card, not another card: icon, one line, two text buttons.
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudUpload, null, tint = Palette.Icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            if (days == null) "No backup yet" else "Last backup ${days}d ago",
            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { DriveBackupReminder.snooze(app) }) { Text("Later", color = Palette.Muted) }
        TextButton(
            onClick = { if (DriveBackup.uri != null) backUp() else startWeekly() },
            enabled = busy == null,
        ) { Text("Back up") }
    }
    Spacer(Modifier.height(4.dp))
}

/**
 * Returns an action that runs [then], first asking for notification permission on Android 13+ when it
 * isn't granted yet (backup failures are told by notification). [then] runs whatever the answer.
 */
@Composable
internal fun rememberAskNotifyThen(then: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(then)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { latest() }
    return {
        val needs = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (!needs || runCatching { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }.isFailure) latest()
    }
}
