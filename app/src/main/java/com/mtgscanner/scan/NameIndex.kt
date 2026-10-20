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

    @Volatile var matcher: CardNameMatcher? = null
        private set

    /** Loads the saved list, then downloads a fresh one if it's missing or over a week old. */
    suspend fun load() = withContext(Dispatchers.IO) {
        if (matcher == null && file.exists()) {
            runCatching { matcher = CardNameMatcher(file.readLines().filter { it.isNotBlank() }) }
        }
        val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > WEEK_MS
        if (stale) {
            val names = runCatching { api.cardNames() }.getOrNull().orEmpty()
            if (names.size > 1000) {
                val tmp = File(dir, "card-names.tmp")
                tmp.writeText(names.joinToString("\n"))
                tmp.renameTo(file)
                matcher = CardNameMatcher(names)
            }
        }
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
