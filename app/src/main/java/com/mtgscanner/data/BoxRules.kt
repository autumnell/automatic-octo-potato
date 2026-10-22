package com.mtgscanner.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The facts about a card that box rules look at. */
data class CardFacts(
    val colors: String,
    val rarity: String,
    val typeLine: String,
    val treatments: Set<String>,
    val foil: Boolean,
    val price: Double?,
    val setCode: String,
    val name: String = "",
    val colorIdentity: String = "",
    val cmc: Double = 0.0,
    val power: String? = null,
    val toughness: String? = null,
    val reserved: Boolean = false,
    /** Formats it's legal in (Scryfall keys like "commander"). */
    val formats: Set<String> = emptySet(),
    /** Collector number, for boxes kept in number order. */
    val number: String = "",
) {
    /** A token, emblem or art card (see [CardExtras]), from the type line. */
    val tokenOrArt: Boolean get() = CardExtras.isExtra(typeLine)
}

/**
 * What belongs in a box. Empty sets mean "any". A card fits when it passes every rule that's set.
 * Colors use W U B R G, plus C (colorless) and M (multicolor).
 */
@Serializable
data class BoxRules(
    val colors: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val treatments: Set<String> = emptySet(),
    /** "any", "foil" or "nonfoil" */
    val finish: String = "any",
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sets: Set<String> = emptySet(),
    /** Card names starting from this letter to [nameTo] (inclusive), like "A"–"M". Used to split a full box. */
    val nameFrom: String? = null,
    val nameTo: String? = null,
    /** Color identity letters (W U B R G, or C for colorless), for Commander. */
    val identity: Set<String> = emptySet(),
    /** "exact": identity is exactly [identity]; "within": fits a deck of that identity. */
    val identityMode: String = "exact",
    /** Mana value range, inclusive. */
    val minCmc: Int? = null,
    val maxCmc: Int? = null,
    /** Power and toughness ranges, inclusive. Cards without a number (non-creatures, "*") don't fit. */
    val minPower: Int? = null,
    val maxPower: Int? = null,
    val minToughness: Int? = null,
    val maxToughness: Int? = null,
    /** Legal in any of these formats (Scryfall keys like "commander"). */
    val formats: Set<String> = emptySet(),
    /** Only cards on the Reserved List. */
    val reserved: Boolean = false,
    /** Only tokens, emblems and art cards. Such a box gets them ahead of every other box. */
    val tokens: Boolean = false,
) {
    val isCatchAll: Boolean
        get() = colors.isEmpty() && rarities.isEmpty() && types.isEmpty() && treatments.isEmpty() &&
            finish == "any" && minPrice == null && maxPrice == null && sets.isEmpty() && nameFrom == null && nameTo == null &&
            identity.isEmpty() && minCmc == null && maxCmc == null && minPower == null && maxPower == null &&
            minToughness == null && maxToughness == null && formats.isEmpty() && !reserved && !tokens

    /**
     * Takes any card, or any card in a name range only (half of a split "Anything" box).
     * Such boxes are checked after every box with real rules.
     */
    val isOpen: Boolean
        get() = copy(nameFrom = null, nameTo = null).isCatchAll

    /** Whether a card name falls in [nameFrom]–[nameTo]. Names starting with a digit or symbol ("#") fit any range. */
    fun inNameRange(name: String): Boolean {
        if (nameFrom == null && nameTo == null) return true
        val letter = nameLetter(name)
        if (letter == '#') return true
        if (nameFrom != null && letter < nameFrom.first().uppercaseChar()) return false
        if (nameTo != null && letter > nameTo.first().uppercaseChar()) return false
        return true
    }

    /**
     * Whether a card fits. [priceSlack] (0.1 = 10%) widens the price range, for cards already in the box:
     * a $4.80 card can stay in a "$5+" box with a 10% buffer.
     */
    fun matches(c: CardFacts, priceSlack: Double = 0.0): Boolean {
        if (colors.isNotEmpty()) {
            val ok = colors.any { sel ->
                when (sel) {
                    "C" -> c.colors.isEmpty()
                    "M" -> c.colors.length > 1
                    else -> c.colors.length == 1 && c.colors.contains(sel)
                }
            }
            if (!ok) return false
        }
        if (rarities.isNotEmpty()) {
            val r = if (c.rarity in setOf("special", "bonus")) "mythic" else c.rarity
            if (r !in rarities) return false
        }
        if (types.isNotEmpty() && types.none { c.typeLine.contains(it, ignoreCase = true) }) return false
        if (treatments.isNotEmpty() && treatments.none { it in c.treatments }) return false
        if (finish == "foil" && !c.foil) return false
        if (finish == "nonfoil" && c.foil) return false
        val p = c.price ?: 0.0
        if (minPrice != null && p < minPrice * (1 - priceSlack)) return false
        if (maxPrice != null && p >= maxPrice * (1 + priceSlack)) return false
        if (sets.isNotEmpty() && c.setCode.uppercase() !in sets.map { it.uppercase() }) return false
        if (!inNameRange(c.name)) return false
        if (identity.isNotEmpty()) {
            val want = identity.filter { it in WUBRG }.toSet()
            val has = c.colorIdentity.uppercase().map { it.toString() }.filter { it in WUBRG }.toSet()
            val ok = if (identityMode == "within") want.containsAll(has) else has == want
            if (!ok) return false
        }
        if (minCmc != null && c.cmc < minCmc) return false
        if (maxCmc != null && c.cmc > maxCmc) return false
        if (minPower != null || maxPower != null) {
            val pw = statValue(c.power) ?: return false
            if (minPower != null && pw < minPower) return false
            if (maxPower != null && pw > maxPower) return false
        }
        if (minToughness != null || maxToughness != null) {
            val t = statValue(c.toughness) ?: return false
            if (minToughness != null && t < minToughness) return false
            if (maxToughness != null && t > maxToughness) return false
        }
        if (formats.isNotEmpty() && formats.none { it in c.formats }) return false
        if (reserved && !c.reserved) return false
        if (tokens && !c.tokenOrArt) return false
        return true
    }

    /** Short summary like "Red · Rare, Mythic · $5+". */
    fun describe(): String {
        if (isCatchAll) return "Any card"
        val parts = mutableListOf<String>()
        if (colors.isNotEmpty()) parts += colors.joinToString(", ") { COLOR_NAMES[it] ?: it }
        if (rarities.isNotEmpty()) parts += rarities.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }
        if (types.isNotEmpty()) parts += types.joinToString(", ")
        if (treatments.isNotEmpty()) parts += treatments.joinToString(", ") { TREATMENT_NAMES[it] ?: it }
        if (finish == "foil") parts += "Foils"
        if (finish == "nonfoil") parts += "Non-foil"
        when {
            minPrice != null && maxPrice != null -> parts += "${money(minPrice)}–${money(maxPrice)}"
            minPrice != null -> parts += "${money(minPrice)}+"
            maxPrice != null -> parts += "Under ${money(maxPrice)}"
        }
        if (sets.isNotEmpty()) parts += sets.joinToString(", ") { it.uppercase() }
        if (nameFrom != null || nameTo != null) parts += "${nameFrom ?: "A"}–${nameTo ?: "Z"}"
        if (identity.isNotEmpty()) {
            val letters = identity.filter { it in WUBRG }.sortedBy { WUBRG.indexOf(it) }.joinToString("").ifEmpty { "C" }
            parts += (if (identityMode == "within") "Within " else "Identity ") + letters
        }
        range("MV", minCmc, maxCmc)?.let { parts += it }
        range("Power", minPower, maxPower)?.let { parts += it }
        range("Toughness", minToughness, maxToughness)?.let { parts += it }
        if (formats.isNotEmpty()) parts += formats.joinToString("/") { FORMAT_NAMES[it] ?: it } + " legal"
        if (reserved) parts += "Reserved List"
        if (tokens) parts += "Tokens & art cards"
        return parts.joinToString(" · ")
    }

    /**
     * Every card that fits [other] also fits this box, so a box with [other]'s rules placed after this one
     * never gets a card. Only says yes when sure.
     */
    fun covers(other: BoxRules): Boolean {
        fun <T> sub(mine: Set<T>, theirs: Set<T>) = mine.isEmpty() || (theirs.isNotEmpty() && mine.containsAll(theirs))
        fun lowAtMost(mine: Double?, theirs: Double?) = mine == null || (theirs != null && theirs >= mine)
        fun highAtLeast(mine: Double?, theirs: Double?) = mine == null || (theirs != null && theirs <= mine)
        if (!sub(colors, other.colors)) return false
        if (!sub(rarities, other.rarities)) return false
        if (!sub(types.map { it.lowercase() }.toSet(), other.types.map { it.lowercase() }.toSet())) return false
        if (!sub(treatments, other.treatments)) return false
        if (finish != "any" && finish != other.finish) return false
        if (!lowAtMost(minPrice, other.minPrice) || !highAtLeast(maxPrice, other.maxPrice)) return false
        if (!sub(sets.map { it.uppercase() }.toSet(), other.sets.map { it.uppercase() }.toSet())) return false
        val from = nameFrom?.firstOrNull()?.uppercaseChar() ?: 'A'
        val to = nameTo?.firstOrNull()?.uppercaseChar() ?: 'Z'
        val oFrom = other.nameFrom?.firstOrNull()?.uppercaseChar() ?: 'A'
        val oTo = other.nameTo?.firstOrNull()?.uppercaseChar() ?: 'Z'
        if (oFrom < from || oTo > to) return false
        if (identity.isNotEmpty()) {
            val same = identity == other.identity && identityMode == other.identityMode
            val within = identityMode == "within" && other.identity.isNotEmpty() && identity.containsAll(other.identity)
            if (!same && !within) return false
        }
        if (!lowAtMost(minCmc?.toDouble(), other.minCmc?.toDouble()) || !highAtLeast(maxCmc?.toDouble(), other.maxCmc?.toDouble())) return false
        if (!lowAtMost(minPower?.toDouble(), other.minPower?.toDouble()) || !highAtLeast(maxPower?.toDouble(), other.maxPower?.toDouble())) return false
        if (!lowAtMost(minToughness?.toDouble(), other.minToughness?.toDouble()) || !highAtLeast(maxToughness?.toDouble(), other.maxToughness?.toDouble())) return false
        if (!sub(formats, other.formats)) return false
        if (reserved && !other.reserved) return false
        if (tokens && !other.tokens) return false
        return true
    }

    fun toJson(): String = if (this == BoxRules()) "" else json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        /**
         * The letter a name files under for name ranges and splits: its first letter, or '#' for a digit or symbol.
         * Same as dividers and Browse ([NameKey]): accents and a leading "The" don't count.
         */
        fun nameLetter(name: String): Char = NameKey.letter(name)

        fun fromJson(s: String): BoxRules =
            if (s.isBlank()) BoxRules() else runCatching { json.decodeFromString(serializer(), s) }.getOrDefault(BoxRules())

        val COLOR_NAMES = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colorless", "M" to "Multicolor")
        val TREATMENT_NAMES = mapOf(
            "borderless" to "Borderless", "showcase" to "Showcase", "extendedart" to "Extended art",
            "fullart" to "Full art", "etched" to "Etched", "promo" to "Promo", "retro" to "Retro frame",
        )
        private val WUBRG = listOf("W", "U", "B", "R", "G")

        /** Formats the app keeps legality for, in display order. */
        val FORMAT_NAMES = linkedMapOf(
            "commander" to "Commander", "pauper" to "Pauper", "modern" to "Modern", "pioneer" to "Pioneer", "standard" to "Standard",
        )

        /** "MV 2–3", "Power 4+", "Toughness ≤2", or null when unset. */
        private fun range(label: String, min: Int?, max: Int?): String? = when {
            min != null && max != null -> if (min == max) "$label $min" else "$label $min–$max"
            min != null -> "$label $min+"
            max != null -> "$label ≤$max"
            else -> null
        }

        /** The number in a power or toughness like "3" or "1+*"; null for "*" or none. */
        fun statValue(s: String?): Int? {
            val t = s?.trim().orEmpty()
            val digits = if (t.startsWith("-")) "-" + t.drop(1).takeWhile { it.isDigit() } else t.takeWhile { it.isDigit() }
            return digits.toIntOrNull()
        }

        val TYPES = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land", "Battle")

        private fun money(v: Double) = if (v % 1.0 == 0.0) "$${v.toInt()}" else "$" + "%.2f".format(java.util.Locale.US, v)

        /** The first box whose rules fit [card], ignoring open boxes (see [isOpen]). Used to sort a catch-all box later. */
        fun <T> ruleMatch(boxes: List<Pair<T, BoxRules>>, card: CardFacts): T? =
            tokensFirst(boxes) { it.second }.firstOrNull { !it.second.isOpen && it.second.matches(card) }?.first

        /** [boxes] with the "Tokens & art cards" boxes moved to the front, the rest in their order. */
        fun <T> tokensFirst(boxes: List<T>, rules: (T) -> BoxRules): List<T> =
            if (boxes.none { rules(it).tokens }) boxes else boxes.filter { rules(it).tokens } + boxes.filterNot { rules(it).tokens }

        /**
         * Picks the box a card belongs in: the first box with rules that fit, in Auto-sort order.
         * If none fits, the [fallback] box, else the first open box (see [isOpen]) whose name range fits.
         */
        fun <T> pick(boxes: List<Pair<T, BoxRules>>, card: CardFacts, fallback: T? = null): T? =
            ruleMatch(boxes, card)
                ?: fallback
                ?: boxes.firstOrNull { it.second.isOpen && it.second.matches(card) }?.first

        /**
         * For the box editor's preview: which [cards] fit [draft], and which of those a box
         * checked earlier would take first. Boxes that take any card never take cards first.
         */
        fun <T> overlap(draft: BoxRules, ahead: List<Pair<T, BoxRules>>, cards: List<CardFacts>): Overlap<T> {
            val rulesAhead = tokensFirst(ahead.filter { !it.second.isOpen }) { it.second }
            val fits = cards.indices.filter { draft.matches(cards[it]) }
            val taken = LinkedHashMap<T, MutableList<Int>>()
            for (i in fits) {
                val first = rulesAhead.firstOrNull { it.second.matches(cards[i]) } ?: continue
                taken.getOrPut(first.first) { mutableListOf() } += i
            }
            return Overlap(fits, taken.entries.sortedByDescending { it.value.size }.map { it.key to it.value.toList() })
        }
    }
}

