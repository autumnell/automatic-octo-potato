package com.mtgscanner.data

import androidx.room.withTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * Cards matched from the list on the phone carry the prices of the day they were saved. When those are a few
 * days old, today's are fetched in the background, a few cards per request, after the card is already added:
 * the scan never waits for them.
 */

private val freshQueue = LinkedHashSet<String>()
private var freshRunning = false

/** Asks Scryfall for today's prices of [id] soon, together with other cards scanned around the same time. */
fun CollectionRepository.freshenLater(id: String) {
    val start = synchronized(freshQueue) {
        freshQueue += id
        (!freshRunning).also { freshRunning = true }
    }
    if (!start) return
    appScope.launch {
        try {
            while (true) {
                // A short pause gathers the cards of a quick run into one request, and lets the card be added first.
                delay(2000)
                val ids = synchronized(freshQueue) {
                    freshQueue.take(75).also { freshQueue.removeAll(it.toSet()) }
                }
                if (ids.isEmpty()) break
                val fresh = runCatching { api.collection(ids) }.getOrNull() ?: break
                localCards.update(fresh)
                savePrices(fresh)
            }
        } finally {
            synchronized(freshQueue) { freshRunning = false; freshQueue.clear() }
        }
    }
}

/** Today's prices on the collection's rows of those printings; rows added in the last few minutes also get them as "price when added". */
private suspend fun CollectionRepository.savePrices(fresh: List<ScryfallCard>) {
    if (fresh.isEmpty()) return
    val byId = fresh.associateBy { it.id }
    val recent = System.currentTimeMillis() - 15 * 60_000L
    val justAdded = observeAll().first().filter { it.scryfallId in byId && it.addedAt >= recent }
    db.withTransaction {
        fresh.forEach { f ->
            dao.setFreshPrinting(
                scryfallId = f.id,
                usd = f.prices.usd?.toDoubleOrNull(),
                foil = (f.prices.usdFoil ?: f.prices.usdEtched)?.toDoubleOrNull(),
                treatments = f.treatments.joinToString(","),
                eur = f.prices.eur?.toDoubleOrNull(),
                eurFoil = f.prices.eurFoil?.toDoubleOrNull(),
                power = f.power ?: f.cardFaces?.firstOrNull()?.power,
                toughness = f.toughness ?: f.cardFaces?.firstOrNull()?.toughness,
                reserved = f.reserved,
                rarity = f.rarity,
                tags = f.scryfallTags().joinToString(","),
            )
        }
        justAdded.forEach { c ->
            val p = byId.getValue(c.scryfallId).prices
            val now = (if (c.foil) (p.usdFoil ?: p.usdEtched ?: p.usd) else p.usd)?.toDoubleOrNull()
            if (now != null && c.purchasePrice == null) dao.get(c.id)?.let { row -> dao.update(row.copy(priceAtAdd = now)) }
        }
    }
}

/**
 * Today's prices for [id], straight from Scryfall (price check shows what a card is worth now, not what the
 * list on the phone saved). Saved to the list and to your copies too. Null when Scryfall can't be reached.
 */
suspend fun CollectionRepository.freshCard(id: String): ScryfallCard? {
    val fresh = runCatching { api.byId(id) }.getOrNull() ?: return null
    runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { localCards.update(listOf(fresh)) } }
    runCatching { savePrices(listOf(fresh)) }
    return fresh
}
