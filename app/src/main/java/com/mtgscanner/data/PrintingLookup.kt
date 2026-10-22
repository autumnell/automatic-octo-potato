package com.mtgscanner.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Finds the printing a decklist or import line names. A line with a set (and number) is looked up as
 * that exact printing first; only when Scryfall has no such printing does it widen, step by step:
 * the same number without leading zeros, the card by name in that set, then the card by name anywhere.
 * Pure, so the order is tested; the caller does the Scryfall calls.
 */
object PrintingLookup {
    /** What a line asks for. */
    data class Wanted(val name: String, val set: String? = null, val number: String? = null, val id: String? = null)

    /** One Scryfall /cards/collection identifier. */
    data class Ident(val id: String? = null, val set: String? = null, val number: String? = null, val name: String? = null)

    /** Arena writes a few set codes differently from Scryfall. */
    private val ALIASES = mapOf("dar" to "dom")

    /** Scryfall's set code for [code]: trimmed, lower case, Arena aliases mapped. Null when blank. */
    fun setCode(code: String?): String? {
        val c = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return ALIASES[c] ?: c
    }

    /** A collector number as given, trimmed; "007/280" style counts drop the "/280". */
    fun number(n: String?): String? = n?.trim()?.substringBefore('/')?.trim()?.takeIf { it.isNotEmpty() }

    /** "007" → "7"; null when it has no leading zeros to drop. */
    private fun unpadded(n: String): String? {
        val t = n.trimStart('0')
        return if (t != n && t.isNotEmpty() && t.first().isDigit()) t else null
    }

    /** The identifiers to try for [w], most exact first. */
    fun attempts(w: Wanted): List<Ident> {
        val set = setCode(w.set)
        val num = number(w.number)
        val name = w.name.trim().takeIf { it.isNotEmpty() }
        return buildList {
            w.id?.takeIf { it.isNotBlank() }?.let { add(Ident(id = it)) }
            if (set != null && num != null) {
                add(Ident(set = set, number = num))
                unpadded(num)?.let { add(Ident(set = set, number = it)) }
            }
            if (name != null) {
                if (set != null) add(Ident(set = set, name = name))
                add(Ident(name = name))
            }
        }.distinct()
    }

    /**
     * Looks every line up, a pass per step: each pass sends the next identifier of every line still not
     * found, so a list costs a few batched calls, not one per line. [lookup] gets identifiers and returns
     * a card (or null) per identifier, in order.
     */
    inline fun <C : Any> resolve(wants: List<Wanted>, lookup: (List<Ident>) -> List<C?>): List<C?> {
        val plans = wants.map { attempts(it) }
        val found = MutableList<C?>(wants.size) { null }
        val steps = plans.maxOfOrNull { it.size } ?: 0
        for (step in 0 until steps) {
            val todo = wants.indices.filter { found[it] == null && step < plans[it].size }
            if (todo.isEmpty()) continue
            val got = lookup(todo.map { plans[it][step] })
            todo.forEachIndexed { k, i -> found[i] = got.getOrNull(k) }
        }
        return found
    }

    /** Whether the card found ([cardSet], [cardNumber]) is the printing asked for. A line with no set matches any. */
    fun samePrinting(set: String?, number: String?, cardSet: String, cardNumber: String): Boolean {
        val s = setCode(set) ?: return true
        if (!s.equals(cardSet, ignoreCase = true)) return false
        val n = number(number) ?: return true
        return n.equals(cardNumber, ignoreCase = true) || (unpadded(n) ?: n).equals(cardNumber, ignoreCase = true)
    }

    /** A Scryfall /cards/collection identifier. */
    fun json(i: Ident): JsonObject = buildJsonObject {
        when {
            i.id != null -> put("id", i.id)
            i.set != null && i.number != null -> { put("set", i.set); put("collector_number", i.number) }
            else -> { put("name", i.name.orEmpty()); if (i.set != null) put("set", i.set) }
        }
    }
}
