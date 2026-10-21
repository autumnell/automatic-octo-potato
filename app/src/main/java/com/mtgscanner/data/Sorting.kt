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
        fun of(key: String): Divider = entries.firstOrNull { it.key == key } ?: NONE
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
     * 1. Under the bulk price floor → the bulk box.
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
        if (floor != null && bulk != null && (card.price ?: 0.0) < floor) return SortDecision(bulk.id, SortReason.BULK, bulk.isFull)
        val extras = policy.extrasBoxId?.let(ids::get)
        if (policy.playsetLimit > 0 && extras != null && ownedInMain >= policy.playsetLimit) {
            return SortDecision(extras.id, SortReason.EXTRA, extras.isFull)
        }
        val fits = boxes.filter { !it.rules.isOpen && it.rules.matches(card) }
        (fits.firstOrNull { !it.isFull } ?: fits.firstOrNull())?.let { return SortDecision(it.id, SortReason.RULES, it.isFull) }
        val open = boxes.filter { it.rules.isOpen && it.kind != "trade" && it.kind != "deck" && it.rules.matches(card) }
        // A fallback box that is half of a split box only takes cards in its name range.
        val fb = boxes.firstOrNull { it.fallback && (!it.rules.isOpen || it.rules.matches(card)) }
            ?: open.firstOrNull { !it.isFull }
            ?: open.firstOrNull()
            ?: return null
        return SortDecision(fb.id, SortReason.FALLBACK, fb.isFull)
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
        Divider.LETTER -> name.trimStart().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.let {
            if (it.isLetter()) it.toString() else "#"
        } ?: "#"
        Divider.SET -> card.setCode.uppercase()
        Divider.TYPE -> BoxRules.TYPES.firstOrNull { card.typeLine.contains(it, ignoreCase = true) } ?: "Other"
        Divider.RARITY -> card.rarity.replaceFirstChar(Char::uppercase)
    }

    /** Sort order for divider labels, so a box can be listed front to back. */
    fun spotOrder(divider: Divider): Comparator<String> = when (divider) {
        Divider.COLOR -> compareBy { listOf("White", "Blue", "Black", "Red", "Green", "Multicolor", "Colorless", "Lands").indexOf(it).let { i -> if (i < 0) 99 else i } }
        Divider.RARITY -> compareBy { listOf("Mythic", "Rare", "Uncommon", "Common").indexOf(it).let { i -> if (i < 0) 99 else i } }
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

    /** Name order inside a box: case and a leading "The" don't matter. */
    fun nameKey(name: String): String = name.trim().lowercase().removePrefix("the ").trim()

    fun position(divider: Divider, name: String, card: CardFacts, contents: List<Pair<String, CardFacts>>): Position {
        val spot = spot(divider, name, card)
        val key = nameKey(name)
        val same = contents.filter { spot(divider, it.first, it.second) == spot }.map { it.first }.distinct()
        if (same.any { nameKey(it) == key }) return Position(spot, null, null, withSame = true)
        val sorted = same.sortedBy(::nameKey)
        val after = sorted.lastOrNull { nameKey(it) < key }
        val before = sorted.firstOrNull { nameKey(it) > key }
        return Position(spot, after, before)
    }

    /** "Rares → Blue → between Counterspell and Cryptic Command". */
    fun where(boxName: String, pos: Position): String =
        listOfNotNull(boxName, pos.spot, pos.text()).joinToString(" → ")

    /**
     * A card that should live somewhere else: its box has rules it no longer fits, or it sits in a
     * box that takes any card while a rule box now fits it. Trade binders and decks are left alone.
     */
    data class Move<T>(val item: T, val from: Long, val to: Long)

    fun <T> misplaced(
        items: List<T>,
        boxOf: (T) -> Long,
        facts: (T) -> CardFacts,
        boxes: List<BoxInfo>,
        policy: SortPolicy = SortPolicy(),
    ): List<Move<T>> {
        val ids = boxes.associateBy { it.id }
        return items.mapNotNull { item ->
            val from = ids[boxOf(item)] ?: return@mapNotNull null
            if (from.kind == "trade" || from.kind == "deck") return@mapNotNull null
            if (from.id == policy.extrasBoxId || from.id == policy.bulkBoxId) return@mapNotNull null
            val f = facts(item)
            val stillFits = !from.rules.isOpen && from.rules.matches(f)
            if (stillFits) return@mapNotNull null
            val target = decide(boxes, f, policy.copy(playsetLimit = 0)) ?: return@mapNotNull null
            if (target.boxId == from.id) return@mapNotNull null
            // A card in an open box that fits its name range only moves when a rule box fits it.
            if (from.rules.isOpen && from.rules.matches(f) && target.reason != SortReason.RULES && target.reason != SortReason.BULK) {
                return@mapNotNull null
            }
            Move(item, from.id, target.boxId)
        }
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
