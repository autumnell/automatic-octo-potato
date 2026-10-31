package com.mtgscanner.data

/** How a box is split inside, so the app can say exactly where a card goes ("Blue", "R", "MH3"). */
enum class Divider(val key: String, val label: String) {
    NONE("", "None"),
    COLOR("color", "Color"),
    PAIRS("pairs", "Color pairs"),
    IDENTITY("identity", "Color identity"),
    TYPE("type", "Card type"),
    MANA("cmc", "Mana value"),
    LETTER("letter", "First letter"),
    SET("set", "Set"),
    RELEASE("release", "Set, oldest first"),
    RARITY("rarity", "Rarity"),
    PRICE("price", "Price band"),
    FOIL("foil", "Foils apart");

    companion object {
        /** The first level of a [BoxLayout] key ("color>letter" gives COLOR). */
        fun of(key: String): Divider {
            val first = key.substringBefore('>')
            if (first.isEmpty()) return NONE
            return entries.firstOrNull { it.key == first } ?: NONE
        }
    }
}

/** The order of cards behind the last divider. [key] is how it's saved at the end of a [BoxLayout] key. */
enum class CardOrder(val key: String, val label: String) {
    NAME("", "Name"),
    NUMBER("#", "Set number"),
    PRICE("$", "Price, high first"),
}

/**
 * How a box is laid out inside: up to three levels of dividers (Color, then Type, then Mana value) and the order
 * of cards behind the last one. Saved in [com.mtgscanner.data.db.Box.divider] as "color", "color>type",
 * "set>#" ("#" = number order, "$" = price order).
 */
data class BoxLayout(val levels: List<Divider> = emptyList(), val order: CardOrder = CardOrder.NAME) {
    constructor(first: Divider, second: Divider = Divider.NONE, byNumber: Boolean = false) :
        this(clean(listOf(first, second)), if (byNumber) CardOrder.NUMBER else CardOrder.NAME)

    val first: Divider get() = levels.getOrElse(0) { Divider.NONE }
    val second: Divider get() = levels.getOrElse(1) { Divider.NONE }
    val hasDividers: Boolean get() = levels.isNotEmpty()
    val byNumber: Boolean get() = order == CardOrder.NUMBER
    /** Placing a card needs its facts: there are dividers, or cards aren't in plain name order. */
    val needsFacts: Boolean get() = hasDividers || order != CardOrder.NAME

    val key: String get() = (levels.map { it.key } + listOfNotNull(order.key.ifEmpty { null })).joinToString(">")

    /** "Color → First letter", "Set · by number", or "None". */
    fun label(): String {
        val parts = levels.joinToString(" → ") { it.label }.ifEmpty { if (order == CardOrder.NAME) "None" else "No dividers" }
        return when (order) {
            CardOrder.NAME -> parts
            CardOrder.NUMBER -> "$parts · by number"
            CardOrder.PRICE -> "$parts · by price"
        }
    }

    companion object {
        const val MAX_LEVELS = 3

        fun clean(levels: List<Divider>): List<Divider> = levels.filter { it != Divider.NONE }.distinct().take(MAX_LEVELS)

        fun of(key: String): BoxLayout {
            val parts = key.split('>').map { it.trim() }.filter { it.isNotEmpty() }
            val divs = parts.mapNotNull { k -> Divider.entries.firstOrNull { it.key == k && it != Divider.NONE } }
            val order = CardOrder.entries.firstOrNull { it.key.isNotEmpty() && it.key in parts } ?: CardOrder.NAME
            return BoxLayout(clean(divs), order)
        }
    }
}

/** Ready-made box orders, offered when making a box. [key] is a [BoxLayout] key. */
data class SortPreset(val label: String, val key: String) {
    companion object {
        val ALL = listOf(
            SortPreset("Color → Type → A–Z", "color>type"),
            SortPreset("Set → Number", "set>#"),
            SortPreset("Set by release → Number", "release>#"),
            SortPreset("A–Z", "letter"),
            SortPreset("Color → Mana value → A–Z", "color>cmc"),
            SortPreset("Rarity → Set → Number", "rarity>set>#"),
            SortPreset("Color identity → A–Z", "identity"),
            SortPreset("Price, high to low", "$"),
        )

        /** The preset with exactly this key, or null for a custom order. */
        fun of(key: String): SortPreset? = BoxLayout.of(key).key.let { k -> ALL.firstOrNull { it.key == k } }
    }
}

