package com.mtgscanner.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtgscanner.BuildConfig
import com.mtgscanner.ui.theme.Palette

/** One "What's new" line on Home after an update, until it's closed. */
object WhatsNew {
    private const val KEY = "whatsNewSeen"

    /**
     * Lines by the first build (versionCode, CI's run number) that has the change. Add a line with a
     * higher number when a later build has something worth saying.
     */
    val NOTES: Map<Int, String> = mapOf(
        1 to "New: copy a deck list, volume-button scanning, pull down to refresh prices.",
        96 to "Stats and tiles follow your currency. More changes can be undone.",
        120 to "New in decks: lend a whole deck, upgrade from your cards, print proxies. Weekly ban and reprint alerts.",
        127 to "Faster scanning: batch mode, loose cards in one shot, set lock reads the number. Price check shows today's price.",
        140 to "The card list keeps downloading after you leave the app. Scans say where a card goes in its box, and any box can be shown as pages.",
        143 to "Search works offline. Say a card name in Where is it?, pinch to resize lists, and card pages put the art first.",
        144 to "Drag to select cards, swipe a card page down to close it, search Settings, and share your stats. The app backs up before big changes.",
        146 to "Hold a tab for shortcuts. Drag to arrange Home in Edit, and swipe back to peek at the last screen.",
        150 to "Copy a card list anywhere and Grimoire offers to add it. Card pages have Move, +1, Sell and Share at the bottom, and price charts read out by finger.",
    )

    /** The newest line for a build newer than [seen] (the last build whose line was closed), or null. */
    fun lineFor(code: Int, seen: Int, notes: Map<Int, String> = NOTES): String? {
        if (code <= seen) return null
        return notes.filterKeys { it in (seen + 1)..code }.maxByOrNull { it.key }?.value
    }

    fun seen(context: Context): Int = context.getSharedPreferences("app", Context.MODE_PRIVATE).getInt(KEY, 0)

    fun markSeen(context: Context, code: Int) {
        context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putInt(KEY, code).apply()
    }
}

/** The line itself, with × to close it. Nothing on a fresh install (the tour covers that) or once closed. */
@Composable
fun WhatsNewLine(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val code = BuildConfig.VERSION_CODE
    var line by remember {
        mutableStateOf(
            if (!Tour.seen(ctx)) { WhatsNew.markSeen(ctx, code); null }
            else WhatsNew.lineFor(code, WhatsNew.seen(ctx)),
        )
    }
    val text = line ?: return
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("New in ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = { WhatsNew.markSeen(ctx, code); line = null }) {
            Icon(Icons.Outlined.Close, "Close", tint = Palette.Icon, modifier = Modifier.size(18.dp))
        }
    }
}
