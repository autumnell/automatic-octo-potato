package com.mtgscanner.util

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class CsvExporterTest {
    private val card = CollectionCard(
        scryfallId = "abc-123", oracleId = null, name = "Lightning Bolt", setCode = "M10", setName = "Magic 2010",
        collectorNumber = "146", rarity = "common", typeLine = "Instant", manaCost = "{R}", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = "", tags = "", priceUsd = 2.5, priceUsdFoil = null, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "ja", releasedAt = null, artist = null, foil = true, quantity = 3,
        condition = "LP", boxId = 1, altered = true, userTags = "trade,red", addedAt = 0, updatedAt = 0,
    )

    private fun export(format: CsvFormat) = StringWriter().also { CsvExporter.write(listOf(card), it, format) }.toString().trim().lines()

    @Test fun manabox() {
        val lines = export(CsvFormat.MANABOX)
        assertEquals("Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,Scryfall ID,Condition,Language", lines[0])
        assertEquals("Lightning Bolt,m10,Magic 2010,146,foil,common,3,abc-123,excellent,ja", lines[1])
    }

    @Test fun moxfield() {
        val lines = export(CsvFormat.MOXFIELD)
        assertTrue(lines[0].startsWith("Count,Tradelist Count,Name,Edition,Condition,Language,Foil,Tags"))
        assertEquals("3,0,Lightning Bolt,m10,Lightly Played,Japanese,foil,\"trade,red\",,146,True,False,", lines[1])
        // Purchase Price is what was paid, not today's price; Proxy follows the card.
        val paid = StringWriter().also { CsvExporter.write(listOf(card.copy(purchasePrice = 1.5, proxy = true)), it, CsvFormat.MOXFIELD) }
            .toString().trim().lines()
        assertTrue(paid[1].endsWith(",True,True,1.50"))
    }

    @Test fun archidekt() {
        val lines = export(CsvFormat.ARCHIDEKT)
        assertEquals("3,Lightning Bolt,Foil,LP,,JA,,\"trade,red\",Magic 2010,m10,,abc-123,146", lines[1])
    }

    @Test fun exportsReadBackIn() {
        for (f in CsvFormat.entries) {
            val text = StringWriter().also { CsvExporter.write(listOf(card), it, f) }.toString()
            val rows = CsvImport.parse(text)
            assertEquals(f.name, 1, rows.size)
            assertEquals(f.name, "Lightning Bolt", rows[0].name)
            assertEquals(f.name, 3, rows[0].quantity)
            assertEquals(f.name, "ja", rows[0].language)
        }
    }

    @Test fun moxfieldPurchasePriceReadsBackIn() {
        val text = StringWriter().also { CsvExporter.write(listOf(card.copy(purchasePrice = 4.25)), it, CsvFormat.MOXFIELD) }.toString()
        val row = CsvImport.parse(text).single()
        assertEquals(4.25, row.purchasePrice!!, 0.0001)
        assertEquals("LP", row.condition)
        assertEquals(true, row.foil)
        assertEquals("146", row.collectorNumber)
        assertEquals("m10", row.setCode)
    }
}