/**
 * Release dates of sets, by lower-case code, noted whenever card facts are built. Lets "Set, oldest first"
 * order divider labels, which are only set codes. Unknown sets go last.
 */
object SetDates {
    private val dates = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun note(code: String, date: String?) {
        if (code.isNotEmpty() && !date.isNullOrEmpty()) dates.putIfAbsent(code.lowercase(), date)
    }

    fun of(code: String): String? = dates[code.lowercase()]
}

/** Names for groups of colors: guilds, shards and wedges. */
object ColorGroups {
    val PAIRS = listOf(
        "WU" to "Azorius", "UB" to "Dimir", "BR" to "Rakdos", "RG" to "Gruul", "GW" to "Selesnya",
        "WB" to "Orzhov", "UR" to "Izzet", "BG" to "Golgari", "RW" to "Boros", "GU" to "Simic",
    )
    val TRIOS = listOf(
        "WUB" to "Esper", "UBR" to "Grixis", "BRG" to "Jund", "RGW" to "Naya", "GWU" to "Bant",
        "WBG" to "Abzan", "URW" to "Jeskai", "BGU" to "Sultai", "RWB" to "Mardu", "GUR" to "Temur",
    )
    const val FOUR = "Four colors"
    const val FIVE = "Five colors"

    /** Every multicolor label, in the order they sit in a box. */
    val MULTI: List<String> = PAIRS.map { it.second } + TRIOS.map { it.second } + FOUR + FIVE

    /** "White", "Azorius", "Esper", "Four colors"… for these colors; null for none. */
    fun name(colors: String): String? {
        val set = colors.uppercase().filter { it in "WUBRG" }.toSet()
        return when (set.size) {
            0 -> null
            1 -> BoxRules.COLOR_NAMES[set.first().toString()]
            2 -> PAIRS.firstOrNull { it.first.toSet() == set }?.second
            3 -> TRIOS.firstOrNull { it.first.toSet() == set }?.second
            4 -> FOUR
            else -> FIVE
        }
    }
}

/**
 * One way to file names everywhere (name ranges, dividers, Browse sections, order in a box):
 * case and accents don't matter and a leading "The" is skipped, so "Éowyn" files under E and
 * "The Wandering Emperor" under W.
 */
object NameKey {
    private val MARKS = Regex("\\p{Mn}+")

    fun of(name: String): String {
        var s = name.trim().lowercase()
        if (s.any { it.code > 127 }) {
            s = s.replace("æ", "ae").replace("œ", "oe").replace("ø", "o").replace("ß", "ss")
            s = MARKS.replace(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD), "")
        }
        return s.removePrefix("the ").trim()
    }

    /** 'A' to 'Z', or '#' for a name starting with a digit or symbol. */
    fun letter(name: String): Char {
        val first = of(name).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: return '#'
        return if (first in 'A'..'Z') first else '#'
    }
}

/** What the sorting rules need to know about a box. Boxes come in Auto-sort order. */
data class BoxInfo(
    val id: Long,
    val rules: BoxRules,
    val kind: String = "box",
    /** Most cards the box holds; 0 means no limit. */
    val capacity: Int = 0,
    /** Cards in it now. */
    val count: Int = 0,
    val fallback: Boolean = false,
) {
    val isFull: Boolean get() = capacity > 0 && count >= capacity
}

/**
 * App-wide sorting choices on top of each box's rules.
 * [playsetLimit]: keep this many of each card in your main boxes (0 = off); extra copies go to [extrasBoxId].
 * [bulkFloor]: cards cheaper than this go to [bulkBoxId], whatever else fits.
 */
