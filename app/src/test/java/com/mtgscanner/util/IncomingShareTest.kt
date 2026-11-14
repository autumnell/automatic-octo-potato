package com.mtgscanner.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A shared zip is a backup to restore; anything else is a list to import. */
class IncomingShareTest {
    @Test fun aZipByTypeOrByName() {
        assertTrue(IncomingShare.isZip("application/zip", null))
        assertTrue(IncomingShare.isZip("application/x-zip-compressed", "export.bin"))
        assertTrue(IncomingShare.isZip("application/octet-stream", "Grimoire-backup.ZIP"))
        assertTrue(IncomingShare.isZip(null, "backup.zip"))
    }

    @Test fun listsAreNotBackups() {
        assertFalse(IncomingShare.isZip("text/csv", "collection.csv"))
        assertFalse(IncomingShare.isZip("text/plain", null))
        assertFalse(IncomingShare.isZip(null, null))
        assertFalse(IncomingShare.isZip(null, "zip-codes.txt"))
        // "gzip" holds "zip" but is not a backup.
        assertFalse(IncomingShare.isZip("application/gzip", "cards.csv.gz"))
    }
}
