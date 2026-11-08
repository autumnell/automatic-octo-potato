package com.mtgscanner.ui.decks

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Space
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A deck's unsaved edits, kept while editing so they survive Android closing the app. */
@Serializable
data class DeckDraft(
    val name: String,
    val text: String,
    val format: String,
    val commanders: String,
    /** When it was kept, in millis. */
    val at: Long = 0,
)

/** One draft per deck (0 = a new deck not saved yet), in its own prefs file. */
object DeckDrafts {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("deck_drafts", Context.MODE_PRIVATE)
    }

    private fun key(deckId: Long) = "d$deckId"

    fun get(deckId: Long): DeckDraft? = try {
        prefs?.getString(key(deckId), null)?.let { json.decodeFromString(DeckDraft.serializer(), it) }
    } catch (e: Exception) {
        null
    }

    fun put(deckId: Long, d: DeckDraft) {
        prefs?.edit()?.putString(key(deckId), json.encodeToString(DeckDraft.serializer(), d))?.apply()
    }

    fun remove(deckId: Long) {
        prefs?.edit()?.remove(key(deckId))?.apply()
    }
}

/** "Unsaved changes from 2 h ago" with Restore and Discard, above the deck page or editor. */
@androidx.compose.runtime.Composable
fun DraftOffer(draft: DeckDraft, onRestore: () -> Unit, onDiscard: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        androidx.compose.ui.Modifier
            .padding(horizontal = Space.s4, vertical = Space.s1)
            .fillMaxWidth()
            .background(Palette.Surface, androidx.compose.foundation.shape.RoundedCornerShape(Corners.Small))
            .padding(start = Space.s4, end = Space.s1),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f).padding(vertical = Space.s3)) {
            Text("Unsaved changes", style = MaterialTheme.typography.titleMedium)
            Text(
                "From ${com.mtgscanner.ui.components.UndoHistory.ago(System.currentTimeMillis(), draft.at)}",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
        }
        TextButton(onClick = onDiscard) { Text("Discard", color = Palette.Muted) }
        TextButton(onClick = onRestore) { Text("Restore") }
    }
}
