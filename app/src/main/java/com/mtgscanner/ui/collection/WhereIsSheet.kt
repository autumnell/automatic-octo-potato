package com.mtgscanner.ui.collection

import com.mtgscanner.ui.theme.Space
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.DisposableEffect
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

/**
 * "Where is it?": type or say a card name, see each copy's box and the divider it sits behind. Tap one to open it.
 * A spoken name gets a spoken answer, so your hands can stay on the cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhereIsSheet(onOpenCard: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as MtgApp).repository
    var query by remember { mutableStateOf("") }
    // The name was spoken: the first answer is read out once.
    var spoken by remember { mutableStateOf<String?>(null) }
    val voice = remember { SpokenAnswer(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { voice.close() } }
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (!said.isNullOrEmpty()) { query = said; spoken = said }
    }
    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a card name")
        voice.warmUp()
        runCatching { listen.launch(intent) }.onFailure {
            android.widget.Toast.makeText(context, "Voice input isn't available on this phone", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    // Results and the text they were found for, worked out once typing pauses.
    var found by remember { mutableStateOf("" to emptyList<Found>()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { found = query to emptyList<Found>(); return@LaunchedEffect }
        delay(150)
        val list = runCatching { withContext(Dispatchers.Default) { repo.findCard(q) } }.getOrDefault(emptyList<Found>())
        found = query to list
        if (spoken == query) {
            spoken = null
            voice.say(
                list.firstOrNull()?.let { f -> "${f.card.name}. ${f.where}" + if (list.size > 1) ". And ${list.size - 1} more." else "" }
                    ?: "$q isn't in your collection",
            )
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Text("Where is it?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.s4))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Card name") },
                trailingIcon = {
                    IconButton(onClick = { startListening() }) { Icon(Icons.Outlined.Mic, "Say a card name", tint = Palette.Icon) }
                },
                singleLine = true,
                shape = RoundedCornerShape(Corners.Small),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Text, unfocusedBorderColor = Palette.Outline, cursorColor = Palette.Text),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2).focusRequester(focus),
            )
            // Keyboard up as the sheet opens.
            LaunchedEffect(Unit) {
                delay(200)
                runCatching { focus.requestFocus() }
            }
            val list = found.second
            val q = query.trim()
            if (q.length >= 2 && found.first == query && list.isEmpty()) {
                EmptyState("Not in your collection", Modifier.padding(vertical = Space.s6))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(list, key = { it.card.id }) { f -> FoundRow(f.card, f.where) { onOpenCard(f.card.id) } }
                }
            }
        }
    }
}

/** Reads short answers aloud with the phone's text-to-speech, started only once voice is used. */
private class SpokenAnswer(private val context: android.content.Context) {
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun warmUp() {
        if (tts != null) return
        tts = android.speech.tts.TextToSpeech(context) { status ->
            ready = status == android.speech.tts.TextToSpeech.SUCCESS
            if (ready) pending?.let { speak(it) }
            pending = null
        }
    }

    fun say(text: String) {
        warmUp()
        if (ready) speak(text) else pending = text
    }

    private fun speak(text: String) {
        // Arrows and dots read as pauses.
        val plain = text.replace("→", ",").replace("·", ",")
        tts?.speak(plain, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "where")
    }

    fun close() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
    }
}
