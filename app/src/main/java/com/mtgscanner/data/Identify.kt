package com.mtgscanner.data

import com.mtgscanner.scan.CardTextGuess
import com.mtgscanner.scan.CardTextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** A Scryfall match for what the camera read, and whether it's safe to add without asking. */
data class Identification(
    val card: ScryfallCard,
    val confident: Boolean,
    /** The exact printing is known: the footer was read, or the artwork matched. */
    val exact: Boolean = false,
    /** You picked this printing for what was read: it's kept as it is, never narrowed again. */
    val picked: Boolean = false,
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
    val (guess, key) = lookupKey(raw, skip != null) ?: return null
    // Sorting one set: have its whole card list on the phone, so each card is found without asking Scryfall.
    SortPrefs.setLock?.let { preloadSet(it) }
    // Seen this session, or on this phone in the last few days (prices that old are fine for a scan).
    val cached: Identification? = lookupCache.get(key)
        ?: identifyStore.get(key, IdentifyStore.FRESH_MS)?.also { lookupCache.put(key, it) }
        // A printing you picked stays picked; only its prices are refreshed.
        ?: identifyStore.get(key, Long.MAX_VALUE)?.takeIf { it.picked }?.also { lookupCache.put(key, it); freshenLater(it.card.id) }
    if (cached == null) {
        val missedAt = synchronized(missCache) { missCache[key] }
        if (missedAt != null && System.currentTimeMillis() - missedAt < CollectionRepository.MISS_TTL_MS) return null
    }
    val base = cached ?: try {
        sharedLookup(key) { doIdentify(guess, skip) }
    } catch (e: IOException) {
        // Offline: an older saved match is better than none.
        if (e is ScryfallException) throw e
        identifyStore.get(key, Long.MAX_VALUE) ?: throw e
    }
    base ?: return null
    noteSet(base.card.set)
    if (base.picked) return base
    val result = (if (guess.collectorNumber == null && skip?.invoke(base.card) != true) choosePrinting(base, guess) else null) ?: base
    // Matched from the list on the phone with prices a few days old: today's follow in the background.
    if (localCards.isStale(result.card.id)) freshenLater(result.card.id)
    return result
}

/**
 * The guess as it's looked up, and the key its match is saved under. A misread set code ("0TJ") is corrected to
 * a real one first, so the exact footer lookup still works. With the set lock on, a read with no set code is
 * looked up in that set first; a set code printed on the card wins (Special Guests and The List carry their own).
 * [skipping]: a card auto-add will skip anyway (say, a basic land) isn't narrowed to its printing, so it's saved apart.
 */
private fun CollectionRepository.lookupKey(raw: CardTextGuess, skipping: Boolean): Pair<CardTextGuess, String>? {
    val read = raw.setCode?.let { s -> raw.copy(setCode = setCodes.fix(s, raw.collectorNumber)) } ?: raw
    val lock = SortPrefs.setLock?.takeIf { read.setCode == null }
    val guess = lock?.let { read.copy(setCode = it) } ?: read
    val key = (guess.key ?: return null) + (lock?.let { "@$it" } ?: "") + (if (skipping) "|s" else "")
    return guess to key
}

/**
 * You picked [card] as the right printing for what was read as [raw]: the same read gives it from now on, in this
 * session and after a restart, instead of the match that was wrong.
 */
suspend fun CollectionRepository.rememberPick(raw: CardTextGuess, skipping: Boolean, card: ScryfallCard) {
    val (_, key) = lookupKey(raw, skipping) ?: return
    val pick = Identification(card, confident = true, exact = true, picked = true)
    lookupCache.put(key, pick)
    synchronized(missCache) { missCache.remove(key) }
    identifyStore.put(key, pick)
}

/**
 * The printing with that set code and number: from the card list on the phone when it's there, so there's no wait.
 * Scryfall is asked only when the list is missing or doesn't know the set yet (a set newer than the list).
 */
private suspend fun CollectionRepository.cardAt(set: String, number: String): ScryfallCard? {
    if (localCards.ready) {
        val local = withContext(Dispatchers.IO) { localCards.bySetAndNumber(set, number) ?: if (localCards.hasSet(set)) MISSING else null }
        if (local === MISSING) return null
        if (local != null) return local
    }
    return api.bySetAndNumber(set, number)
}

