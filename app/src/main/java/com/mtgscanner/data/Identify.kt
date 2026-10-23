package com.mtgscanner.data

import com.mtgscanner.scan.CardTextGuess
import com.mtgscanner.scan.CardTextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** A Scryfall match for what the camera read, and whether it's safe to add without asking. */
data class Identification(
    val card: ScryfallCard,
    val confident: Boolean,
    /** The exact printing is known: the footer was read, or the artwork matched. */
    val exact: Boolean = false,
)

/** A card name read with no connection, good enough to look up later. */
data class OfflineRead(
    val name: String, val setCode: String?, val collectorNumber: String?,
    /** Language read off the card, when not English. */
    val lang: String? = null,
    /** The exact printing, when the artwork matched a downloaded art index. */
    val scryfallId: String? = null,
)

// Identifying scans

/**
 * Finds the card for an OCR guess. Tries the exact printing (set + collector number) first,
 * then the card name (corrected against the offline name list), and uses the artwork to pick
 * the printing when the footer couldn't be read. Throws [IOException] when offline.
 */
suspend fun CollectionRepository.identify(raw: CardTextGuess, skip: ((ScryfallCard) -> Boolean)? = null): Identification? {
    // With the set lock on, a read with no set code is looked up in that set first. A set code printed on the
    // card wins (Special Guests and The List carry their own).
    val lock = SortPrefs.setLock?.takeIf { raw.setCode == null }
    val guess = lock?.let { raw.copy(setCode = it) } ?: raw
    // [skip]: a card auto-add will skip anyway (say, a basic land) isn't narrowed to its printing.
    val key = (guess.key ?: return null) + (lock?.let { "@$it" } ?: "") + (if (skip != null) "|s" else "")
    val cached: Identification? = lookupCache.get(key)
    if (cached == null) {
        val missedAt = synchronized(missCache) { missCache[key] }
        if (missedAt != null && System.currentTimeMillis() - missedAt < CollectionRepository.MISS_TTL_MS) return null
    }
    val base = cached ?: doIdentify(guess, skip).also { r ->
        if (r != null) lookupCache.put(key, r)
        else synchronized(missCache) {
            if (missCache.size > 500) missCache.clear()
            missCache[key] = System.currentTimeMillis()
        }
    }
    base ?: return null
    if (guess.collectorNumber == null && skip?.invoke(base.card) != true) choosePrinting(base, guess)?.let { return it }
    return base
}

/**
 * The footer's number wasn't read, so the name alone gave Scryfall's newest printing. Narrows the printings
 * by the copyright year, then the set symbol, then the artwork; with only the year to go on, the newest
 * printing from that year. Null when nothing narrows it (keep [base]).
 */
private suspend fun CollectionRepository.choosePrinting(base: Identification, guess: CardTextGuess): Identification? {
    val year = guess.year
    val symbol = guess.symbol
    val hash = guess.artHash
    val embedding = guess.artEmbedding
    if (year == null && symbol == null && hash == null && embedding == null) return null
    val all = runCatching { printingsCached(base.card) }.getOrNull() ?: return null
    // With the set code read (or the set lock standing in for it), only that set's printings are candidates.
    val set = guess.setCode
    var pool = set?.let { s -> all.filter { it.set.equals(s, ignoreCase = true) }.ifEmpty { all } } ?: all
    if (pool.size < 2) return null
    pool = PrintingPick.newestFirst(pool) { it.releasedAt }
    var narrowed = false
    if (year != null) {
        val inYear = PrintingPick.byYear(pool, year) { it.releasedAt }
        if (inYear.isNotEmpty()) { pool = inYear; narrowed = true }
    }
    var bySymbol = false
    val sets = pool.map { it.set.lowercase() }.distinct()
    if (symbol != null && sets.size > 1) {
        val winners = runCatching { setSymbols.pick(symbol, sets) }.getOrNull()
        if (winners != null) {
            pool = pool.filter { it.set.lowercase() in winners }
            narrowed = true
            bySymbol = true
        }
    }
    if (pool.size > 1 && (hash != null || embedding != null)) {
        runCatching { art.bestPrinting(pool, hash, embedding) }.getOrNull()?.let { return base.copy(card = it, exact = true) }
    }
    if (!narrowed) return null
    val pick = pool.first()
    return base.copy(card = pick, exact = base.exact || (bySymbol && pool.size == 1))
}

/**
 * Printings sharing the collector number read, narrowed by the copyright year, the set symbol and the art.
 * Returns the pick and whether it's sure (one printing left, or the art matched).
 */
private suspend fun CollectionRepository.narrowSameNumber(same: List<ScryfallCard>, guess: CardTextGuess): Pair<ScryfallCard, Boolean> {
    if (same.size == 1) return same[0] to true
    var pool = PrintingPick.newestFirst(same) { it.releasedAt }
    guess.year?.let { y -> PrintingPick.byYear(pool, y) { it.releasedAt }.takeIf { it.isNotEmpty() }?.let { pool = it } }
    val symbol = guess.symbol
    val sets = pool.map { it.set.lowercase() }.distinct()
    if (pool.size > 1 && symbol != null && sets.size > 1) {
        runCatching { setSymbols.pick(symbol, sets) }.getOrNull()?.let { winners ->
            pool.filter { it.set.lowercase() in winners }.takeIf { it.isNotEmpty() }?.let { pool = it }
        }
    }
    if (pool.size > 1 && (guess.artHash != null || guess.artEmbedding != null)) {
        runCatching { art.bestPrinting(pool, guess.artHash, guess.artEmbedding) }.getOrNull()?.let { return it to true }
    }
    return pool.first() to (pool.size == 1)
}

