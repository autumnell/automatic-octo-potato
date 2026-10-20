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
)

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
) {
    val isCatchAll: Boolean
        get() = colors.isEmpty() && rarities.isEmpty() && types.isEmpty() && treatments.isEmpty() &&
            finish == "any" && minPrice == null && maxPrice == null && sets.isEmpty()

    fun matches(c: CardFacts): Boolean {
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
        if (minPrice != null && p < minPrice) return false
        if (maxPrice != null && p >= maxPrice) return false
        if (sets.isNotEmpty() && c.setCode.uppercase() !in sets.map { it.uppercase() }) return false
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
        return parts.joinToString(" · ")
    }

    fun toJson(): String = if (this == BoxRules()) "" else json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun fromJson(s: String): BoxRules =
            if (s.isBlank()) BoxRules() else runCatching { json.decodeFromString(serializer(), s) }.getOrDefault(BoxRules())

        val COLOR_NAMES = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colorless", "M" to "Multicolor")
        val TREATMENT_NAMES = mapOf(
            "borderless" to "Borderless", "showcase" to "Showcase", "extendedart" to "Extended art",
            "fullart" to "Full art", "etched" to "Etched", "promo" to "Promo", "retro" to "Retro frame",
        )
        val TYPES = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land", "Battle")

        private fun money(v: Double) = if (v % 1.0 == 0.0) "$${v.toInt()}" else "$" + "%.2f".format(java.util.Locale.US, v)

        /**
         * Picks the box a card belongs in: the first box with rules that fit, in Auto-sort order.
         * If none fits, the [fallback] box, else the first box that takes any card.
         */
        fun <T> pick(boxes: List<Pair<T, BoxRules>>, card: CardFacts, fallback: T? = null): T? =
            boxes.firstOrNull { !it.second.isCatchAll && it.second.matches(card) }?.first
                ?: fallback
                ?: boxes.firstOrNull { it.second.isCatchAll }?.first

        /**
         * For the box editor's preview: which [cards] fit [draft], and which of those a box
         * checked earlier would take first. Boxes that take any card never take cards first.
         */
        fun <T> overlap(draft: BoxRules, ahead: List<Pair<T, BoxRules>>, cards: List<CardFacts>): Overlap<T> {
            val rulesAhead = ahead.filter { !it.second.isCatchAll }
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

/** Result of [BoxRules.overlap]: indexes of cards that fit, and the ones each earlier box takes first. */
data class Overlap<T>(val fits: List<Int>, val takenBy: List<Pair<T, List<Int>>>)

/** Ready-made starting points shown when creating a box. */
data class BoxPreset(val name: String, val kind: String, val rules: BoxRules)

val BOX_PRESETS = listOf(
    BoxPreset("Bulk", "box", BoxRules(rarities = setOf("common", "uncommon"), maxPrice = 1.0)),
    BoxPreset("Rares & Mythics", "binder", BoxRules(rarities = setOf("rare", "mythic"))),
    BoxPreset("Borderless & Showcase", "binder", BoxRules(treatments = setOf("borderless", "showcase", "extendedart", "fullart"))),
    BoxPreset("Foils", "binder", BoxRules(finish = "foil")),
    BoxPreset("High value", "binder", BoxRules(minPrice = 10.0)),
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
