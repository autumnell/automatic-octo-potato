package com.mtgscanner.ui.decks

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a deck page worked out last time (curve, commander art, EDHREC link), saved with the deck so a big deck's
 * page shows them at once instead of after every card is looked up. Thrown away once the deck changes.
 */
object DeckPageCache {
    @Serializable
    data class Saved(
        val stamp: String,
        val curve: List<Int> = emptyList(),
        /** "W3 U2": pips by color, WUBRG order. */
        val pips: String = "",
        val leadArt: List<String> = emptyList(),
        val edhrec: String? = null,
    ) {
        fun shape(): DeckShape? = if (curve.size != DeckShape.BUCKETS) null else DeckShape(
            curve,
            pips.split(' ').filter { it.length >= 2 && it[0] in "WUBRG" }.mapNotNull { p -> p.drop(1).toIntOrNull()?.let { p[0] to it } },
        )
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("deck_page", Context.MODE_PRIVATE)
    }

    /** Same list, format and commanders as when it was saved. */
    fun stamp(text: String, format: String, commanders: String): String = "${text.hashCode()}|${text.length}|$format|$commanders"

    fun get(deckId: Long, stamp: String): Saved? {
        val raw = runCatching { prefs?.getString(deckId.toString(), null) }.getOrNull() ?: return null
        return runCatching { json.decodeFromString(Saved.serializer(), raw) }.getOrNull()?.takeIf { it.stamp == stamp }
    }

    fun put(deckId: Long, stamp: String, shape: DeckShape?, leadArt: List<String>, edhrec: String?) {
        if (deckId == 0L) return
        val s = Saved(stamp, shape?.curve.orEmpty(), shape?.pips.orEmpty().joinToString(" ") { "${it.first}${it.second}" }, leadArt, edhrec)
        runCatching { prefs?.edit()?.putString(deckId.toString(), json.encodeToString(Saved.serializer(), s))?.apply() }
    }

    fun forget(deckId: Long) {
        runCatching { prefs?.edit()?.remove(deckId.toString())?.apply() }
    }
}
