package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PullRatesTest {
    private fun pack(id: Long, cost: Double?, value: Double, vararg pulls: Pull) = PackLog(id, "Pack", cost, value, pulls.toList())

    @Test fun roundTrips() {
        val pulls = listOf(
            Pull("Ragavan, Nimble Pilferer", "mh2", "138", 52.1, "mythic", foil = false, count = 1),
            Pull("A|B", "mh2", "5", null, "", foil = true, count = 2),
        )
        val back = PullRates.parse(PullRates.encode(pulls))
        assertEquals(pulls[0], back[0])
        assertEquals(Pull("A/B", "mh2", "5", null, "", true, 2), back[1])
    }

    @Test fun readsOldLines() {
        assertEquals(listOf(Pull("Bolt", "m10", "146", 1.5)), PullRates.parse("Bolt|M10|146|1.50\n"))
    }

    @Test fun rarityWords() {
        assertEquals("mythic", PullRates.rarityKey("Mythic Rare"))
        assertEquals("special", PullRates.rarityKey("bonus"))
        assertEquals("", PullRates.rarityKey(null))
    }

    @Test fun fillsMissingRarity() {
        val out = PullRates.fillRarity(listOf(Pull("x", "mh2", "1", null), Pull("y", "mh2", "2", null, "rare"))) { s, n -> if (s == "mh2" && n == "1") "uncommon" else "common" }
        assertEquals(listOf("uncommon", "rare"), out.map { it.rarity })
    }

    @Test fun summarizes() {
        val packs = listOf(
            pack(1, 5.0, 12.0, Pull("a", "mh2", "1", 10.0, "mythic"), Pull("b", "mh2", "2", 0.1, "common", count = 3)),
            pack(2, 5.0, 1.0, Pull("c", "mh2", "3", 0.5, "rare"), Pull("d", "mh2", "4", 0.1, "uncommon", count = 2)),
            pack(3, null, 2.0, Pull("e", "neo", "1", 2.0, "rare")),
            pack(4, null, 0.0, Pull("f", "neo", "2", null)),
        )
        val g = PullRates.all(packs)
        assertEquals(4, g.packs)
        assertEquals(9, g.cards)
        assertEquals(3, g.known)
        assertEquals(1, g.mythicPacks)
        assertEquals(1.0 / 3, g.mythicRate!!, 1e-9)
        assertEquals(listOf("common", "uncommon", "rare", "mythic"), g.rarities.map { it.rarity })
        assertEquals(2.0 / 3, g.rarities.first { it.rarity == "rare" }.perPack, 1e-9)
        assertEquals(10.0, g.paid, 1e-9)
        assertEquals(2, g.paidPacks)
        assertEquals(13.0, g.pulledPaid, 1e-9)
        assertEquals(3.0, g.net, 1e-9)
        assertEquals(15.0, g.pulled, 1e-9)

        val sets = PullRates.bySet(packs)
        assertEquals(listOf("mh2", "neo"), sets.map { it.key })
        assertNull(PullRates.all(emptyList()).mythicRate)
    }
}
