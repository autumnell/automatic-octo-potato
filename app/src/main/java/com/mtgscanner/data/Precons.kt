package com.mtgscanner.data

import android.content.Context
import com.mtgscanner.util.CardNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** A preconstructed deck in MTGJSON's list: its set, name, file and kind ("Commander Deck", "Theme Deck"…). */
data class PreconEntry(val code: String, val name: String, val fileName: String, val type: String, val releaseDate: String) {
    val isCommander: Boolean get() = type.equals("Commander Deck", ignoreCase = true)
}

/** One line of a precon: copies of a printing, in its section ("Commander", "Main" or "Sideboard"). */
data class PreconCard(val name: String, val count: Int, val set: String?, val number: String?, val section: String, val foil: Boolean = false)

/** A precon's cards. */
data class PreconDeck(val entry: PreconEntry, val cards: List<PreconCard>) {
    val commanders: List<PreconCard> get() = cards.filter { it.section == "Commander" }
    /** Played copies (commanders and main deck). */
    val total: Int get() = cards.filter { it.section != "Sideboard" }.sumOf { it.count }

    /** The list to save, each printing named: "1 Sol Ring (CMR) 263". */
    fun asText(): String {
        fun line(c: PreconCard) = "${c.count} ${c.name}" + if (c.set != null && c.number != null) " (${c.set.uppercase()}) ${c.number}" else ""
        val out = StringBuilder()
        for (section in listOf("Commander", "Main", "Sideboard")) {
            val ls = cards.filter { it.section == section }
            if (ls.isEmpty()) continue
            if (out.isNotEmpty()) out.append("\n")
            out.append(if (section == "Main") "Deck" else section).append("\n")
            ls.forEach { out.append(line(it)).append("\n") }
        }
        return out.toString().trimEnd()
    }

    /** Copies of played cards you own ([owned] by [CardNames.key]), each owned copy counted once. */
    fun ownedCopies(owned: Map<String, Int>): Int {
        val left = owned.toMutableMap()
        return cards.filter { it.section != "Sideboard" }.sumOf { c ->
            val k = CardNames.key(c.name)
            minOf(c.count, left[k] ?: 0).also { left[k] = (left[k] ?: 0) - it }
        }
    }

    /** Played cards you don't own enough of: name to copies missing. */
    fun missing(owned: Map<String, Int>): List<Pair<String, Int>> {
        val need = cards.filter { it.section != "Sideboard" }.groupBy { CardNames.key(it.name) }
        return need.mapNotNull { (k, ls) ->
            val n = ls.sumOf { it.count } - (owned[k] ?: 0)
            if (n > 0) ls.first().name to n else null
        }.sortedBy { it.first.lowercase() }
    }
}

/** Reading MTGJSON's deck files. Pure, for tests. */
object PreconJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** DeckList.json: every deck, newest first. */
    fun list(text: String): List<PreconEntry> {
        val data = (json.parseToJsonElement(text) as? JsonObject)?.get("data") as? JsonArray ?: return emptyList()
        return data.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k])?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
            PreconEntry(
                code = s("code") ?: return@mapNotNull null, name = s("name") ?: return@mapNotNull null,
                fileName = s("fileName") ?: return@mapNotNull null, type = s("type").orEmpty(), releaseDate = s("releaseDate").orEmpty(),
            )
        }.sortedByDescending { it.releaseDate }
    }

    /** decks/<fileName>.json: the deck's commander, main deck and sideboard. */
    fun deck(entry: PreconEntry, text: String): PreconDeck? {
        val data = (json.parseToJsonElement(text) as? JsonObject)?.get("data") as? JsonObject ?: return null
        val cards = ArrayList<PreconCard>()
        fun read(key: String, section: String) {
            (data[key] as? JsonArray)?.forEach { e -> card(e, section)?.let { cards += it } }
        }
        read("commander", "Commander")
        read("mainBoard", "Main")
        read("sideBoard", "Sideboard")
        // The same printing twice in a section (foil and not) is one line.
        val merged = cards.groupBy { listOf(it.section, it.name, it.set, it.number) }
            .map { (_, ls) -> ls.first().copy(count = ls.sumOf { it.count }) }
        return if (merged.isEmpty()) null else PreconDeck(entry, merged)
    }

    private fun card(e: JsonElement, section: String): PreconCard? {
        val o = e as? JsonObject ?: return null
        fun s(k: String) = o[k]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        // A two-faced card is listed by its full name ("A // B"); the deck list keeps the full name.
        val name = s("name") ?: return null
        val count = o["count"]?.jsonPrimitive?.intOrNull ?: 1
        return PreconCard(name, count, s("setCode"), s("number"), section, o["isFoil"]?.jsonPrimitive?.booleanOrNull == true)
    }
}

/** MTGJSON's preconstructed decks, kept on the phone: the list for a week, each deck once opened. */
object Precons {
    private const val BASE = "https://mtgjson.com/api/v5"
    private const val LIST_DAYS = 7
    private val client by lazy { sharedHttpClient.newBuilder().callTimeout(60, TimeUnit.SECONDS).build() }

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "precons").apply { mkdirs() }

    private fun fetch(url: String): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", "MTGScanner/0.1 (personal collection app)").build()
        client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    /** Every precon, newest first; the saved copy when it's fresh or the site can't be reached. Null with neither. */
    suspend fun list(ctx: Context, fresh: Boolean = false): List<PreconEntry>? = withContext(Dispatchers.IO) {
        val f = File(dir(ctx), "DeckList.json")
        val young = f.exists() && System.currentTimeMillis() - f.lastModified() < LIST_DAYS * 86_400_000L
        val text = if (young && !fresh) f.readText() else fetch("$BASE/DeckList.json")?.also { f.writeText(it) } ?: f.takeIf { it.exists() }?.readText()
        text?.let { runCatching { PreconJson.list(it) }.getOrNull() }
    }

    /** One precon's cards. */
    suspend fun deck(ctx: Context, entry: PreconEntry): PreconDeck? = withContext(Dispatchers.IO) {
        val safe = entry.fileName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val f = File(dir(ctx), "$safe.json")
        val text = if (f.exists()) f.readText() else fetch("$BASE/decks/${entry.fileName}.json")?.also { f.writeText(it) }
        text?.let { runCatching { PreconJson.deck(entry, it) }.getOrNull() }
    }
}
