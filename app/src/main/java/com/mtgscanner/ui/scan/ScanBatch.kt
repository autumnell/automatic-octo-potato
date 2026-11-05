package com.mtgscanner.ui.scan

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.update

// Batch mode: scan a whole pile without stopping, then check the list and add everything at once.

fun ScanViewModel.setBatch(on: Boolean) {
    // Turning it off with cards still on the list opens them, so nothing scanned is lost.
    val left = _state.value.batch.orEmpty()
    changeMode {
        it.copy(
            batch = if (on) emptyList() else null,
            stack = if (on) false else it.stack,
            target = if (on && it.target == ScanTarget.CHECK) ScanTarget.COLLECTION else it.target,
            current = null, awaiting = false, foilHint = false,
            status = if (on) "Batch · scan the pile, then review" else "Point your camera at a card",
        )
    }
    if (!on && left.isNotEmpty()) _state.update { it.copy(photo = PhotoRun(loading = false, items = left, title = "Batch")) }
}

/** Opens the batch list to check and add: unsure matches first, so those get checked and the rest trusted. */
fun ScanViewModel.reviewBatch() = _state.update {
    val items = it.batch.orEmpty().sortedByDescending { i -> i.unsure }
    if (items.isEmpty()) it else it.copy(photo = PhotoRun(loading = false, items = items, title = "Batch", fromBatch = true), batch = emptyList())
}

/** "Batch · 12 cards  Review" over the camera while batch mode is on. */
@Composable
fun BatchChip(state: ScanUiState, vm: ScanViewModel, modifier: Modifier = Modifier) {
    val list = state.batch ?: return
    Surface(modifier, shape = RoundedCornerShape(Corners.Medium), color = Color.Black.copy(alpha = 0.75f)) {
        Row(Modifier.padding(start = Space.s3, end = Space.s1, top = Space.s1, bottom = Space.s1), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Batch", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Text(if (list.size == 1) "1 card" else "${list.size} cards", color = Palette.Gold, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = vm::reviewBatch, enabled = list.isNotEmpty()) { Text("Review", color = if (list.isNotEmpty()) Palette.Text else Palette.Muted) }
        }
    }
}
