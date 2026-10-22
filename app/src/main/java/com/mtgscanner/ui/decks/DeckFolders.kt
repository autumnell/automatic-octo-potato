package com.mtgscanner.ui.decks

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DeckFolder(val id: Long, val name: String)

/** Cached tile data for a deck, so the grid doesn't need Scryfall every time. */
@Serializable
data class DeckMeta(
    /** Changes when the deck or its cover changes. */
    val stamp: String,
    /** Color identity letters in WUBRG order. */
    val identity: String,
    /** art_crop image of the cover card. */
    val art: String?,
    /** art_crop image of a second commander (partners), shown beside [art]. */
    val art2: String? = null,
)

/** Folders, which deck is in which folder, cover cards and tile cache. */
@Serializable
data class DeckShelf(
    val folders: List<DeckFolder> = emptyList(),
    /** Deck id to folder id. */
    val folderOf: Map<Long, Long> = emptyMap(),
    /** Deck id to cover card name. */
    val cover: Map<Long, String> = emptyMap(),
    val meta: Map<Long, DeckMeta> = emptyMap(),
)

/** Deck folders and covers, kept in SharedPreferences. */
object DeckShelfStore {
    private const val KEY = "shelf"
    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(DeckShelf())
    val state: StateFlow<DeckShelf> = _state

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("deck_shelf", Context.MODE_PRIVATE)
        prefs = p
        val saved = try { p.getString(KEY, null) } catch (e: Exception) { null }
        _state.value = saved?.let { runCatching { json.decodeFromString(DeckShelf.serializer(), it) }.getOrNull() } ?: DeckShelf()
    }

    private fun edit(f: (DeckShelf) -> DeckShelf) {
        val s = _state.updateAndGet(f)
        prefs?.edit()?.putString(KEY, json.encodeToString(DeckShelf.serializer(), s))?.apply()
    }

    fun createFolder(name: String): Long {
        val id = System.currentTimeMillis()
        edit { it.copy(folders = it.folders + DeckFolder(id, name.trim().ifBlank { "Folder" })) }
        return id
    }

    fun renameFolder(id: Long, name: String) = edit { s ->
        s.copy(folders = s.folders.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })
    }

    /** Removes a folder; its decks go back to the top level. */
    fun deleteFolder(id: Long) = edit { s ->
        s.copy(folders = s.folders.filterNot { it.id == id }, folderOf = s.folderOf.filterValues { it != id })
    }

    /** Puts a deleted folder and its decks back, for Undo. */
    fun restoreFolder(folder: DeckFolder, deckIds: List<Long>) = edit { s ->
        s.copy(
            folders = if (s.folders.any { it.id == folder.id }) s.folders else s.folders + folder,
            folderOf = s.folderOf + deckIds.associateWith { folder.id },
        )
    }

    fun moveDeck(deckId: Long, folderId: Long?) = edit { s ->
        s.copy(folderOf = if (folderId == null) s.folderOf - deckId else s.folderOf + (deckId to folderId))
    }

    fun setCover(deckId: Long, cardName: String?) = edit { s ->
        s.copy(cover = if (cardName == null) s.cover - deckId else s.cover + (deckId to cardName))
    }

    fun putMeta(meta: Map<Long, DeckMeta>) {
        if (meta.isNotEmpty()) edit { it.copy(meta = it.meta + meta) }
    }

    /** Drops folder, cover and cache for decks that no longer exist. */
    fun prune(deckIds: Set<Long>) {
        val s = _state.value
        if (s.folderOf.keys.all { it in deckIds } && s.cover.keys.all { it in deckIds } && s.meta.keys.all { it in deckIds }) return
        edit { st ->
            st.copy(
                folderOf = st.folderOf.filterKeys { it in deckIds },
                cover = st.cover.filterKeys { it in deckIds },
                meta = st.meta.filterKeys { it in deckIds },
            )
        }
    }
}

/** Asks for a folder name. */
@Composable
fun FolderNameDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                placeholder = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picks a folder for a deck. */
@Composable
fun MoveToFolderDialog(
    folders: List<DeckFolder>,
    current: Long?,
    onPick: (Long?) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Move to folder") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item { PickRow(Icons.Filled.Style, "No folder", current == null) { onPick(null) } }
                items(folders, key = { it.id }) { f -> PickRow(Icons.Filled.Folder, f.name, current == f.id) { onPick(f.id) } }
                item { PickRow(Icons.Filled.CreateNewFolder, "New folder", false, onNewFolder) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picks the card whose art shows on the deck tile. */
@Composable
fun CoverPickerDialog(names: List<String>, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Cover card") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item { PickRow(null, "First card", current == null) { onPick(null) } }
                items(names, key = { it }) { n -> PickRow(null, n, current.equals(n, ignoreCase = true)) { onPick(n) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickRow(icon: ImageVector?, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Palette.Icon, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Palette.AccentText else Palette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Filled.Check, null, tint = Palette.AccentText, modifier = Modifier.size(18.dp))
    }
}
