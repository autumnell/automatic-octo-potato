package com.mtgscanner.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** What counts as "new since last time". Kept free of Android so it can be tested. */
object FreshRule {
    /** A move of at least this share of the old price… */
    const val SHARE = 0.2
    /** …and at least this many dollars a copy. */
    const val DOLLARS = 1.0

    /**
     * True when the card's price moved a lot since it was last seen, or it arrived from elsewhere (a restore or an import
     * from another phone keeps its old added date, so it is older than the last look but wasn't there).
     * Cards scanned here since then are newer than the last look and get no mark: you just added them.
     */
    fun isFresh(id: Long, addedAt: Long, price: Double?, seen: Map<Long, Double>, seenAt: Long): Boolean {
        if (seenAt == 0L) return false
        if (id !in seen) return addedAt in 1 until seenAt
        val was = seen[id] ?: return false
        if (was <= 0.0 || price == null) return false
        val move = abs(price - was)
        return move >= DOLLARS && move >= was * SHARE
    }
}

/**
 * The price of every card when it was last on screen in Browse, saved to a small file. Cards whose price moved a lot
 * since, or that came in from another device, get a dot until they've been looked at again.
 */
object SeenCards {
    private const val FILE = "seen-cards.txt"

    /** Card id → price last seen (-1 when it had none). */
    private var seen: Map<Long, Double> = emptyMap()
    /** When [seen] was last saved; 0 before the first save, when nothing is marked. */
    var seenAt by mutableStateOf(0L)
        private set
    private var loaded = false

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        val f = File(context.filesDir, FILE)
        val lines = runCatching { if (f.exists()) f.readLines() else emptyList() }.getOrDefault(emptyList())
        val at = lines.firstOrNull()?.toLongOrNull() ?: 0L
        seen = lines.drop(1).mapNotNull { l ->
            val parts = l.split(' ')
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            id to (parts.getOrNull(1)?.toDoubleOrNull() ?: -1.0)
        }.toMap()
        loaded = true
        seenAt = at
    }

    /** Ids among [cards] to mark. */
    fun fresh(cards: List<CollectionCard>): Set<Long> {
        if (!loaded) return emptySet()
        val s = seen
        val at = seenAt
        return cards.filter { FreshRule.isFresh(it.id, it.addedAt, it.unitPrice(), s, at) }.map { it.id }.toSet()
    }

    /** [cards] were on screen: remember their prices now, keeping what's known for every other card. */
    suspend fun markSeen(context: Context, cards: List<CollectionCard>, all: List<CollectionCard>) = withContext(Dispatchers.IO) {
        if (!loaded) load(context)
        val live = all.map { it.id }.toHashSet()
        val next = HashMap<Long, Double>(all.size)
        // The first save takes in the whole collection, so nothing is marked on the first visit.
        val first = seenAt == 0L
        for (c in all) {
            val keep = seen[c.id]
            if (first || keep == null && c.addedAt >= seenAt) next[c.id] = c.unitPrice() ?: -1.0
            else if (keep != null) next[c.id] = keep
        }
        for (c in cards) if (c.id in live) next[c.id] = c.unitPrice() ?: -1.0
        val now = System.currentTimeMillis()
        runCatching {
            File(context.filesDir, FILE).writeText(buildString {
                append(now).append('\n')
                next.forEach { (id, p) -> append(id).append(' ').append(p).append('\n') }
            })
        }
        seen = next
        seenAt = now
    }
}
