package com.mtgscanner.ui.decks

import com.mtgscanner.util.shareText
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.DeckFormats
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.util.DeckExport
import com.mtgscanner.util.DecklistParser
import kotlinx.coroutines.flow.first

/** The deck's list as Moxfield-style text, commanders first. */
fun deckListText(ui: DeckUi): String = DeckExport.moxfield(
    DecklistParser.parse(ui.text),
    if (DeckFormats.isCommander(ui.format)) ui.commanders.commanders else emptyList(),
)

private fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    runCatching { cm.setPrimaryClip(ClipData.newPlainText(label, text)) }.onSuccess {
        com.mtgscanner.ui.settings.ClipboardWatch.markSeen(ctx)
        Undo.offer("Copied")
    }
}


/** "Copy list", "Share list" and "Duplicate" for the deck's actions sheet. [close] shuts the sheet. */
@Composable
fun DeckListMenuItems(ui: DeckUi, close: () -> Unit, onDuplicate: () -> Unit) {
    val ctx = LocalContext.current
    val title = ui.name.ifBlank { "Deck" }
    DeckActionRow(Icons.Outlined.ContentCopy, "Copy list", "As text, to paste into Moxfield or Archidekt") { close(); copyText(ctx, title, deckListText(ui)) }
    DeckActionRow(Icons.Outlined.Share, "Share list", "Send the list to another app") { close(); shareText(ctx, title, deckListText(ui)) }
    DeckActionRow(Icons.Outlined.FileCopy, "Duplicate", "A new deck with the same list, without its cards") { close(); onDuplicate() }
}

/**
 * A copy of a deck: name + " copy", format, commanders, list and folder. Not its cards, which stay in the
 * original. Returns the new deck's id, or null when there's no such deck.
 */
suspend fun CollectionRepository.duplicateDeck(deckId: Long): Long? {
    val d = observeDeck(deckId).first() ?: return null
    val id = saveDeck(0L, d.name + " copy", d.text, d.format, d.commanders)
    DeckShelfStore.state.value.folderOf[deckId]?.let { DeckShelfStore.moveDeck(id, it) }
    DeckStatusStore.get(appContext, deckId)?.let { DeckStatusStore.set(appContext, id, it) }
    return id
}
