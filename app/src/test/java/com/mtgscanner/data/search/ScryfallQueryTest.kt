package com.mtgscanner.data.search

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ScryfallQueryTest {
    private fun card(
        name: String,
        type: String = "Instant",
        mana: String = "",
        cmc: Double = 0.0,
        colors: String = "",
        identity: String = colors,
        text: String = "",
        set: String = "M10",
        number: String = "1",
        rarity: String = "common",
        power: String? = null,
        toughness: String? = null,
        usd: Double? = null,
        eur: Double? = null,
        foil: Boolean = false,
        lang: String = "en",
        released: String? = "2009-07-17",
        artist: String? = null,
        oracleId: String? = "oid-$name",
        tags: String = "",
        userTags: String = "",
        qty: Int = 1,
        condition: String = "NM",
        boxId: Long = 1,
        addedAt: Long = 0,
        id: Long = 0,
        reserved: Boolean = false,
        proxy: Boolean = false,
        signed: Boolean = false,
        forSale: Boolean = false,
        graded: String = "",
    ) = CollectionCard(
        id = id, scryfallId = "sid-$name", oracleId = oracleId, name = name, setCode = set, setName = "Set $set",
        collectorNumber = number, rarity = rarity, typeLine = type, manaCost = mana, cmc = cmc, colors = colors,
        colorIdentity = identity, oracleText = text, tags = tags, priceUsd = usd, priceUsdFoil = null, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = lang, releasedAt = released, artist = artist, foil = foil,
        quantity = qty, condition = condition, boxId = boxId, userTags = userTags, power = power, toughness = toughness,
        reserved = reserved, proxy = proxy, signed = signed, forSale = forSale, graded = graded,
        addedAt = addedAt, updatedAt = 0, priceEur = eur,
    )

    private val bolt = card("Lightning Bolt", mana = "{R}", cmc = 1.0, colors = "R", text = "Lightning Bolt deals 3 damage to any target.",
        number = "146", usd = 2.5, eur = 2.0, artist = "Christopher Rush", tags = "Instant", userTags = "trade,For Kenji")
    private val counterspell = card("Counterspell", mana = "{U}{U}", cmc = 2.0, colors = "U", text = "Counter target spell.",
        set = "MH2", number = "267", rarity = "uncommon", usd = 1.0, released = "2021-06-18")
    private val goblin = card("Goblin Guide", type = "Creature — Goblin Scout", mana = "{R}", cmc = 1.0, colors = "R",
        text = "Haste\nWhenever Goblin Guide attacks, defending player reveals the top card of their library.", rarity = "rare",
        power = "2", toughness = "2", tags = "Haste,Creature,Goblin,Scout", set = "ZEN", usd = 4.0)
    private val niv = card("Niv-Mizzet, Parun", type = "Legendary Creature — Dragon Wizard", mana = "{U}{U}{U}{R}{R}{R}", cmc = 6.0,
        colors = "UR", text = "This spell can't be countered.\nFlying", rarity = "rare", power = "5", toughness = "5", set = "GRN", usd = 3.0)
    private val sol = card("Sol Ring", type = "Artifact", mana = "{1}", cmc = 1.0, text = "{T}: Add {C}{C}.", rarity = "uncommon", set = "C21", usd = 1.5)
    private val forest = card("Forest", type = "Basic Land — Forest", set = "ZEN", usd = 0.1)
    private val tarmo = card("Tarmogoyf", type = "Creature — Lhurgoyf", mana = "{1}{G}", cmc = 2.0, colors = "G", power = "*", toughness = "1+*",
        text = "Tarmogoyf's power is equal to the number of card types among cards in all graveyards and its toughness is equal to that number plus 1.",
        rarity = "mythic", set = "FUT", usd = 20.0)
    private val manlo = card("Mana Leak", mana = "{1}{U}", cmc = 2.0, colors = "U", text = "Counter target spell unless its controller pays {3}.")
    private val bear = card("Grizzly Bears", type = "Creature — Bear", mana = "{1}{G}", cmc = 2.0, colors = "G", power = "2", toughness = "2")
    private val fireIce = card("Fire // Ice", mana = "{1}{R} // {1}{U}", cmc = 4.0, colors = "UR", text = "Fire deals 2 damage divided as you choose.")
    private val all = listOf(bolt, counterspell, goblin, niv, sol, forest, tarmo, manlo, bear, fireIce)

    private fun names(q: String, env: SearchEnv = SearchEnv(remote = { null }), cards: List<CollectionCard> = all): List<String> {
        val cq = ScryfallQuery.compile(q)
        assertNull("$q should read as syntax", cq.plain)
        return cards.filter { cq.matches(it, env) }.map { it.name }
    }

    @Test fun plainTextHasNoSyntax() {
        for (q in listOf("bolt", "lightning bolt", "3 damage", "half-elf", "Circle of Protection: Red", "+1/+1", "Fire // Ice", "x:y")) {
            val cq = ScryfallQuery.compile(q)
            assertEquals(q, q.trim().lowercase(), cq.plain)
            assertFalse(q, cq.invalid)
        }
    }

    @Test fun invalidSyntaxFallsBackToPlainText() {
        for (q in listOf("t:", "\"open quote", "mv>abc", "c:purple", "(t:elf", "t:elf)", "or t:elf", "t:elf or", "r:legendary", "name:/[/")) {
            val cq = ScryfallQuery.compile(q)
            if (q == "(t:elf") { assertNull(cq.plain); continue } // a missing ) at the end is let go
            assertTrue(q, cq.invalid)
            assertEquals(q.lowercase(), cq.plain)
        }
    }

    @Test fun bareWordsMatchNames() {
        assertEquals(listOf("Lightning Bolt"), names("-goblin bolt"))
        assertEquals(listOf("Lightning Bolt"), names("bolt lightning t:instant"))
        assertEquals(listOf("Lightning Bolt"), names("\"lightning bolt\""))
        assertEquals(emptyList<String>(), names("\"bolt lightning\""))
    }

    @Test fun exactName() {
        assertEquals(listOf("Sol Ring"), names("!\"sol ring\""))
        assertEquals(listOf("Forest"), names("!forest"))
        assertEquals(emptyList<String>(), names("!\"sol\""))
        // A face of a split card counts.
        assertEquals(listOf("Fire // Ice"), names("!fire"))
    }

    @Test fun curlyQuotesWork() {
        assertEquals(listOf("Sol Ring"), names("!“Sol Ring”"))
    }

    @Test fun accentsAreIgnored() {
        val c = card("Lim-Dûl the Necromancer")
        assertEquals(listOf(c.name), names("lim-dul t:instant", cards = listOf(c)))
    }

    @Test fun negationOrAndParentheses() {
        assertEquals(listOf("Counterspell", "Mana Leak"), names("t:instant c:u -t:creature -fire"))
        assertEquals(listOf("Lightning Bolt", "Counterspell"), names("bolt or counterspell"))
        assertEquals(listOf("Lightning Bolt", "Counterspell"), names("bolt OR counterspell"))
        assertEquals(listOf("Goblin Guide", "Grizzly Bears"), names("t:creature (c:r or c:g) pow=2"))
        assertEquals(listOf("Lightning Bolt", "Counterspell", "Sol Ring", "Forest", "Mana Leak", "Fire // Ice"), names("-(t:creature)"))
        // AND binds tighter than OR.
        assertEquals(listOf("Lightning Bolt", "Sol Ring"), names("c:r t:instant -fire or t:artifact"))
    }

    @Test fun colors() {
        assertEquals(listOf("Niv-Mizzet, Parun", "Fire // Ice"), names("c:ur"))
        assertEquals(listOf("Niv-Mizzet, Parun", "Fire // Ice"), names("c:izzet"))
        assertEquals(listOf("Niv-Mizzet, Parun", "Fire // Ice"), names("c=ur"))
        assertEquals(listOf("Lightning Bolt", "Goblin Guide"), names("c=r"))
        assertEquals(listOf("Lightning Bolt", "Counterspell", "Goblin Guide", "Niv-Mizzet, Parun", "Sol Ring", "Forest", "Mana Leak", "Fire // Ice"), names("c<=ur"))
        assertEquals(listOf("Lightning Bolt", "Counterspell", "Goblin Guide", "Sol Ring", "Forest", "Mana Leak"), names("c<ur"))
        assertEquals(listOf("Niv-Mizzet, Parun", "Fire // Ice"), names("c>r"))
        assertEquals(listOf("Sol Ring", "Forest"), names("c:c"))
        assertEquals(listOf("Sol Ring", "Forest"), names("c:colorless"))
        assertEquals(listOf("Niv-Mizzet, Parun", "Fire // Ice"), names("c:m"))
        assertEquals(listOf("Niv-Mizzet, Parun", "Fire // Ice"), names("c:multicolor"))
        assertEquals(listOf("Niv-Mizzet, Parun", "Fire // Ice"), names("c=2"))
        assertEquals(listOf("Sol Ring", "Forest"), names("c<1"))
        assertEquals(listOf("Lightning Bolt", "Goblin Guide"), names("color:red -c:u"))
        assertEquals(listOf("Lightning Bolt", "Goblin Guide"), names("c:red c!=ur"))
    }

    @Test fun identityColonMeansAtMost() {
        assertEquals(listOf("Lightning Bolt", "Goblin Guide", "Sol Ring", "Forest"), names("id:r"))
        assertEquals(listOf("Lightning Bolt", "Counterspell", "Goblin Guide", "Niv-Mizzet, Parun", "Sol Ring", "Forest", "Mana Leak", "Fire // Ice"), names("identity:izzet"))
        assertEquals(listOf("Niv-Mizzet, Parun", "Fire // Ice"), names("id>=ur"))
        assertEquals(listOf("Sol Ring", "Forest"), names("id:c"))
        assertEquals(listOf("Lightning Bolt", "Goblin Guide"), names("id=r"))
        val esper = card("Esper Charm", colors = "WUB")
        assertEquals(listOf("Esper Charm"), names("id:esper", cards = listOf(esper, bolt)))
        assertEquals(listOf("Esper Charm"), names("c:wubrg or id=3", cards = listOf(esper, bolt)))
    }

    @Test fun typeAndOracle() {
        assertEquals(listOf("Goblin Guide", "Niv-Mizzet, Parun", "Tarmogoyf", "Grizzly Bears"), names("t:creature"))
        assertEquals(listOf("Niv-Mizzet, Parun"), names("t:legendary t:creature"))
        assertEquals(listOf("Niv-Mizzet, Parun"), names("t:\"legendary creature\""))
        assertEquals(listOf("Forest"), names("type:basic"))
        assertEquals(listOf("Counterspell", "Mana Leak"), names("o:\"counter target\""))
        assertEquals(listOf("Lightning Bolt"), names("o:\"~ deals 3\""))
        // ~ in a split card is either face.
        assertEquals(listOf("Fire // Ice"), names("o:\"~ deals 2\""))
        assertEquals(listOf("Goblin Guide"), names("oracle:haste"))
        assertEquals(listOf("Counterspell", "Mana Leak"), names("o:/^counter target/"))
        assertEquals(listOf("Goblin Guide", "Grizzly Bears"), names("t:/(scout|bear)$/"))
        assertEquals(listOf("Goblin Guide"), names("kw:haste"))
    }

    @Test fun manaCosts() {
        assertEquals(listOf("Counterspell", "Niv-Mizzet, Parun"), names("m:uu"))
        assertEquals(listOf("Counterspell", "Niv-Mizzet, Parun"), names("m:{U}{U}"))
        assertEquals(listOf("Counterspell"), names("m={U}{U}"))
        assertEquals(listOf("Mana Leak", "Fire // Ice"), names("m:1u"))
        assertEquals(listOf("Sol Ring", "Mana Leak", "Fire // Ice", "Tarmogoyf", "Grizzly Bears").sorted(), names("mana:{1}").sorted())
        assertEquals(listOf("Lightning Bolt", "Goblin Guide", "Forest"), names("m<=r"))
        val hybrid = card("Kitchen Finks", mana = "{1}{G/W}{G/W}", cmc = 3.0)
        assertEquals(listOf("Kitchen Finks"), names("m:{w/g}", cards = listOf(hybrid, bolt)))
    }

    @Test fun manaValue() {
        assertEquals(listOf("Niv-Mizzet, Parun"), names("mv>=5"))
        assertEquals(listOf("Niv-Mizzet, Parun"), names("cmc>5"))
        assertEquals(listOf("Counterspell", "Tarmogoyf", "Mana Leak", "Grizzly Bears"), names("manavalue=2"))
        assertEquals(listOf("Counterspell", "Tarmogoyf", "Mana Leak", "Grizzly Bears"), names("mv:2"))
        assertEquals(listOf("Lightning Bolt", "Goblin Guide", "Sol Ring"), names("mv:odd"))
        assertEquals(listOf("Counterspell", "Niv-Mizzet, Parun", "Forest", "Tarmogoyf", "Mana Leak", "Grizzly Bears", "Fire // Ice"), names("mv:even"))
    }

    @Test fun powerAndToughness() {
        assertEquals(listOf("Niv-Mizzet, Parun"), names("pow>=5"))
        assertEquals(listOf("Goblin Guide", "Niv-Mizzet, Parun", "Grizzly Bears"), names("pow=tou"))
        assertEquals(listOf("Tarmogoyf"), names("pow<tou"))
        assertEquals(listOf("Tarmogoyf"), names("tou=1"))
        assertEquals(listOf("Goblin Guide", "Grizzly Bears"), names("power:2 toughness:2"))
        assertEquals(listOf("Niv-Mizzet, Parun"), names("pt>=10"))
        assertEquals(listOf("Goblin Guide"), names("pow>mv"))
    }

    @Test fun rarity() {
        assertEquals(listOf("Goblin Guide", "Niv-Mizzet, Parun"), names("r:rare"))
        assertEquals(listOf("Goblin Guide", "Niv-Mizzet, Parun"), names("r:r"))
        assertEquals(listOf("Goblin Guide", "Niv-Mizzet, Parun", "Tarmogoyf"), names("r>=rare"))
        assertEquals(listOf("Counterspell", "Sol Ring"), names("rarity=uncommon"))
        assertEquals(listOf("Lightning Bolt", "Counterspell", "Sol Ring", "Forest", "Mana Leak", "Grizzly Bears", "Fire // Ice"), names("r<rare"))
    }

    @Test fun setsNumbersArtistsLanguage() {
        assertEquals(listOf("Goblin Guide", "Forest"), names("s:zen"))
        assertEquals(listOf("Goblin Guide", "Forest"), names("e:ZEN"))
        assertEquals(listOf("Goblin Guide", "Forest"), names("set:zen"))
        assertEquals(listOf("Goblin Guide", "Forest"), names("edition:zen"))
        assertEquals(listOf("Lightning Bolt"), names("s:m10 cn:146"))
        assertEquals(listOf("Lightning Bolt", "Counterspell"), names("cn>100"))
        assertEquals(listOf("Lightning Bolt"), names("number:0146"))
        assertEquals(listOf("Lightning Bolt"), names("a:rush"))
        assertEquals(listOf("Lightning Bolt"), names("artist:\"christopher rush\""))
        val ja = card("Llanowar Elves", lang = "ja")
        assertEquals(listOf("Llanowar Elves"), names("lang:ja", cards = listOf(ja, bolt)))
        assertEquals(listOf("Llanowar Elves", "Lightning Bolt"), names("lang:any", cards = listOf(ja, bolt)))
    }

    @Test fun yearAndDate() {
        assertEquals(listOf("Counterspell"), names("year>=2015"))
        assertEquals(listOf("Counterspell"), names("year=2021"))
        assertEquals(listOf("Counterspell"), names("date>=2021-01-01"))
        assertEquals(listOf("Counterspell"), names("date:2021-06-18"))
        assertEquals(listOf("Counterspell"), names("date=2021-06"))
        assertEquals(9, names("date<2010").size)
    }

    @Test fun prices() {
        assertEquals(listOf("Goblin Guide", "Niv-Mizzet, Parun", "Tarmogoyf"), names("usd>=3"))
        assertEquals(listOf("Forest"), names("usd<0.5"))
        assertEquals(listOf("Lightning Bolt"), names("eur>1"))
        // Cards with no price never match a price term.
        assertEquals(emptyList<String>(), names("eur<1"))
    }

    @Test fun isTerms() {
        assertEquals(listOf("Lightning Bolt", "Counterspell", "Mana Leak", "Fire // Ice"), names("is:spell -t:creature -t:artifact"))
        assertEquals(listOf("Goblin Guide", "Niv-Mizzet, Parun", "Sol Ring", "Forest", "Tarmogoyf", "Grizzly Bears"), names("is:permanent"))
        assertEquals(listOf("Niv-Mizzet, Parun", "Sol Ring"), names("is:historic"))
        assertEquals(listOf("Grizzly Bears"), names("is:bear"))
        assertEquals(listOf("Niv-Mizzet, Parun"), names("is:party"))
        assertEquals(listOf("Grizzly Bears"), names("is:vanilla"))
        val token = card("Goblin", type = "Token Creature — Goblin")
        val foil = card("Shiny", foil = true, reserved = true)
        assertEquals(listOf("Goblin"), names("is:token", cards = listOf(token, foil)))
        assertEquals(listOf("Shiny"), names("is:foil", cards = listOf(token, foil)))
        assertEquals(listOf("Goblin"), names("is:nonfoil", cards = listOf(token, foil)))
        assertEquals(listOf("Goblin"), names("not:foil", cards = listOf(token, foil)))
        assertEquals(listOf("Shiny"), names("is:reserved", cards = listOf(token, foil)))
    }

    @Test fun collectionTerms() {
        val a = card("Alpha", id = 1, boxId = 1, userTags = "For Kenji, trade", condition = "LP", qty = 4, proxy = true, forSale = true)
        val b = card("Beta", id = 2, boxId = 2, condition = "HP", qty = 1, signed = true, graded = "PSA 9")
        val boxes = mapOf(1L to "Red binder", 2L to "Bulk")
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 10, 22)
        val aDay = today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val bDay = LocalDate.of(2025, 3, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val cards = listOf(a.copy(addedAt = aDay), b.copy(addedAt = bDay))
        val env = SearchEnv(boxName = { boxes[it] }, lent = setOf(2L), remote = { null }, today = today, zone = zone)
        fun n(q: String) = names(q, env, cards)
        assertEquals(listOf("Alpha"), n("box:binder"))
        assertEquals(listOf("Alpha"), n("box:\"red binder\""))
        assertEquals(listOf("Beta"), n("box=bulk"))
        assertEquals(listOf("Beta"), n("-box:red"))
        assertEquals(listOf("Alpha"), n("tag:kenji"))
        assertEquals(listOf("Alpha"), n("tag=trade"))
        assertEquals(listOf("Alpha"), n("cond:lp"))
        assertEquals(listOf("Alpha"), n("cond>=lp"))
        assertEquals(listOf("Beta"), n("condition<mp"))
        assertEquals(listOf("Alpha"), n("qty>=2"))
        assertEquals(listOf("Beta"), n("quantity=1"))
        assertEquals(listOf("Alpha"), n("is:proxy"))
        assertEquals(listOf("Beta"), n("is:signed"))
        assertEquals(listOf("Beta"), n("is:graded"))
        assertEquals(listOf("Alpha"), n("is:forsale"))
        assertEquals(listOf("Beta"), n("is:lent"))
        assertEquals(listOf("Alpha"), n("added>=7d"))
        assertEquals(listOf("Alpha"), n("added:2026-10-20"))
        assertEquals(listOf("Beta"), n("added<2026"))
        assertEquals(listOf("Beta"), n("added:2025-03"))
        assertEquals(emptyList<String>(), n("added:today"))
    }

    @Test fun remoteTermsAreSkippedUntilAnswered() {
        val cq = ScryfallQuery.compile("f:modern t:instant")
        assertNull(cq.plain)
        assertEquals(1, cq.asks.size)
        val (ask, label) = cq.asks[0]
        assertEquals("f:", label)
        assertEquals("f:modern t:instant include:extras", ask.query)
        assertFalse(ask.prints)
        // Unknown yet: only t:instant counts.
        val unknown = SearchEnv(remote = { null })
        assertEquals(listOf("Lightning Bolt", "Counterspell", "Mana Leak", "Fire // Ice"), all.filter { cq.matches(it, unknown) }.map { it.name })
        // Scryfall says Bolt and Counterspell.
        val keys = setOf("oid-Lightning Bolt", "name:counterspell")
        val known = SearchEnv(remote = { if (it == ask) keys else null })
        assertEquals(listOf("Lightning Bolt", "Counterspell"), all.filter { cq.matches(it, known) }.map { it.name })
        // Negated.
        val neg = ScryfallQuery.compile("-f:modern t:instant")
        val env2 = SearchEnv(remote = { if (it.query == "f:modern t:instant include:extras") keys else null })
        assertEquals(listOf("Mana Leak", "Fire // Ice"), all.filter { neg.matches(it, env2) }.map { it.name })
    }

    @Test fun remoteAskLeavesOutWhatScryfallReadsDifferently() {
        val cq = ScryfallQuery.compile("t:creature (f:modern or otag:ramp) box:binder usd>1 is:foil c:g -s:fut")
        val queries = cq.asks.map { it.first.query }
        assertEquals(listOf("f:modern t:creature c:g -s:fut include:extras", "otag:ramp t:creature c:g -s:fut include:extras"), queries)
        assertEquals(listOf("f:", "otag:"), cq.asks.map { it.second })
    }

    @Test fun printingTermsMatchBySetAndNumber() {
        val cq = ScryfallQuery.compile("is:fullart")
        val (ask, label) = cq.asks.single()
        assertTrue(ask.prints)
        assertEquals("is:fullart", label)
        val env = SearchEnv(remote = { setOf("zen/1") })
        assertEquals(listOf("Goblin Guide", "Forest"), all.filter { cq.matches(it, env) }.map { it.name })
        assertTrue(ScryfallQuery.compile("is:commander").asks.single().first.prints.not())
        assertNotNull(ScryfallQuery.compile("date>=ori").asks.singleOrNull())
    }

    @Test fun directivesAreIgnored() {
        assertEquals(listOf("Sol Ring"), names("t:artifact include:extras unique:prints order:name"))
        assertTrue(ScryfallQuery.compile("t:artifact include:extras").asks.isEmpty())
    }

    @Test fun onlyUnknownTermsMeansEverything() {
        assertEquals(all.size, names("f:modern").size)
        assertEquals(all.size, names("f:modern or f:legacy").size)
        assertEquals(all.size, names("-f:modern").size)
    }

    @Test fun writesQueriesBack() {
        val cq = ScryfallQuery.compile("-(t:elf or c:g) !\"Sol Ring\" o:\"draw a card\"")
        assertEquals("-(t:elf or c:g) !\"Sol Ring\" o:\"draw a card\"", ScryfallQuery.toScryfall(cq.root!!))
    }

    @Test fun manaParsing() {
        assertEquals(ScryfallQuery.Mana(3, mapOf("W" to 2)), ScryfallQuery.manaOf("{3}{W}{W}"))
        assertEquals(ScryfallQuery.Mana(2, mapOf("W" to 2)), ScryfallQuery.manaOf("2WW"))
        assertEquals(ScryfallQuery.Mana(0, mapOf("G/W" to 1)), ScryfallQuery.manaOf("{W/G}"))
        assertNull(ScryfallQuery.manaOf("{W"))
    }
}
