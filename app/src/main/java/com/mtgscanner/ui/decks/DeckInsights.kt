package com.mtgscanner.ui.decks

import kotlin.math.roundToInt
import kotlin.random.Random

/** One card of a deck for the insights: its copies and what it is. Commanders are flagged and kept apart. */
data class InsightCard(
    val name: String,
    val copies: Int,
    val cmc: Double,
    val typeLine: String,
    val oracleText: String,
    val manaCost: String,
    val commander: Boolean = false,
) {
    val isLand: Boolean get() = DeckShapeMath.isLand(typeLine)
}

/** The deck's likely Commander bracket and what sets it. */
data class Bracket(
    /** 2 (Core), 3 (Upgraded) or 4 (Optimized); 1 and 5 depend on how the deck is played, so they aren't guessed. */
    val level: Int,
    val gameChangers: List<String>,
    val massLandDenial: List<String>,
    val extraTurns: List<String>,
    val tutors: List<String>,
    /** Two-card combos from Commander Spellbook that the deck has; null when they couldn't be checked. */
    val twoCardCombos: List<List<String>>?,
) {
    val label: String get() = when (level) { 2 -> "Core"; 3 -> "Upgraded"; else -> "Optimized" }

    /** What set the level, shortest first, for the deck page. */
    val reasons: List<String> get() = buildList {
        if (gameChangers.isNotEmpty()) add(if (gameChangers.size == 1) "1 Game Changer" else "${gameChangers.size} Game Changers")
        if (massLandDenial.isNotEmpty()) add("mass land denial")
        twoCardCombos?.takeIf { it.isNotEmpty() }?.let { add(if (it.size == 1) "1 two-card combo" else "${it.size} two-card combos") }
        if (extraTurns.size >= 2) add("${extraTurns.size} extra-turn cards")
        if (tutors.size > 3) add("${tutors.size} tutors")
    }
}

/** Commander brackets (Wizards of the Coast, 2025), worked out from the list. Pure, for tests. */
object BracketMath {
    /**
     * Wizards' Game Changers list, used when Scryfall's own list (is:gamechanger) can't be fetched. Scryfall's list
     * wins because Wizards updates it.
     */
    val GAME_CHANGERS: Set<String> = setOf(
        "Drannith Magistrate", "Enlightened Tutor", "Humility", "Serra's Sanctum", "Smothering Tithe", "Teferi's Protection",
        "Consecrated Sphinx", "Cyclonic Rift", "Force of Will", "Fierce Guardianship", "Gifts Ungiven", "Intuition",
        "Mystical Tutor", "Narset, Parter of Veils", "Rhystic Study", "Thassa's Oracle",
        "Ad Nauseam", "Bolas's Citadel", "Braids, Cabal Minion", "Demonic Tutor", "Imperial Seal", "Necropotence",
        "Opposition Agent", "Orcish Bowmasters", "Tergrid, God of Fright", "Vampiric Tutor",
        "Gamble", "Jeska's Will", "Underworld Breach", "Biorhythm", "Farewell",
        "Crop Rotation", "Gaea's Cradle", "Natural Order", "Seedborn Muse", "Survival of the Fittest", "Worldly Tutor",
        "Aura Shards", "Coalition Victory", "Grand Arbiter Augustin IV", "Notion Thief",
        "Ancient Tomb", "Chrome Mox", "Field of the Dead", "Glacial Chasm", "Grim Monolith", "Lion's Eye Diamond",
        "Mana Vault", "Mishra's Workshop", "Mox Diamond", "Panoptic Mirror", "The One Ring", "The Tabernacle at Pendrell Vale",
    )

    // "Destroy all permanents" only when it stops there: Season of Gathering destroys all permanents "of the
    // chosen type" (artifacts or enchantments), which is not land denial.
    private val MLD = Regex("""destroy all (nonbasic )?lands|destroy all [a-z, ]*\band lands|destroy all permanents\.|each player sacrifices (all|that many) lands|lands don't untap|return all lands""", RegexOption.IGNORE_CASE)
    private val EXTRA_TURN = Regex("""takes? an extra turn""", RegexOption.IGNORE_CASE)
    /** Each "search your library for … card(s)" and what it looks for. */
    private val SEARCH = Regex("""search your library for ([^.]*?)\bcards?\b""", RegexOption.IGNORE_CASE)
    /** Ramp, not a tutor: the search finds a land, by name, type or basic land type. */
    private val LAND_SEARCH = Regex("""\b(lands?|basic|plains|island|swamp|mountain|forest|gate|desert|cave|town)\b""", RegexOption.IGNORE_CASE)

    /** A card that tutors: it searches the library for something other than lands. */
    fun isTutor(oracle: String): Boolean = SEARCH.findAll(oracle).any { !LAND_SEARCH.containsMatchIn(it.groupValues[1]) }