/** The printing a real card name stands for, from the phone when the list is there; else Scryfall's name lookup. */
private suspend fun CollectionRepository.cardNamed(name: String, set: String? = null): ScryfallCard? {
    if (localCards.ready) withContext(Dispatchers.IO) { localCards.named(name, set) }?.let { return it }
    return api.named(name, set)
}

/** Stands for "the list knows the set, and the card isn't in it". */
private val MISSING = ScryfallCard(id = "", name = "", set = "", setName = "", collectorNumber = "", rarity = "")

/** A run of [SET_RUN] cards from one set loads that set's list (see [preloadSet]). */
private fun CollectionRepository.noteSet(set: String) {
    val code = set.lowercase()
    val run = synchronized(recentSets) {
        recentSets.addLast(code)
        while (recentSets.size > SET_RUN) recentSets.removeFirst()
        recentSets.size == SET_RUN && recentSets.all { it == code }
    }
    if (run) preloadSet(code)
}

private const val SET_RUN = 3

/**
 * Loads a whole set's cards in the background (a few requests), from the phone when saved in the last few days.
 * While loaded, cards from that set are found without asking Scryfall, even offline.
 */
fun CollectionRepository.preloadSet(set: String) {
    val code = set.lowercase()
    if (setCards.get(code) != null) return
    if (!synchronized(setsLoading) { setsLoading.add(code) }) return
    appScope.launch {
        try {
            val onPhone = if (localCards.ready) withContext(Dispatchers.IO) { localCards.inSet(code) }.takeIf { it.isNotEmpty() } else null
            if (onPhone != null) { setCards.put(code, onPhone); return@launch }
            val saved = cardLists.get("set:$code")
            val list = saved ?: runCatching { api.setCards(code) }.getOrNull()?.also { if (it.isNotEmpty()) cardLists.put("set:$code", it) }
                ?: cardLists.get("set:$code", Long.MAX_VALUE)
            if (!list.isNullOrEmpty()) setCards.put(code, list)
        } finally {
            synchronized(setsLoading) { setsLoading.remove(code) }
        }
    }
}

/** The loaded list of [set]'s cards, or null when it isn't loaded. */
private fun CollectionRepository.loadedSet(set: String?): List<ScryfallCard>? = set?.let { setCards.get(it.lowercase()) }

/**
 * Starts looking up a first good read in the background, so the lookup made once a second frame agrees finds the
 * answer ready (or joins the one under way). Errors are left for that lookup to see.
 */
fun CollectionRepository.prefetch(raw: CardTextGuess, skip: ((ScryfallCard) -> Boolean)? = null) {
    appScope.launch { runCatching { identify(raw, skip) } }
}

/**
 * Runs [block] for [key] once: a second caller while it runs waits for the same answer. The answer is cached
 * (sure matches on the phone too) before anyone gets it.
 */
private suspend fun CollectionRepository.sharedLookup(key: String, block: suspend () -> Identification?): Identification? {
    val job = synchronized(inFlight) {
        inFlight[key] ?: appScope.async {
            block().also { r ->
                if (r != null) {
                    lookupCache.put(key, r)
                    identifyStore.put(key, r)
                } else synchronized(missCache) {
                    if (missCache.size > 500) missCache.clear()
                    missCache[key] = System.currentTimeMillis()
                }
            }
        }.also { d ->
            inFlight[key] = d
            d.invokeOnCompletion { synchronized(inFlight) { if (inFlight[key] === d) inFlight.remove(key) } }
        }
    }
    return job.await()
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
    if (year == null && symbol == null && hash == null && embedding == null && guess.cardPrint == null && guess.frameLook == null) return null
    // With the set code read (or the set lock standing in for it), only that set's printings are candidates.
    val set = guess.setCode
    // From the loaded set when there is one; else from Scryfall, only around the year read when one was, so a
    // card with hundreds of printings (basic lands) is one short search.
    val inLoaded = loadedSet(set)?.filter { c -> c.oracleId != null && c.oracleId == base.card.oracleId }?.takeIf { it.isNotEmpty() }
    val all = inLoaded ?: runCatching {
        val near = year?.takeIf { set == null }?.let { y -> printingsCached(base.card, "year>=${y - 1} year<=${y + 1}") }
        near?.takeIf { it.isNotEmpty() } ?: printingsCached(base.card)
    }.getOrNull() ?: return null
    // A name was read, so the card has a title: never an Art Series card.
    val real = if (LocalPick.isArtCard(base.card)) all else LocalPick.realCards(all)
    var pool = set?.let { s -> real.filter { it.set.equals(s, ignoreCase = true) }.ifEmpty { real } } ?: real
    if (pool.size < 2) return null
    // The card picture model tells printings apart better than the year or symbol read: it goes first.
    com.mtgscanner.scan.CardPrints.pick(guess.cardPrint, pool)?.let { return base.copy(card = it, exact = true) }
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
        runCatching { art.bestPrinting(pool, hash, embedding, guess.artPHash) }.getOrNull()?.let { return base.copy(card = it, exact = true) }
    }
    // Printings the art can't tell apart: by footer, set symbol and colours.
    if (pool.size > 1) runCatching { art.byLook(pool, guess.frameLook) }.getOrNull()?.let { return base.copy(card = it, exact = true) }
    if (!narrowed) return null
    val pick = pool.first()
    return base.copy(card = pick, exact = base.exact || (bySymbol && pool.size == 1))
}