data class SortPolicy(
    val playsetLimit: Int = 0,
    val extrasBoxId: Long? = null,
    val bulkFloor: Double? = null,
    val bulkBoxId: Long? = null,
)

/** Why a card goes where it goes, for the scanner's label. */
enum class SortReason { RULES, BULK, EXTRA, FALLBACK }

data class SortDecision(val boxId: Long, val reason: SortReason, val boxFull: Boolean = false)

object Sorting {
    /**
     * Picks the box for a card.
     * 1. Under the bulk price floor → the bulk box. A card with no price yet isn't bulk.
     * 2. Already own [SortPolicy.playsetLimit] copies in main boxes → the extras box.
     * 3. The first box whose rules fit and that has room, else the first that fits even if full.
     * 4. The fallback box, else the first open box (takes any card, or any card in its name range) that fits.
     *    Trade binders and decks never catch leftovers.
     * Open boxes (see [BoxRules.isOpen]) never take a card ahead of a box with real rules.
     * [ownedInMain] counts copies of this card already in main boxes (see [isMainBox]).
     */
    fun decide(boxes: List<BoxInfo>, card: CardFacts, policy: SortPolicy = SortPolicy(), ownedInMain: Int = 0): SortDecision? {
        val ids = boxes.associateBy { it.id }
        // A "Tokens & art cards" box takes them ahead of the bulk floor, extras and every other box.
        if (card.tokenOrArt) {
            val t = boxes.filter { it.rules.tokens && it.rules.matches(card) }
            (t.firstOrNull { !it.isFull } ?: t.firstOrNull())?.let { return SortDecision(it.id, SortReason.RULES, it.isFull) }
        }
        val floor = policy.bulkFloor
        val bulk = policy.bulkBoxId?.let(ids::get)
        val price = card.price
        if (floor != null && bulk != null && price != null && price < floor) return SortDecision(bulk.id, SortReason.BULK, bulk.isFull)
        val extras = policy.extrasBoxId?.let(ids::get)
        if (policy.playsetLimit > 0 && extras != null && ownedInMain >= policy.playsetLimit) {
            return SortDecision(extras.id, SortReason.EXTRA, extras.isFull)
        }
        val fits = boxes.filter { !it.rules.isOpen && it.rules.matches(card) }
        (fits.firstOrNull { !it.isFull } ?: fits.firstOrNull())?.let { return SortDecision(it.id, SortReason.RULES, it.isFull) }
        val open = boxes.filter { it.rules.isOpen && it.kind != "trade" && it.kind != "deck" && it.rules.matches(card) }
        val fb = fallbackFor(boxes, card)
            ?: open.firstOrNull { !it.isFull }
            ?: open.firstOrNull()
            ?: return null
        return SortDecision(fb.id, SortReason.FALLBACK, fb.isFull)
    }

    /**
     * The fallback box for a card. A fallback box split by name only takes names in its range; the other
     * halves (same rules and kind, another name range) take the rest.
     */
    private fun fallbackFor(boxes: List<BoxInfo>, card: CardFacts): BoxInfo? {
        val fb = boxes.firstOrNull { it.fallback } ?: return null
        if (fb.rules.inNameRange(card.name)) return fb
        val base = fb.rules.copy(nameFrom = null, nameTo = null)
        return boxes.firstOrNull {
            it.id != fb.id && it.kind == fb.kind && it.rules.copy(nameFrom = null, nameTo = null) == base && it.rules.inNameRange(card.name)
        }
    }

    /** Main boxes hold the copies you keep: not trade binders, decks, the extras box or the bulk box. */
    fun isMainBox(box: BoxInfo, policy: SortPolicy): Boolean =
        box.kind != "trade" && box.kind != "deck" && box.id != policy.extrasBoxId && box.id != policy.bulkBoxId

    /** The usual color order, front to back. "Multicolor" stands for every multicolor group in Color pairs. */
    val DEFAULT_COLOR_ORDER = listOf("White", "Blue", "Black", "Red", "Green", "Multicolor", "Colorless", "Lands")

