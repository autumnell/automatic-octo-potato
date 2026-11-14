package com.mtgscanner.ui.decks

import com.mtgscanner.ui.theme.Space
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mtgscanner.data.PhotoListText
import com.mtgscanner.scan.PhotoCards
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Reads a photo of a printed list into decklist text, one card per line. Empty when nothing was read. */
suspend fun readListPhoto(context: Context, uri: Uri): String {
    val bitmap = withContext(Dispatchers.IO) { PhotoCards.decode(context, uri) } ?: return ""
    val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        val text = reader.process(InputImage.fromBitmap(bitmap, 0)).await()
        val lines = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                PhotoListText.Line(line.text, box.left, box.top, box.right, box.bottom)
            }
        }
        withContext(Dispatchers.Default) { PhotoListText.text(lines) }
    } finally {
        runCatching { reader.close() }
        bitmap.recycle()
    }
}

/** "From photo": pick a picture of a printed list; its cards come back as text to check in the paste box. */
@Composable
fun FromPhotoButton(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val text = com.mtgscanner.util.suspendRunCatching { readListPhoto(ctx, uri) }.getOrDefault("")
                busy = false
                if (text.isBlank()) Undo.offer("No cards found") else onText(text)
            }
        }
    }
    TextButton(
        onClick = { runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
        enabled = !busy,
        modifier = modifier,
    ) {
        Icon(Icons.Outlined.PhotoLibrary, null, tint = Palette.Icon, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s2))
        Text(if (busy) "Reading…" else "From photo")
    }
}

/** [current] with [added] after it on new lines, for adding a photo's list to what's already pasted. */
fun appendList(current: String, added: String): String =
    if (current.isBlank()) added else current.trimEnd() + "\n" + added