/** Boxes whose rules can never get a card. */
object RuleCheck {
    private val ALL_COLORS = setOf("W", "U", "B", "R", "G", "C", "M")

    /**
     * [boxes] in Auto-sort order as (id, rules, limited), where limited means the box has a card limit (a full
     * box lets cards through to later boxes, so it never makes another box dead). Returns each dead box's id
     * with the earlier box that takes all its cards, or null when several earlier color boxes together do.
     * Open boxes (any card, or a name range) only get leftovers and are never reported.
     */
    fun <T> deadBoxes(boxes: List<Triple<T, BoxRules, Boolean>>): Map<T, T?> {
        val out = LinkedHashMap<T, T?>()
        val colorsAhead = HashSet<String>()
        boxes.forEachIndexed { i, (id, rules, limited) ->
            // A tokens box is checked ahead of the others, so no earlier box takes its cards.
            if (!rules.isOpen && !rules.tokens) {
                val by = boxes.subList(0, i).firstOrNull { (_, r, lim) -> !lim && !r.isOpen && r.covers(rules) }
                when {
                    by != null -> out[id] = by.first
                    colorsAhead.containsAll(ALL_COLORS) -> out[id] = null
                }
                // A box with only color rules takes every card of those colors.
                if (!limited && rules.copy(colors = emptySet()).isCatchAll) colorsAhead += rules.colors
            }
        }
        return out
    }
}

