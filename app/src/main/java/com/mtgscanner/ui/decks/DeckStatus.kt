package com.mtgscanner.ui.decks

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/** What a deck you don't fully own is for, shown on its tile. A fully owned deck shows "Owned" instead. */
enum class DeckStatus(val label: String) { WANT("Want"), MAYBE("Maybe"), TESTING("Testing") }

/** Each deck's [DeckStatus], by deck id, in the "deck_status" prefs (saved in backups). */
object DeckStatusStore {
    private const val FILE = "deck_status"
    private val statuses = mutableStateMapOf<Long, DeckStatus>()
    private var loaded = false

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            val id = k.toLongOrNull() ?: return@forEach
            val s = DeckStatus.entries.firstOrNull { it.name == v } ?: return@forEach
            statuses[id] = s
        }
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
