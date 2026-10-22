package com.mtgscanner.ui.decks

import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.PrintingLookup
import com.mtgscanner.util.CardNames
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

/** What the deck tiles need to know about one card. */
data class DeckCardInfo(
    val name: String,
    val cmc: Double,
    val manaCost: String,
    val typeLine: String,
    /** Color letters, e.g. "WU". Empty means colorless. */
    val colors: String,
    val colorIdentity: String,
    val oracleText: String,
    val imageUrl: String?,
    /** Scryfall's link to the card on EDHREC; null when not looked up there. */
    val edhrec: String? = null,
)

object DeckNames {
    /** A card name for matching deck lines to cards: "A // B", "A/B" and "A" match (see [CardNames.key]). */
    fun key(name: String): String = CardNames.key(name)
}

/** Card data looked up for decks, kept for the app's lifetime. */
private val cardCache = ConcurrentHashMap<String, DeckCardInfo>()

/** Names Scryfall answered but couldn't find (typos), so they aren't asked for again this session. */
private val unknownNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

/** True when Scryfall has said it has no card by this name. */
fun deckCardUnknown(name: String): Boolean = DeckNames.key(name) in unknownNames

/**
 * Card data for deck card names, keyed by [DeckNames.key]: from your collection first,
 * then Scryfall (by name, in batches). Names Scryfall can't find, or all of them when offline, are left out.
 */
suspend fun CollectionRepository.deckCardInfo(names: Collection<String>): Map<String, DeckCardInfo> {
    val wanted = names.associateBy { DeckNames.key(it) }.filterKeys { it.isNotEmpty() }
    val out = HashMap<String, DeckCardInfo>()
    val owned = observeAll().first().groupBy { DeckNames.key(it.name) }
    val missing = mutableListOf<String>()
    for ((k, name) in wanted) {
        val cached = cardCache[k]
        val mine = owned[k]?.firstOrNull()
        when {
            cached != null -> out[k] = cached
            mine != null -> {
                val info = DeckCardInfo(
                    name = mine.name, cmc = mine.cmc, manaCost = mine.manaCost, typeLine = mine.typeLine,
                    colors = mine.colors, colorIdentity = mine.colorIdentity, oracleText = mine.oracleText,
                    imageUrl = mine.imageUrl,
                )
                cardCache[k] = info
                out[k] = info
            }
            k in unknownNames -> {}
            else -> missing += name
        }
    }
    if (missing.isNotEmpty()) {
        // "A // B" names are tried in full, then by the front face (see PrintingLookup).
        val found = runCatching {
            PrintingLookup.resolve(missing.map { PrintingLookup.Wanted(it) }) { ids -> api.identify(ids.map(PrintingLookup::json)) }
        }.getOrNull()
        missing.zip(found ?: emptyList()).forEach { (n, c) ->
            if (c == null) unknownNames += DeckNames.key(n)
            if (c != null) {
                val info = DeckCardInfo(
                    name = c.name, cmc = c.cmc, manaCost = c.effectiveManaCost, typeLine = c.effectiveTypeLine,
                    colors = c.effectiveColors.joinToString(""), colorIdentity = c.colorIdentity.joinToString(""),
                    oracleText = c.effectiveOracleText, imageUrl = c.imageUrl, edhrec = c.relatedUris["edhrec"],
                )
                val k = DeckNames.key(n)
                cardCache[k] = info
                out[k] = info
            }
        }
    }
    return out
}
