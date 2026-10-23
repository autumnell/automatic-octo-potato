package com.mtgscanner.ui.collection

import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.AppDatabase
import com.mtgscanner.data.db.PricePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The last 30 days of price history for the cards on screen, for the sparklines next to prices.
 * Read in batches by printing and kept in memory, so scrolling and filtering don't touch the database.
 */
object PriceTrends {
    const val DAYS = 30L
    /** Forget the cache after a while so new prices show up. */
    private const val MAX_AGE_MS = 10 * 60_000L

    private val lock = Mutex()
    private val cache = HashMap<String, List<PricePoint>>()
    private var cacheDay = ""
    private var cacheAt = 0L

    /** History by Scryfall id for [ids], oldest first. Unknown ids get an empty list. */
    suspend fun history(repo: CollectionRepository, ids: Set<String>): Map<String, List<PricePoint>> = lock.withLock {
        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        if (today != cacheDay || now - cacheAt > MAX_AGE_MS) {
            cache.clear()
            cacheDay = today
            cacheAt = now
        }
        val missing = ids.filter { it !in cache }
        if (missing.isNotEmpty()) {
            val since = LocalDate.now().minusDays(DAYS).toString()
            val found = withContext(Dispatchers.IO) { runCatching { query(repo.db, missing, since) }.getOrDefault(emptyMap()) }
            missing.forEach { cache[it] = found[it].orEmpty() }
        }
        ids.associateWith { cache[it].orEmpty() }
    }

    private fun query(db: AppDatabase, ids: List<String>, since: String): Map<String, List<PricePoint>> {
        val out = HashMap<String, MutableList<PricePoint>>()
        // SQLite allows 999 arguments per query.
        ids.chunked(500).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            val args: Array<Any?> = arrayOf<Any?>(since, *chunk.toTypedArray())
            db.query(
                "SELECT scryfallId, day, usd, usdFoil FROM price_history WHERE day >= ? AND scryfallId IN ($marks) ORDER BY day",
                args,
            ).use { c ->
                while (c.moveToNext()) {
                    val p = PricePoint(
                        scryfallId = c.getString(0),
                        day = c.getString(1),
                        usd = if (c.isNull(2)) null else c.getDouble(2),
                        usdFoil = if (c.isNull(3)) null else c.getDouble(3),
                    )
                    out.getOrPut(p.scryfallId) { mutableListOf() }.add(p)
                }
            }
        }
        return out
    }
}
