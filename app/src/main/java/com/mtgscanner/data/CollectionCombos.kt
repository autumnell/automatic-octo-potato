package com.mtgscanner.data

import com.mtgscanner.util.CardNames
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request

/** A combo whose every piece you own: each card with where its copies are, and what the combo makes. */
data class OwnedCombo(val id: String, val pieces: List<Pair<String, String>>, val produces: List<String>)

/** Reading the combo list (built from Commander Spellbook by combos.yml) against a collection. Pure, for tests. */
object CollectionComboMath {
    /** Lines are "id<TAB>card|card…<TAB>result|result…". Combos with fewer cards come first, then by result. */
    fun owned(lines: Sequence<String>, owned: Set<String>, where: (String) -> String, limit: Int = 500): List<OwnedCombo> {
        val out = ArrayList<OwnedCombo>()
        for (line in lines) {
            val parts = line.split('\t')
            if (parts.size < 2) continue
            val cards = parts[1].split('|').filter { it.isNotBlank() }
            if (cards.size < 2 || cards.any { CardNames.key(it) !in owned }) continue
            val makes = parts.getOrNull(2)?.split('|')?.filter { it.isNotBlank() }.orEmpty()
            out += OwnedCombo(parts[0], cards.map { it to where(CardNames.key(it)) }, makes)
        }
        return out.sortedWith(compareBy({ it.pieces.size }, { it.produces.firstOrNull() ?: "" })).take(limit)
    }

    /** "Binder ×2 · Deck: Atraxa" for a card's copies, biggest pile first. */
    fun whereText(copies: List<Pair<String, Int>>): String =
        copies.groupBy { it.first }.mapValues { (_, v) -> v.sumOf { it.second } }.entries
            .sortedByDescending { it.value }.joinToString(" · ") { (b, n) -> if (n > 1) "$b ×$n" else b }
}

private const val COMBO_FILE = "combos.tsv.gz"
private const val COMBO_URL = "https://github.com/autumnell/automatic-octo-potato/releases/download/models/$COMBO_FILE"
private val comboClient by lazy { sharedHttpClient.newBuilder().callTimeout(60, TimeUnit.SECONDS).build() }

/** The combo list: from the APK, else a copy downloaded before, else downloaded now. Null when there is none. */
private suspend fun CollectionRepository.comboList(): InputStream? = withContext(Dispatchers.IO) {
    runCatching { appContext.assets.open(COMBO_FILE) }.getOrNull()?.let { return@withContext GZIPInputStream(it) }
    val saved = File(appContext.filesDir, COMBO_FILE)
    // Downloaded again after two weeks, keeping the old copy when that fails.
    val stale = saved.exists() && System.currentTimeMillis() - saved.lastModified() > 14L * 24 * 3600 * 1000
    if (!saved.exists() || stale) {
        val part = File(appContext.filesDir, "$COMBO_FILE.part")
        val ok = runCatching {
            comboClient.newCall(Request.Builder().url(COMBO_URL).build()).execute().use { r ->
                if (!r.isSuccessful) return@use false
                part.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
                true
            }
        }.getOrDefault(false)
        if (!ok || !(saved.delete() || !saved.exists()) || !part.renameTo(saved)) {
            part.delete()
            if (!saved.exists()) return@withContext null
        }
    }
    runCatching { GZIPInputStream(saved.inputStream()) }.getOrNull()
}

/** Combos you could build from cards you own, with the box (or deck) each piece is in. Null when the list can't be had. */
suspend fun CollectionRepository.ownedCombos(): List<OwnedCombo>? {
    val cards = observeAll().first()
    val places = dao.allPlaces().associateBy { it.id }
    return withContext(Dispatchers.Default) {
        val byKey = cards.groupBy { CardNames.key(it.name) }
        val where = { key: String ->
            CollectionComboMath.whereText(byKey[key].orEmpty().map { c ->
                (places[c.boxId]?.let { b -> if (b.deckId != 0L) "Deck: ${b.name}" else b.name } ?: "Unsorted") to c.quantity
            })
        }
        val input = comboList() ?: return@withContext null
        input.bufferedReader().useLines { CollectionComboMath.owned(it, byKey.keys, where) }
    }
}
