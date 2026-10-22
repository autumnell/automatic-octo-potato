package com.mtgscanner.data

import com.mtgscanner.util.DeckLine

/**
 * A deck format. [id] is Scryfall's legality key ("" = none). [commander] formats are singleton decks led
 * by a commander (or an oathbreaker); [copies] is the most copies of a card a deck may play.
 */
data class DeckFormat(
    val id: String,
    val label: String,
    val commander: Boolean = false,
    /** Shown before "More…" in the picker. */
    val common: Boolean = false,
    val copies: Int = 4,
)

/** The formats a deck can be marked as. Pure, for tests. */
object DeckFormats {
    val NONE = DeckFormat("", "None", common = true, copies = 0)

    val ALL: List<DeckFormat> = listOf(
        DeckFormat("commander", "Commander", commander = true, common = true, copies = 1),
        DeckFormat("standard", "Standard", common = true),
        DeckFormat("modern", "Modern", common = true),
        DeckFormat("pioneer", "Pioneer", common = true),
        DeckFormat("pauper", "Pauper", common = true),
        DeckFormat("legacy", "Legacy"),
        DeckFormat("vintage", "Vintage"),
        DeckFormat("brawl", "Brawl", commander = true, copies = 1),
        DeckFormat("standardbrawl", "Standard Brawl", commander = true, copies = 1),
        DeckFormat("oathbreaker", "Oathbreaker", commander = true, copies = 1),
        DeckFormat("paupercommander", "PDH", commander = true, copies = 1),
        DeckFormat("duel", "Duel Commander", commander = true, copies = 1),
        DeckFormat("historic", "Historic"),
        DeckFormat("timeless", "Timeless"),
        DeckFormat("alchemy", "Alchemy"),
        DeckFormat("explorer", "Explorer"),
        DeckFormat("premodern", "Premodern"),
        DeckFormat("penny", "Penny Dreadful"),
    )

    fun of(id: String?): DeckFormat = ALL.firstOrNull { it.id == id } ?: NONE

    fun isCommander(id: String?): Boolean = of(id).commander

    /** Moxfield's format name ("commander", "pauperEdh", "duelCommander"…) as a format id; "" when unknown. */
    fun fromMoxfield(name: String?): String {
        val k = name.orEmpty().lowercase().filter { it.isLetter() }
        return when (k) {
            "pauperedh", "paupercommander", "pdh" -> "paupercommander"
            "duelcommander", "duel" -> "duel"
            "historicbrawl", "brawl" -> "brawl"
            "standardbrawl" -> "standardbrawl"
            "pennydreadful", "penny" -> "penny"
            "edh" -> "commander"
            else -> ALL.firstOrNull { it.id == k }?.id.orEmpty()
        }
    }

    /** Archidekt's numbered formats, the ones this app knows; "" for the rest. */
    fun fromArchidekt(n: Int?): String = when (n) {
        1 -> "standard"
        2 -> "modern"
        3 -> "commander"
        4 -> "legacy"
        5 -> "vintage"
        6 -> "pauper"
        10 -> "penny"
        12 -> "duel"
        13 -> "brawl"
        14 -> "oathbreaker"
        15 -> "pioneer"
        16 -> "historic"
        17 -> "paupercommander"
        18 -> "alchemy"
        19 -> "explorer"
        20 -> "brawl"
        22 -> "premodern"
        24 -> "timeless"
        else -> ""
    }

    /** A guess for a pasted list with no format set: a list with a commander is a Commander deck. */
    fun guess(lines: List<DeckLine>): String = if (lines.any { it.section == "Commander" }) "commander" else ""
}

