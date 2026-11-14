package com.mtgscanner.ui.decks

import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.PrintingLookup
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.tagList
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
    /** Scryfall's legality by format key; a format that's missing isn't known. */
    val legalities: Map<String, String> = emptyMap(),
    /** Printed power and toughness (the front face's), for the playtest table; null for non-creatures. */
    val power: String? = null,
    val toughness: String? = null,
)

object DeckNames {
    /** A card name for matching deck lines to cards: "A // B", "A/B" and "A" match (see [CardNames.key]). */
    fun key(name: String): String = CardNames.key(name)
}

/** Card data looked up for decks, kept for the app's lifetime. */
private val cardCache = ConcurrentHashMap<String, DeckCardInfo>()

/** Names Scryfall answered but couldn't find (typos), so they aren't asked for again this session. */
private val unknownNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

/** Saved cards Scryfall couldn't match by name, so their legality isn't asked for again this session. */
private val noLegality: MutableSet<String> = ConcurrentHashMap.newKeySet()

/** True when Scryfall has said it has no card by this name. */
fun deckCardUnknown(name: String): Boolean = DeckNames.key(name) in unknownNames

/** The formats a saved card's tags cover ("legal:modern"); see scryfallTags. */
private val TAGGED_FORMATS = listOf("standard", "pioneer", "modern", "commander", "pauper")

private fun CollectionCard.deckInfo(): DeckCardInfo {
    val legal = tagList().filter { it.startsWith("legal:") }.map { it.removePrefix("legal:") }.toSet()
    return DeckCardInfo(
        name = name, cmc = cmc, manaCost = manaCost, typeLine = typeLine,
        colors = colors, colorIdentity = colorIdentity, oracleText = oracleText,
        imageUrl = imageUrl, power = power, toughness = toughness,
        // No legality tags at all: not known (an old or hand-made row), rather than "not legal anywhere".
        legalities = if (legal.isEmpty()) emptyMap() else TAGGED_FORMATS.associateWith { if (it in legal) "legal" else "not_legal" },
    )
}

/**
 * Card data for deck card names, keyed by [DeckNames.key]: from your collection first,
 * then Scryfall (by name, in batches). Names Scryfall can't find, or all of them when offline, are left out.
 * With a [format], cards whose legality in it isn't known yet are asked of Scryfall in the same batches.
 */
suspend fun CollectionRepository.deckCardInfo(names: Collection<String>, format: String = ""): Map<String, DeckCardInfo> {
    val wanted = names.associateBy { DeckNames.key(it) }.filterKeys { it.isNotEmpty() }
    val out = HashMap<String, DeckCardInfo>()
    val owned = observeAll().first().groupBy { DeckNames.key(it.name) }
    val missing = mutableListOf<String>()
    for ((k, name) in wanted) {
        if (k in unknownNames) continue
        val info = cardCache[k] ?: owned[k]?.firstOrNull()?.deckInfo()?.also { cardCache[k] = it }
        if (info != null) out[k] = info
        val needsLegality = info != null && format.isNotEmpty() && format !in info.legalities && k !in noLegality
        if (info == null || needsLegality) missing += name
    }
    if (missing.isNotEmpty()) {
        // "A // B" names are tried in full, then by the front face (see PrintingLookup).
        // Offline is a failure (nothing found), but a cancelled caller stays cancelled.
        val found = com.mtgscanner.util.suspendRunCatching {
            PrintingLookup.resolve(missing.map { PrintingLookup.Wanted(it) }) { ids -> api.identify(ids.map(PrintingLookup::json)) }
        }.getOrNull()
        missing.zip(found ?: emptyList()).forEach { (n, c) ->
            val k = DeckNames.key(n)
            val had = out[k]
            when {
                c == null && had != null -> noLegality += k
                c == null -> unknownNames += k
                // A saved card keeps its own picture; only the legality and links are added.
                had != null -> {
                    val info = had.copy(legalities = c.legalities, edhrec = had.edhrec ?: c.relatedUris["edhrec"])
                    cardCache[k] = info
                    out[k] = info
                }
                else -> {
                    val info = DeckCardInfo(
                        name = c.name, cmc = c.cmc, manaCost = c.effectiveManaCost, typeLine = c.effectiveTypeLine,
                        colors = c.effectiveColors.joinToString(""), colorIdentity = c.colorIdentity.joinToString(""),
                        oracleText = c.effectiveOracleText, imageUrl = c.imageUrl, edhrec = c.relatedUris["edhrec"],
                        legalities = c.legalities,
                        power = c.power ?: c.cardFaces?.firstOrNull()?.power,
                        toughness = c.toughness ?: c.cardFaces?.firstOrNull()?.toughness,
                    )
                    cardCache[k] = info
                    out[k] = info
                }
            }
        }
    }
    return out
}
