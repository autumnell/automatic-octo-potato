package com.mtgscanner.data

import com.mtgscanner.util.DecklistParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckLinksTest {
    @Test fun findsLinks() {
        assertEquals("https://moxfield.com/decks/abC-12_x", DeckLinks.find("look https://moxfield.com/decks/abC-12_x nice"))
        assertEquals("https://archidekt.com/decks/123456", DeckLinks.find("https://archidekt.com/decks/123456/my_deck"))
        assertEquals(null, DeckLinks.find("4 Lightning Bolt"))
    }

    @Test fun moxfieldV3() {
        val body = """{"name":"Bolt Tribal","boards":{
            "commanders":{"cards":{"a":{"quantity":1,"card":{"name":"Zada, Hedron Grinder","set":"bfz","cn":"162"}}}},
            "mainboard":{"cards":{"b":{"quantity":4,"finish":"foil","card":{"name":"Lightning Bolt","set":"m10","cn":"146"}}}},
            "sideboard":{"cards":{}}}}"""
        val d = DeckLinks.parseMoxfield(Json.parseToJsonElement(body))!!
        assertEquals("Bolt Tribal", d.name)
        val lines = DecklistParser.parse(d.text)
        assertEquals("Commander", lines.first { it.name == "Zada, Hedron Grinder" }.section)
        val bolt = lines.first { it.name == "Lightning Bolt" }
        assertEquals(4, bolt.quantity)
        assertEquals("146", bolt.collectorNumber)
        assertTrue(bolt.foil)
    }

    @Test fun moxfieldV2() {
        val body = """{"name":"Old","mainboard":{"Shock":{"quantity":2,"card":{"name":"Shock","set":"m19","cn":"156"}}}}"""
        val d = DeckLinks.parseMoxfield(Json.parseToJsonElement(body))!!
        assertEquals(2, DecklistParser.parse(d.text).single().quantity)
    }

    @Test fun archidekt() {
        val body = """{"name":"Arch","categories":[{"name":"Maybe","includedInDeck":false},{"name":"Ramp","includedInDeck":true}],
            "cards":[
              {"quantity":1,"categories":["Commander"],"card":{"oracleCard":{"name":"Atraxa, Praetors' Voice"},"edition":{"editioncode":"one"},"collectorNumber":"196"}},
              {"quantity":1,"categories":["Ramp"],"modifier":"Foil","card":{"oracleCard":{"name":"Sol Ring"},"edition":{"editioncode":"cmr"},"collectorNumber":"263"}},
              {"quantity":1,"categories":["Maybe"],"card":{"oracleCard":{"name":"Nope"}}},
              {"quantity":2,"categories":["Sideboard"],"card":{"oracleCard":{"name":"Duress"}}}
            ]}"""
        val lines = DecklistParser.parse(DeckLinks.parseArchidekt(Json.parseToJsonElement(body))!!.text)
        assertEquals(listOf("Atraxa, Praetors' Voice", "Sol Ring", "Duress"), lines.map { it.name })
        assertEquals(listOf("Commander", "Main", "Sideboard"), lines.map { it.section })
        assertTrue(lines[1].foil)
    }

    @Test fun formatsAndOathbreaker() {
        val mox = """{"name":"Oath","format":"oathbreaker","boards":{
            "commanders":{"cards":{"a":{"quantity":1,"card":{"name":"Chandra, Torch of Defiance","set":"kld","cn":"110"}}}},
            "signatureSpells":{"cards":{"s":{"quantity":1,"card":{"name":"Fireball","set":"m10","cn":"136"}}}},
            "companions":{"cards":{"c":{"quantity":1,"card":{"name":"Lurrus of the Dream-Den","set":"iko","cn":"226"}}}},
            "mainboard":{"cards":{"b":{"quantity":1,"card":{"name":"Shock","set":"m19","cn":"156"}}}}}}"""
        val d = DeckLinks.parseMoxfield(Json.parseToJsonElement(mox))!!
        assertEquals("oathbreaker", d.format)
        val c = DeckCommanders.fromLines(DecklistParser.parse(d.text))
        assertEquals(listOf("Chandra, Torch of Defiance"), c.commanders)
        assertEquals("Fireball", c.signature)
        assertEquals("Lurrus of the Dream-Den", c.companion)
        assertEquals("paupercommander", DeckFormats.fromMoxfield("pauperEdh"))
        assertEquals("", DeckFormats.fromMoxfield("gladiator"))
        val arch = """{"name":"A","deckFormat":3,"cards":[{"quantity":1,"categories":["Commander"],"card":{"oracleCard":{"name":"Atraxa, Praetors' Voice"}}}]}"""
        assertEquals("commander", DeckLinks.parseArchidekt(Json.parseToJsonElement(arch))!!.format)
    }
}
