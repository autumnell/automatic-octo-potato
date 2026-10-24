package com.mtgscanner.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One lookup by the scanner: what the camera read and what it matched. */
@Serializable
data class ScanLogEntry(
    val at: Long = System.currentTimeMillis(),
    /** Names read from the card, best first. */
    val read: List<String> = emptyList(),
    val setCode: String? = null,
    val collectorNumber: String? = null,
    /** The artwork was hashed, so printings could be compared by art. */
    val art: Boolean = false,
    /** "matched", "unsure", "no match", "offline", "error" */
    val outcome: String = "",
    val matchName: String? = null,
    val matchSet: String? = null,
    val matchNumber: String? = null,
    val ms: Long = 0,
    /** From the card first giving a read to this answer: what scanning a card feels like. 0 when not known. */
    val seenMs: Long = 0,
    /** Frames read before this lookup started. */
    val frames: Int = 0,
    val note: String? = null,
)

/**
 * Hidden log of recent scans, kept on the phone (last [MAX] lookups) so scanning problems can be
 * shared and fixed. Turned on by tapping the version number in Settings seven times.
 */
class ScanLog(private val dir: File) {
    private val file get() = File(dir, "scan-log.jsonl")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Mutex()
    private val _entries = MutableStateFlow<List<ScanLogEntry>?>(null)
    /** Newest first. */
    val entries: StateFlow<List<ScanLogEntry>?> = _entries

    /** Lines in the file, which can run past [MAX] until the next trim. */
    private var fileLines = 0

    private suspend fun loaded(): List<ScanLogEntry> {
        _entries.value?.let { return it }
        val (lines, list) = withContext(Dispatchers.IO) {
            if (!file.exists()) 0 to emptyList()
            else {
                val raw = file.readLines()
                raw.size to raw.mapNotNull { runCatching { json.decodeFromString(ScanLogEntry.serializer(), it) }.getOrNull() }.reversed().take(MAX)
            }
        }
        fileLines = lines
        _entries.value = list
        return list
    }

    suspend fun load() = lock.withLock { loaded() }

    /** Adds one lookup. Appends a line; the file is only rewritten when it has grown to twice [MAX]. */
    suspend fun add(e: ScanLogEntry) = lock.withLock {
        val list = (listOf(e) + loaded()).take(MAX)
        _entries.value = list
        withContext(Dispatchers.IO) {
            runCatching {
                if (fileLines + 1 > MAX * 2) {
                    file.writeText(list.reversed().joinToString("\n") { json.encodeToString(ScanLogEntry.serializer(), it) } + "\n")
                    fileLines = list.size
                } else {
                    file.appendText(json.encodeToString(ScanLogEntry.serializer(), e) + "\n")
                    fileLines++
                }
            }
        }
    }

    suspend fun clear() = lock.withLock {
        _entries.value = emptyList()
        fileLines = 0
        withContext(Dispatchers.IO) { file.delete() }
    }

    /** Plain text to paste into a message. */
    fun asText(list: List<ScanLogEntry>): String = buildString {
        appendLine("Grimoire scan log (${list.size} lookups, newest first)")
        ScanLogStats.of(list)?.let { appendLine(it.text) }
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
        list.forEach { e ->
            append(fmt.format(java.util.Date(e.at))).append("  ").append(e.outcome.uppercase())
            append("  read=").append(e.read.joinToString(" | ").ifEmpty { "-" })
            if (e.setCode != null || e.collectorNumber != null) append("  footer=${e.setCode ?: "?"} #${e.collectorNumber ?: "?"}")
            if (e.art) append("  art")
            if (e.matchName != null) append("  -> ${e.matchName} (${e.matchSet?.uppercase()} #${e.matchNumber})")
            append("  ${e.ms}ms")
            if (e.seenMs > 0) append("  seen=${e.seenMs}ms frames=${e.frames}")
            e.note?.let { append("  note=").append(it) }
            appendLine()
        }
    }

    companion object { const val MAX = 500 }
}

/** How scanning is going, over the log: the share matched and how long a card takes. Pure, for tests. */
data class ScanLogStats(val lookups: Int, val matchedShare: Double, val medianSeenMs: Long?, val medianLookupMs: Long?) {
    /** "92% matched · 0.8 s per card (lookup 0.3 s)". */
    val text: String get() = buildString {
        append("${(matchedShare * 100).toInt()}% matched")
        medianSeenMs?.let { append(" · %.1f s per card".format(java.util.Locale.US, it / 1000.0)) }
        medianLookupMs?.let { append(" (lookup %.1f s)".format(java.util.Locale.US, it / 1000.0)) }
    }

    companion object {
        fun of(list: List<ScanLogEntry>): ScanLogStats? {
            if (list.isEmpty()) return null
            fun median(xs: List<Long>): Long? = xs.sorted().takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
            val matched = list.count { it.outcome == "matched" }
            return ScanLogStats(
                lookups = list.size,
                matchedShare = matched.toDouble() / list.size,
                medianSeenMs = median(list.filter { it.outcome == "matched" && it.seenMs > 0 }.map { it.seenMs }),
                medianLookupMs = median(list.filter { it.ms > 0 }.map { it.ms }),
            )
        }
    }
}