    /** Front-to-back color order for color dividers; set from Settings (see SortPrefs.colorOrder). */
    @Volatile var colorOrder: List<String> = DEFAULT_COLOR_ORDER

    /** [order] with any missing color label added in its usual place, and anything else dropped. */
    fun fullColorOrder(order: List<String>): List<String> {
        val kept = order.filter { it in DEFAULT_COLOR_ORDER }.distinct()
        return kept + DEFAULT_COLOR_ORDER.filter { it !in kept }
    }

    val PRICE_BANDS = listOf("$20+", "$5–20", "$1–5", "Under $1", "No price")
    val MANA_SPOTS = (0..6).map { it.toString() } + listOf("7+", "Lands")
    val FOIL_SPOTS = listOf("Non-foil", "Foil")

    private fun isLand(card: CardFacts) = card.typeLine.contains("Land", ignoreCase = true)

    /** Where in a box a card goes, by the box's [divider]. Null when the box has no dividers. */
    fun spot(divider: Divider, name: String, card: CardFacts): String? = when (divider) {
        Divider.NONE -> null
        Divider.COLOR -> when {
            card.colors.isEmpty() -> if (isLand(card)) "Lands" else "Colorless"
            card.colors.length > 1 -> "Multicolor"
            else -> BoxRules.COLOR_NAMES[card.colors] ?: card.colors
        }
        Divider.PAIRS -> ColorGroups.name(card.colors) ?: if (isLand(card)) "Lands" else "Colorless"
        Divider.IDENTITY -> ColorGroups.name(card.colorIdentity) ?: "Colorless"
        Divider.LETTER -> NameKey.letter(name).toString()
        Divider.SET, Divider.RELEASE -> card.setCode.uppercase()
        Divider.TYPE -> BoxRules.TYPES.firstOrNull { card.typeLine.contains(it, ignoreCase = true) } ?: "Other"
        Divider.RARITY -> card.rarity.replaceFirstChar(Char::uppercase)
        Divider.MANA -> when {
            isLand(card) && card.cmc == 0.0 -> "Lands"
            card.cmc >= 7 -> "7+"
            else -> card.cmc.toInt().toString()
        }
        Divider.PRICE -> when (val p = card.price) {
            null -> "No price"
            else -> when {
                p >= 20 -> "$20+"
                p >= 5 -> "$5–20"
                p >= 1 -> "$1–5"
                else -> "Under $1"
            }
        }
        Divider.FOIL -> if (card.foil) "Foil" else "Non-foil"
    }

    /** Where in a box a card goes with every level: "Blue → Creature → 2". Null when the box has no dividers. */
    fun spot(layout: BoxLayout, name: String, card: CardFacts): String? =
        layout.levels.mapNotNull { spot(it, name, card) }.joinToString(SPOT_SEP).ifEmpty { null }

    /** Between the levels of a two-level divider label. */
    const val SPOT_SEP = " → "

    /** Front-to-back order of labels from [spot]: by the first level, then the next. */
    fun spotOrder(layout: BoxLayout): Comparator<String> {
        val levels = layout.levels
        if (levels.size <= 1) return spotOrder(levels.firstOrNull() ?: Divider.NONE)
        return Comparator { a, b ->
            val pa = a.split(SPOT_SEP)
            val pb = b.split(SPOT_SEP)
            for ((i, d) in levels.withIndex()) {
                val c = spotOrder(d).compare(pa.getOrElse(i) { "" }, pb.getOrElse(i) { "" })
                if (c != 0) return@Comparator c
            }
            0
        }
    }

    private fun byList(list: List<String>): Comparator<String> =
        compareBy<String> { list.indexOf(it).let { i -> if (i < 0) 999 else i } }.thenBy { it }

    /** Color pairs and identity: the color order, with every multicolor group where "Multicolor" sits. */
    private fun groupOrder(): List<String> =
        fullColorOrder(colorOrder).flatMap { if (it == "Multicolor") ColorGroups.MULTI else listOf(it) }

