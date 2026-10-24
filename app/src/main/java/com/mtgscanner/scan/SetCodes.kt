package com.mtgscanner.scan

import com.mtgscanner.data.ScryfallApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Corrects a set code read off a card's footer to a real one. The reader confuses letters and digits that look
 * alike ("0TJ" for OTJ, "M1D" for MID); a code no set has would fail the footer lookup and fall back to the slower
 * name-only path. Pure, for tests.
 */
object SetCodeFix {
    /** Characters the text reader mixes up, both ways. */
    private val LOOKALIKES: Map<Char, String> = mapOf(
        '0' to "OQD", 'O' to "0QD", 'Q' to "O0", 'D' to "O0",
        '1' to "ILT", 'I' to "1LT", 'L' to "1I", 'T' to "1I",
        '5' to "S", 'S' to "5", '8' to "B", 'B' to "8", '2' to "Z", 'Z' to "2",
        '6' to "G", 'G' to "6", 'U' to "V", 'V' to "U", '4' to "A", 'A' to "4",
    )

    /**
     * The real code [read] most likely is, upper case: [read] itself when a set has it, else the one known code
     * that one or two look-alike swaps reach (preferring a set big enough for [number]). Null when none, or more
     * than one equally likely, is found: the read is then left as it is.
     */
    fun fix(read: String, known: Map<String, Int>, number: String? = null): String? {
        val up = read.uppercase()
        if (known.isEmpty() || up.lowercase() in known) return up
        val n = number?.takeWhile { it.isDigit() }?.toIntOrNull()
        fun fits(code: String) = n == null || (known[code.lowercase()] ?: 0) >= n
        for (swaps in 1..2) {
            val hits = variants(up, swaps).filter { it.lowercase() in known }.distinct()
            val good = hits.filter(::fits).ifEmpty { hits }
            if (good.size == 1) return good[0]
            if (good.size > 1) return null
        }
        return null
    }

    /** [s] with exactly [swaps] characters replaced by their look-alikes. */
    private fun variants(s: String, swaps: Int): List<String> {
        val out = ArrayList<String>()
        fun go(chars: CharArray, from: Int, left: Int) {
            if (left == 0) { out += String(chars); return }
            for (i in from until chars.size) {
                val orig = chars[i]
                for (c in LOOKALIKES[orig].orEmpty()) {
                    chars[i] = c
                    go(chars, i + 1, left - 1)
                }
                chars[i] = orig
            }
        }
        go(s.toCharArray(), 0, swaps)
        return out
    }
}

/** Every paper set's code and size, kept on the phone and refreshed weekly, for [SetCodeFix]. */
class SetCodes(private val dir: File, private val api: ScryfallApi) {
    private val file get() = File(dir, "set-codes.txt")

    /** Code (lower case) to card count; empty until loaded. */
    @Volatile var known: Map<String, Int> = emptyMap()
        private set

    suspend fun load() = withContext(Dispatchers.IO) {
        if (known.isEmpty() && file.exists()) known = runCatching { parse(file.readLines()) }.getOrDefault(emptyMap())
        val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > WEEK_MS
        if (stale) {
            val sets = runCatching { api.sets() }.getOrNull().orEmpty()
            if (sets.size > 100) {
                val lines = sets.map { "${it.code.lowercase()}\t${maxOf(it.cardCount, it.printedSize ?: 0)}" }
                runCatching {
                    val tmp = File(dir, "set-codes.tmp")
                    tmp.writeText(lines.joinToString("\n"))
                    tmp.renameTo(file)
                }
                known = parse(lines)
            }
        }
    }

    /** [read] corrected to a real set code (see [SetCodeFix]), or [read] when it can't be. */
    fun fix(read: String, number: String?): String = SetCodeFix.fix(read, known, number) ?: read

    private fun parse(lines: List<String>): Map<String, Int> = lines.mapNotNull { l ->
        val parts = l.split('\t')
        parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { it to (parts.getOrNull(1)?.toIntOrNull() ?: 0) }
    }.toMap()

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}
