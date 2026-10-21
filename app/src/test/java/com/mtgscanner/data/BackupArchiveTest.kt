package com.mtgscanner.data

import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.PricePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class BackupArchiveTest {
    private val tmp: File = Files.createTempDirectory("archive").toFile()
    private val backup = Backup(version = Backup.CURRENT, exportedAt = 5, boxes = listOf(Box(id = 1, name = "Red", createdAt = 1)))

    @Test fun zipRoundTripStreamsPricesAndPhotos() {
        val photo = File(tmp, "card-1-100.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, backup, listOf(photo, File(tmp, "missing.jpg"))) { emit ->
            repeat(4500) { i -> emit(PricePoint("id$i", "2026-01-01", if (i % 2 == 0) i.toDouble() else null, null)) }
        }
        val stage = File(tmp, "stage")
        val back = BackupArchive.read(ByteArrayInputStream(out.toByteArray()), stage)
        assertEquals(backup, back.copy(staged = null))
        assertEquals(stage.path, back.staged)
        assertEquals(listOf("card-1-100.jpg"), BackupArchive.stagedPhotos(back).map { it.name })
        assertTrue(BackupArchive.stagedPhotos(back).single().readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        val sizes = mutableListOf<Int>()
        var first: PricePoint? = null
        BackupArchive.forEachPriceChunk(back) { chunk -> sizes += chunk.size; if (first == null) first = chunk.first() }
        assertEquals(listOf(2000, 2000, 500), sizes)
        assertEquals(PricePoint("id0", "2026-01-01", 0.0, null), first)
    }

    @Test fun oldJsonBackupsStillRead() {
        val json = ByteArrayOutputStream().also { BackupCodec.encode(backup.copy(version = 2), it) }.toByteArray()
        val back = BackupArchive.read(ByteArrayInputStream(json), File(tmp, "stage2"))
        assertEquals(2, back.version)
        assertEquals(null, back.staged)
    }

    @Test fun emptyOrDamagedFilesAreRefused() {
        assertThrows(BadBackupException::class.java) { BackupArchive.read(ByteArrayInputStream(ByteArray(0)), File(tmp, "s3")) }
        assertThrows(BadBackupException::class.java) { BackupArchive.read(ByteArrayInputStream("PK junk".toByteArray()), File(tmp, "s4")) }
    }
}
