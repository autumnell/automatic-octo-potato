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
        153 to "Reading Japanese, Chinese and Korean names is now an optional download in Settings › Data.",
        155 to "Pick how a box is sorted when you make it: Color → Type → A–Z, Set → Number and more, up to three levels. Boxes can have a location and rows, and you can set your own color order.",
        157 to "Adding a card from Search can now make a new box or deck on the spot, or put the card straight into a deck.",
        159 to "New: Know cards by picture, an optional download in Settings › Data. It finds cards whose text can't be read (foils, glare, old frames, other languages) and picks the exact printing.",
        163 to "Only boxes you mark To be sorted (box ⋮ menu) count toward cards to sort.",
        165 to "A bell next to the logo on Home lists every notification the app has sent. Clear them one by one or all at once.",
        167 to "Scanning reads the foil star, keeps a photo of cards set aside, reads videos and picks printings by footer and colour. Decks › New › Decks you can almost build.",
        169 to "Glare-free reads, centering, voice commands and proxy hints in Scan settings. Wishlist: Ordered → Received. Decks › New: precons and shortages. Draw odds and salt on decks.",
        172 to "Settings › Data › Card finder: pick Grimoire's corner model or YOLO.",
        174 to "Settings › Data is sorted into sections. The YOLO card finder now comes inside the app.",
        178 to "Everything the scanner uses downloads on first open, with the best models. A name read never lands on an art card.",
        180 to "Cards are known by their picture more often: the match was too strict for real phone scans.",
        186 to "A clear card is added sooner: when its picture agrees with the name read, one look is enough.",
        190 to "Lands have their own box: colorless boxes no longer take them, and a Lands box is made next to yours.",
        191 to "Scanning is faster: a card whose picture is unmistakable is added on the first look, and the camera reads as soon as the card is steady.",
        198 to "Showcase and borderless cards are read by their name, not their type line. A misread set number no longer adds the wrong card when the picture shows another.",
        201 to "A cleaner scan screen: the camera fills the screen, and finish, set, language and copies moved into Scan settings.",
        203 to "Newer cards are known by their picture too: every card the picture match was missing is added on Wi-Fi, newest first.",
        213 to "A more polished feel: cards, decks and boxes give a little when pressed, lists slide when cards come and go, loading shows quiet placeholders, and pop-up messages match the app's colours.",
        215 to "A tidier card panel when scanning: cost beside the name, a bigger price, a Normal/Foil switch and quieter buttons.",
        216 to "Collection lists give the cards more room: the search, filters and recent cards slide away as you scroll down and come back when you scroll up.",
        219 to "More room and less in the way: the tab bar and headers slide off while you scroll, removing a card no longer asks first (Undo is there), pop-ups are slimmer and swipe away, wishlist, trade and goal rows swipe, totals count up, and new cards glow as they land.",
        222 to "Slimmer Collection: a search bar in place of the big Browse button, a thin value bar (tap for the ring), a small \"to sort\" chip, one menu by Boxes, shorter box cards with their menu inside, a one-line totals strip, recent cards on request, a smaller price chart with 7d and 30d, and lighter titles.",
        224 to "Scanning: the frame now moves onto the card it finds and follows it, and an added card flies off to the pile from right there.",
        225 to "Search counts the printing you're looking at, not every printing of the card, says how many are in other printings, and shows foils apart (\"2 · 1 foil\"), as do the card page's Versions.",
        226 to "Quicker, surer scans: the picture is checked while the text is read, the picture search is several times faster, the card model runs the quickest way your phone allows, \"Looks like…\" shows the likely card early, a half-read name can pick between near look-alikes, and a crooked outline gets a second look.",
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