/**
 * Printings sharing the collector number read, narrowed by the copyright year, the set symbol and the art.
 * Returns the pick and whether it's sure (one printing left, or the art matched).
 */
private suspend fun CollectionRepository.narrowSameNumber(same: List<ScryfallCard>, guess: CardTextGuess): Pair<ScryfallCard, Boolean> {
    val real = LocalPick.realCards(same)
    if (real.size == 1) return real[0] to true
    com.mtgscanner.scan.CardPrints.pick(guess.cardPrint, real)?.let { return it to true }
    var pool = PrintingPick.newestFirst(real) { it.releasedAt }
    guess.year?.let { y -> PrintingPick.byYear(pool, y) { it.releasedAt }.takeIf { it.isNotEmpty() }?.let { pool = it } }
    val symbol = guess.symbol
    val sets = pool.map { it.set.lowercase() }.distinct()
    if (pool.size > 1 && symbol != null && sets.size > 1) {
        runCatching { setSymbols.pick(symbol, sets) }.getOrNull()?.let { winners ->
            pool.filter { it.set.lowercase() in winners }.takeIf { it.isNotEmpty() }?.let { pool = it }
        }
    }
    if (pool.size > 1 && (guess.artHash != null || guess.artEmbedding != null)) {
        runCatching { art.bestPrinting(pool, guess.artHash, guess.artEmbedding, guess.artPHash) }.getOrNull()?.let { return it to true }
    }
    if (pool.size > 1) runCatching { art.byLook(pool, guess.frameLook) }.getOrNull()?.let { return it to true }
    return pool.first() to (pool.size == 1)
}

/**
 * A card's printings ([narrow]ed by extra search terms), from memory, then the phone (a few days old at most),
 * then Scryfall. Offline, an older saved list stands in.
 */
private suspend fun CollectionRepository.printingsCached(card: ScryfallCard, narrow: String = ""): List<ScryfallCard> {
    val k = "prints:" + (card.oracleId ?: card.name) + (if (narrow.isBlank()) "" else "|$narrow")
    printingsCache.get(k)?.let { return it }
    // Every printing is on the phone: the narrowing terms only shorten a search, and callers filter anyway.
    val oracle = card.oracleId
    if (oracle != null && localCards.ready) {
        withContext(Dispatchers.IO) { localCards.printings(oracle) }.takeIf { it.isNotEmpty() }?.let { printingsCache.put(k, it); return it }
    }
    cardLists.get(k)?.let { printingsCache.put(k, it); return it }
    val list = try {
        api.printingsOf(card.oracleId, card.name, narrow)
    } catch (e: IOException) {
        if (e is ScryfallException) throw e
        cardLists.get(k, Long.MAX_VALUE) ?: throw e
    }
    printingsCache.put(k, list)
    cardLists.put(k, list)
    return list
}

