package com.mtgscanner.ui.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.Found
import com.mtgscanner.data.findCard
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** "Where is it?": type a card name, see each copy's box and the divider it sits behind. Tap one to open it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhereIsSheet(onOpenCard: (Long) -> Unit, onDismiss: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as MtgApp).repository
    var query by remember { mutableStateOf("") }
    // Results and the text they were found for, worked out once typing pauses.
    var found by remember { mutableStateOf("" to emptyList<Found>()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { found = query to emptyList<Found>(); return@LaunchedEffect }
        delay(150)
        found = query to runCatching { withContext(Dispatchers.Default) { repo.findCard(q) } }.getOrDefault(emptyList<Found>())
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Text("Where is it?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Card name") },
                singleLine = true,
                shape = RoundedCornerShape(Corners.Small),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Accent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            )
            // Keyboard up as the sheet opens.
            LaunchedEffect(Unit) {
                delay(200)
                runCatching { focus.requestFocus() }
            }
            val list = found.second
            val q = query.trim()
            if (q.length >= 2 && found.first == query && list.isEmpty()) {
                EmptyState("Not in your collection", Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(list, key = { it.card.id }) { f -> FoundRow(f.card, f.where) { onOpenCard(f.card.id) } }
                }
            }
        }
    }
}
