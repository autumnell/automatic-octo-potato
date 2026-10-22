package com.mtgscanner.data

import com.mtgscanner.util.DeckLine
import com.mtgscanner.util.DecklistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckFormatsTest {
    private val legend = "Legendary Creature — Human Wizard"
    private val thrasios = RulesCard("Thrasios, Triton Hero", "Legendary Creature — Merfolk Wizard", "{4}: Scry 1.\nPartner (You can have two commanders if both have partner.)", "GU")
    private val tymna = RulesCard("Tymna the Weaver", legend, "Lifelink\nPartner (You can have two commanders if both have partner.)", "WB")
    private val pir = RulesCard("Pir, Imaginative Rascal", legend, "Partner with Toothy, Imaginary Friend (When this creature enters, target player may put Toothy into their hand.)\nIf one or more counters would be put on a permanent your team controls, that many plus one are put on it instead.", "G")
    private val toothy = RulesCard("Toothy, Imaginary Friend", "Legendary Creature — Illusion", "Partner with Pir, Imaginative Rascal (When this creature enters, target player may put Pir into their hand.)", "U")
    private val wilson = RulesCard("Wilson, Refined Grizzly", "Legendary Creature — Bear Warrior", "Choose a Background (You can have a Background as a second commander.)\nReach, trample, ward {2}", "G")
    private val background = RulesCard("Raised by Giants", "Legendary Enchantment — Background", "Commander creatures you own have base power and toughness 10/10.", "G")
    private val rose = RulesCard("Rose Tyler", "Legendary Creature — Human", "Doctor's companion (You can have two commanders if the other is the Doctor.)", "W")
    private val doctor = RulesCard("The Tenth Doctor", "Legendary Creature — Time Lord Doctor", "Allons-y!", "URG")
    private val friend = RulesCard("Will the Wise", legend, "Friends forever (You can have two commanders if both have friends forever.)", "U")
    private val friend2 = RulesCard("Dustin, Gadget Genius", legend, "Friends forever (You can have two commanders if both have friends forever.)", "U")
    private val atraxa = RulesCard("Atraxa, Praetors' Voice", "Legendary Creature — Phyrexian Angel Horror", "Flying, vigilance, deathtouch, lifelink\nAt the beginning of your end step, proliferate.", "WUBG")
    private val chandra = RulesCard("Chandra, Torch of Defiance", "Legendary Planeswalker — Chandra", "+1: Exile the top card of your library.", "R")
    private val shock = RulesCard("Shock", "Instant", "Shock deals 2 damage to any target.", "R")
    private val lurrus = RulesCard("Lurrus of the Dream-Den", "Legendary Creature — Cat Nightmare", "Companion — Each permanent card in your starting deck has mana value 2 or less.\nLifelink", "WB")

    @Test fun formats() {
        assertTrue(DeckFormats.isCommander("commander"))
        assertTrue(DeckFormats.isCommander("paupercommander"))
        assertFalse(DeckFormats.isCommander("modern"))
        assertFalse(DeckFormats.isCommander(""))
        assertEquals("None", DeckFormats.of("nonsense").label)
        assertEquals("duel", DeckFormats.fromMoxfield("duelCommander"))
        assertEquals("brawl", DeckFormats.fromMoxfield("historicBrawl"))
        assertEquals("modern", DeckFormats.fromMoxfield("Modern"))
        assertEquals("commander", DeckFormats.fromArchidekt(3))
        assertEquals("", DeckFormats.fromArchidekt(7))
        assertEquals("commander", DeckFormats.guess(DecklistParser.parse("1 Atraxa *CMDR*\n1 Sol Ring")))
        assertEquals("", DeckFormats.guess(DecklistParser.parse("4 Lightning Bolt")))
    }

    @Test fun commandersRoundTrip() {
        val c = DeckCommanders(listOf("Thrasios, Triton Hero", "Tymna the Weaver"), companion = "Lurrus of the Dream-Den")
        assertEquals(c, DeckCommanders.decode(c.encode()))
        assertTrue(DeckCommanders.decode("").isEmpty)
        assertTrue(DeckCommanders.decode(null).isEmpty)
        // Names with colons survive; a third commander is ignored.
        val odd = DeckCommanders.decode("commander:Circle of Protection: Red\ncommander:B\ncommander:C\nsignature:Fireball")
        assertEquals(listOf("Circle of Protection: Red", "B"), odd.commanders)
        assertEquals("Fireball", odd.signature)
    }

    @Test fun commandersFromALists() {
        val lines = DecklistParser.parse("Commander\n1 Thrasios, Triton Hero\n1 Tymna the Weaver\n\nCompanion\n1 Lurrus of the Dream-Den\n\nDeck\n1 Sol Ring")
        val c = DeckCommanders.fromLines(lines)
        assertEquals(listOf("Thrasios, Triton Hero", "Tymna the Weaver"), c.commanders)
        assertEquals("Lurrus of the Dream-Den", c.companion)
        assertNull(c.signature)
        assertTrue(DeckCommanders.fromLines(DecklistParser.parse("4 Lightning Bolt")).isEmpty)
    }

    @Test fun commandersNotInTheListAreAdded() {
        val text = "1 Sol Ring\n1 Atraxa, Praetors' Voice"
        val lines = DecklistParser.parse(text)
        // Already in the list: unchanged.
        assertEquals(text, DeckCommanders.addMissingTo(text, lines, DeckCommanders(listOf("Atraxa, Praetors' Voice"))))
        val out = DeckCommanders.addMissingTo(text, lines, DeckCommanders(listOf("Atraxa, Praetors' Voice", "Thrasios, Triton Hero"), companion = "Lurrus of the Dream-Den"))
        val parsed = DecklistParser.parse(out)
        assertEquals("Main", parsed.first { it.name == "Sol Ring" }.section)
        assertEquals("Commander", parsed.first { it.name == "Thrasios, Triton Hero" }.section)
        assertEquals("Companion", parsed.first { it.name == "Lurrus of the Dream-Den" }.section)
        assertEquals(4, parsed.size)
    }

    @Test fun whoCanLead() {
        assertTrue(CommanderRules.canLead(atraxa, "commander"))
        assertFalse(CommanderRules.canLead(chandra, "commander"))
        assertTrue(CommanderRules.canLead(chandra, "brawl"))
        assertTrue(CommanderRules.canLead(chandra, "oathbreaker"))
        assertFalse(CommanderRules.canLead(atraxa, "oathbreaker"))
        assertFalse(CommanderRules.canLead(shock, "commander"))
        assertTrue(CommanderRules.canLead(RulesCard("Grizzly Bears", "Creature — Bear"), "paupercommander"))
        assertFalse(CommanderRules.canLead(RulesCard("Grizzly Bears", "Creature — Bear"), "commander"))
        assertTrue(CommanderRules.canLead(RulesCard("Teferi, Temporal Archmage", "Legendary Planeswalker — Teferi", "Teferi, Temporal Archmage can be your commander."), "commander"))
    }

    @Test fun partners() {
        assertEquals(PairKind.PARTNER, CommanderRules.pairKind(thrasios))
        assertEquals(PairKind.PARTNER_WITH, CommanderRules.pairKind(pir))
        assertEquals("Toothy, Imaginary Friend", CommanderRules.partnerName(pir))
        assertEquals(PairKind.BACKGROUND, CommanderRules.pairKind(wilson))
        assertEquals(PairKind.DOCTORS_COMPANION, CommanderRules.pairKind(rose))
        assertEquals(PairKind.DOCTOR, CommanderRules.pairKind(doctor))
        assertEquals(PairKind.FRIENDS_FOREVER, CommanderRules.pairKind(friend))
        assertNull(CommanderRules.pairKind(atraxa))
        assertFalse(CommanderRules.allowsSecond(atraxa))

        assertTrue(CommanderRules.canPair(thrasios, tymna))
        assertFalse(CommanderRules.canPair(thrasios, thrasios))
        assertFalse(CommanderRules.canPair(thrasios, atraxa))
        assertTrue(CommanderRules.canPair(pir, toothy))
        assertTrue(CommanderRules.canPair(toothy, pir))
        assertFalse(CommanderRules.canPair(pir, thrasios))
        assertTrue(CommanderRules.canPair(wilson, background))
        assertTrue(CommanderRules.canPair(background, wilson))
        assertFalse(CommanderRules.canPair(wilson, atraxa))
        assertTrue(CommanderRules.canPair(rose, doctor))
        assertTrue(CommanderRules.canPair(doctor, rose))
        assertFalse(CommanderRules.canPair(doctor, thrasios))
        assertTrue(CommanderRules.canPair(friend, friend2))
        assertFalse(CommanderRules.canPair(friend, thrasios))
    }

    @Test fun partnerGroups() {
        val a = RulesCard("A", legend, "Partner—Survivors (You can have two commanders if both have this ability.)")
        val b = RulesCard("B", legend, "Partner—Survivors (You can have two commanders if both have this ability.)")
        val c = RulesCard("C", legend, "Partner—Father & Son (You can have two commanders if both have this ability.)")
        assertTrue(CommanderRules.canPair(a, b))
        assertFalse(CommanderRules.canPair(a, c))
        assertFalse(CommanderRules.canPair(a, thrasios))
    }

    @Test fun slotsAndSearches() {
        assertTrue(CommanderRules.fits(lurrus, CommanderSlot.COMPANION, "commander", null))
        assertFalse(CommanderRules.fits(atraxa, CommanderSlot.COMPANION, "commander", null))
        assertTrue(CommanderRules.fits(shock, CommanderSlot.SIGNATURE, "oathbreaker", chandra))
        assertTrue(CommanderRules.fits(tymna, CommanderSlot.SECOND, "commander", thrasios))
        assertFalse(CommanderRules.fits(tymna, CommanderSlot.SECOND, "commander", null))
        assertEquals("is:commander", CommanderRules.searchFilter(CommanderSlot.COMMANDER, "commander", null))
        assertEquals("t:creature r:uncommon", CommanderRules.searchFilter(CommanderSlot.COMMANDER, "paupercommander", null))
        assertEquals("t:planeswalker", CommanderRules.searchFilter(CommanderSlot.COMMANDER, "oathbreaker", null))
        assertEquals("!\"Toothy, Imaginary Friend\"", CommanderRules.searchFilter(CommanderSlot.SECOND, "commander", pir))
        assertEquals("t:background", CommanderRules.searchFilter(CommanderSlot.SECOND, "commander", wilson))
        assertEquals("(t:instant or t:sorcery) id<=r", CommanderRules.searchFilter(CommanderSlot.SIGNATURE, "oathbreaker", chandra))
        assertEquals("kw:companion", CommanderRules.searchFilter(CommanderSlot.COMPANION, "commander", null))
    }

    @Test fun copyLimits() {
        assertEquals(1, CommanderRules.copyLimit(shock, "commander"))
        assertEquals(4, CommanderRules.copyLimit(shock, "modern"))
        assertNull(CommanderRules.copyLimit(shock, ""))
        assertNull(CommanderRules.copyLimit(RulesCard("Forest", "Basic Land — Forest"), "commander"))
        assertNull(CommanderRules.copyLimit(RulesCard("Relentless Rats", "Creature — Rat", "A deck can have any number of cards named Relentless Rats."), "commander"))
        assertEquals(7, CommanderRules.copyLimit(RulesCard("Seven Dwarves", "Creature — Dwarf", "A deck can have up to seven cards named Seven Dwarves."), "commander"))
        assertNull(CommanderRules.copyLimit(null, "commander"))
    }

    @Test fun issues() {
        val cards = listOf(atraxa, shock, lurrus, RulesCard("Forest", "Basic Land — Forest"), RulesCard("Sol Ring", "Artifact", "{T}: Add {C}{C}.", ""))
            .associateBy { it.name.lowercase() }
        val info = { n: String -> cards[n.lowercase()] }
        val lines = listOf(
            DeckLine(1, "Atraxa, Praetors' Voice", section = "Commander"),
            DeckLine(2, "Sol Ring"),
            DeckLine(1, "Shock"),
            DeckLine(30, "Forest"),
            DeckLine(1, "Unknown Card"),
            DeckLine(3, "Shock", section = "Sideboard"),
        )
        val i = CommanderRules.issues(lines, "commander", DeckCommanders(listOf("Atraxa, Praetors' Voice")), info)
        assertEquals(listOf("Shock"), i.offColor)
        assertEquals(listOf("Sol Ring"), i.overLimit)
        // No commander set: only copies are checked.
        assertEquals(emptyList<String>(), CommanderRules.issues(lines, "commander", DeckCommanders(), info).offColor)
        // A commander that isn't known: colors aren't checked.
        assertTrue(CommanderRules.issues(lines, "commander", DeckCommanders(listOf("Nobody")), info).offColor.isEmpty())
        // Modern: four of a card is fine, the sideboard isn't counted.
        assertTrue(CommanderRules.issues(lines, "modern", DeckCommanders(), info).isEmpty)
        assertTrue(CommanderRules.issues(lines, "", DeckCommanders(), info).isEmpty)
    }

    @Test fun legality() {
        fun card(name: String, type: String, legal: Map<String, String>, text: String = "", id: String = "") = RulesCard(name, type, text, id, legal)
        val cards = listOf(
            card("Atraxa, Praetors' Voice", atraxa.typeLine, mapOf("commander" to "legal", "modern" to "not_legal"), id = "WUBG"),
            card("Mana Crypt", "Artifact", mapOf("commander" to "banned", "vintage" to "restricted", "modern" to "not_legal")),
            card("Sol Ring", "Artifact", mapOf("commander" to "legal", "vintage" to "restricted", "modern" to "banned")),
            card("Brainstorm", "Instant", mapOf("commander" to "legal", "vintage" to "restricted", "modern" to "not_legal"), id = "U"),
            card("Lightning Bolt", "Instant", mapOf("commander" to "legal", "modern" to "legal", "vintage" to "legal"), id = "R"),
            card("Rhystic Study", "Enchantment", emptyMap(), id = "U"),
            card("Kodama", "Creature — Spirit", mapOf("paupercommander" to "restricted")),
        ).associateBy { com.mtgscanner.util.CardNames.key(it.name) }
        val info = { n: String -> cards[com.mtgscanner.util.CardNames.key(n)] }
        val edh = listOf(
            DeckLine(1, "Atraxa, Praetors' Voice", section = "Commander"),
            DeckLine(1, "Mana Crypt"),
            DeckLine(1, "Sol Ring"),
            DeckLine(1, "Rhystic Study"),
            DeckLine(1, "Mana Crypt", section = "Maybeboard"),
        )
        val i = CommanderRules.issues(edh, "commander", DeckCommanders(listOf("Atraxa, Praetors' Voice")), info)
        assertEquals(listOf("Mana Crypt"), i.banned)
        assertTrue(i.notLegal.isEmpty())
        assertEquals(1, i.count)

        val modern = listOf(
            DeckLine(4, "Lightning Bolt"),
            DeckLine(1, "Sol Ring"),
            DeckLine(2, "Brainstorm", section = "Sideboard"),
            DeckLine(4, "Rhystic Study"),
        )
        val m = CommanderRules.issues(modern, "modern", DeckCommanders(), info)
        assertEquals(listOf("Sol Ring"), m.banned)
        assertEquals(listOf("Brainstorm"), m.notLegal)
        assertTrue(m.overLimit.isEmpty())
        assertEquals(2, m.count)

        // Vintage: restricted cards are one copy.
        val v = CommanderRules.issues(listOf(DeckLine(2, "Brainstorm"), DeckLine(1, "Sol Ring"), DeckLine(4, "Lightning Bolt")), "vintage", DeckCommanders(), info)
        assertEquals(listOf("Brainstorm"), v.overLimit)
        assertTrue(v.banned.isEmpty() && v.notLegal.isEmpty())

        // PDH: a restricted card is fine as the commander, not in the deck.
        val pdh = listOf(DeckLine(1, "Kodama", section = "Commander"))
        assertTrue(CommanderRules.issues(pdh, "paupercommander", DeckCommanders(listOf("Kodama")), info).isEmpty)
        assertEquals(listOf("Kodama"), CommanderRules.issues(listOf(DeckLine(1, "Kodama")), "paupercommander", DeckCommanders(), info).notLegal)
    }
}
