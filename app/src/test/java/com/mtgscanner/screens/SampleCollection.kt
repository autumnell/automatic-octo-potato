package com.mtgscanner.screens

import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.ValueSnapshot
import com.mtgscanner.data.db.WishlistItem
import java.time.LocalDate

/** A small made-up collection for the screen pictures and the big-collection timing. No pictures, no network. */
object SampleCollection {
    /** name, set code, set name, number, rarity, type, mana cost, colors, price. */
    private val cards = listOf(
        listOf("Lightning Bolt", "m11", "Magic 2011", "149", "common", "Instant", "{R}", "R", "1.20"),
        listOf("Counterspell", "mh2", "Modern Horizons 2", "267", "uncommon", "Instant", "{U}{U}", "U", "0.85"),
        listOf("Llanowar Elves", "dom", "Dominaria", "168", "common", "Creature — Elf Druid", "{G}", "G", "0.30"),
        listOf("Swords to Plowshares", "sta", "Strixhaven Mystical Archive", "10", "uncommon", "Instant", "{W}", "W", "2.40"),
        listOf("Thoughtseize", "2xm", "Double Masters", "107", "rare", "Sorcery", "{B}", "B", "11.50"),
        listOf("Sol Ring", "cmr", "Commander Legends", "472", "uncommon", "Artifact", "{1}", "", "1.75"),
        listOf("Arcane Signet", "cmr", "Commander Legends", "297", "common", "Artifact", "{2}", "", "0.60"),
        listOf("Rhystic Study", "pcy", "Prophecy", "45", "common", "Enchantment", "{2}{U}", "U", "38.00"),
        listOf("Cultivate", "m21", "Core Set 2021", "177", "common", "Sorcery", "{2}{G}", "G", "0.40"),
        listOf("Lightning Greaves", "2xm", "Double Masters", "267", "uncommon", "Artifact — Equipment", "{2}", "", "2.10"),
        listOf("Atraxa, Praetors' Voice", "2xm", "Double Masters", "190", "mythic", "Legendary Creature — Phyrexian Angel Horror", "{G}{W}{U}{B}", "WUBG", "22.00"),
        listOf("Smothering Tithe", "rna", "Ravnica Allegiance", "22", "rare", "Enchantment", "{3}{W}", "W", "16.80"),
        listOf("Dockside Extortionist", "c19", "Commander 2019", "24", "rare", "Creature — Goblin Pirate", "{1}{R}", "R", "45.00"),
        listOf("Fatal Push", "aer", "Aether Revolt", "57", "uncommon", "Instant", "{B}", "B", "1.90"),
        listOf("Path to Exile", "con", "Conflux", "15", "uncommon", "Instant", "{W}", "W", "3.10"),
        listOf("Birds of Paradise", "m12", "Magic 2012", "165", "rare", "Creature — Bird", "{G}", "G", "6.70"),
        listOf("Dark Ritual", "sta", "Strixhaven Mystical Archive", "88", "common", "Instant", "{B}", "B", "0.90"),
        listOf("Brainstorm", "sta", "Strixhaven Mystical Archive", "37", "common", "Instant", "{U}", "U", "1.10"),
        listOf("Command Tower", "cmr", "Commander Legends", "350", "common", "Land", "", "", "0.25"),
        listOf("Evolving Wilds", "m21", "Core Set 2021", "246", "common", "Land", "", "", "0.15"),
        listOf("Mountain", "m21", "Core Set 2021", "271", "common", "Basic Land — Mountain", "", "", "0.05"),
        listOf("Island", "m21", "Core Set 2021", "263", "common", "Basic Land — Island", "", "", "0.05"),
        listOf("Ragavan, Nimble Pilferer", "mh2", "Modern Horizons 2", "138", "mythic", "Legendary Creature — Monkey Pirate", "{R}", "R", "55.00"),
        listOf("Esper Sentinel", "mh2", "Modern Horizons 2", "12", "rare", "Artifact Creature — Human Soldier", "{W}", "W", "18.40"),
        listOf("Teferi, Time Raveler", "war", "War of the Spark", "221", "rare", "Legendary Planeswalker — Teferi", "{1}{W}{U}", "WU", "4.20"),
        listOf("Omnath, Locus of Creation", "znr", "Zendikar Rising", "232", "mythic", "Legendary Creature — Elemental", "{R}{G}{W}{U}", "WURG", "3.30"),
        listOf("Murktide Regent", "mh2", "Modern Horizons 2", "52", "mythic", "Creature — Dragon", "{5}{U}{U}", "U", "9.60"),
        listOf("The One Ring", "ltr", "The Lord of the Rings: Tales of Middle-earth", "246", "mythic", "Legendary Artifact", "{4}", "", "62.00"),
        listOf("Orcish Bowmasters", "ltr", "The Lord of the Rings: Tales of Middle-earth", "103", "rare", "Creature — Orc Archer", "{1}{B}", "B", "34.00"),
        listOf("Sheoldred, the Apocalypse", "dmu", "Dominaria United", "107", "mythic", "Legendary Creature — Phyrexian Praetor", "{2}{B}{B}", "B", "71.00"),
    )

