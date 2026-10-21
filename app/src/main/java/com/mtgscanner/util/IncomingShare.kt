package com.mtgscanner.util

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Something shared to the app or opened with it, waiting to be imported. */
sealed class Incoming {
    data class File(val uri: Uri) : Incoming()
    data class Text(val text: String) : Incoming()
}

/** "Share to Grimoire" and "Open with": a CSV, decklist or deck link from another app, picked up by the import screen. */
object IncomingShare {
    val pending = MutableStateFlow<Incoming?>(null)

    /** Takes a VIEW or SEND intent. Returns true when it carried a file or text. */
    fun accept(intent: Intent?): Boolean {
        val found: Incoming? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { Incoming.File(it) }
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { Incoming.File(it) }
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { Incoming.Text(it) }
            else -> null
        }
        if (found != null) pending.value = found
        return found != null
    }
}

/** Requests from app shortcuts that a screen acts on once it's showing. */
object OpenRequests {
    /** Start the scanner in price check mode. */
    val priceCheck = MutableStateFlow(false)
}
