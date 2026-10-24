package com.mtgscanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Cards the scanner identified, kept on the phone by what was read, so a card seen before (after a restart too)
 * comes up without asking Scryfall. Prices go stale, so entries are used directly only while fresh; older ones
 * stand in when Scryfall can't be reached. Only sure matches are kept, so one wrong guess doesn't stick.
 */
class IdentifyStore(dir: File) {
    @Serializable
    private class Entry(val k: String, val at: Long, val confident: Boolean, val exact: Boolean, val card: ScryfallCard)

    private val file = File(dir, "identified.jsonl")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Mutex()
    /** Newest use last. */
    private val map = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private var loaded = false
    private var lines = 0

    /** The saved match for [key] no older than [maxAgeMs], or null. */
    suspend fun get(key: String, maxAgeMs: Long): Identification? = lock.withLock {
        ensureLoaded()
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.at > maxAgeMs) return null
        Identification(e.card, e.confident, e.exact)
    }

    /** Saves a sure match; unsure ones are skipped. */
    suspend fun put(key: String, id: Identification) {
        if (!id.exact && !id.confident) return
        lock.withLock {
            ensureLoaded()
            val e = Entry(key, System.currentTimeMillis(), id.confident, id.exact, id.card)
            map[key] = e
            trim()
            withContext(Dispatchers.IO) {
                runCatching { file.appendText(json.encodeToString(Entry.serializer(), e) + "\n") }
            }
            lines++
            // The file only grows; rewrite it with what's kept once it's well past that.
            if (lines > MAX * 2) compact()
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists()) return@runCatching
                file.forEachLine { l ->
                    if (l.isBlank()) return@forEachLine
                    lines++
                    runCatching { json.decodeFromString(Entry.serializer(), l) }.getOrNull()?.let { map[it.k] = it }
                }
            }
        }
        trim()
        if (lines > MAX * 2) compact()
    }

    private fun trim() {
        while (map.size > MAX) map.remove(map.keys.first())
    }

    private suspend fun compact() = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, "identified.tmp")
            tmp.bufferedWriter().use { w -> map.values.forEach { w.write(json.encodeToString(Entry.serializer(), it)); w.newLine() } }
            if (tmp.renameTo(file)) lines = map.size
        }
    }

    companion object {
        const val MAX = 2000
        /** How long a saved match is used without asking Scryfall: its price is at most this old. */
        const val FRESH_MS = 3L * 24 * 60 * 60 * 1000
    }
}