/** Result of [BoxRules.overlap]: indexes of cards that fit, and the ones each earlier box takes first. */
data class Overlap<T>(val fits: List<Int>, val takenBy: List<Pair<T, List<Int>>>)

/** Ready-made starting points shown when creating a box. */
data class BoxPreset(
    val name: String,
    val kind: String,
    val rules: BoxRules,
    /** A [Divider] key for inside the box. */
    val divider: String = "",
    /** Takes cards no other box fits. */
    val fallback: Boolean = false,
)

/** Several boxes made in one tap, in Auto-sort order. */
data class BoxTemplate(val name: String, val blurb: String, val boxes: List<BoxPreset>) {
    companion object {
        /** One box per set you own most of (up to [max]), then one for every other set, split by set. */
        fun setBoxes(setCounts: List<Pair<String, Int>>, max: Int = 8): BoxTemplate {
            val top = setCounts.filter { it.second > 0 }.sortedByDescending { it.second }.take(max).map { it.first.uppercase() }
            val boxes = top.map { BoxPreset(it, "box", BoxRules(sets = setOf(it))) } +
                BoxPreset("Other sets", "box", BoxRules(), divider = Divider.SET.key, fallback = true)
            return BoxTemplate("Set boxes", "A box per set", boxes)
        }
    }
}