    fun of(cards: List<InsightCard>, gameChangers: Set<String>, twoCardCombos: List<List<String>>?): Bracket {
        val gcKeys = gameChangers.map { DeckNames.key(it) }.toSet()
        fun names(test: (InsightCard) -> Boolean) = cards.filter(test).map { it.name }.distinct()
        val gc = names { DeckNames.key(it.name) in gcKeys }
        val mld = names { !it.isLand && MLD.containsMatchIn(it.oracleText) }
        val turns = names { EXTRA_TURN.containsMatchIn(it.oracleText) }
        val tutors = names { !it.isLand && isTutor(it.oracleText) }
        val combos = twoCardCombos.orEmpty()
        val level = when {
            gc.size > 3 || mld.isNotEmpty() -> 4
            gc.isNotEmpty() || combos.isNotEmpty() || turns.size >= 2 || tutors.size > 3 -> 3
            else -> 2
        }
        return Bracket(level, gc, mld, turns, tutors, twoCardCombos)
    }
}

/** A deck's lands against its curve and colors. */
data class ManaBase(
    val lands: Int,
    /** Frank Karsten's land count for this curve; null for decks too small for his formula. */
    val suggested: Int?,
    val avgManaValue: Double,
    /** Lands (and land-searching cards) that can make each color. */
    val sources: Map<Char, Int>,
    /** Sources each color should have, from its share of the colored pips. */
    val wanted: Map<Char, Int>,
) {
    /** "Add 2 lands", "Add 3 green sources"… empty when it looks right. */
    val advice: List<String> get() = buildList {
        if (suggested != null) {
            val d = suggested - lands
            if (d >= 2) add("Add $d lands")
            if (d <= -2) add("Cut ${-d} lands")
        }
        wanted.forEach { (c, want) ->
            val short = want - (sources[c] ?: 0)
            if (short >= 2) add("Add $short ${ManaBaseMath.colorName(c)} sources")
        }
    }
}

/** Land count and color sources (Frank Karsten's formulas). Pure, for tests. */
object ManaBaseMath {
    private val ADD = Regex("""add[^.]*""", RegexOption.IGNORE_CASE)
    private val BASICS = mapOf("plains" to 'W', "island" to 'U', "swamp" to 'B', "mountain" to 'R', "forest" to 'G')
    private val CHEAP_HELP = Regex("""add \{|search your library for (a|up to \w+) (basic )?(land|forest|plains|island|swamp|mountain)|draws? (a|two|three) cards?""", RegexOption.IGNORE_CASE)

    fun colorName(c: Char): String = when (c) { 'W' -> "white"; 'U' -> "blue"; 'B' -> "black"; 'R' -> "red"; 'G' -> "green"; else -> "colorless" }