/** Who leads a deck: up to two commanders, a companion and (Oathbreaker) a signature spell, by card name. */
data class DeckCommanders(
    val commanders: List<String> = emptyList(),
    val companion: String? = null,
    val signature: String? = null,
) {
    val isEmpty: Boolean get() = commanders.isEmpty() && companion == null && signature == null

    /** Every name set, commanders first. */
    val all: List<String> get() = commanders + listOfNotNull(companion, signature)

    /** One "role:name" per line, the way it's saved in the deck. */
    fun encode(): String = buildList {
        commanders.forEach { add("commander:$it") }
        companion?.let { add("companion:$it") }
        signature?.let { add("signature:$it") }
    }.joinToString("\n")

    companion object {
        fun decode(s: String?): DeckCommanders {
            if (s.isNullOrBlank()) return DeckCommanders()
            val cs = mutableListOf<String>()
            var companion: String? = null
            var signature: String? = null
            for (line in s.lines()) {
                val role = line.substringBefore(':', "").trim().lowercase()
                val name = line.substringAfter(':', "").trim().takeIf { it.isNotEmpty() } ?: continue
                when (role) {
                    "commander" -> if (cs.size < 2) cs += name
                    "companion" -> companion = name
                    "signature" -> signature = name
                }
            }
            return DeckCommanders(cs, companion, signature)
        }

        /** Read from a list's Commander, Companion and Signature sections. */
        fun fromLines(lines: List<DeckLine>): DeckCommanders = DeckCommanders(
            commanders = lines.filter { it.section == "Commander" }.map { it.name }.distinct().take(2),
            companion = lines.firstOrNull { it.section == "Companion" }?.name,
            signature = lines.firstOrNull { it.section == "Signature" }?.name,
        )

        /**
         * [text] with each name in [c] that the list doesn't have added at the end under its section, so the
         * deck check counts it. Names already in the list (any section) are left where they are.
         */
        fun addMissingTo(text: String, lines: List<DeckLine>, c: DeckCommanders): String {
            fun key(n: String) = com.mtgscanner.util.CardNames.key(n)
            val have = lines.map { key(it.name) }.toMutableSet()
            val blocks = mutableListOf<String>()
            fun add(header: String, names: List<String>) {
                val missing = names.filter { have.add(key(it)) }
                if (missing.isNotEmpty()) blocks += header + "\n" + missing.joinToString("\n") { "1 $it" }
            }
            add("Commander", c.commanders)
            add("Companion", listOfNotNull(c.companion))
            add("Signature Spell", listOfNotNull(c.signature))
            if (blocks.isEmpty()) return text
            return text.trimEnd() + "\n\n" + blocks.joinToString("\n\n")
        }
    }
}

/** What the commander rules need to know about a card. */
data class RulesCard(
    val name: String,
    val typeLine: String,
    val oracleText: String = "",
    /** Color identity letters. */
    val identity: String = "",
)

/** How a commander takes a second one. */
enum class PairKind { PARTNER, PARTNER_WITH, FRIENDS_FOREVER, BACKGROUND, DOCTORS_COMPANION, DOCTOR }

/** Which commander slot is being filled. */
enum class CommanderSlot { COMMANDER, SECOND, COMPANION, SIGNATURE }

/** Commander, partner, companion and copy-limit rules. Pure, for tests. */
object CommanderRules {
    private val PARTNER_WITH = Regex("""(?m)^(?:[^:\n]*: )?Partner with ([^(\n]+?)\s*(?:\(|$)""")
    private val PARTNER = Regex("""(?m)^(?:[^:\n]*: )?Partner(?:\s*[—-]\s*([^(\n]+?))?\s*(?:\(|$)""")
    private val FRIENDS = Regex("""(?m)^(?:[^:\n]*: )?Friends forever""", RegexOption.IGNORE_CASE)
    private val BACKGROUND = Regex("""(?m)^(?:[^:\n]*: )?Choose a Background""", RegexOption.IGNORE_CASE)
    private val COMPANION_OF_DOCTOR = Regex("""(?m)^(?:[^:\n]*: )?Doctor['’]s companion""", RegexOption.IGNORE_CASE)
    private val ANY_NUMBER = Regex("""A deck can have any number of cards named""", RegexOption.IGNORE_CASE)
    private val UP_TO = Regex("""A deck can have up to (\w+) cards named""", RegexOption.IGNORE_CASE)

