package com.mtgscanner.data

/** Matches full set names ("Magic 2010", "Time Spiral \"Timeshifted\"") to Scryfall set codes. Pure, for tests. */
object SetNames {
    /** A name with case, punctuation and spacing dropped, so small differences still match. */
    fun key(name: String): String = name.lowercase().replace("&", "and").filter { it.isLetterOrDigit() }

    /** Key to set code, from (name, code) pairs. The first set with a name wins. */
    fun index(sets: List<Pair<String, String>>): Map<String, String> {
        val out = HashMap<String, String>()
        sets.forEach { (name, code) -> out.putIfAbsent(key(name), code.lowercase()) }
        return out
    }

    /** The code for [name], also trying it without "Magic: The Gathering —" style prefixes and edition words. */
    fun codeFor(name: String, index: Map<String, String>): String? {
        val k = key(name)
        index[k]?.let { return it }
        val tries = listOf(
            k.removePrefix("magicthegathering"),
            k.removeSuffix("edition"),
            k.removeSuffix("set"),
            "core" + k.removePrefix("coreset"),
            k.replace("coreset", "core"),
        )
        return tries.firstNotNullOfOrNull { t -> t.takeIf { it.isNotEmpty() }?.let(index::get) }
    }
}
