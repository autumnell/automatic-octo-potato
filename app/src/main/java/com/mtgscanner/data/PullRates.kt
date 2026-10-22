package com.mtgscanner.data

import java.util.Locale

/** One line of a logged pack: a card that came out of it. */
data class Pull(
    val name: String,
    val set: String,
    val number: String,
    /** Price per copy when opened. */
    val price: Double?,
    /** "common", "uncommon", "rare", "mythic", "special", or "" when not known. */
    val rarity: String = "",
    val foil: Boolean = false,
    val count: Int = 1,
)

/** A logged pack, with its cost (the one typed later, when there was none). */
data class PackLog(val id: Long, val name: String, val cost: Double?, val value: Double, val pulls: List<Pull>)

data class RarityCount(val rarity: String, val cards: Int, val perPack: Double)

/** Pull numbers for a group of packs: all of them, or one set's. */
data class PullGroup(
    val key: String,
    val label: String,
    val packs: Int,
    val cards: Int,
    /** Per rarity, common first; only rarities that were pulled. */
    val rarities: List<RarityCount>,
    /** Packs with at least one mythic, out of [known]. */
    val mythicPacks: Int,
    /** Packs whose cards' rarities are known. */
    val known: Int,
    /** Total paid, over the [paidPacks] packs with a price. */
    val paid: Double,
    val paidPacks: Int,
    /** Value pulled from those same paid packs. */
    val pulledPaid: Double,
    /** Value pulled from every pack. */
    val pulled: Double,
) {
    val mythicRate: Double? get() = if (known > 0) mythicPacks.toDouble() / known else null
    val net: Double get() = pulledPaid - paid
}

/** Reads and writes the pulls of a logged pack and adds them up. */
object PullRates {
    val ORDER = listOf("common", "uncommon", "rare", "mythic", "special")

    /** Scryfall's rarity word as one of [ORDER]; "" when blank. */
    fun rarityKey(r: String?): String = when (val k = r?.trim()?.lowercase().orEmpty()) {
        "" -> ""
        "common", "uncommon", "rare", "mythic" -> k
        "mythic rare" -> "mythic"
        else -> "special"
    }

    private fun clean(s: String) = s.replace('|', '/').replace('\n', ' ')

    /** One line per card: "name|set|number|price|rarity|f|count". Older logs have only the first four. */
    fun encode(pulls: List<Pull>): String = pulls.joinToString("\n") { p ->
        listOf(
            clean(p.name), clean(p.set), clean(p.number),
            p.price?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
            rarityKey(p.rarity), if (p.foil) "f" else "", p.count.coerceAtLeast(1).toString(),
        ).joinToString("|")
    }

    fun parse(text: String): List<Pull> = text.lines().mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val f = line.split('|')
        Pull(
            name = f[0].trim(),
            set = f.getOrNull(1)?.trim()?.lowercase().orEmpty(),
            number = f.getOrNull(2)?.trim().orEmpty(),
            price = f.getOrNull(3)?.trim()?.removePrefix("$")?.toDoubleOrNull(),
            rarity = rarityKey(f.getOrNull(4)),
            foil = f.getOrNull(5)?.trim() == "f",
            count = f.getOrNull(6)?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        )
    }

    /** Fills in unknown rarities (older logs) from [lookup] by set and collector number. */
    fun fillRarity(pulls: List<Pull>, lookup: (set: String, number: String) -> String?): List<Pull> =
        pulls.map { p -> if (p.rarity.isNotEmpty()) p else p.copy(rarity = rarityKey(lookup(p.set.lowercase(), p.number))) }

    /** The set most of a pack's cards came from; "" for an empty pack. */
    fun mainSet(pack: PackLog): String =
        pack.pulls.groupBy { it.set.lowercase() }.maxByOrNull { (_, l) -> l.sumOf { it.count } }?.key.orEmpty()

    fun summarize(key: String, label: String, packs: List<PackLog>): PullGroup {
        val known = packs.filter { p -> p.pulls.any { it.rarity.isNotEmpty() } }
        val counts = HashMap<String, Int>()
        known.forEach { p -> p.pulls.forEach { if (it.rarity.isNotEmpty()) counts[it.rarity] = (counts[it.rarity] ?: 0) + it.count } }
        val paid = packs.filter { (it.cost ?: 0.0) > 0.0 }
        return PullGroup(
            key = key,
            label = label,
            packs = packs.size,
            cards = packs.sumOf { p -> p.pulls.sumOf { it.count } },
            rarities = ORDER.mapNotNull { r ->
                val n = counts[r] ?: 0
                if (n == 0) null else RarityCount(r, n, n.toDouble() / known.size)
            },
            mythicPacks = known.count { p -> p.pulls.any { it.rarity == "mythic" } },
            known = known.size,
            paid = paid.sumOf { it.cost ?: 0.0 },
            paidPacks = paid.size,
            pulledPaid = paid.sumOf { it.value },
            pulled = packs.sumOf { it.value },
        )
    }

    /** Every pack together. */
    fun all(packs: List<PackLog>): PullGroup = summarize("", "All packs", packs)

    /** One group per set (the set most of a pack came from), most packs first. */
    fun bySet(packs: List<PackLog>, setName: (String) -> String = { it.uppercase() }): List<PullGroup> =
        packs.groupBy { mainSet(it) }
            .filterKeys { it.isNotEmpty() }
            .map { (code, l) -> summarize(code, setName(code), l) }
            .sortedWith(compareByDescending<PullGroup> { it.packs }.thenBy { it.label })
}
