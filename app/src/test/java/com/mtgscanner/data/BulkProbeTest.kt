package com.mtgscanner.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.fail
import org.junit.Test

/** Temporary: what Scryfall's bulk list looks like from CI. Fails on purpose to print it. */
class BulkProbeTest {
    @Test fun probe() {
        val c = OkHttpClient()
        val sb = StringBuilder()
        for (path in listOf("bulk-data", "bulk-data/default_cards", "bulk-data/default-cards")) {
            val req = Request.Builder().url("https://api.scryfall.com/$path")
                .header("User-Agent", "MTGScanner/0.1 (personal collection app)").header("Accept", "application/json").build()
            runCatching {
                c.newCall(req).execute().use { r -> sb.append("\n[$path] ${r.code} ${r.body?.string()?.take(1500)}") }
            }.onFailure { sb.append("\n[$path] EXC $it") }
        }
        val uri = runCatching { kotlinx.coroutines.runBlocking { ScryfallApi().bulkDownloadUri("default_cards") } }
        sb.append("\nAPI: $uri")
        fail(sb.toString())
    }
}
