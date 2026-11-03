package com.mtgscanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * Art matching from the card list on the phone: the sets most of your collection is from get an art index on
 * Wi-Fi, built from the saved list (only the pictures are downloaded), and a card the art matches is read from
 * the list too, so a card with no readable name is found without Scryfall, even offline.
 */

/** How many of your biggest sets get art matching by themselves. */
const val ART_AUTO_SETS = 6

/** Queues art indexes for the sets you own most cards from, once a week, on Wi-Fi, when the card list is here. */
suspend fun CollectionRepository.artForBiggestSets() {
    // Knowing cards by their picture covers every printing: only new printings need adding, no art indexes.
    if (com.mtgscanner.scan.CardPrints.ready) {
        if (localCards.ready) {
            com.mtgscanner.scan.CardPrints.addNewPrintings(
                appContext, { day -> withContext(Dispatchers.IO) { localCards.releasedSince(day) } }, localCards.onWifi(),
            )
        }
        return
    }
    if (!localCards.artAuto || !localCards.ready || !localCards.onWifi()) return
    val today = LocalDate.now()
    val last = runCatching { LocalDate.parse(localCards.artCheckedDay) }.getOrNull()
    if (last != null && last.plusDays(7) > today) return
    val sets = ArtAutoPick.pick(dao.ownedSetCodes(), artIndex.sets.value.map { it.set }.toSet())
    localCards.artCheckedDay = today.toString()
    if (sets.isNotEmpty()) artIndex.enqueue(sets)
}

/** Which sets to index, kept free of Android so it can be tested. */
object ArtAutoPick {
    /** The first [n] of [owned] (most cards first) that aren't indexed yet, skipping tokens and odd codes. */
    fun pick(owned: List<String>, indexed: Set<String>, n: Int = ART_AUTO_SETS): List<String> =
        owned.asSequence().map { it.lowercase() }.filter { it.isNotBlank() && it.all(Char::isLetterOrDigit) }
            .take(n).filter { it !in indexed }.toList()
}

/** One printing by id: from the list on the phone when it has it, else Scryfall. */
suspend fun CollectionRepository.cardById(id: String): ScryfallCard? {
    if (localCards.ready) withContext(Dispatchers.IO) { localCards.byId(id) }?.let { return it }
    return api.byId(id)
}

/**
 * Every printing of [card], newest first: from the card list on the phone when it's there (at once, and offline),
 * else from Scryfall. Printings with prices a few days old get today's in the background.
 */
suspend fun CollectionRepository.printingsFor(card: ScryfallCard): List<ScryfallCard> {
    val oracle = card.oracleId
    if (oracle != null && localCards.ready) {
        val local = withContext(Dispatchers.IO) { localCards.printings(oracle) }
        if (local.isNotEmpty()) {
            local.filter { localCards.isStale(it.id) }.take(75).forEach { freshenLater(it.id) }
            return local.sortedByDescending { it.releasedAt ?: "" }
        }
    }
    return api.printings(card)
}

/**
 * The card a picture with no readable text shows: by the card picture model across every printing when it's on
 * ([com.mtgscanner.scan.CardPrints]), else by the downloaded art indexes. [onlySet] is the set lock. Scans
 * thousands of entries: call off the main thread.
 */
fun CollectionRepository.matchPicture(guess: com.mtgscanner.scan.CardTextGuess, onlySet: String?): com.mtgscanner.scan.ArtHit? {
    if (guess.artOnlyPrints.isNotEmpty()) {
        com.mtgscanner.scan.CardPrints.match(guess.artOnlyPrints.first(), guess.artOnlyPrints.drop(1), onlySet)?.let { h ->
            val g = com.mtgscanner.scan.CardPrints.gallery
            return com.mtgscanner.scan.ArtHit(
                h.id, h.name, h.set, h.number, 0, 0, h.score, standOut = h.standOut, lead = h.score - h.runnerUp,
                onlyPrinting = g?.onlyPicture(h.id) == true,
            )
        }
    }
    if (guess.artOnly.isEmpty()) return null
    // All the frame's embeddings come from one model; the store uses them only if it's still the chosen one.
    val embeddings = guess.artOnlyEmbeddings
    val model = embeddings.firstOrNull()?.model
    val p = guess.artOnlyP.filter { it != 0L }.toLongArray()
    return artIndex.match(guess.artOnly.toLongArray(), onlySet, embeddings.filter { it.model == model }.map { it.values }, model, p)
}
