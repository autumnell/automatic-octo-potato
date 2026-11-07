package com.mtgscanner.ui.home

import com.mtgscanner.ui.theme.Space
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
        1 to "New: copy a deck list, volume-button scanning, pull down to refresh prices",
        96 to "Stats and tiles follow your currency. More changes can be undone.",
        120 to "New in decks: lend a whole deck, upgrade from your cards, print proxies. Weekly ban and reprint alerts.",
        127 to "Faster scanning: batch mode, loose cards in one shot, set lock reads the number. Price check shows today's price.",
        140 to "The card list keeps downloading after you leave the app. Scans say where a card goes in its box, and any box can be shown as pages.",
        143 to "Search works offline. Say a card name in Where is it?, pinch to resize lists, and card pages put the art first.",
        144 to "Drag to select cards, swipe a card page down to close it, search Settings, and share your stats. The app backs up before big changes.",
        146 to "Hold a tab for shortcuts. Drag to arrange Home in Edit, and swipe back to peek at the last screen.",
        150 to "Copy a card list anywhere and Grimoire offers to add it. Card pages have Move, +1, Sell and Share at the bottom, and price charts read out by finger.",
        153 to "Reading Japanese, Chinese and Korean names is now an optional download in Settings › Data",
        155 to "Pick how a box is sorted when you make it: Color → Type → A–Z, Set → Number and more, up to three levels. Boxes can have a location and rows, and you can set your own color order.",
        157 to "Adding a card from Search can now make a new box or deck on the spot, or put the card straight into a deck",
        159 to "New: Know cards by picture, an optional download in Settings › Data. It finds cards whose text can't be read (foils, glare, old frames, other languages) and picks the exact printing.",
        163 to "Only boxes you mark To be sorted (box ⋮ menu) count toward cards to sort",
        165 to "A bell next to the logo on Home lists every notification the app has sent. Clear them one by one or all at once.",
        167 to "Scanning reads the foil star, keeps a photo of cards set aside, reads videos and picks printings by footer and colour. Decks › New › Decks you can almost build.",
        169 to "Glare-free reads, centering, voice commands and proxy hints in Scan settings. Wishlist: Ordered → Received. Decks › New: precons and shortages. Draw odds and salt on decks.",
        172 to "Settings › Data › Card finder: pick Grimoire's corner model or YOLO.",
        174 to "Settings › Data is sorted into sections. The YOLO card finder now comes inside the app.",
        178 to "Everything the scanner uses downloads on first open, with the best models. A name read never lands on an art card.",
        180 to "Cards are known by their picture more often: the match was too strict for real phone scans",
        186 to "A clear card is added sooner: when its picture agrees with the name read, one look is enough",
        190 to "Lands have their own box: colorless boxes no longer take them, and a Lands box is made next to yours",
        191 to "Scanning is faster: a card whose picture is unmistakable is added on the first look, and the camera reads as soon as the card is steady",
        198 to "Showcase and borderless cards are read by their name, not their type line. A misread set number no longer adds the wrong card when the picture shows another.",
        201 to "A cleaner scan screen: the camera fills the screen, and finish, set, language and copies moved into Scan settings",
        203 to "Newer cards are known by their picture too: every card the picture match was missing is added on Wi-Fi, newest first",
        213 to "A more polished feel: cards, decks and boxes give a little when pressed, lists slide when cards come and go, loading shows quiet placeholders, and pop-up messages match the app's colours",
        215 to "A tidier card panel when scanning: cost beside the name, a bigger price, a Normal/Foil switch and quieter buttons",
        216 to "Collection lists give the cards more room: the search, filters and recent cards slide away as you scroll down and come back when you scroll up",
        219 to "More room and less in the way: the tab bar and headers slide off while you scroll, removing a card no longer asks first (Undo is there), pop-ups are slimmer and swipe away, wishlist, trade and goal rows swipe, totals count up, and new cards glow as they land",
        222 to "Slimmer Collection: a search bar in place of the big Browse button, a thin value bar (tap for the ring), a small \"to sort\" chip, one menu by Boxes, shorter box cards with their menu inside, a one-line totals strip, recent cards on request, a smaller price chart with 7d and 30d, and lighter titles",
        224 to "Scanning: the frame now moves onto the card it finds and follows it, and an added card flies off to the pile from right there",
        225 to "Search counts the printing you're looking at, not every printing of the card, says how many are in other printings, and shows foils apart (\"2 · 1 foil\"), as do the card page's Versions",
        226 to "Quicker, surer scans: the picture is checked while the text is read, the picture search is several times faster, the card model runs the quickest way your phone allows, \"Looks like…\" shows the likely card early, a half-read name can pick between near look-alikes, a crooked outline gets a second look, and the picture model now allows for how phone photos look, which got 97% of the test scans right (was 79%)",
        238 to "Steadier app: every update is now checked against collections from every older version, so no cards go missing; clear cards are taken on their first frame more often; a half-read name confirms a clear picture; and screen readers say mana costs and price charts in words",
        239 to "Tidier look: every screen now uses the same spacing steps, slightly squarer corners and calmer buttons, following one written design system",
        252 to "A calmer, bolder look: engraved Cinzel headings, a bigger collection value, more room between sections, red only on Scan and the main button, lighter type, plainer wording and smoother motion. The font picker is gone.",
        260 to "Headings are now in Fraunces, a softer book serif, in place of the engraved capitals",
        263 to "Boxes and Decks: the search moved up into the heading row, next to the menu and New",
        267 to "Collection has one search again: it finds boxes or decks first, then the cards. Light mode is a soft grey instead of cream, and box value changes say since added",
        270 to "Playtest a deck: from its menu, play turns on a table with tokens, counters and tapping, alone or against another of your decks or a precon that plays itself. Complete decks drop the Pull list and the owned numbers. Pick a deck per seat in Game to track its win rate (shown and editable on the deck), and browse every precon in Search",
        274 to "Commander decks open with their art across the top, and a complete deck shows its cards as pictures grouped by type. The grid button by Find in deck switches between pictures and the list",
        278 to "Playtest has its own button on the deck page and matches your deck's format: life to match, and only decks and precons of that format to play against. The deck menu is a tidier list with a line on each action. Pulling to refresh in a box or deck updates just those cards",
        281 to "The mana curve and colors sit on the commander art at the top of a deck. Tap a card in a deck to slide up its details. The legality footer and the cards-in-other-decks line are gone",
        286 to "Playtest starts with your opening hand: keep or mulligan, then put cards on the bottom. Drag a card up from your hand to play it, and the battlefield sorts creatures, other permanents and lands into rows. Draw odds can show 2, 3 or more of a group, like a 4-land opening hand",
        290 to "Editing a deck has tabs. Cards: change a card's printing and count. Combos: Commander Spellbook's combos in the deck and one card away. EDHREC: how often decks play each card, with add and remove",
        292 to "Turn the phone sideways for two columns in Browse, Search and Decks (Scan, Game and playtest stay upright). Playtest stacks matching lands and animates plays and taps. The Cards tab in deck editing groups by type and has Find. Combos and EDHREC show card pictures",
        296 to "A deck page shows its Commander bracket, a mana base check and goldfish odds. Decks have a sideboard and a maybeboard for cards you're considering (not counted or played). From your cards groups cards like EDHREC and lets you pick the box to take each one from. Compare decks from the deck menu, and Decks › New › Combos you could build finds Commander Spellbook combos you own",
        300 to "Game setup is a few quick screens: pick the format, the number of players, where everyone sits and their names. Every player gets random card art, and a short tour shows how the table works (again from the menu's Game page)",
        306 to "Tap ⋯ on a player's panel to flip it over for commander damage from each player, poison and commander tax. The Game tab opens on the format tiles, and the middle button sits dead center",
        310 to "The back of a player's panel fits short panels: commander damage and counters sit side by side. Game setup tiles are calmer, and the player count shows each seat's color",
        314 to "Collection has an All tab with every card you own. The search above the tabs filters it, and Select, sorting and the views work as in Browse",
        330 to "On the back of a player's panel, commander damage is laid out like the table: each player's tile sits where they sit, and your own seat says You",
        334 to "Sort, Group and Filters are slimmer and share one row with Select and the card size button",
        340 to "The game menu is one page: next turn, table, dice and game. Search has the search key on the keyboard and its tips under ?. Home can show cards you viewed recently. Tapped playtest cards stay upright, dimmed with a small turn mark. Accent colors are five muted ones that suit the look",
        344 to "A deck's Bracket, Mana base and Goldfish sheets are grouped into panels, with the big numbers on top and bars for color sources and land drops",
        348 to "Win rate, Bracket, Mana base and Goldfish share one small grid on the deck page. The Bracket sheet lists all five brackets",
        352 to "On a Commander deck the bracket sits under the commander's name: tap it to see why or set your own, EDHREC is at the top right of the art and the salt score at the bottom right. Win rate, Mana base and Goldfish share one slim row. Tutors and mass land denial now come from Scryfall's card tags, so land searches and Season of Gathering no longer count",
        358 to "Bug fixes: moving a card listed in two printings moves both, MTGO sideboards stay sideboards when edited, the game screen keeps its upright lock between pages, win rates never go negative, and hand-set brackets are saved in backups",
        362 to "Bug fixes from a full review: stopping batch scanning keeps the pile, quick edits on a card page no longer undo each other, a removed wishlist card keeps its Ordered mark on Undo, the first setup finishes without the card finder, Home sections drag smoothly, the weekly watch skips brand-new cards, set names like Theros import right, and opening the app from a widget no longer crashes",
        366 to "The scanner checks the card picture even when the name and set code read fine, so showcase, borderless and other arts of a card in the same set are told apart. A flavor text signature naming another card (Hop to It's “Ms. Bumbleflower”) is no longer read as the card's name",
        372 to "Deck editing opens on Cards: add a card by typing its name, or search all of Magic with type, format and commander color filters. A new deck can be saved empty. The scan screen's add-to menu lists your decks: scanned cards go into the deck and onto its list",
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
    Row(modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("New in ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = { WhatsNew.markSeen(ctx, code); line = null }) {
            Icon(Icons.Outlined.Close, "Close", tint = Palette.Icon, modifier = Modifier.size(18.dp))
        }
    }
}
