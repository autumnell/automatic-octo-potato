package com.mtgscanner.data

import com.mtgscanner.data.search.QNode
import com.mtgscanner.data.search.ScryfallQuery
import com.mtgscanner.data.search.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a Search query against the card list on the phone, for instant results and for searching offline.
 * Needs something to narrow by: a name, an exact name or a set (e:). Returns null when the list isn't on the
 * phone or the query can't be answered there (only Scryfall can, like "t:dragon" alone).
 * [unique] is Scryfall's: "cards" keeps one printing per card, "prints" keeps them all.
 */
suspend fun CollectionRepository.searchOnPhone(query: String, order: String, unique: String, limit: Int = 300): List<ScryfallCard>? =
    withContext(Dispatchers.IO) {
        val list = localCards
        if (!list.ready) return@withContext null
        val compiled = runCatching { ScryfallQuery.compile(query) }.getOrNull() ?: return@withContext null
        val plain = compiled.plain
        val root = compiled.root
        val candidates: List<ScryfallCard> = if (plain != null) {
            list.nameHas(plain.split(' ').filter { it.isNotBlank() })
        } else {
            val leaves = (root as? QNode.And)?.kids.orEmpty().ifEmpty { listOfNotNull(root) }
                .mapNotNull { (it as? QNode.Leaf)?.term }
            val exact = leaves.filterIsInstance<Term.Name>().firstOrNull { it.exact }
            val set = leaves.filterIsInstance<Term.Field>().firstOrNull { it.key == "s" && it.op in setOf(":", "=") && it.regex == null }
            val words = leaves.filterIsInstance<Term.Name>().filter { !it.exact }.flatMap { it.text.split(' ') }.filter { it.isNotBlank() }
            when {
                exact != null -> list.allNamed(exact.text)
                set != null -> list.inSet(set.value)
                words.isNotEmpty() -> list.nameHas(words)
                else -> return@withContext null
            }
        }
        val now = System.currentTimeMillis()
        val hits = if (root == null) candidates else candidates.filter { compiled.matches(it.toEntity(false, 1, now)) }
        val picked = if (unique == "prints") hits
        else hits.groupBy { it.oracleId ?: it.name }.values.mapNotNull { LocalPick.newest(it) }
        sortLikeScryfall(picked, order).take(limit)
    }

/** Search's sort orders, as Scryfall applies them. */
internal fun sortLikeScryfall(cards: List<ScryfallCard>, order: String): List<ScryfallCard> = when (order) {
    "released" -> cards.sortedByDescending { it.releasedAt ?: "" }
    "set" -> cards.sortedWith(compareBy<ScryfallCard> { it.collectorNumber.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.collectorNumber })
    "usd" -> cards.sortedByDescending { it.prices.usd?.toDoubleOrNull() ?: it.prices.usdFoil?.toDoubleOrNull() ?: -1.0 }
    "cmc" -> cards.sortedWith(compareBy<ScryfallCard> { it.cmc }.thenBy { it.name })
    else -> cards.sortedBy { it.name.lowercase() }
}

/** Card names starting with [prefix] from the card list on the phone, for suggestions as you type. Empty without the list. */
suspend fun CollectionRepository.nameSuggestions(prefix: String, limit: Int = 8): List<String> =
    withContext(Dispatchers.IO) { if (!localCards.ready || prefix.isBlank()) emptyList() else localCards.namesStarting(prefix, limit) }

/** Names for an add box as you type: from the phone's list at once when it has any, otherwise Scryfall's. */
suspend fun CollectionRepository.suggestNames(text: String): List<String> {
    val q = text.trim()
    if (q.isEmpty()) return emptyList()
    return nameSuggestions(q).ifEmpty { runCatching { api.autocomplete(q) }.getOrDefault(emptyList()) }
}
