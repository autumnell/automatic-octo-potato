package com.mtgscanner.data

import com.mtgscanner.data.PrintingLookup.Ident
import com.mtgscanner.data.PrintingLookup.Wanted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintingLookupTest {
    private data class Card(val name: String, val set: String, val number: String)

    /** A tiny Scryfall: answers identifiers the way /cards/collection does (name alone = default printing). */
    private class FakeScryfall(val cards: List<Card>) {
        val calls = mutableListOf<List<Ident>>()
        fun lookup(ids: List<Ident>): List<Card?> {
            calls += ids
            return ids.map { i ->
                when {
                    i.set != null && i.number != null -> cards.firstOrNull { it.set == i.set && it.number.equals(i.number, true) }
                    i.set != null -> cards.firstOrNull { it.set == i.set && it.name.equals(i.name, true) }
                    else -> cards.firstOrNull { it.name.equals(i.name, true) }
                }
            }
        }
    }

    private val ring = listOf(
        Card("Sol Ring", "cmr", "263"), // default printing
        Card("Sol Ring", "fdc", "123"),
        Card("Sol Ring", "fdc", "456"),
        Card("Forest", "zen", "7"),
    )

    @Test fun setAndNumberWinsCaseInsensitively() {
        val s = FakeScryfall(ring)
        val got = PrintingLookup.resolve(listOf(Wanted("Sol Ring", "FDC", "123"), Wanted("Sol Ring", "fdc", "456"))) { s.lookup(it) }
        assertEquals(listOf(ring[1], ring[2]), got)
        assertEquals(1, s.calls.size)
        assertEquals(Ident(set = "fdc", number = "123"), s.calls[0][0])
    }

    @Test fun setWithoutNumberGetsThatSet() {
        val s = FakeScryfall(ring)
        val got = PrintingLookup.resolve(listOf(Wanted("Sol Ring", "FDC"))) { s.lookup(it) }
        assertEquals(ring[1], got.single())
        assertEquals(Ident(set = "fdc", name = "Sol Ring"), s.calls[0][0])
    }

    @Test fun fallsBackStepByStep() {
        val s = FakeScryfall(ring)
        val got = PrintingLookup.resolve(
            listOf(
                Wanted("Sol Ring", "fdc", "999"), // no such number: same set by name
                Wanted("Sol Ring", "xyz", "1"), // no such set: default printing
                Wanted("Forest", "zen", "007"), // padded number
                Wanted("Nope", "fdc", "1"),
                Wanted("Sol Ring"),
            ),
        ) { s.lookup(it) }
        assertEquals(listOf(ring[1], ring[0], ring[3], null, ring[0]), got)
        // Only lines still missing are sent again.
        assertEquals(5, s.calls[0].size)
        assertTrue(s.calls.drop(1).all { it.size < 5 })
    }

    @Test fun idFirstThenPrinting() {
        assertEquals(
            listOf(Ident(id = "abc"), Ident(set = "fdc", number = "12"), Ident(set = "fdc", name = "Sol Ring"), Ident(name = "Sol Ring")),
            PrintingLookup.attempts(Wanted("Sol Ring", "FDC", "12", "abc")),
        )
        assertEquals(listOf(Ident(name = "Sol Ring")), PrintingLookup.attempts(Wanted("Sol Ring")))
    }

    @Test fun samePrinting() {
        assertTrue(PrintingLookup.samePrinting("FDC", "123", "fdc", "123"))
        assertTrue(PrintingLookup.samePrinting("fdc", null, "fdc", "456"))
        assertTrue(PrintingLookup.samePrinting(null, null, "cmr", "263"))
        assertTrue(PrintingLookup.samePrinting("zen", "007", "zen", "7"))
        assertTrue(PrintingLookup.samePrinting("DAR", "1", "dom", "1"))
        assertFalse(PrintingLookup.samePrinting("fdc", "123", "cmr", "263"))
        assertFalse(PrintingLookup.samePrinting("fdc", "123", "fdc", "456"))
    }

    @Test fun jsonIdentifiers() {
        assertEquals("""{"set":"fdc","collector_number":"123"}""", PrintingLookup.json(Ident(set = "fdc", number = "123")).toString())
        assertEquals("""{"name":"Sol Ring","set":"fdc"}""", PrintingLookup.json(Ident(set = "fdc", name = "Sol Ring")).toString())
        assertEquals("""{"name":"Sol Ring"}""", PrintingLookup.json(Ident(name = "Sol Ring")).toString())
    }
}
