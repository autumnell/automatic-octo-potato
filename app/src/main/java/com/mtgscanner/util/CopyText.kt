package com.mtgscanner.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Copies text (a card name, a list of names) to the clipboard and says so in the app's snackbar. */
object CopyText {
    fun copy(context: Context, text: String, label: String = "Card name") {
        if (text.isBlank()) return
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        runCatching { clip.setPrimaryClip(ClipData.newPlainText(label, text)) }
            // Copied here, so it isn't offered back as a list to add.
            .onSuccess { com.mtgscanner.ui.settings.ClipboardWatch.markSeen(context) }
            .onSuccess { com.mtgscanner.ui.components.Undo.offer("Copied") }
    }
}