/** Ready-made systems. "Set boxes" is built from your cards with [BoxTemplate.setBoxes]. */
val BOX_TEMPLATES = listOf(
    BoxTemplate(
        "Rares and bulk", "Rares binder, bulk box",
        listOf(
            BoxPreset("Rares & Mythics", "binder", BoxRules(rarities = setOf("rare", "mythic"))),
            BoxPreset("Bulk", "box", BoxRules(), divider = Divider.COLOR.key, fallback = true),
        ),
    ),
    BoxTemplate(
        "Color binders", "One binder per color",
        listOf(
            BoxPreset("White", "binder", BoxRules(colors = setOf("W"))),
            BoxPreset("Blue", "binder", BoxRules(colors = setOf("U"))),
            BoxPreset("Black", "binder", BoxRules(colors = setOf("B"))),
            BoxPreset("Red", "binder", BoxRules(colors = setOf("R"))),
            BoxPreset("Green", "binder", BoxRules(colors = setOf("G"))),
            BoxPreset("Multicolor", "binder", BoxRules(colors = setOf("M"))),
            BoxPreset("Colorless", "binder", BoxRules(colors = setOf("C")), fallback = true),
        ),
    ),
    BoxTemplate("Set boxes", "A box per set", emptyList()),
)

val BOX_PRESETS = listOf(
    BoxPreset("Bulk", "box", BoxRules(rarities = setOf("common", "uncommon"), maxPrice = 1.0)),
    BoxPreset("Rares & Mythics", "binder", BoxRules(rarities = setOf("rare", "mythic"))),
    BoxPreset("Borderless & Showcase", "binder", BoxRules(treatments = setOf("borderless", "showcase", "extendedart", "fullart"))),
    BoxPreset("Foils", "binder", BoxRules(finish = "foil")),
    BoxPreset("High value", "binder", BoxRules(minPrice = 10.0)),
    BoxPreset("Reserved List", "binder", BoxRules(reserved = true)),
    BoxPreset("Tokens & art cards", "box", BoxRules(tokens = true)),
    BoxPreset("Pauper", "box", BoxRules(formats = setOf("pauper"))),
    BoxPreset("White", "box", BoxRules(colors = setOf("W"))),
    BoxPreset("Blue", "box", BoxRules(colors = setOf("U"))),
    BoxPreset("Black", "box", BoxRules(colors = setOf("B"))),
    BoxPreset("Red", "box", BoxRules(colors = setOf("R"))),
    BoxPreset("Green", "box", BoxRules(colors = setOf("G"))),
    BoxPreset("Multicolor", "box", BoxRules(colors = setOf("M"))),
    BoxPreset("Colorless & Artifacts", "box", BoxRules(colors = setOf("C"))),
    BoxPreset("Lands", "box", BoxRules(types = setOf("Land"))),
    BoxPreset("Trade binder", "trade", BoxRules()),
    BoxPreset("Anything", "box", BoxRules()),
)
