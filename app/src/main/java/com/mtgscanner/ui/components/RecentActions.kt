package com.mtgscanner.ui.components

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * The last 10 changes with Undo, newest first. [onUndo] gets the entry's id and should run
 * [Undo.undoThrough] in a scope that outlives the sheet ([Undo.performThrough]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentActionsSheet(onUndo: (Long) -> Unit, onDismiss: () -> Unit) {
    val recent by Undo.recent.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    /** An older entry waiting for "also undoes newer ones" to be confirmed. */
    var confirm by remember { mutableStateOf<Pair<Long, Int>?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Text(
            "Recent actions",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
        )
        if (recent.isEmpty()) {
            EmptyState("Nothing to undo", Modifier.padding(bottom = Space.s8))
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = Space.s4)) {
                items(recent, key = { it.id }) { e ->
                    Row(
                        Modifier.animateItem().fillMaxWidth().heightIn(min = 56.dp).padding(start = Space.s4, end = Space.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.label, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(UndoHistory.ago(now, e.at), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        }
                        TextButton(onClick = {
                            val newer = Undo.newerThan(e.id) ?: return@TextButton
                            if (newer == 0) onUndo(e.id) else confirm = e.id to newer
                        }) { Text("Undo", color = Palette.AccentText) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = Space.s4), color = Palette.Rule)
                }
            }
        }
    }

    confirm?.let { (id, newer) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Palette.Surface,
            title = { Text("Undo ${newer + 1} actions?") },
            text = { Text(if (newer == 1) "The newer one goes too." else "The $newer newer ones go too.") },
            confirmButton = { TextButton(onClick = { confirm = null; onUndo(id) }) { Text("Undo") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

/** The app's snackbar; holding it opens Recent actions. */
@Composable
fun UndoSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SlimSnackbarHost(state, modifier, onLongPress = { Undo.showRecent.value = true })
}

/** Shows [RecentActionsSheet] while [Undo.showRecent] is set. Put once at the app root. */
@Composable
fun RecentActionsHost() {
    val show by Undo.showRecent.collectAsStateWithLifecycle()
    // App-wide scope, so an undo keeps running after the sheet (or the app) closes.
    if (show) RecentActionsSheet(onUndo = { id -> Undo.performThrough(id) }, onDismiss = { Undo.showRecent.value = false })
}