private suspend fun CollectionRepository.printingsCached(card: ScryfallCard): List<ScryfallCard> {
    val k = card.oracleId ?: card.name
    printingsCache.get(k)?.let { return it }
    val list = api.printings(card)
    printingsCache.put(k, list)
    return list
}

/** What to save for later when the camera read a name but Scryfall can't be reached. */
suspend fun CollectionRepository.offlineRead(guess: CardTextGuess): OfflineRead? {
    val best = withContext(Dispatchers.Default) { nameIndex.correct(guess.nameCandidates) }.firstOrNull()?.takeIf { it.score >= 0.85 }
    if (best != null) return OfflineRead(best.name, guess.setCode, guess.collectorNumber, lang = guess.lang)
    // No English name (only the footer, or a name in Japanese, Chinese or Korean): set and number find it later.
    val set = guess.setCode ?: return null
    val number = guess.collectorNumber ?: return null
    return OfflineRead(guess.printedName ?: "${set.uppercase()} #$number", set, number, lang = guess.lang)
}

private suspend fun CollectionRepository.doIdentify(guess: CardTextGuess, skip: ((ScryfallCard) -> Boolean)? = null): Identification? {
    // Real card names close to what was read come first, then the raw text.
    // Comparing against every card name is heavy: keep it off the main thread.
    val corrected = withContext(Dispatchers.Default) { nameIndex.correct(guess.nameCandidates) }
    val names = (corrected.map { it.name } + guess.nameCandidates).distinct()
    fun bestSimilarity(card: ScryfallCard): Double {
        val cardNames = listOf(card.name) + card.name.split(" // ")
        return names.maxOfOrNull { n -> cardNames.maxOf { CardTextParser.similarity(n, it) } } ?: 0.0
    }

    if (guess.setCode != null && guess.collectorNumber != null) {
        val card = runCatching { api.bySetAndNumber(guess.setCode, guess.collectorNumber) }
            .onFailure { if (it is IOException && it !is ScryfallException) throw it }
            .getOrNull()
        if (card != null) {
            val sim = bestSimilarity(card)
            // If we couldn't read a name, trust the footer; otherwise the name has to agree.
            if (names.isEmpty() || sim >= 0.6) return Identification(card, confident = true, exact = true)
        }
        // A token prints its card set's code: the number may be in the token set ("tmom") instead.
        if (names.isNotEmpty()) {
            val token = runCatching { api.bySetAndNumber("t" + guess.setCode, guess.collectorNumber) }
                .onFailure { if (it is IOException && it !is ScryfallException) throw it }
                .getOrNull()
            if (token != null && bestSimilarity(token) >= 0.6) return Identification(token, confident = true, exact = true)
        }
    }

    // The name read is a token's or emblem's: look in the extras, which the name lookup is not made for.
    corrected.firstOrNull()?.takeIf { it.extra && it.score >= 0.85 }?.let { t ->
        runCatching { findExtra(t.name, guess) }
            .onFailure { if (it is IOException && it !is ScryfallException) throw it }
            .getOrNull()?.let { return it }
    }

    val knownGood = corrected.firstOrNull()?.takeIf { it.score >= 0.85 }?.name
    for (name in names) {
        val inSet = guess.setCode?.let { set -> runCatching { api.named(name, set) }.getOrNull() }
        val card = inSet ?: api.named(name) ?: continue
        val sim = bestSimilarity(card)
        if (sim < 0.5) continue
        val confident = sim >= 0.85 || card.name == knownGood
        // Skipped anyway (a basic land with "Skip basics" on): don't search its many printings.
        if (skip?.invoke(card) == true) return Identification(card, confident = confident)
        // Fuzzy search returns the newest printing; with the collector number but no set code (2003-2019 cards
        // print "241/350"), several sets can have that number: narrow them, and it's exact only if one is left.
        val number = guess.collectorNumber
        if (inSet == null && number != null) {
            val same = runCatching { printingsCached(card) }.getOrNull().orEmpty()
                .filter { it.collectorNumber.equals(number, ignoreCase = true) }
            if (same.isNotEmpty()) {
                val (pick, sure) = narrowSameNumber(same, guess)
                return Identification(pick, confident = confident, exact = sure)
            }
        }
        return Identification(card, confident = confident, exact = inSet != null && number != null)
    }
    guess.printedName?.let { printed -> return identifyPrinted(printed, guess) }
    return null
}

/**
 * A name read in Japanese, Chinese or Korean: searched among Scryfall's printed names in that language, the
 * whole name first, then each half (one misread letter spoils a search), and the closest printed name taken
 * when it's close enough. The English printing with the same set and number is used when there is one, so
 * prices, art indexes and printing choice work as for any card; the language read is recorded with it.
 */
private suspend fun CollectionRepository.identifyPrinted(printed: String, guess: CardTextGuess): Identification? {
    val lang = guess.lang?.takeIf { it in com.mtgscanner.scan.CjkNames.LANGS }
    for (term in com.mtgscanner.scan.CjkNames.searchTerms(printed)) {
        val found = runCatching { api.searchPrinted(term, lang) }
            .onFailure { if (it is IOException && it !is ScryfallException) throw it }
            .getOrNull().orEmpty()
        if (found.isEmpty()) continue
        val names = found.map { c -> c to (c.printedName ?: c.cardFaces.orEmpty().firstNotNullOfOrNull { it.printedName }) }
        val (card, score) = com.mtgscanner.scan.CjkNames.best(printed, names) ?: continue
        val english = if (card.lang != "en") {
            runCatching { api.bySetAndNumber(card.set, card.collectorNumber) }
                .onFailure { if (it is IOException && it !is ScryfallException) throw it }
                .getOrNull()?.takeIf { it.oracleId == null || it.oracleId == card.oracleId }
        } else null
        return Identification(english ?: card, confident = score >= 0.85)
    }
    return null
}
