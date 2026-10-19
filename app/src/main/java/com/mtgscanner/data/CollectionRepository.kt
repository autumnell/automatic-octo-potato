package com.mtgscanner.data

import com.mtgscanner.data.db.CardDao
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.scan.CardTextGuess
import com.mtgscanner.scan.CardTextParser
import kotlinx.coroutines.flow.Flow

/** A Scryfall match for what the camera read, and whether it's safe to add without asking. */
data class Identification(val card: ScryfallCard, val confident: Boolean)

class CollectionRepository(
    private val dao: CardDao,
    val api: ScryfallApi,
) {
    fun observeAll(): Flow<List<CollectionCard>> = dao.observeAll()
    fun observe(id: Long): Flow<CollectionCard?> = dao.observe(id)

    private val lookupCache = HashMap<String, Identification?>()

    /**
     * Finds the card for an OCR guess. Tries the exact printing (set + collector number) first,
     * then a fuzzy name search limited to the set, then a fuzzy search across all sets.
     */
    suspend fun identify(guess: CardTextGuess): Identification? {
        val key = guess.key ?: return null
        synchronized(lookupCache) { if (lookupCache.containsKey(key)) return lookupCache[key] }

        val result = doIdentify(guess)
        synchronized(lookupCache) { lookupCache[key] = result }
        return result
    }

    private suspend fun doIdentify(guess: CardTextGuess): Identification? {
        val names = guess.nameCandidates
        fun bestSimilarity(card: ScryfallCard): Double {
            val cardNames = listOf(card.name) + card.name.split(" // ")
            return names.maxOfOrNull { n -> cardNames.maxOf { CardTextParser.similarity(n, it) } } ?: 0.0
        }

        if (guess.setCode != null && guess.collectorNumber != null) {
            val card = runCatching { api.bySetAndNumber(guess.setCode, guess.collectorNumber) }.getOrNull()
            if (card != null) {
                val sim = bestSimilarity(card)
                // If we couldn't read a name, trust the footer; otherwise the name has to agree.
                if (names.isEmpty() || sim >= 0.6) return Identification(card, confident = true)
            }
        }

        for (name in names) {
            val inSet = guess.setCode?.let { set -> runCatching { api.named(name, set) }.getOrNull() }
            val card = inSet ?: api.named(name) ?: continue
            val sim = bestSimilarity(card)
            if (sim < 0.5) continue
            // Fuzzy search returns the newest printing; if we know the collector number, pick that one.
            val exact = if (inSet == null && guess.collectorNumber != null) {
                runCatching { api.printings(card) }.getOrNull()
                    ?.firstOrNull { it.collectorNumber.equals(guess.collectorNumber, ignoreCase = true) }
            } else null
            return Identification(exact ?: card, confident = sim >= 0.85)
        }
        return null
    }

    /** Adds [qty] copies, merging with an existing entry for the same printing and finish. Returns the row id. */
    suspend fun add(card: ScryfallCard, foil: Boolean, qty: Int = 1): Long {
        val now = System.currentTimeMillis()
        val existing = dao.find(card.id, foil)
        if (existing != null) {
            dao.update(existing.copy(quantity = existing.quantity + qty, updatedAt = now))
            return existing.id
        }
        return dao.insert(card.toEntity(foil, qty, now))
    }

    /** Removes [qty] copies (used for undo). Deletes the row when it reaches zero. */
    suspend fun remove(id: Long, qty: Int = 1) {
        val c = dao.get(id) ?: return
        if (c.quantity <= qty) dao.delete(c)
        else dao.update(c.copy(quantity = c.quantity - qty, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setQuantity(id: Long, qty: Int) {
        val c = dao.get(id) ?: return
        if (qty <= 0) dao.delete(c) else dao.update(c.copy(quantity = qty, updatedAt = System.currentTimeMillis()))
    }

    suspend fun update(card: CollectionCard) = dao.update(card.copy(updatedAt = System.currentTimeMillis()))

    /** Switches foil on/off, merging into the other entry if one already exists. Returns the surviving id. */
    suspend fun setFoil(id: Long, foil: Boolean): Long {
        val c = dao.get(id) ?: return id
        if (c.foil == foil) return id
        val other = dao.find(c.scryfallId, foil)
        return if (other != null) {
            dao.update(other.copy(quantity = other.quantity + c.quantity, updatedAt = System.currentTimeMillis()))
            dao.delete(c)
            other.id
        } else {
            dao.update(c.copy(foil = foil, updatedAt = System.currentTimeMillis()))
            id
        }
    }

    suspend fun delete(id: Long) { dao.get(id)?.let { dao.delete(it) } }

    /** Pulls today's prices from Scryfall for every card in the collection. Returns how many were updated. */
    suspend fun refreshPrices(): Int {
        val all = dao.all()
        if (all.isEmpty()) return 0
        val fresh = api.collection(all.map { it.scryfallId }).associateBy { it.id }
        val updated = all.mapNotNull { c ->
            val f = fresh[c.scryfallId] ?: return@mapNotNull null
            c.copy(
                priceUsd = f.prices.usd?.toDoubleOrNull(),
                priceUsdFoil = (f.prices.usdFoil ?: f.prices.usdEtched)?.toDoubleOrNull(),
            )
        }
        dao.updateAll(updated)
        return updated.size
    }

    suspend fun allCards(): List<CollectionCard> = dao.all()
}

/** Tags come from Scryfall data: keywords plus the words in the type line. */
fun ScryfallCard.scryfallTags(): List<String> {
    val typeWords = effectiveTypeLine
        .split("//", "—")
        .flatMap { it.trim().split(' ') }
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.first().isUpperCase() }
    val formats = legalities.filterValues { it == "legal" }.keys
        .filter { it in setOf("standard", "pioneer", "modern", "commander", "pauper") }
        .map { "legal:$it" }
    return (keywords + typeWords + formats).distinct()
}

fun ScryfallCard.toEntity(foil: Boolean, qty: Int, now: Long) = CollectionCard(
    scryfallId = id,
    oracleId = oracleId,
    name = name,
    setCode = set.uppercase(),
    setName = setName,
    collectorNumber = collectorNumber,
    rarity = rarity,
    typeLine = effectiveTypeLine,
    manaCost = effectiveManaCost,
    cmc = cmc,
    colors = effectiveColors.joinToString(""),
    colorIdentity = colorIdentity.joinToString(""),
    oracleText = effectiveOracleText,
    tags = scryfallTags().joinToString(","),
    priceUsd = prices.usd?.toDoubleOrNull(),
    priceUsdFoil = (prices.usdFoil ?: prices.usdEtched)?.toDoubleOrNull(),
    imageUrl = imageUrl,
    smallImageUrl = smallImageUrl,
    scryfallUri = scryfallUri,
    lang = lang,
    releasedAt = releasedAt,
    artist = artist,
    foil = foil,
    quantity = qty,
    addedAt = now,
    updatedAt = now,
)
