package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportTest {
    @Test fun manaBoxExport() {
        val csv = "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID,Purchase price,Misprint,Altered,Condition,Language,Purchase price currency\n" +
            "\"Sol Ring\",cmr,Commander Legends,263,normal,uncommon,2,1,8a4b1b6e-1a2b-4c3d-9e8f-0123456789ab,1.0,false,false,near_mint,en,USD\n" +
            "\"Fire // Ice\",mh2,Modern Horizons 2,290,foil,rare,1,2,,0,false,false,lightly_played,en,USD\n"
        val rows = CsvImport.parse(csv)
        assertEquals(2, rows.size)
        assertEquals("Sol Ring", rows[0].name)
        assertEquals(2, rows[0].quantity)
        assertEquals("cmr", rows[0].setCode)
        assertEquals("263", rows[0].collectorNumber)
        assertEquals("8a4b1b6e-1a2b-4c3d-9e8f-0123456789ab", rows[0].scryfallId)
        assertTrue(rows[1].foil)
        assertEquals("LP", rows[1].condition)
    }

    @Test fun moxfieldExport() {
        val csv = "\"Count\",\"Tradelist Count\",\"Name\",\"Edition\",\"Condition\",\"Language\",\"Foil\",\"Tags\",\"Last Modified\",\"Collector Number\"\n" +
            "\"4\",\"0\",\"Lightning Bolt\",\"m11\",\"Near Mint\",\"English\",\"\",\"\",\"2024-01-01\",\"149\"\n"
        val r = CsvImport.parse(csv).single()
        assertEquals(4, r.quantity)
        assertEquals("Lightning Bolt", r.name)
        assertEquals("m11", r.setCode)
        assertEquals("149", r.collectorNumber)
        assertEquals("NM", r.condition)
    }

    @Test fun quotedCommasAndNewlines() {
        val rows = CsvImport.readCsv("a,\"b, c\",\"d \"\"e\"\"\"\n1,\"x\ny\",2\n")
        assertEquals(listOf("a", "b, c", "d \"e\""), rows[0])
        assertEquals("x\ny", rows[1][1])
    }
}
