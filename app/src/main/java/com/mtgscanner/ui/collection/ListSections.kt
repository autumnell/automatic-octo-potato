package com.mtgscanner.ui.collection

import com.mtgscanner.data.db.CollectionCard

/** Section headers for long card lists, and the math behind the sticky header and letter rail. */
object ListSections {
    /** Shorter lists don't need headers. */
    const val MIN_CARDS = 20

    /** "A" to "Z", or "#" for names starting with a digit or symbol. Accents are dropped (É → E). */
    fun letterOf(name: String): String {
        val ch = name.trimStart().firstOrNull() ?: return "#"
        val base = java.text.Normalizer.normalize(ch.toString(), java.text.Normalizer.Form.NFD).firstOrNull() ?: ch
        val up = base.uppercaseChar()
        return when {
            up in 'A'..'Z' -> up.toString()
            up == 'Æ' -> "A"
            else -> "#"
        }
    }

    /**
     * Splits an already-sorted, ungrouped list into sections: by first letter when sorted by name,
     * by set when sorted by set. Null when the sort has no natural sections or the list is short.
     */
    fun sections(cards: List<CollectionCard>, sort: SortBy): List<Pair<String, List<CollectionCard>>>? {
        if (cards.size < MIN_CARDS) return null
        return when (sort) {
            SortBy.NAME -> consecutive(cards) { letterOf(it.name) }
            SortBy.SET -> consecutive(cards) { "${it.setName} (${it.setCode.uppercase()})" }
            else -> null
        }
    }

    /** Groups runs of equal keys, keeping the order. */
    fun <T> consecutive(items: List<T>, key: (T) -> String): List<Pair<String, List<T>>> {
        val out = ArrayList<Pair<String, List<T>>>()
        var cur: String? = null
        var run = ArrayList<T>()
        for (it in items) {
            val k = key(it)
            if (k != cur) {
                if (cur != null) out.add(cur to run)
                cur = k
                run = ArrayList()
            }
            run.add(it)
        }
        if (cur != null) out.add(cur to run)
        return out
    }

    /** Where each titled group's header sits in the flat list (header, its cards, next header...). -1 for untitled groups. */
    fun headerIndices(groups: List<Pair<String, List<*>>>): List<Int> {
        var i = 0
        return groups.map { (title, items) ->
            val at = if (title.isNotEmpty()) i.also { i++ } else -1
            i += items.size
            at
        }
    }

    /** The group whose header is at or above [firstVisible], or -1 above the first header. */
    fun sectionAt(headers: List<Int>, firstVisible: Int): Int {
        var found = -1
        headers.forEachIndexed { g, at -> if (at in 0..firstVisible) found = g }
        return found
    }

    /** Letter rail entries: each distinct single-letter title with the index of its first header. Empty unless every title is a letter. */
    fun rail(groups: List<Pair<String, List<*>>>, headers: List<Int>): List<Pair<String, Int>> {
        if (groups.size < 2 || groups.any { it.first.length != 1 }) return emptyList()
        val seen = LinkedHashMap<String, Int>()
        groups.forEachIndexed { g, (title, _) -> if (title !in seen && headers[g] >= 0) seen[title] = headers[g] }
        return seen.toList()
    }
}

/** How full a box with a set capacity is. */
enum class BoxFill { OK, NEAR, FULL }

object BoxFillMath {
    fun state(cards: Int, capacity: Int): BoxFill = when {
        capacity <= 0 -> BoxFill.OK
        cards >= capacity -> BoxFill.FULL
        cards * 10 >= capacity * 9 -> BoxFill.NEAR
        else -> BoxFill.OK
    }

    /** One segment per slot for tiny boxes, else ten (one per 10%). */
    fun segments(capacity: Int): Int = if (capacity in 1..10) capacity else 10

    /** Segments to light: all only when full, at least one once a card is in. */
    fun lit(cards: Int, capacity: Int, segments: Int): Int {
        if (capacity <= 0 || cards <= 0) return 0
        if (cards >= capacity) return segments
        val n = (cards.toLong() * segments / capacity).toInt()
        return n.coerceIn(1, segments - 1)
    }
}
