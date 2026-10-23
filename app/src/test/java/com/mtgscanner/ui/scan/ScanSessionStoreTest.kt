package com.mtgscanner.ui.scan

import com.mtgscanner.data.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScanSessionStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun card(name: String) = ScryfallCard(
        id = "id-$name", name = name, set = "m10", setName = "Magic 2010", collectorNumber = "1", rarity = "rare",
        oracleText = "Text of $name", flavorText = "Flavor of $name",
        legalities = mapOf("modern" to "legal", "standard" to "not_legal", "vintage" to "restricted"),
        purchaseUris = mapOf("tcgplayer" to "https://example.com/$name"),
    )

    private fun entry(key: Long, name: String, count: Int = 1) = ScanEntry(key = key, rowId = key * 10, card = card(name), foil = false, count = count)

    @Test fun eachLineIsWrittenOnceAndComesBack() {
        val a = entry(1, "Bolt")
        val b = entry(2, "Bears", count = 3)
        val c = entry(3, "Elves")
        val saved = SavedScanSession(
            session = listOf(b, a),
            sort = SortSession(listOf(b), done = setOf(7L)),
            pack = PackDraft("Pack", 4.0, listOf(c, a)),
        )
        val store = ScanSessionStore(tmp.root)
        store.save(saved)
        val text = File(tmp.root, "scan-session.json").readText()
        assertEquals(1, Regex("\"name\":\"Bears\"").findAll(text).count())
        assertFalse("Flavor" in text)
        assertFalse("tcgplayer" in text)

        val back = store.load()!!
        assertEquals(listOf(2L, 1L), back.session.map { it.key })
        assertEquals(listOf(2L), back.sort!!.entries.map { it.key })
        assertEquals(setOf(7L), back.sort!!.done)
        assertEquals(listOf(3L, 1L), back.pack!!.entries.map { it.key })
        assertEquals("Pack", back.pack!!.name)
        assertEquals(3, back.session.first().count)
        assertTrue(back.entries.isEmpty())
        // What adding the card again needs is kept.
        val bears = back.session.first().card
        assertEquals("Text of Bears", bears.oracleText)
        assertEquals(mapOf("modern" to "legal", "vintage" to "restricted"), bears.legalities)
        assertNull(bears.flavorText)
    }

    @Test fun sortOnlyIsNotEmpty() {
        val store = ScanSessionStore(tmp.root)
        store.save(SavedScanSession(sort = SortSession(listOf(entry(1, "Bolt")))))
        assertEquals(listOf(1L), store.load()!!.sort!!.entries.map { it.key })
        store.save(SavedScanSession())
        assertNull(store.load())
    }

    @Test fun oldFilesStillLoad() {
        val old = SavedScanSession(session = listOf(entry(1, "Bolt")), pack = PackDraft("P", null, listOf(entry(1, "Bolt"))))
        val json = kotlinx.serialization.json.Json { encodeDefaults = false }
        File(tmp.root, "scan-session.json").writeText(json.encodeToString(SavedScanSession.serializer(), old))
        val back = ScanSessionStore(tmp.root).load()!!
        assertEquals(old.session, back.session)
        assertEquals(old.pack, back.pack)
    }

    @Test fun sameAsComparesByReference() {
        val list = listOf(entry(1, "Bolt"))
        assertTrue(SavedScanSession(session = list).sameAs(SavedScanSession(session = list)))
        assertFalse(SavedScanSession(session = list).sameAs(SavedScanSession(session = list.toList())))
    }
}