    /** Sort order for divider labels, so a box can be listed front to back. */
    fun spotOrder(divider: Divider): Comparator<String> = when (divider) {
        Divider.COLOR -> byList(fullColorOrder(colorOrder))
        Divider.PAIRS, Divider.IDENTITY -> byList(groupOrder())
        Divider.RARITY -> byList(listOf("Mythic", "Rare", "Uncommon", "Common"))
        Divider.TYPE -> byList(BoxRules.TYPES + "Other")
        Divider.MANA -> byList(MANA_SPOTS)
        Divider.PRICE -> byList(PRICE_BANDS)
        Divider.FOIL -> byList(FOIL_SPOTS)
        Divider.RELEASE -> compareBy<String> { SetDates.of(it) ?: "9999" }.thenBy { it }
        // "#" (digits and symbols) goes in front, like a real box.
        else -> naturalOrder()
    }

    /**
     * Where a card sits among the others behind the same divider, in name order:
     * the names just before and after it. [contents] are the cards already in the box (name and facts).
     */
    data class Position(
        /** The divider label, or null when the box has none. */
        val spot: String?,
        /** The name just before it, or null when it goes first. */
        val after: String?,
        /** The name just after it, or null when it goes last. */
        val before: String?,
        /** Another copy or printing with the same name is already there. */
        val withSame: Boolean = false,
    ) {
        /** "between Counterspell and Cryptic Command", "after Opt", "first", "with its copies". */
        fun text(): String = when {
            withSame -> "with its copies"
            after != null && before != null -> "between $after and $before"
            after != null -> "after $after"
            before != null -> "before $before"
            else -> "first card"
        }
    }

    /** Name order inside a box: case, accents and a leading "The" don't matter (see [NameKey]). */
    fun nameKey(name: String): String = NameKey.of(name)

    /** Collector number order: set, then the number's digits, then the rest ("12a" after "12"). */
    fun numberKey(card: CardFacts): String {
        val digits = card.number.takeWhile { it.isDigit() }
        return card.setCode.lowercase() + "|" + digits.padStart(6, '0') + "|" + card.number.drop(digits.length).lowercase()
    }

    /** The order of cards behind one divider: by name, collector number, or price (dearest first, no price last). */
    fun cardOrder(order: CardOrder): Comparator<Pair<String, CardFacts>> = when (order) {
        CardOrder.NAME -> compareBy { nameKey(it.first) }
        CardOrder.NUMBER -> compareBy<Pair<String, CardFacts>> { numberKey(it.second) }.thenBy { nameKey(it.first) }
        CardOrder.PRICE -> compareByDescending<Pair<String, CardFacts>> { it.second.price ?: -1.0 }.thenBy { nameKey(it.first) }
    }

    /** Every card of a box front to back: by divider, then [BoxLayout.order]. */
    fun boxOrder(layout: BoxLayout): Comparator<Pair<String, CardFacts>> {
        val spots = spotOrder(layout)
        val cards = cardOrder(layout.order)
        return Comparator { a, b ->
            val sa = spot(layout, a.first, a.second).orEmpty()
            val sb = spot(layout, b.first, b.second).orEmpty()
            spots.compare(sa, sb).takeIf { it != 0 } ?: cards.compare(a, b)
        }
    }

    fun position(divider: Divider, name: String, card: CardFacts, contents: List<Pair<String, CardFacts>>): Position =
        position(BoxLayout(divider), name, card, contents)

    /** Where a card goes among the cards behind the same divider(s), in the box's [BoxLayout.order]. */
    fun position(layout: BoxLayout, name: String, card: CardFacts, contents: List<Pair<String, CardFacts>>): Position {
        val spot = spot(layout, name, card)
        val key = nameKey(name)
        val same = contents.filter { spot(layout, it.first, it.second) == spot }
        if (same.any { nameKey(it.first) == key }) return Position(spot, null, null, withSame = true)
        val order = cardOrder(layout.order)
        val mine = name to card
        val sorted = same.sortedWith(order)
        val after = sorted.lastOrNull { order.compare(it, mine) <= 0 }?.first
        val before = sorted.firstOrNull { order.compare(it, mine) > 0 }?.first
        return Position(spot, after, before)
    }

