package com.mtgscanner.data

import com.mtgscanner.scan.CardTextGuess

/**
 * Finds a token or emblem by the name read, since Scryfall's name lookup is made for cards. Its printings are
 * narrowed by the set code read (a token's own set is "t" plus the card set, "tmom" for "MOM") and the
 * collector number; else the newest printing. Null when no token or emblem has that name.
 */
internal suspend fun CollectionRepository.findExtra(name: String, guess: CardTextGuess): Identification? {
    val all = api.searchAll(exactNameQuery(name) + " include:extras", unique = "prints", maxPages = 3, order = "released")
        .filter { CardExtras.isExtra(it) }
    if (all.isEmpty()) return null
    val pick = TokenPick.pick(all, guess.setCode, guess.collectorNumber, { it.set }, { it.collectorNumber }) ?: return null
    return Identification(pick.first, confident = true, exact = pick.second)
}

/** Picking a token's printing. Pure Kotlin for tests. */
object TokenPick {
    /** The set codes a footer's [set] can mean for a token or art card: "MOM" → mom, tmom, amom. */
    fun setsFor(set: String): List<String> {
        val s = set.lowercase()
        return listOf(s, "t$s", "a$s")
    }

    /**
     * From [printings] (newest first), the one the footer names, and whether it's sure (set and number agree).
     * Without a usable footer, the newest one in the set read, else the newest.
     */
    fun <T> pick(printings: List<T>, set: String?, number: String?, setOf: (T) -> String, numberOf: (T) -> String): Pair<T, Boolean>? {
        if (printings.isEmpty()) return null
        val sets = set?.let { setsFor(it) }
        val inSet = sets?.let { s -> printings.filter { setOf(it).lowercase() in s } }.orEmpty()
        val n = number?.trimStart('0')?.lowercase()
        if (n != null && n.isNotEmpty()) {
            val pool = inSet.ifEmpty { printings }
            pool.firstOrNull { numberOf(it).trimStart('0').lowercase() == n }?.let { return it to inSet.isNotEmpty() }
        }
        return (inSet.firstOrNull() ?: printings.first()) to false
    }
}
