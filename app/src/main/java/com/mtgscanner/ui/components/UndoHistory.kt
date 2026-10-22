package com.mtgscanner.ui.components

/**
 * The last few changes that offered Undo, newest first. Pure: [R] is whatever restores the change.
 *
 * Most restores put back a snapshot of the cards as they were. Run out of order, an older snapshot
 * could wipe out a newer change to the same cards, so undoing an older entry first undoes every
 * newer one still listed, newest first ([takeThrough]). That's the same order a stack of Undo taps
 * would run them in, so each snapshot lands on the state it was taken from.
 */
class UndoHistory<R>(private val max: Int = 10) {
    data class Entry<R>(val id: Long, val label: String, val at: Long, val restore: R)

    private var nextId = 1L
    private var list: List<Entry<R>> = emptyList()

    /** Newest first. */
    val entries: List<Entry<R>> get() = list

    /** Adds a change; the oldest drops off past [max]. Returns its id. */
    fun record(label: String, at: Long, restore: R): Long {
        val id = nextId++
        list = (listOf(Entry(id, label, at, restore)) + list).take(max)
        return id
    }

    /** How many newer entries undoing [id] would also undo; null when [id] isn't listed. */
    fun newerThan(id: Long): Int? = list.indexOfFirst { it.id == id }.takeIf { it >= 0 }

    /**
     * Removes [id] and every newer entry, and returns them newest first, in the order to run them.
     * Empty when [id] isn't listed (already undone, or dropped off the end).
     */
    fun takeThrough(id: Long): List<Entry<R>> {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return emptyList()
        val taken = list.take(i + 1)
        list = list.drop(i + 1)
        return taken
    }

    fun clear() { list = emptyList() }

    companion object {
        /** "just now", "3 min ago", "2 h ago", "1 day ago". */
        fun ago(now: Long, at: Long): String {
            val s = ((now - at) / 1000).coerceAtLeast(0)
            return when {
                s < 60 -> "just now"
                s < 3600 -> "${s / 60} min ago"
                s < 86_400 -> "${s / 3600} h ago"
                else -> (s / 86_400).let { if (it == 1L) "1 day ago" else "$it days ago" }
            }
        }
    }
}
