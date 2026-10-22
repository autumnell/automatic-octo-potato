package com.mtgscanner.ui.home

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.ValueSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class YearReviewTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 22)
    private fun at(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun card(
        id: Long, name: String, set: String, added: String, price: Double?, qty: Int = 1,
        atAdd: Double? = price, foil: Boolean = false, proxy: Boolean = false, sid: String = "s$id",
    ) = CollectionCard(
        id = id, scryfallId = sid, oracleId = null, name = name, setCode = set, setName = set.uppercase() + " set",
        collectorNumber = "1", rarity = "rare", typeLine = "", manaCost = "", cmc = 0.0, colors = "", colorIdentity = "",
        oracleText = "", tags = "", priceUsd = if (foil) null else price, priceUsdFoil = if (foil) price else null,
        imageUrl = "img$id", smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null,
        foil = foil, quantity = qty, boxId = 1, priceAtAdd = atAdd, proxy = proxy, addedAt = at(added), updatedAt = at(added),
    )

    private val cards = listOf(
        card(1, "Opt", "dom", "2026-02-01", 0.2, qty = 10),
        card(2, "Ragavan", "mh2", "2026-03-05", 52.0, atAdd = 60.0),
        card(3, "Bolt", "2xm", "2025-06-01", 3.0, qty = 4, atAdd = 1.0),
        card(4, "Fake Lotus", "lea", "2026-04-01", 9999.0, proxy = true),
        card(5, "Sheoldred", "dmu", "2026-05-01", 80.0, atAdd = 70.0, foil = true),
    )

    @Test fun `added copies best pull and top set for the year`() {
        val r = YearReviewMath.compute(2026, cards, emptyList(), emptyList(), today, zone)
        assertEquals(12, r.added)
        assertEquals("Sheoldred", r.bestPull?.name)
        assertEquals(80.0, r.bestPull!!.amount, 1e-9)
        assertEquals(Triple("dom", "DOM set", 10), r.topSet)
    }

    @Test fun `biggest gain this year uses price at add for new cards and history for older ones`() {
        val points = listOf(
            PricePoint("s3", "2025-12-29", 1.0, null),
            PricePoint("s3", "2026-01-05", 1.5, null),
            PricePoint("s3", "2026-10-01", 2.9, null),
        )
        val r = YearReviewMath.compute(2026, cards, emptyList(), points, today, zone)
        // Bolt: 4 × (3.0 now − 1.5 in January) = 6; Sheoldred: 80 − 70 = 10; Ragavan lost value.
        assertEquals("Sheoldred", r.biggestGain?.name)
        assertEquals(10.0, r.biggestGain!!.amount, 1e-9)
    }

    @Test fun `a past year uses history at both ends`() {
        val points = listOf(
            PricePoint("s3", "2025-07-01", 1.2, null),
            PricePoint("s3", "2025-12-29", 2.0, null),
            PricePoint("s3", "2026-03-01", 9.0, null),
        )
        val r = YearReviewMath.compute(2025, cards, emptyList(), points, today, zone)
        assertEquals(4, r.added)
        assertEquals("Bolt", r.bestPull?.name)
        // Added in 2025 at $1, worth $2 at year end: 4 × 1 = 4.
        assertEquals(4.0, r.biggestGain!!.amount, 1e-9)
    }

    @Test fun `value start and end come from the year's snapshots`() {
        val snaps = listOf(
            ValueSnapshot("2025-12-31", 90.0, 1),
            ValueSnapshot("2026-01-03", 100.0, 2),
            ValueSnapshot("2026-06-01", 150.0, 3),
            ValueSnapshot("2026-10-20", 180.0, 4),
        )
        val r = YearReviewMath.compute(2026, cards, snaps, emptyList(), today, zone)
        assertEquals(100.0, r.valueStart!!, 1e-9)
        assertEquals(180.0, r.valueEnd!!, 1e-9)
    }

    @Test fun `an empty year`() {
        val r = YearReviewMath.compute(2020, cards, emptyList(), emptyList(), today, zone)
        assertEquals(0, r.added)
        assertNull(r.bestPull)
        assertNull(r.biggestGain)
        assertNull(r.topSet)
        assertNull(r.valueStart)
        assertTrue(r.isEmpty)
    }

    @Test fun `years list`() {
        val snaps = listOf(ValueSnapshot("2024-05-05", 1.0, 1))
        assertEquals(listOf(2026, 2025, 2024), YearReviewMath.years(cards, snaps, today, zone))
        assertEquals(listOf(2026), YearReviewMath.years(emptyList(), emptyList(), today, zone))
    }
}
