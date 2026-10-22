package com.mtgscanner.ui.decks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.DeckLinks
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.Palette

/** Saves the deck behind a public Moxfield or Archidekt link as a deck list, with Undo. */
suspend fun CollectionRepository.saveDeckFromLink(url: String) {
    val deck = try {
        DeckLinks.fetch(url.trim())
    } catch (e: java.io.IOException) {
        Undo.offer(e.message ?: "Couldn't get that deck")
        return
    }
    // The format the site has, and the commanders from the list's Commander and Companion sections.
    val lines = com.mtgscanner.util.DecklistParser.parse(deck.text)
    val format = deck.format.ifEmpty { com.mtgscanner.data.DeckFormats.guess(lines) }
    val id = saveDeck(0L, deck.name, deck.text, format, com.mtgscanner.data.DeckCommanders.fromLines(lines).encode())
    Undo.offer("Saved deck: ${deck.name}") { deleteDeck(id) }
}

/** Decks › New › From a link: paste a Moxfield or Archidekt link. */
@Composable
fun DeckLinkDialog(onDismiss: () -> Unit, onGet: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Deck from a link") },
        text = {
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                placeholder = { Text("moxfield.com/decks/…") }, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            val link = DeckLinks.find(url.trim().let { if (it.startsWith("http")) it else "https://$it" })
            TextButton(onClick = { link?.let(onGet) }, enabled = link != null) { Text("Get deck") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
