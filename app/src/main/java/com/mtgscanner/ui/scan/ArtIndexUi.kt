package com.mtgscanner.ui.scan

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.data.SortPrefs
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.launch
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.components.ruleBelow

/**
 * Art match settings in the scanner's settings sheet: which sets have an art index, the download under way,
 * and buttons to get more (one set, or every set in the collection).
 */
@Composable
fun ArtIndexSection(vm: ScanViewModel) {
    val have by vm.artSets.collectAsStateWithLifecycle()
    val download by vm.artDownload.collectAsStateWithLifecycle()
    val failed by vm.artFailed.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var owned by remember { mutableStateOf<List<String>?>(null) }
    val scope = rememberCoroutineScope()
    val names = remember(sets) { sets.associate { it.code to it.name } }

    Column(Modifier.fillMaxWidth().padding(bottom = Space.s2)) {
        Text("Art match", style = MaterialTheme.typography.labelLarge, color = Palette.Text, modifier = Modifier.padding(top = Space.s4, bottom = Space.s1))
        Text("Finds cards by their art when no text reads", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)

        download?.let { d ->
            Spacer(Modifier.height(Space.s3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val count = if (d.total > 0) " · ${d.done}/${d.total}" else ""
                val waiting = if (d.queued > 0) " · ${d.queued} more" else ""
                Text("Getting ${d.set.uppercase()}$count$waiting", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (d.queued > 0) TextButton(onClick = vm::cancelArtDownloads) { Text("Stop", color = Palette.Muted) }
            }
            LinearProgressIndicator(
                progress = { if (d.total > 0) d.done.toFloat() / d.total else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Corners.Small)),
                color = Palette.Accent,
            )
        }
        failed?.let { Text("Couldn't get ${it.uppercase()}", style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.padding(top = Space.s2)) }

        // Indexes made without the chosen model still work by hash; updating them downloads every picture again.
        val model by vm.artModel.collectAsStateWithLifecycle()
        val modelDownload by vm.artModelDownload.collectAsStateWithLifecycle()
        val stale = remember(have, model, modelDownload, download) { vm.staleArt() }
        var askUpdate by remember { mutableStateOf(false) }
        if (stale.isNotEmpty() && download == null) {
            Row(Modifier.fillMaxWidth().padding(top = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (stale.size == 1) "1 index on the old model" else "${stale.size} indexes on the old model",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { askUpdate = true }) { Text("Update") }
            }
        }
        if (askUpdate) AlertDialog(
            onDismissRequest = { askUpdate = false },
            containerColor = Palette.Surface,
            title = { Text("Update ${stale.size} art indexes?") },
            text = { Text("Downloads their pictures again. Best on Wi-Fi.", color = Palette.Muted) },
            confirmButton = { TextButton(onClick = { vm.rebuildArt(stale); askUpdate = false }) { Text("Update") } },
            dismissButton = { TextButton(onClick = { askUpdate = false }) { Text("Later") } },
        )

        val lock = SortPrefs.setLock
        have.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(top = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                SetIcon(s.set, size = 18.dp, tint = if (s.set == lock) Palette.AccentText else Palette.Text)
                Spacer(Modifier.width(Space.s3))
                Column(Modifier.weight(1f)) {
                    Text(names[s.set] ?: s.set.uppercase(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.set.uppercase()} · ${s.cards} cards" + if (s.set == lock) " · locked" else "", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                IconButton(onClick = { vm.deleteArt(s.set) }) { Icon(Icons.Outlined.Close, "Remove ${s.set.uppercase()}", tint = Palette.Icon, modifier = Modifier.size(18.dp)) }
            }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            SelectPill("Add set", false, { vm.loadSets(); picking = true }, leading = {
                Icon(Icons.Outlined.Add, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
            })
            SelectPill("Sets I own", false, {
                vm.loadSets()
                scope.launch { owned = runCatching { vm.ownedSets() }.getOrDefault(emptyList()) }
            })
            if (lock != null && have.none { it.set == lock } && download?.set != lock) {
                SelectPill("Get ${lock.uppercase()}", false, { vm.downloadArt(listOf(lock)) })
            }
        }
    }

    if (picking) ArtSetPicker(vm, onDismiss = { picking = false })

    owned?.let { all ->
        val missing = all.filter { code -> have.none { it.set == code } }
        val cards = missing.sumOf { code -> sets.firstOrNull { it.code == code }?.cardCount ?: 0 }
        AlertDialog(
            onDismissRequest = { owned = null },
            containerColor = Palette.Surface,
            title = { Text(if (missing.isEmpty()) "All set" else "Get ${missing.size} sets?") },
            text = {
                Text(
                    if (missing.isEmpty()) "Every set you own is here"
                    else (if (cards > 0) "About $cards pictures. " else "") + "Best on Wi-Fi.",
                    color = Palette.Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.downloadArt(missing); owned = null }, enabled = missing.isNotEmpty()) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { owned = null }) { Text("Cancel") } },
        )
    }
}

/** Pick one set to download an art index for. A typed code works when the list can't load. */
@Composable
private fun ArtSetPicker(vm: ScanViewModel, onDismiss: () -> Unit) {
    val sets by vm.sets.collectAsStateWithLifecycle()
    val have by vm.artSets.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = (if (q.isEmpty()) sets else sets.filter { it.code == q || it.name.lowercase().contains(q) })
        .filter { s -> have.none { it.set == s.code } }
    val typed = q.takeIf { it.length in 2..6 && it.all { c -> c.isLetterOrDigit() } && shown.none { s -> s.code == it } }
    fun pick(code: String) { vm.downloadArt(listOf(code)); onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Add set") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Name or code") })
                Spacer(Modifier.height(Space.s2))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    if (typed != null) item(key = "typed") { PickRow(typed, typed.uppercase(), "Set code") { pick(typed) } }
                    items(shown, key = { it.code }) { s ->
                        PickRow(s.code, s.name, s.code.uppercase() + " · ${s.cardCount} cards") { pick(s.code) }
                    }
                    if (sets.isEmpty() && typed == null) item(key = "empty") {
                        Text("Type a set code", color = Palette.Muted, modifier = Modifier.padding(vertical = Space.s4))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PickRow(code: String, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).ruleBelow().padding(horizontal = Space.s1, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetIcon(code, size = 20.dp, tint = Palette.Text)
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}
