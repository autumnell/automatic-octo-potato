package com.mtgscanner.ui.settings

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalWindowInfo
import com.mtgscanner.ui.theme.Space
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.Incoming
import com.mtgscanner.util.IncomingShare

/**
 * "Add cards from your clipboard?" when text was copied in another app since the last look. Only the clip's
 * description (type and time) is checked until Paste is tapped, so Android's "pasted from your clipboard"
 * notice never shows on its own. Paste hands the text to the usual list import.
 */
object ClipboardWatch {
    private const val PREFS = "app"
    private const val KEY = "clipSeen"
    /** Older clips than this aren't offered: they were copied for something else. */
    private const val MAX_AGE_MS = 30 * 60_000L

    /** The time of a new text clip worth offering, or null. */
    fun newClip(context: Context): Long? = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val d = cm.primaryClipDescription ?: return null
        if (!d.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !d.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) return null
        val at = d.timestamp
        val seen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, 0L)
        at.takeIf { it > seen && System.currentTimeMillis() - it < MAX_AGE_MS }
    }.getOrNull()

    /** The clip up to [at] has been offered (or was copied by this app), so it isn't offered again. */
    fun markSeen(context: Context, at: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY, at).apply()
    }

    fun read(context: Context): String? = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()
}

@Composable
fun ClipboardOffer(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var clipAt by remember { mutableStateOf<Long?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { clipAt = ClipboardWatch.newClip(ctx) }
    // Android 10+ only lets an app see the clipboard once its window has focus, which comes after resume.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (focused) clipAt = ClipboardWatch.newClip(ctx) }
    val at = clipAt ?: return
    fun done() { ClipboardWatch.markSeen(ctx, at); clipAt = null }
    Surface(
        modifier.padding(horizontal = Space.s4).widthIn(max = 480.dp).fillMaxWidth(),
        shape = RoundedCornerShape(Corners.Medium),
        color = Palette.SurfaceHigh,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(start = Space.s4), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ContentPaste, null, tint = Palette.Icon)
            Text(
                "Add cards from your clipboard?",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Text,
                modifier = Modifier.weight(1f).padding(start = Space.s3),
            )
            TextButton(onClick = {
                val text = ClipboardWatch.read(ctx)
                done()
                if (!text.isNullOrBlank()) IncomingShare.pending.value = Incoming.Text(text)
            }) { Text("Paste") }
            IconButton(onClick = { done() }) { Icon(Icons.Outlined.Close, "Not now", tint = Palette.Icon) }
        }
    }
}
