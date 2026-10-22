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
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.sync.HouseSync
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.launch
import com.mtgscanner.ui.theme.Corners

/** Settings › Data: "Shared collection" (beta), two phones keeping one collection through a shared file. */
@Composable
internal fun SharedCollectionRow() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    remember { HouseSync.load(app) }
    var picking by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    fun runSync(join: HouseSync.Join = HouseSync.Join.COMBINE) {
        note = null
        scope.launch {
            note = when (val r = HouseSync.sync(app, join)) {
                is HouseSync.Outcome.Done -> if (r.changes == 0) "Up to date" else "${r.changes} changes"
                is HouseSync.Outcome.Failed -> r.why
            }
        }
    }

    fun linked(uri: Uri, isNew: Boolean) {
        HouseSync.link(app, uri)
        if (isNew) { runSync(); return }
        scope.launch {
            val shared = HouseSync.cardsIn(app, uri)
            val here = runCatching { (app as MtgApp).repository.allCards().size }.getOrDefault(0)
            if (shared > 0 && here > 0) joining = shared to here else runSync()
        }
    }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        if (uri != null) linked(uri, isNew = true)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) linked(uri, isNew = false)
    }

    val on = HouseSync.uri != null
    Column(
        Modifier.fillMaxWidth().then(if (on) Modifier else Modifier.clickable { picking = true })
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Devices, null, tint = Palette.Accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Shared collection", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(Corners.Small), color = Palette.SurfaceHigh) {
                        Text("Beta", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                val body = when {
                    !on -> "Two phones, one collection"
                    note != null -> note!!
                    HouseSync.lastSync == 0L -> "Not synced yet"
                    else -> "Synced ${ago(HouseSync.lastSync)}" + if (HouseSync.otherSync > 0) " · other phone ${ago(HouseSync.otherSync)}" else ""
                }
                Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
            }
            if (HouseSync.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Accent)
        }
        if (on) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp)) {
                TextButton(enabled = !HouseSync.syncing, onClick = { runSync() }) { Text("Sync now") }
                TextButton(enabled = !HouseSync.syncing, onClick = { note = null; HouseSync.unlink(app) }) { Text("Stop", color = Palette.Muted) }
            }
        }
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            containerColor = Palette.Surface,
            title = { Text("Shared collection") },
            text = { Text("Pick one file both phones can open, like in a shared Drive folder.") },
            confirmButton = {
                TextButton(onClick = { picking = false; createLauncher.launch("grimoire-shared.gz") }) { Text("New file") }
            },
            dismissButton = {
                TextButton(onClick = { picking = false; openLauncher.launch(arrayOf("*/*")) }) { Text("Open file") }
            },
        )
    }

    joining?.let { (shared, here) ->
        AlertDialog(
            onDismissRequest = { joining = null; HouseSync.unlink(app) },
            containerColor = Palette.Surface,
            title = { Text("Both have cards") },
            text = {
                Column {
                    Text("Shared file: $shared · This phone: $here")
                    Spacer(Modifier.size(8.dp))
                    Text("Use shared replaces the cards on this phone.", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { joining = null; runSync(HouseSync.Join.USE_SHARED) }) { Text("Use shared", color = Palette.Danger) }
                    TextButton(onClick = { joining = null; runSync(HouseSync.Join.COMBINE) }) { Text("Combine") }
                }
            },
            dismissButton = { TextButton(onClick = { joining = null; HouseSync.unlink(app) }) { Text("Cancel") } },
        )
    }
}

/** "just now", "5 min ago", "3 h ago", "2 d ago". */
private fun ago(at: Long): String {
    val m = (System.currentTimeMillis() - at) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> "${m / (24 * 60)} d ago"
    }
}
