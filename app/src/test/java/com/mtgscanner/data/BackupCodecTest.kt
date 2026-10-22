package com.mtgscanner.data

import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PricePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCodecTest {
    private val card = CollectionCard(
        id = 7, scryfallId = "abc-123", oracleId = "o1", name = "Lightning Bolt", setCode = "M10", setName = "Magic 2010",
        collectorNumber = "146", rarity = "common", typeLine = "Instant", manaCost = "{R}", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = "", tags = "", priceUsd = 2.5, priceUsdFoil = null, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "ja", releasedAt = null, artist = null, foil = true, quantity = 3,
        condition = "LP", boxId = 1, purchasePrice = 1.25, userTags = "trade", addedAt = 10, updatedAt = 20,
    )

    private fun encode(b: Backup) = ByteArrayOutputStream().also { BackupCodec.encode(b, it) }.toByteArray()
    private fun decode(text: String) = BackupCodec.decode(ByteArrayInputStream(text.toByteArray()))

    @Test fun roundTrip() {
        val b = Backup(
            version = Backup.CURRENT, exportedAt = 1234,
            boxes = listOf(Box(id = 1, name = "Red", createdAt = 5, capacity = 800)),
            cards = listOf(card),
            priceHistory = listOf(PricePoint("abc-123", "2026-01-01", 2.0, null)),
            prefs = mapOf(
                "sorting" to mapOf("bulkFloorCents" to PrefValue("i", "10"), "setLock" to PrefValue("s", "mh3")),
                "game" to mapOf("names" to PrefValue("set", set = listOf("Rae", "Kenji"))),
            ),
        )
        val back = BackupCodec.decode(ByteArrayInputStream(encode(b)))
        assertEquals(b, back)
        assertEquals(3, back.cardCount)
    }

    @Test fun refusesAnythingThatIsNotABackup() {
        assertThrows(BadBackupException::class.java) { decode("{}") }
        assertThrows(BadBackupException::class.java) { decode("[]") }
        assertThrows(BadBackupException::class.java) { decode("not json") }
        // A deck export or other JSON: no version marker.
        assertThrows(BadBackupException::class.java) { decode("""{"name":"Deck","cards":[]}""") }
        // Marked, but empty: restoring it would wipe the app.
        assertThrows(BadBackupException::class.java) { decode("""{"version":2,"boxes":[],"cards":[]}""") }
    }

    @Test fun readsOldBackupsWithoutSettings() {
        val old = String(encode(Backup(version = 1, boxes = listOf(Box(id = 1, name = "Red", createdAt = 5)))))
            .replace(""","prefs":{}""", "")
        val b = decode(old)
        assertEquals(1, b.version)
        assertTrue(b.prefs.isEmpty())
        assertEquals("Red", b.boxes.single().name)
    }

    @Test fun decksKeepFormatAndCommanders() {
        val deck = com.mtgscanner.data.db.Deck(id = 3, name = "Atraxa", text = "1 Atraxa", createdAt = 1, updatedAt = 2, format = "commander", commanders = "commander:Atraxa")
        val b = Backup(version = Backup.CURRENT, boxes = listOf(Box(id = 1, name = "Red", createdAt = 5)), decks = listOf(deck))
        assertEquals(deck, BackupCodec.decode(ByteArrayInputStream(encode(b))).decks.single())
        // Backups from before DB v13 have decks without them.
        val old = """{"version":3,"boxes":[{"id":1,"name":"Red","createdAt":5}],"decks":[{"id":3,"name":"Old","text":"4 Shock","createdAt":1,"updatedAt":2}]}"""
        val d = decode(old).decks.single()
        assertEquals("", d.format)
        assertEquals("", d.commanders)
        assertEquals("4 Shock", d.text)
    }

    @Test fun prefValuesKeepTheirTypes() {
        val v = BackupCodec.prefValues(mapOf("a" to "x", "b" to 3, "c" to 4L, "d" to 0.5f, "e" to true, "f" to setOf("y")))
        assertEquals(PrefValue("s", "x"), v["a"])
        assertEquals(PrefValue("i", "3"), v["b"])
        assertEquals(PrefValue("l", "4"), v["c"])
        assertEquals(PrefValue("f", "0.5"), v["d"])
        assertEquals(PrefValue("b", "true"), v["e"])
        assertEquals(PrefValue("set", set = listOf("y")), v["f"])
    }
}