    /** The colors a land (or a card that fetches a land) can make, within the deck's [identity]. */
    fun colorsOf(card: InsightCard, identity: Set<Char>): Set<Char> {
        val text = card.oracleText
        val type = card.typeLine.substringBefore("//").lowercase()
        val out = HashSet<Char>()
        BASICS.forEach { (word, c) -> if (Regex("""\b$word\b""").containsMatchIn(type)) out += c }
        ADD.findAll(text).forEach { m ->
            val clause = m.value
            "WUBRG".forEach { c -> if ("{$c}" in clause) out += c }
            if (Regex("""any (one )?colou?r|any combination of colou?rs|mana of any""", RegexOption.IGNORE_CASE).containsMatchIn(clause)) out += identity
        }
        // Fetch lands and the like: any basic of the deck's colors.
        if (Regex("""search your library for an? (basic )?land""", RegexOption.IGNORE_CASE).containsMatchIn(text)) out += identity
        Regex("""search your library for an? (basic )?(plains|island|swamp|mountain|forest)( or (plains|island|swamp|mountain|forest))?""", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { m -> BASICS.forEach { (w, c) -> if (w in m.value.lowercase()) out += c } }
        return out.filter { it in identity }.toSet()
    }

    /**
     * Karsten's land count: 31.42 + 3.13 × average mana value − 0.28 × cheap ramp and draw, for 100-card decks;
     * 19.59 + 1.90 × average − 0.28 × cheap ramp and draw for 60. Commanders count in the average.
     */
    fun of(cards: List<InsightCard>, identity: Set<Char>): ManaBase {
        val total = cards.sumOf { it.copies }
        val lands = cards.filter { it.isLand && !it.commander }
        val spells = cards.filter { !it.isLand }
        val spellCount = spells.sumOf { it.copies }
        val avg = if (spellCount == 0) 0.0 else spells.sumOf { it.cmc * it.copies } / spellCount
        val cheap = spells.filter { !it.commander && it.cmc <= 2 && CHEAP_HELP.containsMatchIn(it.oracleText) }.sumOf { it.copies }
        val suggested = when {
            total >= 95 -> (31.42 + 3.13 * avg - 0.28 * cheap).roundToInt()
            total >= 58 -> (19.59 + 1.90 * avg - 0.28 * cheap).roundToInt()
            else -> null
        }
        val landCount = lands.sumOf { it.copies }
        val sources = identity.associateWith { c -> cards.filter { it.isLand || it.cmc == 0.0 }.filter { c in colorsOf(it, identity) }.sumOf { it.copies } }
            .filterKeys { it in "WUBRG" }
        val pips = HashMap<Char, Int>()
        spells.forEach { s -> Regex("""\{([WUBRG])(/[WUBRGP])?\}""").findAll(s.manaCost).forEach { m -> val c = m.groupValues[1][0]; pips[c] = (pips[c] ?: 0) + s.copies } }
        val pipTotal = pips.values.sum()
        val base = suggested ?: landCount
        // Each color gets its share of the lands, and at least a third of them when it's played at all.
        val wanted = if (pipTotal == 0) emptyMap() else pips.filterKeys { it in identity }.mapValues { (_, n) ->
            maxOf((base * n.toDouble() / pipTotal).roundToInt(), (base / 3.0).roundToInt())
        }
        return ManaBase(landCount, suggested, avg, sources, wanted)
    }
}

/** How a deck draws when it plays alone, from many shuffles. */
data class Goldfish(
    val games: Int,
    /** Opening 7s with 2 to 5 lands. */
    val keepable: Double,
    /** A land played on each of turns 1 to 4, by turn. */
    val landDrop: List<Double>,
    /** The first commander castable on the turn equal to its mana value, from lands alone; null without one. */
    val commanderOnCurve: Double?,
    val commanderCost: Int?,
    /** Lands in play on turn 5, on average. */
    val landsTurn5: Double,
)

/** Plays a deck alone many times: draw, play a land a turn, see what was possible. Pure, for tests. */
object GoldfishMath {
    fun of(cards: List<InsightCard>, games: Int = 1000, random: Random = Random(1), onPlay: Boolean = true): Goldfish? {
        val library = cards.filter { !it.commander }.flatMap { c -> List(c.copies) { c.isLand } }
        if (library.size < 20) return null
        val cmdr = cards.firstOrNull { it.commander && !it.isLand }?.cmc?.roundToInt()?.takeIf { it > 0 }
        var keep = 0
        val drops = IntArray(4)
        var onCurve = 0
        var lands5 = 0
        repeat(games) {
            val deck = library.shuffled(random)
            val hand7 = deck.take(7).count { it }
            if (hand7 in 2..5) keep++
            var inHand = hand7
            var played = 0
            var drawn = 7
            for (turn in 1..maxOf(5, cmdr ?: 0)) {
                if (!(onPlay && turn == 1) && drawn < deck.size) { if (deck[drawn]) inHand++; drawn++ }
                if (inHand > 0) { inHand--; played++ }
                if (turn <= 4 && played >= turn) drops[turn - 1]++
                if (turn == cmdr && played >= cmdr) onCurve++
                if (turn == 5) lands5 += played
            }
        }
        return Goldfish(
            games, keep.toDouble() / games, drops.map { it.toDouble() / games },
            cmdr?.let { onCurve.toDouble() / games }, cmdr, lands5.toDouble() / games,
        )
    }
}

/** Two decks side by side: the cards only in one, only in the other, and in both. */
data class DeckComparison(val onlyA: List<Pair<String, Int>>, val onlyB: List<Pair<String, Int>>, val both: List<String>)

object DeckCompare {
    /** [a] and [b] are name to copies; names match by [DeckNames.key], copies count by the difference. */
    fun diff(a: List<Pair<String, Int>>, b: List<Pair<String, Int>>): DeckComparison {
        fun tally(l: List<Pair<String, Int>>) = l.groupBy { DeckNames.key(it.first) }.mapValues { (_, v) -> v.first().first to v.sumOf { it.second } }
        val ta = tally(a)
        val tb = tally(b)
        val onlyA = ta.mapNotNull { (k, v) -> val d = v.second - (tb[k]?.second ?: 0); if (d > 0) v.first to d else null }.sortedBy { it.first }
        val onlyB = tb.mapNotNull { (k, v) -> val d = v.second - (ta[k]?.second ?: 0); if (d > 0) v.first to d else null }.sortedBy { it.first }
        val both = ta.filterKeys { it in tb }.values.map { it.first }.sorted()
        return DeckComparison(onlyA, onlyB, both)
    }
}
