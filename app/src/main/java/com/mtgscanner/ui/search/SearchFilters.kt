package com.mtgscanner.ui.search

import kotlinx.serialization.Serializable

/** A type line term to require (IS) or exclude (NOT). */
@Serializable
data class TypeTerm(val word: String, val not: Boolean = false)

/** A number filter such as mana value < 3. [op] is one of [SearchFilters.OPS]; [value] may be negative, or "*". */
@Serializable
data class StatTerm(val stat: String, val op: String = "=", val value: String = "")

/** Extra Scryfall filters picked in the Search filter sheet. */
@Serializable
data class SearchFilters(
    /** One result per card (unique=cards) instead of every printing. */
    val groupPrintings: Boolean = true,
    /** Scryfall format key, such as "commander"; only cards legal in it are shown. */
    val format: String? = null,
    val types: List<TypeTerm> = emptyList(),
    val stats: List<StatTerm> = emptyList(),
    /** Scryfall language code; "" means default (English), "any" means all. */
    val lang: String = "",
    val order: String = "name",
) {
    val unique: String get() = if (groupPrintings) "cards" else "prints"

    /** How many filters differ from the defaults, for the badge on the filter button. */
    val activeCount: Int
        get() = (if (!groupPrintings) 1 else 0) +
            (if (format != null) 1 else 0) +
            types.size + validStats.size +
            (if (lang.isNotEmpty()) 1 else 0) +
            (if (order != "name") 1 else 0)

    /** True when the filters alone narrow the search, so it can run with no words typed. */
    val narrows: Boolean get() = format != null || types.isNotEmpty() || validStats.isNotEmpty()

    private val validStats: List<StatTerm>
        get() = stats.filter { it.stat in STATS && it.op in OPS && validValue(it.op, it.value.trim()) }

    /** The Scryfall query terms for these filters, joined by spaces. Empty when none apply. */
    fun toQuery(): String = buildList {
        if (format != null) add("f:$format")
        types.forEach { t ->
            val w = t.word.trim().lowercase()
            if (w.isNotEmpty()) {
                val term = if (w.contains(' ')) "t:\"$w\"" else "t:$w"
                add(if (t.not) "-$term" else term)
            }
        }
        validStats.forEach { add("${it.stat}${it.op}${it.value.trim()}") }
        if (lang.isNotEmpty()) add("lang:$lang")
    }.joinToString(" ")

    /**
     * [text] plus these filters, as one Scryfall query. Text with an "or" is wrapped in
     * parentheses so the filters apply to every branch, not just the last one.
     */
    fun combine(text: String): String {
        val t = text.trim()
        val q = toQuery()
        val words = if (q.isNotEmpty() && OR.containsMatchIn(t)) "($t)" else t
        return listOf(words, q).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Cycles a type chip: off → IS → NOT → off. */
    fun cycleType(word: String): SearchFilters {
        val cur = types.firstOrNull { it.word.equals(word, ignoreCase = true) }
        val rest = types.filterNot { it.word.equals(word, ignoreCase = true) }
        return copy(
            types = when {
                cur == null -> rest + TypeTerm(word)
                !cur.not -> rest + TypeTerm(cur.word, not = true)
                else -> rest
            },
        )
    }

    fun stat(stat: String): StatTerm = stats.firstOrNull { it.stat == stat } ?: StatTerm(stat)

    /** Sets the term for [t]'s stat. A blank "=" term is dropped; a blank one with another op is kept so the op sticks while typing. */
    fun withStat(t: StatTerm): SearchFilters =
        copy(stats = stats.filterNot { it.stat == t.stat } + listOfNotNull(t.takeIf { it.value.isNotBlank() || it.op != "=" }))

    companion object {
        val STATS = listOf("mv", "pow", "tou")
        val OPS = listOf("=", "<", ">", "<=", ">=")
        private val OR = Regex("""(?i)(^|\s)or(\s|$)""")

        /** A number (may be negative or have a decimal point), or "*" with "=". */
        private fun validValue(op: String, v: String): Boolean =
            v.toDoubleOrNull() != null || (v == "*" && op == "=")

        /** Keeps what a stat value field may hold: a leading minus, digits and one dot, or a lone "*". */
        fun cleanStatValue(v: String): String {
            if (v.contains('*')) return "*"
            val out = StringBuilder()
            v.forEach { ch ->
                when {
                    ch == '-' && out.isEmpty() -> out.append(ch)
                    ch == '.' && '.' !in out -> out.append(ch)
                    ch.isDigit() -> out.append(ch)
                }
            }
            return out.toString().take(5)
        }

        /** Label to Scryfall format key. */
        val MAIN_FORMATS = listOf(
            "Standard" to "standard", "Modern" to "modern", "Legacy" to "legacy", "Commander" to "commander",
            "Pioneer" to "pioneer", "Vintage" to "vintage", "Pauper" to "pauper", "Historic" to "historic", "Brawl" to "brawl",
        )
        val MORE_FORMATS = listOf(
            "Timeless" to "timeless", "Explorer" to "explorer", "Alchemy" to "alchemy", "Oathbreaker" to "oathbreaker",
            "Penny" to "penny", "Duel" to "duel", "Old School" to "oldschool", "Premodern" to "premodern",
            "Predh" to "predh", "Gladiator" to "gladiator", "Pauper EDH" to "paupercommander", "Std. Brawl" to "standardbrawl",
            "Future" to "future",
        )

        val SUPERTYPES = listOf("Legendary", "Basic", "Snow", "World")
        val CARD_TYPES = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land", "Battle", "Kindred")
        val SUBTYPES = listOf("Equipment", "Aura", "Vehicle", "Saga", "Human", "Elf", "Goblin", "Zombie", "Dragon", "Angel", "Vampire", "Wizard")

        /** Label to Scryfall code; "" is the default (English). */
        val LANGUAGES = listOf(
            "English" to "", "Any" to "any", "Spanish" to "es", "French" to "fr", "German" to "de", "Italian" to "it",
            "Portuguese" to "pt", "Japanese" to "ja", "Korean" to "ko", "Russian" to "ru", "Chinese (S)" to "zhs",
            "Chinese (T)" to "zht", "Phyrexian" to "ph",
        )

        /** Label to Scryfall order. */
        val ORDERS = listOf(
            "Name" to "name", "Newest" to "released", "Set" to "set", "Rarity" to "rarity", "Color" to "color",
            "Price" to "usd", "Mana value" to "cmc", "Power" to "power", "Toughness" to "toughness",
            "EDHREC" to "edhrec", "Artist" to "artist",
        )

        fun statLabel(stat: String): String = when (stat) {
            "mv" -> "Mana value"
            "pow" -> "Power"
            else -> "Toughness"
        }
    }
}

/** A saved Scryfall search: the typed words plus filters. */
@Serializable
data class SavedScryfallSearch(val name: String, val text: String, val filters: SearchFilters)