    /** How many of [contents] sit in front of this card in the box, front to back. */
    fun indexIn(layout: BoxLayout, name: String, card: CardFacts, contents: List<Pair<String, CardFacts>>): Int {
        val order = boxOrder(layout)
        val mine = name to card
        return contents.count { order.compare(it, mine) < 0 }
    }

    /**
     * The row (1-based) of a box with [rows] rows that holds the card at [index] of [total], filled front row
     * first. With a [capacity], each row holds an equal share of it; without, the cards are shared out evenly.
     * Null for a one-row box.
     */
    fun row(index: Int, total: Int, rows: Int, capacity: Int = 0): Int? {
        if (rows <= 1) return null
        val perRow = if (capacity > 0) (capacity + rows - 1) / rows else ((total.coerceAtLeast(1) + rows - 1) / rows)
        return (index / perRow.coerceAtLeast(1) + 1).coerceIn(1, rows)
    }

    /**
     * Divider labels to print for a box: every spot its cards sit behind, front to back. A box with one
     * level and a fixed list (color, rarity, type, letter…) gets the whole list, so new cards have a place.
     */
    fun tabs(layout: BoxLayout, cards: List<Pair<String, CardFacts>>): List<String> {
        if (!layout.hasDividers) return emptyList()
        val used = cards.mapNotNull { spot(layout, it.first, it.second) }.toSet()
        val fixed: List<String> = if (layout.levels.size == 1) when (layout.first) {
            Divider.COLOR -> fullColorOrder(colorOrder)
            Divider.RARITY -> listOf("Mythic", "Rare", "Uncommon", "Common")
            Divider.TYPE -> BoxRules.TYPES + "Other"
            Divider.LETTER -> listOf("#") + ('A'..'Z').map { it.toString() }
            Divider.MANA -> MANA_SPOTS
            Divider.PRICE -> PRICE_BANDS
            Divider.FOIL -> FOIL_SPOTS
            else -> emptyList()
        } else emptyList()
        return (fixed + used).distinct().sortedWith(spotOrder(layout))
    }

    /** "Rares → Blue → between Counterspell and Cryptic Command". */
    fun where(boxName: String, pos: Position, row: Int? = null): String =
        listOfNotNull(boxName, row?.let { "Row $it" }, pos.spot, pos.text()).joinToString(SPOT_SEP)

    /**
     * A card that should live somewhere else: its box has rules it no longer fits, it sits in a box that takes
     * any card while a rule box now fits it, or it's in the bulk box and now costs more than the bulk floor.
     * Trade binders and decks are left alone; extras only leave for the bulk box.
     */
    data class Move<T>(val item: T, val from: Long, val to: Long)

