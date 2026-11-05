package com.mtgscanner.ui.decks

import com.mtgscanner.ui.theme.Space
import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.DeckCheck
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.CardNames
import com.mtgscanner.util.DeckLine

/** What a deck you don't fully own is for, shown on its tile. A fully owned deck shows "Owned" instead. */
enum class DeckStatus(val label: String) { WANT("Want"), MAYBE("Maybe"), TESTING("Testing") }

/** Each deck's [DeckStatus], by deck id, in the "deck_status" prefs (saved in backups). */
object DeckStatusStore {
    private const val FILE = "deck_status"
    private val statuses = mutableStateMapOf<Long, DeckStatus>()
    private var loaded = false

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            val id = k.toLongOrNull() ?: return@forEach
            val s = DeckStatus.entries.firstOrNull { it.name == v } ?: return@forEach
            statuses[id] = s
        }
    }

    /** Reads the file again, after a backup restore replaced or cleared it. */
    @Synchronized
    fun reload(context: Context) {
        statuses.clear()
        loaded = false
        load(context)
    }

    fun get(context: Context, deckId: Long): DeckStatus? {
        load(context)
        return statuses[deckId]
    }

    /** Sets or (with null) clears a deck's status. */
    fun set(context: Context, deckId: Long, status: DeckStatus?) {
        load(context)
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (status == null) {
            statuses.remove(deckId)
            p.remove(deckId.toString())
        } else {
            statuses[deckId] = status
            p.putString(deckId.toString(), status.name)
        }
        p.apply()
    }
}

/** The tags a deck tile can show, in filter order. */
object DeckTags {
    const val OWNED = "Owned"
    val ALL: List<String> = listOf(OWNED) + DeckStatus.entries.map { it.label }
}

/** A deck has every played card. */
val DeckRow.fullyOwned: Boolean get() = cards > 0 && owned >= cards

/** The tag on a deck's tile: "Owned" when it's complete, else its [DeckStatus] label, or null. */
fun deckTag(context: Context, row: DeckRow): String? =
    if (row.fullyOwned) DeckTags.OWNED else DeckStatusStore.get(context, row.deck.id)?.label

/** Whether a list is complete from the collection. Pure, for tests. */
object DeckOwnership {
    /** Every played copy is owned: [owned] is copies by [CardNames.key]. Empty lists aren't complete. */
    fun complete(lines: List<DeckLine>, owned: Map<String, Int>): Boolean {
        val need = lines.filter { it.section in DeckCheck.PLAYED }
            .groupBy { CardNames.key(it.name) }.mapValues { (_, v) -> v.sumOf { it.quantity } }
        return need.isNotEmpty() && need.all { (k, n) -> (owned[k] ?: 0) >= n }
    }
}

/** A deck tagged Want, Maybe or Testing whose cards are now all owned: Home says so once. */
@Composable
fun DeckCompleteNotes(complete: List<Pair<Long, String>>, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    complete.filter { DeckStatusStore.get(ctx, it.first) != null }.forEach { (id, name) ->
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(id) }.padding(start = Space.s4, end = Space.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$name is complete", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            // Closing clears the deck's tag: it shows "Owned" now anyway.
            IconButton(onClick = { DeckStatusStore.set(ctx, id, null) }) {
                Icon(
                    Icons.Outlined.Close, "Close", tint = Palette.Icon,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
