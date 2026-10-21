package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(1.0, rows[0].purchasePrice!!, 0.0001)
        assertEquals(null, rows[1].purchasePrice)
        assertEquals("en", rows[0].language)
    }

    @Test fun languageNamesBecomeCodes() {
        assertEquals("ja", CsvImport.languageCode("Japanese"))
        assertEquals("de", CsvImport.languageCode("DE"))
        assertEquals("zhs", CsvImport.languageCode("Chinese Simplified"))
        assertEquals(null, CsvImport.languageCode(" "))
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

    @Test fun prefersTheSetCodeColumnOverTheSetName() {
        val rows = CsvImport.parse("Count,Name,Edition,Edition Code,Card Number\n1,Shock,Magic 2010,m10,155\n")
        assertEquals("m10", rows[0].setCode)
        val tcg = CsvImport.parse("Quantity,Name,Set,Set Code\n1,Shock,Core Set 2019,\n")
        assertEquals(null, tcg[0].setCode)
    }

    @Test fun decklistTextImports() {
        val rows = CsvImport.parseAny("4 Lightning Bolt\n1 Sol Ring (CMR) 263\n2x Lightning Bolt\n\nMaybeboard\n1 Duress\n")
        assertEquals(2, rows.size)
        assertEquals(6, rows.first { it.name == "Lightning Bolt" }.quantity)
        val ring = rows.first { it.name == "Sol Ring" }
        assertEquals("cmr", ring.setCode)
        assertEquals("263", ring.collectorNumber)
    }

    @Test fun csvStillWinsAndRandomTextIsNotCards() {
        assertEquals("Shock", CsvImport.parseAny("Quantity,Name\n2,Shock\n").single().name)
        assertTrue(CsvImport.parseAny("Hello there\nThis is a note\n1 thing").isEmpty())
    }

    @Test fun semicolonAndTabFiles() {
        val semi = CsvImport.parse("Quantity;Name;Set code\n2;Shock;m19\n1;\"Fire; Ice\";mh2\n")
        assertEquals(listOf("Shock", "Fire; Ice"), semi.map { it.name })
        assertEquals("m19", semi[0].setCode)
        val tab = CsvImport.parse("Quantity\tName\n3\tShock\n")
        assertEquals(3, tab.single().quantity)
        val sep = CsvImport.parse("sep=;\nCount;Name;Price\n1;Shock;1,50\n")
        assertEquals("Shock", sep.single().name)
        val sepTab = CsvImport.parse("sep=\t\nCount\tName\n1\tShock\n")
        assertEquals("Shock", sepTab.single().name)
    }

    @Test fun manaBoxConditionsReadBack() {
        val csv = "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,Scryfall ID,Condition,Language\n" +
            "A,m10,Magic 2010,1,normal,common,1,,played,en\nB,m10,Magic 2010,2,normal,common,1,,poor,en\n"
        assertEquals(listOf("HP", "DMG"), CsvImport.parse(csv).map { it.condition })
        // Deckbox and TCGplayer files keep their own scale: Played is Moderately Played.
        assertEquals("MP", CsvImport.parse("Count,Name,Condition\n1,A,Played\n").single().condition)
    }

    @Test fun flagsAndFullSetNames() {
        val csv = "Count,Tradelist Count,Name,Edition,Card Number,Condition,Language,Foil,Signed,Altered Art,Proxy\n" +
            "1,0,Shock,Magic 2010,155,Near Mint,English,foil,signed,,True\n"
        val r = CsvImport.parse(csv).single()
        assertEquals(null, r.setCode)
        assertEquals("Magic 2010", r.setName)
        assertTrue(r.foil && r.signed && r.proxy)
        assertFalse(r.altered)
    }

    @Test fun otherApps() {
        val tcg = CsvImport.parse(
            "Quantity,Name,Simple Name,Set,Card Number,Set Code,Printing,Condition,Language\n" +
                "2,Shock (Borderless),Shock,Core Set 2019,156,M19,Normal,Lightly Played Foil,English\n",
        ).single()
        assertEquals("Shock", tcg.name)
        assertEquals("m19", tcg.setCode)
        assertEquals("LP", tcg.condition)
        assertTrue(tcg.foil)

        val dragon = CsvImport.parse(
            "sep=,\nFolder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language,Price Bought,Date Bought\n" +
                "Binder,1,0,Shock,M19,Core Set 2019,156,Played,Foil,English,0.25,2024-01-01\n",
        ).single()
        assertEquals("HP", dragon.condition)
        assertEquals(0.25, dragon.purchasePrice!!, 1e-9)
        assertTrue(dragon.foil)

        val mkm = CsvImport.parse(
            "idArticle;idProduct;English Name;Local Name;Exp.;Exp. Name;Price;Language;Condition;Foil?;Signed?;Playset?;Altered?;Comments;Amount\n" +
                "1;2;Shock;Shock;M19;Core 2019;0,10;7;LP;X;;;;;3\n",
        ).single()
        assertEquals(3, mkm.quantity)
        assertEquals("ja", mkm.language)
        assertEquals("MP", mkm.condition)
        assertEquals(null, mkm.setCode)
        assertEquals("Core 2019", mkm.setName)
        assertTrue(mkm.foil)
    }

    @Test fun decklistFoilMarkerImports() {
        val rows = CsvImport.parseAny("1 Sol Ring (CMR) 263 *F*\n1 Sol Ring (CMR) 263\n")
        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.foil })
    }
}