/** What to save for later when the camera read a name but Scryfall can't be reached. */
suspend fun CollectionRepository.offlineRead(raw: CardTextGuess): OfflineRead? {
    val guess = raw.setCode?.let { s -> raw.copy(setCode = setCodes.fix(s, raw.collectorNumber)) } ?: raw
    val best = withContext(Dispatchers.Default) { nameIndex.correct(guess.nameCandidates) }.firstOrNull()?.takeIf { it.score >= 0.85 }
    if (best != null) return OfflineRead(best.name, guess.setCode, guess.collectorNumber, lang = guess.lang)
    // No English name (only the footer, or a name in Japanese, Chinese or Korean): set and number find it later.
    val set = guess.setCode ?: return null
    val number = guess.collectorNumber ?: return null
    return OfflineRead(guess.printedName ?: "${set.uppercase()} #$number", set, number, lang = guess.lang)
}

/**
 * The card a footer alone names ([card], by set and number), checked against the card's picture: a misread digit
 * names another card of the same set. When the picture clearly shows another card of that set, that card; when it
 * clearly shows a card of another set, null (the next frame tries again); else [card].
 */
private suspend fun CollectionRepository.footerOnly(card: ScryfallCard, guess: CardTextGuess): Identification? {
    val hit = withContext(Dispatchers.Default) {
        runCatching { com.mtgscanner.scan.CardPrints.match(guess.cardPrint) }.getOrNull()
    } ?: return Identification(card, confident = true, exact = true)
    if (com.mtgscanner.util.CardNames.key(hit.name) == com.mtgscanner.util.CardNames.key(card.name)) return Identification(card, confident = true, exact = true)
    if (!hit.set.equals(card.set, ignoreCase = true)) return null
    val shown = runCatching { cardById(hit.id) }.getOrNull() ?: return null
    return Identification(shown, confident = true, exact = true)
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

    // The set is loaded (set lock, or a run of cards from it): look there first, no download needed.
    loadedSet(guess.setCode)?.let { local ->
        val number = guess.collectorNumber
        if (number != null) {
            local.firstOrNull { it.collectorNumber.equals(number, ignoreCase = true) }?.let { c ->
                if (names.isEmpty()) footerOnly(c, guess)?.let { return it }
                else if (bestSimilarity(c) >= 0.6) return Identification(c, confident = true, exact = true)
            }
        }
        if (names.isNotEmpty()) {
            val best = local.maxByOrNull { bestSimilarity(it) }
            if (best != null && bestSimilarity(best) >= 0.85 && skip?.invoke(best) != true) {
                return Identification(best, confident = true, exact = number != null && best.collectorNumber.equals(number, ignoreCase = true))
            }
        }
    }

    if (guess.setCode != null && guess.collectorNumber != null) {
        val card = runCatching { cardAt(guess.setCode, guess.collectorNumber) }
            .onFailure { if (it is IOException && it !is ScryfallException) throw it }
            .getOrNull()
        if (card != null) {
            val sim = bestSimilarity(card)
            // If we couldn't read a name, trust the footer unless the picture shows another card; otherwise the
            // name has to agree.
            if (names.isEmpty()) footerOnly(card, guess)?.let { return it }
            else if (sim >= 0.6) return Identification(card, confident = true, exact = true)
        }
        // A token prints its card set's code: the number may be in the token set ("tmom") instead.
        if (names.isNotEmpty()) {
            val token = runCatching { cardAt("t" + guess.setCode, guess.collectorNumber) }
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
        val inSet = guess.setCode?.let { set -> runCatching { cardNamed(name, set) }.getOrNull() }
        val card = inSet ?: cardNamed(name) ?: continue
        val sim = bestSimilarity(card)
        if (sim < 0.5) continue
        val confident = sim >= 0.85 || card.name == knownGood
        // Skipped anyway (a basic land with "Skip basics" on): don't search its many printings.
        if (skip?.invoke(card) == true) return Identification(card, confident = confident)
        // Fuzzy search returns the newest printing; with the collector number but no set code (2003-2019 cards
        // print "241/350"), several sets can have that number: narrow them, and it's exact only if one is left.
        val number = guess.collectorNumber
        if (inSet == null && number != null) {
            // Only the printings with that number: one short search instead of every printing.
            val same = runCatching { printingsCached(card, "cn:${number.lowercase()}") }.getOrNull().orEmpty()
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
