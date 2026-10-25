package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class BulkUriTest {
    // The shape Scryfall's /bulk-data list has had since October 2026: no download_uri, a gzipped JSON Lines file.
    private val list = """{"object":"list","has_more":false,"data":[
        {"object":"bulk_data","type":"oracle_cards","jsonl_download_uri":"https://data.scryfall.io/oracle-cards/a.jsonl.gz","compressed_size":100},
        {"object":"bulk_data","type":"default_cards","jsonl_download_uri":"https://data.scryfall.io/default-cards/b.jsonl.gz","compressed_size":80019996}
    ]}"""

    @Test fun findsTheFileByType() {
        assertEquals(BulkFile("https://data.scryfall.io/default-cards/b.jsonl.gz", 80019996), ScryfallApi().bulkUriIn(list, "default_cards"))
    }

    @Test fun olderShapeStillWorks() {
        val old = """{"data":[{"type":"default_cards","download_uri":"https://data.scryfall.io/default-cards/b.json","size":5}]}"""
        assertEquals(BulkFile("https://data.scryfall.io/default-cards/b.json", 5), ScryfallApi().bulkUriIn(old, "default_cards"))
    }

    @Test fun missingTypeIsNull() {
        assertNull(ScryfallApi().bulkUriIn(list, "all_cards"))
        assertNull(ScryfallApi().bulkUriIn("""{"object":"error"}""", "default_cards"))
        assertNull(ScryfallApi().bulkUriIn("not json", "default_cards"))
    }

    @Test fun unzipsOnlyWhenZipped() {
        val text = "{\"a\":1}\n{\"a\":2}\n"
        val zipped = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(text.toByteArray()) } }.toByteArray()
        assertEquals(text, gunzipIfNeeded(ByteArrayInputStream(zipped)).readBytes().decodeToString())
        assertEquals(text, gunzipIfNeeded(ByteArrayInputStream(text.toByteArray())).readBytes().decodeToString())
    }
}
