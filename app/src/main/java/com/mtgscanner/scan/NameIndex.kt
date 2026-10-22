package com.mtgscanner.scan

import com.mtgscanner.data.ScryfallApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Every Magic card name, kept on the phone. Downloaded from Scryfall once and refreshed weekly,
 * so names read by the camera can be corrected instantly, even offline.
 */
class NameIndex(private val dir: File, private val api: ScryfallApi) {
    private val file get() = File(dir, "card-names.txt")
    /** Token and emblem names, which Scryfall's name list leaves out. */
    private val extrasFile get() = File(dir, "token-names.txt")

    private fun saved(f: File): List<String> = if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()

    @Volatile var matcher: CardNameMatcher? = null
        private set

    /** Loads the saved list, then downloads a fresh one if it's missing or over a week old. */
    suspend fun load() = withContext(Dispatchers.IO) {
        if (matcher == null && file.exists()) {
            runCatching { matcher = CardNameMatcher(saved(file), runCatching { saved(extrasFile) }.getOrDefault(emptyList())) }
        }
        val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > WEEK_MS
        // The token list is newer than the card list on phones that had the app before: fetch it on its own once.
        val extrasStale = !extrasFile.exists() || System.currentTimeMillis() - extrasFile.lastModified() > WEEK_MS
        if (stale || extrasStale) {
            val names = if (stale) runCatching { api.cardNames() }.getOrNull().orEmpty() else emptyList()
            val extras = if (extrasStale) runCatching { api.tokenNames() }.getOrNull().orEmpty() else emptyList()
            if (names.size > 1000) write(file, names)
            if (extras.size > 50) write(extrasFile, extras)
            if (names.size > 1000 || extras.size > 50) {
                val all = names.takeIf { it.size > 1000 } ?: saved(file)
                if (all.isNotEmpty()) matcher = CardNameMatcher(all, extras.takeIf { it.size > 50 } ?: saved(extrasFile))
            }
        }
    }

    private fun write(f: File, names: List<String>) {
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(names.joinToString("\n"))
        tmp.renameTo(f)
    }

    /** The real card names closest to what was read, best first. */
    fun correct(candidates: List<String>): List<CardNameMatcher.Match> {
        val m = matcher ?: return emptyList()
        return candidates.mapNotNull { m.best(it) }
            .groupBy { it.name }.map { (_, v) -> v.maxBy { it.score } }
            .sortedByDescending { it.score }
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}