    /**
     * Plans the moves, counting cards in and out of each box as it goes so full boxes stay full.
     * [copies] is how many cards an item is (for box capacity); [ownedInMain] counts copies already in main
     * boxes, for cards leaving the bulk box under the keep-a-playset rule. [priceSlack] (0.1 = 10%) lets a card
     * stay in a box whose price rule it only just misses, so cards near a price line don't bounce between
     * boxes. [pinned] items stay where they are.
     */
    fun <T> misplaced(
        items: List<T>,
        boxOf: (T) -> Long,
        facts: (T) -> CardFacts,
        boxes: List<BoxInfo>,
        policy: SortPolicy = SortPolicy(),
        copies: (T) -> Int = { 1 },
        ownedInMain: (T) -> Int = { 0 },
        priceSlack: Double = 0.0,
        pinned: (T) -> Boolean = { false },
    ): List<Move<T>> {
        val live = boxes.toMutableList()
        val index = HashMap<Long, Int>()
        boxes.forEachIndexed { i, b -> index[b.id] = i }
        val noPlayset = policy.copy(playsetLimit = 0)
        val out = ArrayList<Move<T>>()
        for (item in items) {
            if (pinned(item)) continue
            val fromAt = index[boxOf(item)] ?: continue
            val from = live[fromAt]
            if (from.kind == "trade" || from.kind == "deck") continue
            val f = facts(item)
            val target: SortDecision = when (from.id) {
                policy.extrasBoxId -> decide(live, f, noPlayset)?.takeIf { it.reason == SortReason.BULK } ?: continue
                policy.bulkBoxId -> {
                    // Stays while it has no price or still costs under the floor (plus the buffer).
                    val floor = policy.bulkFloor
                    val p = f.price
                    if (floor == null || p == null || p < floor * (1 + priceSlack)) continue
                    decide(live, f, policy, ownedInMain(item)) ?: continue
                }
                else -> {
                    // A token left in another box moves once a tokens box takes it.
                    val tokenBox = f.tokenOrArt && !from.rules.tokens && live.any { it.rules.tokens && it.rules.matches(f) }
                    if (!tokenBox && !from.rules.isOpen && from.rules.matches(f, priceSlack)) continue
                    val d = decide(live, f, noPlayset) ?: continue
                    // A card in an open box that fits its name range only moves when a rule box fits it.
                    if (from.rules.isOpen && from.rules.matches(f) && d.reason != SortReason.RULES && d.reason != SortReason.BULK) continue
                    d
                }
            }
            if (target.boxId == from.id) continue
            val n = copies(item)
            live[fromAt] = live[fromAt].let { it.copy(count = it.count - n) }
            index[target.boxId]?.let { i -> live[i] = live[i].let { it.copy(count = it.count + n) } }
            out += Move(item, from.id, target.boxId)
        }
        return out
    }

    /**
     * For "Sort out": where a card in [sourceId] should go, or null when it stays. It stays when its own box is
     * the first that takes it in Auto-sort order (rules, room, bulk floor), or when only a fallback or open box
     * would take it. [boxes] include the source box, counted without this card.
     */
    fun sortOutTarget(boxes: List<BoxInfo>, sourceId: Long, card: CardFacts, policy: SortPolicy): SortDecision? {
        val d = decide(boxes, card, policy.copy(playsetLimit = 0)) ?: return null
        if (d.boxId == sourceId) return null
        if (d.reason != SortReason.RULES && d.reason != SortReason.BULK) return null
        return d
    }

    /**
     * Splits a box's cards into two halves by first letter, for "split a full box". Returns the letter the
     * second half starts at. Pass only the cards in the box's name range. Letters follow [BoxRules.nameLetter];
     * names starting with a digit or symbol fit any range, so they stay with the first half.
     */
    fun splitLetter(names: List<Pair<String, Int>>): Char? {
        val grouped = names.groupBy { BoxRules.nameLetter(it.first) }.mapValues { (_, v) -> v.sumOf { it.second } }
        val other = grouped['#'] ?: 0
        val byLetter = grouped.filterKeys { it != '#' }.toSortedMap()
        if (byLetter.size < 2) return null
        val total = other + byLetter.values.sum()
        var run = other
        var first = true
        for ((letter, n) in byLetter) {
            if (!first && run + n / 2 >= total / 2) return letter
            run += n
            first = false
        }
        return byLetter.lastKey()
    }

    /**
     * Copies in each half when a box splits at [at]: the second half gets names from [at] on, the first the rest
     * (names starting with a digit or symbol stay in the first). Pass only the cards in the box's name range.
     */
    fun splitCounts(names: List<Pair<String, Int>>, at: Char): Pair<Int, Int> {
        var first = 0
        var second = 0
        for ((name, n) in names) {
            val letter = BoxRules.nameLetter(name)
            if (letter != '#' && letter >= at) second += n else first += n
        }
        return first to second
    }

    /** Letters a box with name range [from]–[to] can split at, so each half keeps at least one letter. */
    fun splitLetters(from: String?, to: String?): List<Char> {
        val lo = from?.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: 'A'
        val hi = to?.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: 'Z'
        return if (hi <= lo) emptyList() else ((lo + 1)..hi).toList()
    }
}