    val boxNames = listOf("Rares binder", "Commander staples", "Red and green bulk", "Blue box", "Lands")

    fun card(i: Int, boxId: Long, now: Long = System.currentTimeMillis()): CollectionCard {
        val c = cards[i % cards.size]
        val round = i / cards.size
        val price = c[8].toDouble() * (1.0 + (round % 7) * 0.05)
        return CollectionCard(
            scryfallId = "sample-$i", oracleId = "oracle-${i % cards.size}", name = c[0], setCode = c[1], setName = c[2],
            collectorNumber = c[3], rarity = c[4], typeLine = c[5], manaCost = c[6],
            cmc = c[6].count { it == '{' }.toDouble(), colors = c[7], colorIdentity = c[7], oracleText = "", tags = "",
            priceUsd = price, priceUsdFoil = price * 1.8, imageUrl = null, smallImageUrl = null, scryfallUri = "", lang = "en",
            releasedAt = "2021-06-18", artist = "Sample Artist", foil = i % 9 == 0, quantity = 1 + (i % 4), condition = "NM",
            boxId = boxId, priceAtAdd = price * 0.9, purchasePrice = null, addedAt = now - i * 3_600_000L, updatedAt = now - i * 3_600_000L,
        )
    }

    /** Fills [repo] with [count] card rows in a few boxes, a deck, a wishlist and some value history. */
    suspend fun fill(repo: CollectionRepository, count: Int = cards.size * 2) {
        val dao = repo.db.cardDao()
        val now = System.currentTimeMillis()
        val ids = boxNames.mapIndexed { i, name ->
            dao.insertBox(Box(name = name, kind = if (i == 0) "binder" else "box", color = i + 1, sortOrder = i, createdAt = now - i))
        }
        dao.putCards(List(count) { card(it, ids[it % ids.size], now) })
        val today = LocalDate.now()
        dao.putSnapshots(List(60) { d ->
            ValueSnapshot(today.minusDays((59 - d).toLong()).toString(), 900.0 + d * 6 + (d % 5) * 9, count + d)
        })
        dao.putPricePoints(List(60) { d ->
            PricePoint("sample-0", today.minusDays((59 - d).toLong()).toString(), 1.0 + (d % 9) * 0.03, 2.1)
        })
        dao.putWishlist(listOf(
            WishlistItem(oracleId = "w1", scryfallId = "w1", name = "Mana Crypt", imageUrl = null, priceUsd = 180.0),
            WishlistItem(oracleId = "w2", scryfallId = "w2", name = "Cyclonic Rift", imageUrl = null, priceUsd = 24.5, wanted = 2),
        ))
        repo.saveDeck(
            0, "Atraxa superfriends",
            "1 Atraxa, Praetors' Voice\n1 Sol Ring\n1 Arcane Signet\n1 Command Tower\n1 Smothering Tithe\n1 Rhystic Study\n1 Teferi, Time Raveler\n1 Cyclonic Rift\n30 Island",
            format = "commander", commanders = "Atraxa, Praetors' Voice",
        )
    }
}
