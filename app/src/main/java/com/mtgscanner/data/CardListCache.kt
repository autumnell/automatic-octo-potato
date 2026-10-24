package com.mtgscanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Lists of Scryfall cards kept on the phone by what was asked for: a card's printings, or a whole set's cards.
 * A card scanned again (after a restart too) then needs no download to pick its printing. Kept a few days, as
 * prices go stale; older lists still stand in when Scryfall can't be reached.
 */
class CardListCache(dir: File) {
    private val folder = File(dir, "card-lists")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(ScryfallCard.serializer())

    private fun fileFor(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(folder, "$hash.json")
    }

    /** The list saved for [key] no older than [maxAgeMs], or null. */
    suspend fun get(key: String, maxAgeMs: Long = FRESH_MS): List<ScryfallCard>? = withContext(Dispatchers.IO) {
        val f = fileFor(key)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > maxAgeMs) return@withContext null
        runCatching { json.decodeFromString(serializer, f.readText()) }.getOrNull()
    }

    suspend fun put(key: String, list: List<ScryfallCard>) = withContext(Dispatchers.IO) {
        if (list.isEmpty()) return@withContext
        runCatching {
            folder.mkdirs()
            val tmp = File(folder, "write.tmp")
            tmp.writeText(json.encodeToString(serializer, list))
            tmp.renameTo(fileFor(key))
            trim()
        }
    }

    /** Keeps the newest [MAX_FILES] lists. */
    private fun trim() {
        val files = folder.listFiles { f -> f.name.endsWith(".json") } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    companion object {
        const val FRESH_MS = 3L * 24 * 60 * 60 * 1000
        const val MAX_FILES = 400
    }
}