    private fun front(type: String) = type.substringBefore(" // ")

    /** Whether [c] can lead a deck of [format]. */
    fun canLead(c: RulesCard, format: String): Boolean {
        val t = front(c.typeLine)
        val legendary = "Legendary" in t
        val canBe = c.oracleText.contains("can be your commander", ignoreCase = true)
        return when (format) {
            "oathbreaker" -> "Planeswalker" in t
            "paupercommander" -> "Creature" in t
            "brawl", "standardbrawl" -> legendary && ("Creature" in t || "Planeswalker" in t) || canBe
            else -> legendary && "Creature" in t || canBe
        }
    }

    /** How [c] takes a second commander, or null. */
    fun pairKind(c: RulesCard): PairKind? = when {
        PARTNER_WITH.containsMatchIn(c.oracleText) -> PairKind.PARTNER_WITH
        PARTNER.containsMatchIn(c.oracleText) -> PairKind.PARTNER
        FRIENDS.containsMatchIn(c.oracleText) -> PairKind.FRIENDS_FOREVER
        BACKGROUND.containsMatchIn(c.oracleText) -> PairKind.BACKGROUND
        COMPANION_OF_DOCTOR.containsMatchIn(c.oracleText) -> PairKind.DOCTORS_COMPANION
        isDoctor(c) -> PairKind.DOCTOR
        else -> null
    }

    private fun isDoctor(c: RulesCard) = front(c.typeLine).let { "Time Lord" in it && "Doctor" in it && "Creature" in it }
    private fun isBackground(c: RulesCard) = "Background" in front(c.typeLine)

    /** The name a "Partner with" card names. */
    fun partnerName(c: RulesCard): String? = PARTNER_WITH.find(c.oracleText)?.groupValues?.get(1)?.trim()

    /** "Partner—Survivors" style group; null for plain partner. */
    private fun partnerGroup(c: RulesCard): String? = PARTNER.find(c.oracleText)?.groupValues?.get(1)?.trim()?.ifEmpty { null }

    /** Whether the deck can have a second commander alongside [first]. */
    fun allowsSecond(first: RulesCard): Boolean = pairKind(first) != null

    /** Whether [a] and [b] can be commanders together. */
    fun canPair(a: RulesCard, b: RulesCard): Boolean {
        if (a.name.equals(b.name, ignoreCase = true)) return false
        val ka = pairKind(a)
        val kb = pairKind(b)
        return when {
            ka == PairKind.PARTNER_WITH && partnerName(a).equals(b.name, ignoreCase = true) -> true
            kb == PairKind.PARTNER_WITH && partnerName(b).equals(a.name, ignoreCase = true) -> true
            ka == PairKind.PARTNER && kb == PairKind.PARTNER -> partnerGroup(a).equals(partnerGroup(b), ignoreCase = true)
            ka == PairKind.FRIENDS_FOREVER && kb == PairKind.FRIENDS_FOREVER -> true
            ka == PairKind.BACKGROUND -> isBackground(b)
            kb == PairKind.BACKGROUND -> isBackground(a)
            ka == PairKind.DOCTORS_COMPANION -> isDoctor(b)
            kb == PairKind.DOCTORS_COMPANION -> isDoctor(a)
            else -> false
        }
    }

    /** Whether [c] fits [slot] (with [first] the first commander, for the second slot). */
    fun fits(c: RulesCard, slot: CommanderSlot, format: String, first: RulesCard?): Boolean = when (slot) {
        CommanderSlot.COMMANDER -> canLead(c, format)
        CommanderSlot.SECOND -> first != null && canPair(first, c)
        CommanderSlot.COMPANION -> c.oracleText.contains("Companion —") || c.oracleText.contains("Companion -")
        CommanderSlot.SIGNATURE -> front(c.typeLine).let { "Instant" in it || "Sorcery" in it }
    }

    private fun quoted(s: String) = "\"" + s.replace("\"", "") + "\""

