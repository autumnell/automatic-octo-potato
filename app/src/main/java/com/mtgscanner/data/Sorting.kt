package com.mtgscanner.data

/** How a box is split inside, so the app can say exactly where a card goes ("Blue", "R", "MH3"). */
enum class Divider(val key: String, val label: String) {
    NONE("", "None"),
    COLOR("color", "Color"),
    LETTER("letter", "First letter"),
    SET("set", "Set"),
    TYPE("type", "Card type"),
    RARITY("rarity", "Rarity");

    companion object {
        /** The first level of a [BoxLayout] key ("color>letter" gives COLOR). */
        fun of(key: String): Divider {
            val first = key.substringBefore('>')
            if (first.isEmpty()) return NONE
            return entries.firstOrNull { it.key == first } ?: NONE
        }
    }
}

/**
 * How a box is laid out inside: up to two levels of dividers (Color, then Letter) and the order of cards
 * behind the last one (name, or collector number). Saved in [com.mtgscanner.data.db.Box.divider] as
 * "color", "color>letter", "set>#" ("#" = number order).
 */
data class BoxLayout(val first: Divider = Divider.NONE, val second: Divider = Divider.NONE, val byNumber: Boolean = false) {
    val levels: List<Divider> get() = listOf(first, second).filter { it != Divider.NONE }.distinct()
    val hasDividers: Boolean get() = levels.isNotEmpty()

    val key: String get() = (levels.map { it.key } + (if (byNumber) listOf("#") else emptyList())).joinToString(">")

    /** "Color → First letter", "Set · by number", or "None". */
    fun label(): String {
        val parts = levels.joinToString(" → ") { it.label }.ifEmpty { "None" }
        return if (byNumber) "$parts · by number" else parts
    }

    companion object {
        fun of(key: String): BoxLayout {
            val parts = key.split('>').map { it.trim() }.filter { it.isNotEmpty() }
            val divs = parts.filter { it != "#" }.mapNotNull { k -> Divider.entries.firstOrNull { it.key == k && it != Divider.NONE } }
            return BoxLayout(divs.getOrElse(0) { Divider.NONE }, divs.getOrElse(1) { Divider.NONE }, "#" in parts)
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

    /** Where in a box a card goes, by the box's [divider]. Null when the box has no dividers. */
    fun spot(divider: Divider, name: String, card: CardFacts): String? = when (divider) {
        Divider.NONE -> null
        Divider.COLOR -> when {
            card.colors.isEmpty() -> if (card.typeLine.contains("Land", ignoreCase = true)) "Lands" else "Colorless"
            card.colors.length > 1 -> "Multicolor"
            else -> BoxRules.COLOR_NAMES[card.colors] ?: card.colors
        }
        Divider.LETTER -> NameKey.letter(name).toString()
        Divider.SET -> card.setCode.uppercase()
        Divider.TYPE -> BoxRules.TYPES.firstOrNull { card.typeLine.contains(it, ignoreCase = true) } ?: "Other"
        Divider.RARITY -> card.rarity.replaceFirstChar(Char::uppercase)
    }

    /** Where in a box a card goes with two levels: "Blue → C". Null when the box has no dividers. */
    fun spot(layout: BoxLayout, name: String, card: CardFacts): String? =
        layout.levels.mapNotNull { spot(it, name, card) }.joinToString(SPOT_SEP).ifEmpty { null }

    /** Between the levels of a two-level divider label. */
    const val SPOT_SEP = " → "

    /** Front-to-back order of two-level labels from [spot]: by the first level, then the second. */
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

    /** Sort order for divider labels, so a box can be listed front to back. */
    fun spotOrder(divider: Divider): Comparator<String> = when (divider) {
        Divider.COLOR -> compareBy { listOf("White", "Blue", "Black", "Red", "Green", "Multicolor", "Colorless", "Lands").indexOf(it).let { i -> if (i < 0) 99 else i } }
        Divider.RARITY -> compareBy { listOf("Mythic", "Rare", "Uncommon", "Common").indexOf(it).let { i -> if (i < 0) 99 else i } }
        Divider.TYPE -> compareBy { (BoxRules.TYPES + "Other").indexOf(it).let { i -> if (i < 0) 99 else i } }
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

    fun position(divider: Divider, name: String, card: CardFacts, contents: List<Pair<String, CardFacts>>): Position =
        position(BoxLayout(divider), name, card, contents)

    /** Where a card goes among the cards behind the same divider(s), in name or (with [BoxLayout.byNumber]) number order. */
    fun position(layout: BoxLayout, name: String, card: CardFacts, contents: List<Pair<String, CardFacts>>): Position {
        val spot = spot(layout, name, card)
        val key = nameKey(name)
        val same = contents.filter { spot(layout, it.first, it.second) == spot }
        if (same.any { nameKey(it.first) == key }) return Position(spot, null, null, withSame = true)
        if (layout.byNumber) {
            val mine = numberKey(card)
            val sorted = same.map { it.first to numberKey(it.second) }.sortedBy { it.second }
            return Position(spot, sorted.lastOrNull { it.second <= mine }?.first, sorted.firstOrNull { it.second > mine }?.first)
        }
        val sorted = same.map { it.first }.distinct().sortedBy(::nameKey)
        val after = sorted.lastOrNull { nameKey(it) < key }
        val before = sorted.firstOrNull { nameKey(it) > key }
        return Position(spot, after, before)
    }

    /**
     * Divider labels to print for a box: every spot its cards sit behind, front to back. A box with one
     * level and a fixed list (color, rarity, type, letter) gets the whole list, so new cards have a place.
     */
    fun tabs(layout: BoxLayout, cards: List<Pair<String, CardFacts>>): List<String> {
        if (!layout.hasDividers) return emptyList()
        val used = cards.mapNotNull { spot(layout, it.first, it.second) }.toSet()
        val fixed: List<String> = if (layout.levels.size == 1) when (layout.first) {
            Divider.COLOR -> listOf("White", "Blue", "Black", "Red", "Green", "Multicolor", "Colorless", "Lands")
            Divider.RARITY -> listOf("Mythic", "Rare", "Uncommon", "Common")
            Divider.TYPE -> BoxRules.TYPES + "Other"
            Divider.LETTER -> listOf("#") + ('A'..'Z').map { it.toString() }
            else -> emptyList()
        } else emptyList()
        return (fixed + used).distinct().sortedWith(spotOrder(layout))
    }

    /** "Rares → Blue → between Counterspell and Cryptic Command". */
    fun where(boxName: String, pos: Position): String =
        listOfNotNull(boxName, pos.spot, pos.text()).joinToString(" → ")

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
                    if (!from.rules.isOpen && from.rules.matches(f, priceSlack)) continue
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
