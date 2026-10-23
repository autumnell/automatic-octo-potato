package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class ArtIndexTest {
    // Hashes with about half their bits set, far apart from each other.
    private val bolt = 0x0F0F_0F0F_0F0F_0F0FL
    private val bear = 0x3333_3333_3333_3333L
    private val elf = 0x5555_5555_5555_5555L

    /** [h] with its lowest [n] bits flipped. */
    private fun flip(h: Long, n: Int): Long = h xor ((1L shl n) - 1)

    private fun entry(h: Long, name: String, set: String = "m10", id: String = "$name-$set") = ArtEntry(h, id, name, set, "1")

    private val index = ArtIndex(listOf(entry(bolt, "Lightning Bolt"), entry(bear, "Grizzly Bears"), entry(elf, "Llanowar Elves")))

    @Test fun findsCloseArt() {
        val hit = index.match(longArrayOf(flip(bolt, 4)))
        assertEquals("Lightning Bolt", hit?.name)
        assertEquals(4, hit?.distance)
    }

    @Test fun tooFarIsNoMatch() {
        assertNull(index.match(longArrayOf(flip(bolt, ArtIndex.MAX_DISTANCE + 1))))
    }

    @Test fun needsAClearLead() {
        // Two different cards almost as close: not sure which, so nothing.
        val near = flip(bolt, 2)
        val twins = ArtIndex(listOf(entry(bolt, "Lightning Bolt"), entry(near, "Shock")))
        assertNull(twins.match(longArrayOf(flip(bolt, 1))))
    }

    @Test fun printingsOfOneCardDontCompete() {
        // Same art in two sets: either is the right card.
        val reprints = ArtIndex(listOf(entry(bolt, "Lightning Bolt", "m10"), entry(flip(bolt, 1), "Lightning Bolt", "a25"), entry(bear, "Grizzly Bears")))
        assertEquals("Lightning Bolt", reprints.match(longArrayOf(bolt))?.name)
    }

    @Test fun bestOfSeveralWaysUp() {
        // The picture hashed upside down matches nothing; the right way up matches.
        val hit = index.match(longArrayOf(0x7777_0000_7777_0000L, flip(elf, 3)))
        assertEquals("Llanowar Elves", hit?.name)
        assertEquals(3, hit?.distance)
    }

    @Test fun flatPicturesAreIgnored() {
        val dark = ArtIndex(listOf(entry(0L, "Black Lotus"), entry(bear, "Grizzly Bears")))
        assertNull(dark.match(longArrayOf(1L)))
        assertFalse(ArtIndex.useful(-1L))
        assertTrue(ArtIndex.useful(bear))
    }

    @Test fun setLockLimitsTheSearch() {
        val two = ArtIndex(listOf(entry(bolt, "Lightning Bolt", "m10"), entry(flip(bolt, 6), "Shock", "m19")))
        // Across both sets the two are too close to call; with the lock only one is left.
        assertNull(two.match(longArrayOf(flip(bolt, 3))))
        assertEquals("Shock", two.match(longArrayOf(flip(bolt, 5)), onlySet = "M19")?.name)
        assertTrue(two.hasSet("m19"))
        assertTrue(two.hasSet("M19"))
        assertFalse(two.hasSet("mom"))
    }

    @Test fun emptyIndex() {
        assertNull(ArtIndex(emptyList()).match(longArrayOf(bolt)))
        assertNull(index.match(LongArray(0)))
    }

    @Test fun voteNeedsFramesThatAgree() {
        val v = ArtVote(needed = 2)
        assertNull(v.offer("a"))
        assertEquals("a", v.offer("a"))
        assertEquals("a", v.offer("a"))
        assertNull(v.offer("b"))
        assertNull(v.offer(null))
        assertNull(v.offer("b"))
        assertEquals("b", v.offer("b"))
        v.reset()
        assertNull(v.offer("b"))
    }

    @Test fun codecRoundTrip() {
        val entries = listOf(ArtEntry(bolt, "id-1", "Lightning Bolt", "m10", "146"), ArtEntry(-5L, "id-2", "Fire // Ice", "m10", "12a"))
        val out = ByteArrayOutputStream()
        ArtIndexCodec.write("M10", entries, out)
        val (set, back) = ArtIndexCodec.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals("m10", set)
        assertEquals(entries, back)
        assertEquals("m10" to 2, ArtIndexCodec.header(ByteArrayInputStream(out.toByteArray())))
    }

    @Test(expected = IOException::class) fun codecRejectsOtherFiles() {
        ArtIndexCodec.read(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
    }

    @Test fun skipRulesRoundTrip() {
        val r = SkipRules(basics = true, under = 0.25)
        assertEquals(r, SkipRules.decode(r.encode()))
        assertNull(SkipRules.decode(""))
        assertNull(SkipRules.decode("not json"))
        assertNull(SkipRules.decode(SkipRules().encode()))
        assertEquals("basic land", r.reason("Basic Land — Island", "normal", 1.0))
        assertEquals("under $0.25", r.reason("Instant", "normal", 0.1))
        assertNull(r.reason("Instant", "normal", null))
    }

    @Test fun settingsGiveTheirSkipRules() {
        val s = ScanSettings(skipTokens = true, skipUnder = 0.5)
        assertEquals(SkipRules(tokens = true, under = 0.5), s.skipRules(pricesShown = true))
        // Prices hidden: the price floor doesn't count.
        assertEquals(SkipRules(tokens = true), s.skipRules(pricesShown = false))
        assertNull(ScanSettings().skipRules(pricesShown = true))
    }
}
