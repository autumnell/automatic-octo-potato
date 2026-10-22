package com.mtgscanner.ui.decks

/** "Find in deck" on a deck's Cards view. Pure, for tests. */
object DeckFind {
    private fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Whether [name] matches what was typed: any part of it, ignoring case, spaces and punctuation. Blank matches all. */
    fun matches(name: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (name.contains(q, ignoreCase = true)) return true
        val sq = squash(q)
        return sq.isNotEmpty() && squash(name).contains(sq)
    }
}