    /** The Scryfall search for cards that fit [slot], before what the user typed. */
    fun searchFilter(slot: CommanderSlot, format: String, first: RulesCard?): String = when (slot) {
        CommanderSlot.COMMANDER -> when (format) {
            "oathbreaker" -> "t:planeswalker"
            "paupercommander" -> "t:creature r:uncommon"
            "brawl", "standardbrawl" -> "(is:commander or (t:legendary t:planeswalker)) legal:$format"
            else -> "is:commander"
        }
        CommanderSlot.SECOND -> when (first?.let { pairKind(it) }) {
            PairKind.PARTNER_WITH -> "!" + quoted(partnerName(first).orEmpty())
            PairKind.PARTNER -> partnerGroup(first)?.let { "o:" + quoted("Partner—$it") } ?: "kw:partner -o:\"partner with\""
            PairKind.FRIENDS_FOREVER -> "o:\"friends forever\""
            PairKind.BACKGROUND -> "t:background"
            PairKind.DOCTORS_COMPANION -> "t:\"time lord\" t:doctor"
            PairKind.DOCTOR -> "o:\"doctor's companion\""
            null -> "is:commander"
        }
        CommanderSlot.COMPANION -> "kw:companion"
        CommanderSlot.SIGNATURE -> "(t:instant or t:sorcery)" + first?.let { " id<=" + it.identity.lowercase().ifEmpty { "c" } }.orEmpty()
    }

    /** The most copies of [c] a deck of [format] may play; null when there's no limit. */
    fun copyLimit(c: RulesCard?, format: String): Int? {
        val f = DeckFormats.of(format)
        if (f.copies <= 0 || c == null) return null
        if ("Basic" in front(c.typeLine) && "Land" in front(c.typeLine)) return null
        if (ANY_NUMBER.containsMatchIn(c.oracleText)) return null
        UP_TO.find(c.oracleText)?.let { m -> wordNumber(m.groupValues[1])?.let { return it } }
        return f.copies
    }

    private fun wordNumber(w: String): Int? = w.toIntOrNull() ?: listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    ).indexOf(w.lowercase()).takeIf { it >= 0 }

    /** Cards outside the commanders' colors, and cards over the copy limit. */
    data class Issues(val offColor: List<String> = emptyList(), val overLimit: List<String> = emptyList()) {
        val isEmpty: Boolean get() = offColor.isEmpty() && overLimit.isEmpty()
    }

    /**
     * Checks a list against [format]: copies over the limit, and (for commander formats, when every commander
     * is known) cards outside the commanders' color identity. [info] gives what's known about a card by name;
     * cards it doesn't know are skipped. Banned cards aren't checked.
     */
    fun issues(lines: List<DeckLine>, format: String, commanders: DeckCommanders, info: (String) -> RulesCard?): Issues {
        val f = DeckFormats.of(format)
        if (f.id.isEmpty()) return Issues()
        val played = lines.filter { it.section == "Main" || it.section == "Commander" || it.section == "Signature" }
        val byName = played.groupBy { com.mtgscanner.util.CardNames.key(it.name) }
        val over = byName.values.mapNotNull { ls ->
            val n = ls.sumOf { it.quantity }
            val limit = copyLimit(info(ls.first().name), format) ?: return@mapNotNull null
            if (n > limit) ls.first().name else null
        }
        val off = if (!f.commander || commanders.commanders.isEmpty()) emptyList() else {
            val leads = commanders.commanders.map { info(it) }
            if (leads.any { it == null }) emptyList() else {
                val colors = leads.joinToString("") { it!!.identity.uppercase() }.toSet()
                val names = played.map { it.name } + listOfNotNull(commanders.companion, commanders.signature)
                names.distinctBy { it.lowercase() }.filter { n ->
                    val c = info(n) ?: return@filter false
                    c.identity.uppercase().any { it in "WUBRG" && it !in colors }
                }
            }
        }
        return Issues(off, over)
    }
}
