package com.mtgscanner.ui.scan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** What survives the app closing mid-pile: the scan list, sorting session, set-aside cards, pack and box check. */
@Serializable
data class SavedScanSession(
    val session: List<ScanEntry> = emptyList(),
    val sort: SortSession? = null,
    val review: List<ReviewItem> = emptyList(),
    val pack: PackDraft? = null,
    val boxCheck: BoxCheckRun? = null,
) {
    val isEmpty: Boolean get() = session.isEmpty() && sort == null && review.isEmpty() && pack == null && boxCheck == null

    companion object {
        fun of(s: ScanUiState) = SavedScanSession(s.session, s.sort, s.review, s.pack, s.boxCheck)
    }
}

/** Keeps [SavedScanSession] in one JSON file in filesDir. Written through a temp file so a crash can't half-write it. */
class ScanSessionStore(dir: File) {
    private val file = File(dir, "scan-session.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun load(): SavedScanSession? = runCatching {
        if (!file.exists()) null else json.decodeFromString(SavedScanSession.serializer(), file.readText())
    }.getOrNull()

    fun save(s: SavedScanSession) {
        runCatching {
            if (s.isEmpty) { file.delete(); return }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(SavedScanSession.serializer(), s))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }
}
