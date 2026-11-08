package com.mtgscanner.util

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Something shared to the app or opened with it, waiting to be imported. */
sealed class Incoming {
    data class File(val uri: Uri) : Incoming()
    data class Text(val text: String) : Incoming()
    /** A Grimoire backup (.zip) opened from Files or Drive: shown as a restore preview. */
    data class Backup(val uri: Uri) : Incoming()
}

/** "Share to Grimoire" and "Open with": a CSV, decklist or deck link from another app, picked up by the import screen. */
object IncomingShare {
    val pending = MutableStateFlow<Incoming?>(null)

    /** Takes a VIEW or SEND intent. Returns true when it carried a file or text. */
    fun accept(intent: Intent?): Boolean {
        val found: Incoming? = when (intent?.action) {
            // Box links (grimoire://box/12) open a box instead; see MainActivity.
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme != com.mtgscanner.data.BoxLinks.SCHEME }?.let { file(it, intent.type) }
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { file(it, intent.type) }
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { Incoming.Text(it) }
            else -> null
        }
        if (found != null) pending.value = found
        return found != null
    }

    /** A zip is a backup to restore; anything else is a list to import. */
    private fun file(uri: Uri, type: String?): Incoming =
        if (isZip(type, uri.lastPathSegment)) Incoming.Backup(uri) else Incoming.File(uri)

    fun isZip(type: String?, name: String?): Boolean =
        type?.lowercase()?.let { "zip" in it } == true || name?.lowercase()?.endsWith(".zip") == true
}

/** Requests from app shortcuts that a screen acts on once it's showing. */
object OpenRequests {
    /** Start the scanner in price check mode. */
    val priceCheck = MutableStateFlow(false)
    /** Start the scanner in batch mode (held Scan tab). */
    val batch = MutableStateFlow(false)
    /** Start a Box check of this box (from a "not checked in 3 months" nudge). */
    val boxCheck = MutableStateFlow<Long?>(null)
}
