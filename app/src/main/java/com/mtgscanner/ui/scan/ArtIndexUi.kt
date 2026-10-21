package com.mtgscanner.ui.scan

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.mtgscanner.scan.ArtModel
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.launch

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

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("Art match", style = MaterialTheme.typography.labelLarge, color = Palette.Accent, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        Text("Finds cards by their art when no text reads.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)

        ArtModelPicker(vm)

        download?.let { d ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val count = if (d.total > 0) " · ${d.done}/${d.total}" else ""
                val waiting = if (d.queued > 0) " · ${d.queued} more" else ""
                Text("Getting ${d.set.uppercase()}$count$waiting", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (d.queued > 0) TextButton(onClick = vm::cancelArtDownloads) { Text("Stop", color = Palette.Muted) }
            }
            LinearProgressIndicator(
                progress = { if (d.total > 0) d.done.toFloat() / d.total else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Palette.Accent,
            )
        }
        failed?.let { Text("Couldn't get ${it.uppercase()}", style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.padding(top = 6.dp)) }

        // Indexes made without the chosen model still work by hash; updating them downloads every picture again.
        val model by vm.artModel.collectAsStateWithLifecycle()
        val modelDownload by vm.artModelDownload.collectAsStateWithLifecycle()
        val stale = remember(have, model, modelDownload, download) { vm.staleArt() }
        var askUpdate by remember { mutableStateOf(false) }
        if (stale.isNotEmpty() && download == null) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SetIcon(s.set, size = 18.dp, tint = if (s.set == lock) Palette.Accent else Palette.Text)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(names[s.set] ?: s.set.uppercase(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.set.uppercase()} · ${s.cards} cards" + if (s.set == lock) " · locked" else "", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                IconButton(onClick = { vm.deleteArt(s.set) }) { Icon(Icons.Filled.Close, "Remove ${s.set.uppercase()}", tint = Palette.Muted, modifier = Modifier.size(18.dp)) }
            }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectPill("Add set", false, { vm.loadSets(); picking = true }, leading = {
                Icon(Icons.Filled.Add, null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
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
                    if (missing.isEmpty()) "Every set you own is here."
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

/**
 * Standard or Advanced art model. Picking one downloads it (with progress) and deletes the other's file; art
 * indexes made with the other model keep matching by hash until you update them.
 */
@Composable
private fun ArtModelPicker(vm: ScanViewModel) {
    val chosen by vm.artModel.collectAsStateWithLifecycle()
    val download by vm.artModelDownload.collectAsStateWithLifecycle()
    val failed by vm.artModelFailed.collectAsStateWithLifecycle()
    Text("Art model", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
    ArtModel.entries.forEach { m ->
        val note = when (m) {
            ArtModel.STANDARD -> "Quick and small"
            ArtModel.ADVANCED -> "Better with look-alike art"
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.selectArtModel(m) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = chosen == m, onClick = { vm.selectArtModel(m) }, colors = RadioButtonDefaults.colors(selectedColor = Palette.Accent))
            Column(Modifier.weight(1f)) {
                Text("${m.label} · ${m.sizeLabel}", style = MaterialTheme.typography.titleSmall)
                Text(note, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
    }
    val d = download
    if (d != null) {
        val mb = { b: Long -> "%.1f".format(java.util.Locale.US, b / 1_000_000.0) }
        Text(
            "Getting ${d.model.label} · ${mb(d.done)}/${mb(d.total)} MB · best on Wi-Fi",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        LinearProgressIndicator(
            progress = { if (d.total > 0) (d.done.toFloat() / d.total).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Palette.Accent,
        )
    } else if (failed) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Couldn't get the model", style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.selectArtModel(chosen) }) { Text("Retry") }
        }
    } else if (!vm.artModelHere(chosen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Not downloaded yet. Best on Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.selectArtModel(chosen) }) { Text("Get it") }
        }
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
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    if (typed != null) item(key = "typed") { PickRow(typed, typed.uppercase(), "Set code") { pick(typed) } }
                    items(shown, key = { it.code }) { s ->
                        PickRow(s.code, s.name, s.code.uppercase() + " · ${s.cardCount} cards") { pick(s.code) }
                    }
                    if (sets.isEmpty() && typed == null) item(key = "empty") {
                        Text("Type a set code.", color = Palette.Muted, modifier = Modifier.padding(vertical = 16.dp))
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetIcon(code, size = 20.dp, tint = Palette.Text)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}
