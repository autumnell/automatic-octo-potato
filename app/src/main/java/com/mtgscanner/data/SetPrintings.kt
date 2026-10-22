package com.mtgscanner.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Printings of one card within a set: a set can hold the normal card and borderless, showcase or extended-art
 * ones under other collector numbers. Picks the normal one for lines that name only the set, and labels the rest.
 */
object SetPrintings {
    private val LABELS = linkedMapOf(
        "borderless" to "Borderless", "showcase" to "Showcase", "extendedart" to "Extended art", "fullart" to "Full art",
        "etched" to "Etched", "retro" to "Retro frame", "promo" to "Promo",
    )

    /** "Borderless", "Showcase"…; null for a regular printing. [treatments] as [ScryfallCard.treatments] gives them. */
    fun label(treatments: Collection<String>): String? = LABELS.entries.firstOrNull { it.key in treatments }?.value

    /** Same, from a copy's stored treatments ("borderless,promo"). */
    fun label(stored: String): String? = label(stored.split(',').map { it.trim() })

    /** Not the set's normal printing: borderless, showcase, extended art, full art, retro frame, a promo or gold border. */
    fun isVariant(c: ScryfallCard): Boolean =
        c.treatments.isNotEmpty() || c.promoTypes.isNotEmpty() || (c.borderColor != null && c.borderColor != "black" && c.borderColor != "white")

    private fun numberValue(n: String): Int = n.takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE

    /** The normal printing among one card's printings in a set: the lowest number that isn't a variant. */
    fun regular(cards: List<ScryfallCard>): ScryfallCard? {
        val order = compareBy<ScryfallCard>({ numberValue(it.collectorNumber) }, { it.collectorNumber.length }, { it.collectorNumber })
        return cards.filterNot(::isVariant).minWithOrNull(order) ?: cards.minWithOrNull(order)
    }

    /** Scryfall searches for every printing of each (oracle id, set code), a few cards per search. */
    fun queries(wants: List<Pair<String, String>>, perQuery: Int = 12): List<String> =
        wants.distinct().chunked(perQuery).map { ch -> ch.joinToString(" or ") { (o, s) -> "(oracleid:$o e:$s)" } }

    /** Whether [w] names a set but no collector number (and no Scryfall id), so its printing is open. */
    fun setOnly(w: PrintingLookup.Wanted): Boolean =
        w.id.isNullOrBlank() && PrintingLookup.setCode(w.set) != null && PrintingLookup.number(w.number) == null
}

/** Normal printing per "oracleId|set", kept for the app's lifetime. */
private val regularCache = ConcurrentHashMap<String, ScryfallCard>()

/** Keys already searched for, found or not, so a check doesn't search again for the same card. */
private val regularAsked: MutableSet<String> = ConcurrentHashMap.newKeySet()

/**
 * For lines that name a set but no collector number: that set's normal printing of each found card, instead of
 * whichever printing the lookup returned (it may be a borderless or showcase one). Others, and anything a failed
 * search leaves unknown, stay as found.
 */
suspend fun ScryfallApi.preferRegular(wants: List<PrintingLookup.Wanted>, found: List<ScryfallCard?>): List<ScryfallCard?> {
    fun key(o: String, s: String) = "$o|${s.lowercase()}"
    val todo = found.indices.filter { i ->
        val c = found[i]
        c?.oracleId != null && SetPrintings.setOnly(wants[i]) && PrintingLookup.setCode(wants[i].set).equals(c.set, ignoreCase = true)
    }.toSet()
    if (todo.isEmpty()) return found
    val ask = todo.map { found[it]!!.oracleId!! to found[it]!!.set.lowercase() }.distinct()
        .filter { (o, s) -> key(o, s) !in regularAsked }
    for (chunk in ask.chunked(12)) {
        val q = SetPrintings.queries(chunk, perQuery = 12).single()
        val cards = try {
            searchAll(q, unique = "prints", maxPages = 3)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            break
        }
        cards.filter { it.oracleId != null }.groupBy { key(it.oracleId!!, it.set) }
            .forEach { (k, cs) -> SetPrintings.regular(cs)?.let { regularCache[k] = it } }
        chunk.forEach { (o, s) -> regularAsked += key(o, s) }
    }
    return found.mapIndexed { i, c -> if (c != null && i in todo) regularCache[key(c.oracleId!!, c.set)] ?: c else c }
}
